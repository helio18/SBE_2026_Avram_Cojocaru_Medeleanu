$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir "build.ps1"
$protobufJar = Join-Path $scriptDir "lib\protobuf-java-4.28.3.jar"
$runtimeClasspath = ".\bin;$protobufJar"

function Get-EnvInt {
    param([string]$Name, [int]$DefaultValue)
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $DefaultValue
    }
    return [int]$value
}

function Start-JavaProcess {
    param([string]$Name, [string]$OutDir, [string[]]$Arguments)
    $stdoutLog = Join-Path $OutDir ($Name + ".stdout.log")
    $stderrLog = Join-Path $OutDir ($Name + ".stderr.log")
    return Start-Job -Name $Name -WarningAction SilentlyContinue -ArgumentList $scriptDir, $stdoutLog, $stderrLog, $Arguments -ScriptBlock {
        param($workingDir, $stdoutPath, $stderrPath, $javaArguments)
        Set-Location $workingDir
        & java @javaArguments 1> $stdoutPath 2> $stderrPath
        if ($LASTEXITCODE -ne 0) {
            throw "java exited with code $LASTEXITCODE"
        }
    }
}

function Wait-ForProcesses {
    param($Processes)
    $jobs = @($Processes)
    if ($null -eq $Processes -or $jobs.Count -eq 0) {
        return
    }
    Wait-Job -Id ($jobs | ForEach-Object { $_.Id }) -WarningAction SilentlyContinue | Out-Null
    foreach ($job in $jobs) {
        if ($job.State -eq "Failed") {
            $message = (Receive-Job -Id $job.Id -Keep -WarningAction SilentlyContinue 2>$null | Out-String).Trim()
            if ([string]::IsNullOrWhiteSpace($message)) {
                $message = "$($job.Name) failed"
            }
            throw $message
        }
    }
}

function Stop-All {
    param($Processes)
    foreach ($job in @($Processes)) {
        if ($null -eq $job) {
            continue
        }
        try {
            if ($job.State -eq "Running") {
                Stop-Job -Id $job.Id -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
            }
        } catch {
        }
        try {
            Remove-Job -Id $job.Id -Force -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        } catch {
        }
    }
}

function Read-StatsMap {
    param([string]$Path)
    $map = @{}
    foreach ($line in Get-Content $Path) {
        if ($line -match '^(.*?)=(.*)$') {
            $map[$matches[1]] = $matches[2]
        }
    }
    return $map
}

function Format-Number {
    param([double]$Value, [int]$Decimals)
    return [string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0:F$Decimals}", $Value)
}

$durationSeconds = Get-EnvInt -Name "DURATION_SECONDS" -DefaultValue 60
$totalSubscriptions = Get-EnvInt -Name "TOTAL_SUBSCRIPTIONS" -DefaultValue 10000
$publicationRate = Get-EnvInt -Name "PUBLICATION_RATE" -DefaultValue 50
$subscriberCount = Get-EnvInt -Name "SUBSCRIBER_COUNT" -DefaultValue 3
$subSendGraceSeconds = Get-EnvInt -Name "SUB_SEND_GRACE_SECONDS" -DefaultValue 8
$drainGraceSeconds = Get-EnvInt -Name "DRAIN_GRACE_SECONDS" -DefaultValue 5

Write-Host "== Compiling sources =="
& $buildScript

New-Item -ItemType Directory -Force -Path (Join-Path $scriptDir "output") | Out-Null
$outputRoot = Join-Path $scriptDir ("output\compare." + [System.Guid]::NewGuid().ToString("N").Substring(0, 6))
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
Write-Host "Output root: $outputRoot"

$brokerListText = "B1@localhost:5001,B2@localhost:5002,B3@localhost:5003"
$brokerListPub = "B1@localhost:7001,B2@localhost:7002,B3@localhost:7003"

function Run-Transport {
    param([string]$Transport)

    $outDir = Join-Path $outputRoot $Transport
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $stopFile = Join-Path $outDir "STOP"
    if (Test-Path $stopFile) {
        Remove-Item -Path $stopFile -Force
    }

    Write-Host ""
    Write-Host "== Transport: $Transport (company-equals=100, rate=$publicationRate, ${durationSeconds}s) =="

    $brokerProcesses = @(
        (Start-JavaProcess -Name "B1" -OutDir $outDir -Arguments @(
                "-cp", $runtimeClasspath, "project.broker.BrokerMain",
                "--id=B1", "--port=5001", "--pub-port=7001",
                "--peers=B2@localhost:5002,B3@localhost:5003",
                "--stop-file=$stopFile", "--stats-file=$(Join-Path $outDir 'B1.stats')")),
        (Start-JavaProcess -Name "B2" -OutDir $outDir -Arguments @(
                "-cp", $runtimeClasspath, "project.broker.BrokerMain",
                "--id=B2", "--port=5002", "--pub-port=7002",
                "--peers=B1@localhost:5001,B3@localhost:5003",
                "--stop-file=$stopFile", "--stats-file=$(Join-Path $outDir 'B2.stats')")),
        (Start-JavaProcess -Name "B3" -OutDir $outDir -Arguments @(
                "-cp", $runtimeClasspath, "project.broker.BrokerMain",
                "--id=B3", "--port=5003", "--pub-port=7003",
                "--peers=B1@localhost:5001,B2@localhost:5002",
                "--stop-file=$stopFile", "--stats-file=$(Join-Path $outDir 'B3.stats')"))
    )
    Start-Sleep -Seconds 2

    $subscriptionsPerSubscriber = [int]($totalSubscriptions / $subscriberCount)
    $remainder = $totalSubscriptions % $subscriberCount

    $subscriberProcesses = @()
    for ($index = 1; $index -le $subscriberCount; $index++) {
        $subId = "S$index"
        $listenPort = 6000 + $index
        $seed = 100000 + $index
        $subsForThis = $subscriptionsPerSubscriber
        if ($index -le $remainder) {
            $subsForThis++
        }
        $subscriberProcesses += Start-JavaProcess -Name $subId -OutDir $outDir -Arguments @(
            "-cp", $runtimeClasspath, "project.subscriber.SubscriberMain",
            "--id=$subId", "--listen-port=$listenPort", "--brokers=$brokerListText",
            "--subscriptions=$subsForThis", "--company-frequency=100",
            "--value-frequency=0", "--drop-frequency=0", "--variation-frequency=0", "--date-frequency=0",
            "--company-equals=100", "--seed=$seed", "--threads=2", "--sub-id-prefix=$subId",
            "--stop-file=$stopFile", "--stats-file=$(Join-Path $outDir ($subId + '.stats'))")
    }
    Start-Sleep -Seconds $subSendGraceSeconds

    if ($Transport -eq "text") {
        $publisherBrokers = $brokerListText
    } else {
        $publisherBrokers = $brokerListPub
    }
    $publicationsTotal = $publicationRate * $durationSeconds + 1000
    $publisher = Start-JavaProcess -Name "P1" -OutDir $outDir -Arguments @(
        "-cp", $runtimeClasspath, "project.publisher.PublisherMain",
        "--id=P1", "--brokers=$publisherBrokers", "--transport=$Transport",
        "--publications=$publicationsTotal", "--rate=$publicationRate", "--duration-seconds=$durationSeconds",
        "--seed=200001", "--threads=2", "--pub-id-prefix=P1",
        "--stats-file=$(Join-Path $outDir 'P1.stats')")

    $allJobs = $brokerProcesses + $subscriberProcesses + @($publisher)
    try {
        Wait-ForProcesses -Processes @($publisher)
        Start-Sleep -Seconds $drainGraceSeconds
        Set-Content -Path $stopFile -Value ""
        Wait-ForProcesses -Processes $subscriberProcesses
        Wait-ForProcesses -Processes $brokerProcesses
    } finally {
        Stop-All -Processes $allJobs
    }

    $pubStats = Read-StatsMap -Path (Join-Path $outDir "P1.stats")
    $totalNotifications = 0L
    $weightedLatency = 0.0
    foreach ($statsFile in @(Get-ChildItem -Path $outDir -Filter "S*.stats" -File)) {
        $stats = Read-StatsMap -Path $statsFile.FullName
        $n = [long]$stats["notificationsReceived"]
        $totalNotifications += $n
        if ($n -gt 0) {
            $weightedLatency += $n * [double]$stats["averageLatencyMs"]
        }
    }
    $avgLatency = 0.0
    if ($totalNotifications -gt 0) {
        $avgLatency = $weightedLatency / $totalNotifications
    }

    Start-Sleep -Seconds 2

    return [PSCustomObject]@{
        Transport = $Transport
        PublicationsSent = [long]$pubStats["publicationsSent"]
        BytesSent = [long]$pubStats["bytesSent"]
        AvgBytesPerPublication = [double]$pubStats["avgBytesPerPublication"]
        NotificationsDelivered = $totalNotifications
        AvgLatencyMs = $avgLatency
    }
}

$textResult = Run-Transport -Transport "text"
$protobufResult = Run-Transport -Transport "protobuf"

$bytesText = $textResult.AvgBytesPerPublication
$bytesProto = $protobufResult.AvgBytesPerPublication
$reduction = 0.0
if ($bytesText -gt 0) {
    $reduction = 100.0 * ($bytesText - $bytesProto) / $bytesText
}

$report = Join-Path $outputRoot "compare-report.md"
$lines = @(
    "# Comparatie transport publicatii: text (default) vs protobuf (bonus)",
    "",
    "Acelasi scenariu (company-equals=100, nesaturat) rulat cu ambele mecanisme de serializare pentru publicatiile publisher -> broker.",
    "",
    "- Durata feed: ``$durationSeconds`` s, rata ``$publicationRate`` pub/s, ``$totalSubscriptions`` subscriptii, ``$subscriberCount`` subscriberi, 3 brokeri.",
    "",
    "| Metrica | Text (fara bonus) | Protobuf (bonus) |",
    "| --- | --- | --- |",
    "| Publicatii emise | $($textResult.PublicationsSent) | $($protobufResult.PublicationsSent) |",
    "| Octeti / publicatie (medie) | $(Format-Number -Value $bytesText -Decimals 2) B | $(Format-Number -Value $bytesProto -Decimals 2) B |",
    "| Total octeti emisi | $($textResult.BytesSent) | $($protobufResult.BytesSent) |",
    "| Notificari livrate | $($textResult.NotificationsDelivered) | $($protobufResult.NotificationsDelivered) |",
    "| Latenta medie livrare | $(Format-Number -Value $textResult.AvgLatencyMs -Decimals 3) ms | $(Format-Number -Value $protobufResult.AvgLatencyMs -Decimals 3) ms |",
    "",
    "Reducere dimensiune payload prin protobuf: **$(Format-Number -Value $reduction -Decimals 1)%** per publicatie."
)
Set-Content -Path $report -Value $lines
Write-Host ""
Get-Content $report
Write-Host ""
Write-Host "Artifacts kept in: $outputRoot"
