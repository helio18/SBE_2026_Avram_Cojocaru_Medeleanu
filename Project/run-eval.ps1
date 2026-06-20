$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir "build.ps1"
$projectBin = Join-Path $scriptDir "bin"
$protobufJar = Join-Path $scriptDir "lib\protobuf-java-4.28.3.jar"
$runtimeClasspath = ".\bin;$protobufJar"

function Get-EnvInt {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [int]$DefaultValue
    )

    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $DefaultValue
    }
    return [int]$value
}

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host "== $Message =="
}

function Start-JavaProcess {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [Parameter(Mandatory = $true)]
        [string]$OutDir,
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )

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
    param(
        [double]$Value,
        [int]$Decimals
    )

    return [string]::Format(
        [System.Globalization.CultureInfo]::InvariantCulture,
        "{0:F$Decimals}",
        $Value)
}

function Aggregate-Scenario {
    param(
        [string]$OutDir,
        [string]$ScenarioName,
        [int]$CompanyEquals
    )

    $report = Join-Path $OutDir "report.md"

    $totalNotifications = 0L
    $totalSubscriptions = 0L
    $totalPublications = 0L
    $weightedLatency = 0.0
    $totalForLatency = 0L

    foreach ($statsFile in @(Get-ChildItem -Path $OutDir -Filter "S*.stats" -File -ErrorAction SilentlyContinue)) {
        $stats = Read-StatsMap -Path $statsFile.FullName
        $notifications = [long]$stats["notificationsReceived"]
        $subscriptions = [long]$stats["subscriptionsSent"]
        $latency = [double]$stats["averageLatencyMs"]

        $totalNotifications += $notifications
        $totalSubscriptions += $subscriptions

        if ($notifications -gt 0) {
            $weightedLatency += $notifications * $latency
            $totalForLatency += $notifications
        }
    }

    foreach ($statsFile in @(Get-ChildItem -Path $OutDir -Filter "P*.stats" -File -ErrorAction SilentlyContinue)) {
        $stats = Read-StatsMap -Path $statsFile.FullName
        $totalPublications += [long]$stats["publicationsSent"]
    }

    $averageLatency = 0.0
    if ($totalForLatency -gt 0) {
        $averageLatency = $weightedLatency / $totalForLatency
    }

    $matchRatePercent = 0.0
    if ($totalPublications -gt 0 -and $totalSubscriptions -gt 0) {
        $matchRatePercent = 100.0 * $totalNotifications / ($totalPublications * $totalSubscriptions)
    }

    $lines = @(
        "## Scenariu: $ScenarioName",
        "",
        "- Frecventa operatorului ``=`` pe ``company``: ${CompanyEquals}%",
        "- Subscriptii inregistrate (total pe toti subscriberii): $totalSubscriptions",
        "- Publicatii emise (total pe toti publisherii): $totalPublications",
        "- Notificari primite (total pe toti subscriberii): $totalNotifications",
        "- Latenta medie de livrare: **$(Format-Number -Value $averageLatency -Decimals 3) ms**",
        "- Rata de matching (notificari / (publicatii x subscriptii)): **$(Format-Number -Value $matchRatePercent -Decimals 4)%**"
    )

    Set-Content -Path $report -Value $lines
    Get-Content $report
}

$durationSeconds = Get-EnvInt -Name "DURATION_SECONDS" -DefaultValue 180
$totalSubscriptions = Get-EnvInt -Name "TOTAL_SUBSCRIPTIONS" -DefaultValue 10000
$publicationRate = Get-EnvInt -Name "PUBLICATION_RATE" -DefaultValue 50
$publisherCount = Get-EnvInt -Name "PUBLISHER_COUNT" -DefaultValue 1
$subscriberCount = Get-EnvInt -Name "SUBSCRIBER_COUNT" -DefaultValue 3
$subSendGraceSeconds = Get-EnvInt -Name "SUB_SEND_GRACE_SECONDS" -DefaultValue 8
$drainGraceSeconds = Get-EnvInt -Name "DRAIN_GRACE_SECONDS" -DefaultValue 5

Write-Step "Compiling sources"
& $buildScript

New-Item -ItemType Directory -Force -Path (Join-Path $scriptDir "output") | Out-Null
$outputRoot = Join-Path $scriptDir ("output\eval." + [System.Guid]::NewGuid().ToString("N").Substring(0, 6))
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
Write-Host "Output root: $outputRoot"

$allProcesses = @()

function Run-Scenario {
    param(
        [string]$ScenarioName,
        [int]$CompanyEquals
    )

    $scenarioProcesses = @()
    $outDir = Join-Path $outputRoot $ScenarioName
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    $stopFile = Join-Path $outDir "STOP"
    if (Test-Path $stopFile) {
        Remove-Item -Path $stopFile -Force
    }

    Write-Step "Scenario $ScenarioName (company-equals=$CompanyEquals%)"

    # Subscriberii si peering-ul intre brokeri folosesc porturile text (5001-3).
    # Publisher-ul foloseste pub-port-urile binare (7001-3) pentru protobuf.
    $brokerListText = "B1@localhost:5001,B2@localhost:5002,B3@localhost:5003"
    $brokerListPub = "B1@localhost:7001,B2@localhost:7002,B3@localhost:7003"

    $brokerProcesses = @(
        (Start-JavaProcess -Name "B1" -OutDir $outDir -Arguments @(
                "-cp", $runtimeClasspath,
                "project.broker.BrokerMain",
                "--id=B1",
                "--port=5001",
                "--pub-port=7001",
                "--peers=B2@localhost:5002,B3@localhost:5003",
                "--stop-file=$stopFile",
                "--stats-file=$(Join-Path $outDir 'B1.stats')")),
        (Start-JavaProcess -Name "B2" -OutDir $outDir -Arguments @(
                "-cp", $runtimeClasspath,
                "project.broker.BrokerMain",
                "--id=B2",
                "--port=5002",
                "--pub-port=7002",
                "--peers=B1@localhost:5001,B3@localhost:5003",
                "--stop-file=$stopFile",
                "--stats-file=$(Join-Path $outDir 'B2.stats')")),
        (Start-JavaProcess -Name "B3" -OutDir $outDir -Arguments @(
                "-cp", $runtimeClasspath,
                "project.broker.BrokerMain",
                "--id=B3",
                "--port=5003",
                "--pub-port=7003",
                "--peers=B1@localhost:5001,B2@localhost:5002",
                "--stop-file=$stopFile",
                "--stats-file=$(Join-Path $outDir 'B3.stats')"))
    )

    $scenarioProcesses += $brokerProcesses
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
            "-cp", $runtimeClasspath,
            "project.subscriber.SubscriberMain",
            "--id=$subId",
            "--listen-port=$listenPort",
            "--brokers=$brokerListText",
            "--subscriptions=$subsForThis",
            "--company-frequency=100",
            "--value-frequency=0",
            "--drop-frequency=0",
            "--variation-frequency=0",
            "--date-frequency=0",
            "--company-equals=$CompanyEquals",
            "--seed=$seed",
            "--threads=2",
            "--sub-id-prefix=$subId",
            "--stop-file=$stopFile",
            "--stats-file=$(Join-Path $outDir ($subId + '.stats'))")
    }

    $scenarioProcesses += $subscriberProcesses
    Start-Sleep -Seconds $subSendGraceSeconds

    $publisherProcesses = @()
    for ($index = 1; $index -le $publisherCount; $index++) {
        $pubId = "P$index"
        $seed = 200000 + $index
        $publicationsTotal = $publicationRate * $durationSeconds + 1000

        $publisherProcesses += Start-JavaProcess -Name $pubId -OutDir $outDir -Arguments @(
            "-cp", $runtimeClasspath,
            "project.publisher.PublisherMain",
            "--id=$pubId",
            "--brokers=$brokerListPub",
            "--publications=$publicationsTotal",
            "--rate=$publicationRate",
            "--duration-seconds=$durationSeconds",
            "--seed=$seed",
            "--threads=2",
            "--pub-id-prefix=$pubId",
            "--stats-file=$(Join-Path $outDir ($pubId + '.stats'))")
    }

    $scenarioProcesses += $publisherProcesses
    $script:allProcesses = $scenarioProcesses

    try {
        Write-Host "[$ScenarioName] publishers running for ${durationSeconds}s..."
        Wait-ForProcesses -Processes $publisherProcesses
        Write-Host "[$ScenarioName] publishers finished, draining ${drainGraceSeconds}s..."

        Start-Sleep -Seconds $drainGraceSeconds
        Set-Content -Path $stopFile -Value ""

        Wait-ForProcesses -Processes $subscriberProcesses
        Wait-ForProcesses -Processes $brokerProcesses
    } finally {
        Stop-All -Processes $scenarioProcesses
        $script:allProcesses = @()
    }

    Aggregate-Scenario -OutDir $outDir -ScenarioName $ScenarioName -CompanyEquals $CompanyEquals
}

try {
    Run-Scenario -ScenarioName "scenario-A-eq-100" -CompanyEquals 100
    Run-Scenario -ScenarioName "scenario-B-eq-25" -CompanyEquals 25

    Write-Step "Final report"

    $processorInfo = ""
    try {
        $processorInfo = (Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name).Trim()
    } catch {
    }
    if ([string]::IsNullOrWhiteSpace($processorInfo)) {
        $processorInfo = [Environment]::GetEnvironmentVariable("PROCESSOR_IDENTIFIER")
    }
    if ([string]::IsNullOrWhiteSpace($processorInfo)) {
        $processorInfo = "unknown"
    }

    $javaVersion = (cmd /c "java -version 2>&1" | Select-Object -First 1).ToString()
    $finalReport = Join-Path $outputRoot "final-report.md"

    $finalLines = @(
        "# Raport evaluare proiect SBE 2026",
        "",
        "## Configuratie",
        "",
        "- Durata feed publicatii per scenariu: ``$durationSeconds`` secunde",
        "- Subscriptii totale per scenariu: ``$totalSubscriptions``",
        "- Subscriberi: ``$subscriberCount`` (balansare round-robin pe brokeri)",
        "- Publisheri: ``$publisherCount`` (rata ``$publicationRate`` pub/s)",
        "- Brokeri: 3 in topologie triunghi B1-B2-B3-B1",
        "- Procesor: ``$processorInfo``",
        "- Java: ``$javaVersion``",
        ""
    )
    $finalLines += Get-Content (Join-Path $outputRoot "scenario-A-eq-100\report.md")
    $finalLines += ""
    $finalLines += Get-Content (Join-Path $outputRoot "scenario-B-eq-25\report.md")

    Set-Content -Path $finalReport -Value $finalLines
    Get-Content $finalReport

    Write-Host ""
    Write-Host "Artifacts kept in: $outputRoot"
} finally {
    Stop-All -Processes $allProcesses
}
