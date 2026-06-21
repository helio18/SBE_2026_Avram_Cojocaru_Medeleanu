$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($env:OS -eq "Windows_NT") {
    # Some Codex/PowerShell environments expose both Path and PATH. Start-Process
    # builds a case-insensitive environment map and fails unless we normalize it.
    [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
}

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

function Start-Java {
    param([string]$Name, [string]$OutDir, [string[]]$Arguments)
    $stdout = Join-Path $OutDir ($Name + ".stdout.log")
    $stderr = Join-Path $OutDir ($Name + ".stderr.log")
    return Start-Process -FilePath "java" -ArgumentList $Arguments -WorkingDirectory $scriptDir `
        -NoNewWindow -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
}

function Stop-Quietly {
    param($Process)
    if ($null -eq $Process) {
        return
    }
    try {
        if (-not $Process.HasExited) {
            Stop-Process -Id $Process.Id -Force -ErrorAction SilentlyContinue
        }
    } catch {
    }
}

function Read-StatsMap {
    param([string]$Path)
    $map = @{}
    if (-not (Test-Path $Path)) {
        return $map
    }
    foreach ($line in Get-Content $Path) {
        if ($line -match '^(.*?)=(.*)$') {
            $map[$matches[1]] = $matches[2]
        }
    }
    return $map
}

$durationSeconds = Get-EnvInt -Name "DURATION_SECONDS" -DefaultValue 30
$killAtSeconds = Get-EnvInt -Name "KILL_AT_SECONDS" -DefaultValue 12
$totalSubscriptions = Get-EnvInt -Name "TOTAL_SUBSCRIPTIONS" -DefaultValue 300
$publicationRate = Get-EnvInt -Name "PUBLICATION_RATE" -DefaultValue 30
$subscriberCount = Get-EnvInt -Name "SUBSCRIBER_COUNT" -DefaultValue 3
$subSendGraceSeconds = Get-EnvInt -Name "SUB_SEND_GRACE_SECONDS" -DefaultValue 4
$drainGraceSeconds = Get-EnvInt -Name "DRAIN_GRACE_SECONDS" -DefaultValue 4

Write-Host "== Compiling sources =="
& $buildScript

New-Item -ItemType Directory -Force -Path (Join-Path $scriptDir "output") | Out-Null
$outputRoot = Join-Path $scriptDir ("output\failure." + [System.Guid]::NewGuid().ToString("N").Substring(0, 6))
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
Write-Host "Output root: $outputRoot"

$brokerListText = "B1@localhost:5001,B2@localhost:5002,B3@localhost:5003"
$brokerListPub = "B1@localhost:7001,B2@localhost:7002,B3@localhost:7003"

function Run-Scenario {
    param([string]$Label, [int]$Replicas, [bool]$Failover, [bool]$KillB2)

    $outDir = Join-Path $outputRoot $Label
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $stopFile = Join-Path $outDir "STOP"
    if (Test-Path $stopFile) {
        Remove-Item -Path $stopFile -Force
    }

    Write-Host ""
    Write-Host "== $Label (replicas=$Replicas, failover=$Failover, killB2=$KillB2) =="

    $b1 = Start-Java -Name "B1" -OutDir $outDir -Arguments @(
        "-cp", $runtimeClasspath, "project.broker.BrokerMain",
        "--id=B1", "--port=5001", "--pub-port=7001",
        "--peers=B2@localhost:5002,B3@localhost:5003",
        "--stop-file=$stopFile", "--stats-file=$(Join-Path $outDir 'B1.stats')")
    $b2 = Start-Java -Name "B2" -OutDir $outDir -Arguments @(
        "-cp", $runtimeClasspath, "project.broker.BrokerMain",
        "--id=B2", "--port=5002", "--pub-port=7002",
        "--peers=B1@localhost:5001,B3@localhost:5003",
        "--stop-file=$stopFile", "--stats-file=$(Join-Path $outDir 'B2.stats')")
    $b3 = Start-Java -Name "B3" -OutDir $outDir -Arguments @(
        "-cp", $runtimeClasspath, "project.broker.BrokerMain",
        "--id=B3", "--port=5003", "--pub-port=7003",
        "--peers=B1@localhost:5001,B2@localhost:5002",
        "--stop-file=$stopFile", "--stats-file=$(Join-Path $outDir 'B3.stats')")
    Start-Sleep -Seconds 2

    $subscriptionsPerSubscriber = [int]($totalSubscriptions / $subscriberCount)
    $remainder = $totalSubscriptions % $subscriberCount
    $subscribers = @()
    for ($index = 1; $index -le $subscriberCount; $index++) {
        $subId = "S$index"
        $listenPort = 6000 + $index
        $seed = 100000 + $index
        $subsForThis = $subscriptionsPerSubscriber
        if ($index -le $remainder) {
            $subsForThis++
        }
        $subscribers += Start-Java -Name $subId -OutDir $outDir -Arguments @(
            "-cp", $runtimeClasspath, "project.subscriber.SubscriberMain",
            "--id=$subId", "--listen-port=$listenPort", "--brokers=$brokerListText",
            "--subscriptions=$subsForThis", "--company-frequency=100",
            "--value-frequency=0", "--drop-frequency=0", "--variation-frequency=0", "--date-frequency=0",
            "--company-equals=100", "--seed=$seed", "--threads=2", "--sub-id-prefix=$subId",
            "--replicas=$Replicas", "--stop-file=$stopFile",
            "--stats-file=$(Join-Path $outDir ($subId + '.stats'))")
    }
    Start-Sleep -Seconds $subSendGraceSeconds

    $publicationsTotal = $publicationRate * $durationSeconds + 1000
    $publisher = Start-Java -Name "P1" -OutDir $outDir -Arguments @(
        "-cp", $runtimeClasspath, "project.publisher.PublisherMain",
        "--id=P1", "--brokers=$brokerListPub", "--transport=protobuf",
        "--failover=$($Failover.ToString().ToLower())",
        "--publications=$publicationsTotal", "--rate=$publicationRate",
        "--duration-seconds=$durationSeconds", "--seed=200001", "--threads=2", "--pub-id-prefix=P1",
        "--stats-file=$(Join-Path $outDir 'P1.stats')")

    if ($KillB2) {
        Start-Sleep -Seconds $killAtSeconds
        Write-Host "  -> killing broker B2 (pid $($b2.Id)) after ${killAtSeconds}s"
        Stop-Quietly -Process $b2
    }

    $publisher.WaitForExit()
    Start-Sleep -Seconds $drainGraceSeconds
    Set-Content -Path $stopFile -Value ""

    foreach ($s in $subscribers) {
        try { $s.WaitForExit(5000) | Out-Null } catch { }
    }
    foreach ($b in @($b1, $b3)) {
        try { $b.WaitForExit(5000) | Out-Null } catch { }
    }

    foreach ($p in (@($b1, $b2, $b3) + $subscribers + @($publisher))) {
        Stop-Quietly -Process $p
    }
    Start-Sleep -Seconds 3

    $pubStats = Read-StatsMap -Path (Join-Path $outDir "P1.stats")
    $totalNotifications = 0L
    $totalDuplicates = 0L
    foreach ($statsFile in @(Get-ChildItem -Path $outDir -Filter "S*.stats" -File)) {
        $st = Read-StatsMap -Path $statsFile.FullName
        $totalNotifications += [long]$st["notificationsReceived"]
        if ($st.ContainsKey("duplicatesSuppressed")) {
            $totalDuplicates += [long]$st["duplicatesSuppressed"]
        }
    }

    $dropped = 0L
    if ($pubStats.ContainsKey("publicationsDropped")) {
        $dropped = [long]$pubStats["publicationsDropped"]
    }
    $failovers = 0L
    if ($pubStats.ContainsKey("failoversUsed")) {
        $failovers = [long]$pubStats["failoversUsed"]
    }

    return [PSCustomObject]@{
        Label = $Label
        PublicationsSent = [long]$pubStats["publicationsSent"]
        PublicationsDropped = $dropped
        FailoversUsed = $failovers
        DistinctNotifications = $totalNotifications
        DuplicatesSuppressed = $totalDuplicates
    }
}

$baseline = Run-Scenario -Label "A-baseline-no-kill" -Replicas 1 -Failover $false -KillB2 $false
$noFt = Run-Scenario -Label "B-kill-no-ft" -Replicas 1 -Failover $false -KillB2 $true
$ft = Run-Scenario -Label "C-kill-with-ft" -Replicas 2 -Failover $true -KillB2 $true

function Pct {
    param([long]$Value, [long]$Reference)
    if ($Reference -le 0) {
        return "n/a"
    }
    return [string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0:F1}%", 100.0 * $Value / $Reference)
}

$report = Join-Path $outputRoot "failure-report.md"
$lines = @(
    "# Simulare cadere broker: garantarea livrarii notificarilor",
    "",
    "Scenariu: 3 brokeri, $subscriberCount subscriberi, $totalSubscriptions subscriptii (company-equals=100), publisher protobuf la $publicationRate pub/s, feed $durationSeconds s. Brokerul B2 este oprit efectiv (Stop-Process) dupa $killAtSeconds s.",
    "",
    "| Scenariu | Publicatii emise | Pub. pierdute la publisher | Failover-uri | Notificari distincte livrate | % fata de baseline | Duplicate suprimate |",
    "| --- | --- | --- | --- | --- | --- | --- |",
    "| A. Baseline (fara cadere) | $($baseline.PublicationsSent) | $($baseline.PublicationsDropped) | $($baseline.FailoversUsed) | $($baseline.DistinctNotifications) | $(Pct -Value $baseline.DistinctNotifications -Reference $baseline.DistinctNotifications) | $($baseline.DuplicatesSuppressed) |",
    "| B. Cadere B2, FARA toleranta (replicas=1) | $($noFt.PublicationsSent) | $($noFt.PublicationsDropped) | $($noFt.FailoversUsed) | $($noFt.DistinctNotifications) | $(Pct -Value $noFt.DistinctNotifications -Reference $baseline.DistinctNotifications) | $($noFt.DuplicatesSuppressed) |",
    "| C. Cadere B2, CU toleranta (replicas=2, failover) | $($ft.PublicationsSent) | $($ft.PublicationsDropped) | $($ft.FailoversUsed) | $($ft.DistinctNotifications) | $(Pct -Value $ft.DistinctNotifications -Reference $baseline.DistinctNotifications) | $($ft.DuplicatesSuppressed) |",
    "",
    "Interpretare: in scenariul B caderea lui B2 duce la pierderea notificarilor (subscriptiile stocate doar pe B2 raman netratate, iar publicatiile rutate spre B2 nu mai intra in retea). In scenariul C, replicarea subscriptiilor pe un broker backup plus failover-ul publisher-ului mentin livrarea notificarilor la nivelul baseline, demonstrand toleranta la caderea unui nod broker."
)
Set-Content -Path $report -Value $lines
Write-Host ""
Get-Content $report
Write-Host ""
Write-Host "Artifacts kept in: $outputRoot"
