$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($env:OS -eq "Windows_NT") {
    $existingPath = [Environment]::GetEnvironmentVariable("Path", "Process")
    if ([string]::IsNullOrEmpty($existingPath)) {
        $existingPath = [Environment]::GetEnvironmentVariable("PATH", "Process")
    }
    [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
    [Environment]::SetEnvironmentVariable("Path", $existingPath, "Process")
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$buildScript = Join-Path $scriptDir "build.ps1"
$protobufJar = Join-Path $scriptDir "lib\protobuf-java-4.28.3.jar"
$runtimeClasspath = ".\bin;$protobufJar"
$cryptoKey = "super-secret-passphrase"

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

$durationSeconds = Get-EnvInt -Name "DURATION_SECONDS" -DefaultValue 15
$totalSubscriptions = Get-EnvInt -Name "TOTAL_SUBSCRIPTIONS" -DefaultValue 300
$publicationRate = Get-EnvInt -Name "PUBLICATION_RATE" -DefaultValue 20
$subscriberCount = Get-EnvInt -Name "SUBSCRIBER_COUNT" -DefaultValue 3
$subSendGraceSeconds = Get-EnvInt -Name "SUB_SEND_GRACE_SECONDS" -DefaultValue 4
$drainGraceSeconds = Get-EnvInt -Name "DRAIN_GRACE_SECONDS" -DefaultValue 4

Write-Host "== Compiling sources =="
& $buildScript

New-Item -ItemType Directory -Force -Path (Join-Path $scriptDir "output") | Out-Null
$outputRoot = Join-Path $scriptDir ("output\crypto." + [System.Guid]::NewGuid().ToString("N").Substring(0, 6))
New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null
Write-Host "Output root: $outputRoot"

$brokerListText = "B1@localhost:5001,B2@localhost:5002,B3@localhost:5003"
$brokerListPub = "B1@localhost:7001,B2@localhost:7002,B3@localhost:7003"

function Run-Mode {
    param([bool]$Encrypt)

    $label = "plaintext"
    if ($Encrypt) {
        $label = "encrypted"
    }
    $outDir = Join-Path $outputRoot $label
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $stopFile = Join-Path $outDir "STOP"
    if (Test-Path $stopFile) {
        Remove-Item -Path $stopFile -Force
    }

    Write-Host ""
    Write-Host "== Mode: $label (brokerul NU primeste cheia) =="

    $brokers = @()
    $brokerConfigs = @(
        @{ Id = "B1"; Port = 5001; PubPort = 7001; Peers = "B2@localhost:5002,B3@localhost:5003" },
        @{ Id = "B2"; Port = 5002; PubPort = 7002; Peers = "B1@localhost:5001,B3@localhost:5003" },
        @{ Id = "B3"; Port = 5003; PubPort = 7003; Peers = "B1@localhost:5001,B2@localhost:5002" }
    )
    foreach ($bc in $brokerConfigs) {
        $brokers += Start-Java -Name $bc.Id -OutDir $outDir -Arguments @(
            "-cp", $runtimeClasspath, "project.broker.BrokerMain",
            "--id=$($bc.Id)", "--port=$($bc.Port)", "--pub-port=$($bc.PubPort)",
            "--peers=$($bc.Peers)", "--stop-file=$stopFile",
            "--stats-file=$(Join-Path $outDir ($bc.Id + '.stats'))",
            "--dump-store=$(Join-Path $outDir ($bc.Id + '.store'))")
    }
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
        $subArgs = @(
            "-cp", $runtimeClasspath, "project.subscriber.SubscriberMain",
            "--id=$subId", "--listen-port=$listenPort", "--brokers=$brokerListText",
            "--subscriptions=$subsForThis", "--company-frequency=100",
            "--value-frequency=0", "--drop-frequency=0", "--variation-frequency=0", "--date-frequency=0",
            "--company-equals=100", "--seed=$seed", "--threads=2", "--sub-id-prefix=$subId",
            "--stop-file=$stopFile", "--stats-file=$(Join-Path $outDir ($subId + '.stats'))")
        if ($Encrypt) {
            $subArgs += @("--encrypt=true", "--crypto-key=$cryptoKey")
        }
        $subscribers += Start-Java -Name $subId -OutDir $outDir -Arguments $subArgs
    }
    Start-Sleep -Seconds $subSendGraceSeconds

    $publicationsTotal = $publicationRate * $durationSeconds + 1000
    $pubArgs = @(
        "-cp", $runtimeClasspath, "project.publisher.PublisherMain",
        "--id=P1", "--brokers=$brokerListPub", "--transport=protobuf",
        "--publications=$publicationsTotal", "--rate=$publicationRate",
        "--duration-seconds=$durationSeconds", "--seed=200001", "--threads=2", "--pub-id-prefix=P1",
        "--stats-file=$(Join-Path $outDir 'P1.stats')")
    if ($Encrypt) {
        $pubArgs += @("--encrypt=true", "--crypto-key=$cryptoKey")
    }
    $publisher = Start-Java -Name "P1" -OutDir $outDir -Arguments $pubArgs

    $publisher.WaitForExit()
    Start-Sleep -Seconds $drainGraceSeconds
    Set-Content -Path $stopFile -Value ""

    foreach ($s in $subscribers) {
        try { $s.WaitForExit(5000) | Out-Null } catch { }
    }
    foreach ($b in $brokers) {
        try { $b.WaitForExit(5000) | Out-Null } catch { }
    }
    foreach ($p in ($brokers + $subscribers + @($publisher))) {
        Stop-Quietly -Process $p
    }
    Start-Sleep -Seconds 3

    $totalNotifications = 0L
    foreach ($statsFile in @(Get-ChildItem -Path $outDir -Filter "S*.stats" -File)) {
        $st = Read-StatsMap -Path $statsFile.FullName
        $totalNotifications += [long]$st["notificationsReceived"]
    }

    return [PSCustomObject]@{
        Label = $label
        Notifications = $totalNotifications
        StoreFile = (Join-Path $outDir "B1.store")
    }
}

$plain = Run-Mode -Encrypt $false
$enc = Run-Mode -Encrypt $true

Write-Host ""
Write-Host "================ REZULTAT ================"
Write-Host ("Notificari livrate  plaintext : {0}" -f $plain.Notifications)
Write-Host ("Notificari livrate  criptat   : {0}" -f $enc.Notifications)
$identical = ($plain.Notifications -eq $enc.Notifications)
Write-Host ("Rezultat matching identic     : {0}" -f $identical)

Write-Host ""
Write-Host "Ce stocheaza brokerul B1 (primele 3 subscriptii) -- PLAINTEXT (lizibil):"
if (Test-Path $plain.StoreFile) {
    Get-Content $plain.StoreFile -TotalCount 3 | ForEach-Object { Write-Host "  $_" }
}
Write-Host ""
Write-Host "Ce stocheaza brokerul B1 (primele 3 subscriptii) -- CRIPTAT (doar token-uri):"
if (Test-Path $enc.StoreFile) {
    Get-Content $enc.StoreFile -TotalCount 3 | ForEach-Object { Write-Host "  $_" }
}

$report = Join-Path $outputRoot "crypto-report.md"
$lines = @(
    "# Demonstratie matching pe continut criptat",
    "",
    "Acelasi scenariu rulat in clar si criptat (publisher+subscriber au cheia ``$cryptoKey``; brokerii NU primesc cheia). $totalSubscriptions subscriptii, publisher la $publicationRate pub/s, feed $durationSeconds s.",
    "",
    "| Mod | Notificari livrate |",
    "| --- | --- |",
    "| Plaintext | $($plain.Notifications) |",
    "| Criptat | $($enc.Notifications) |",
    "",
    "Rezultat matching identic: **$identical** (criptarea pastreaza semantica de matching).",
    "",
    "Continutul stocat de brokerul B1, plaintext (lizibil):",
    "",
    '```'
)
if (Test-Path $plain.StoreFile) {
    $lines += (Get-Content $plain.StoreFile -TotalCount 3)
}
$lines += '```'
$lines += ""
$lines += "Continutul stocat de brokerul B1, criptat (doar token-uri, fara plaintext):"
$lines += ""
$lines += '```'
if (Test-Path $enc.StoreFile) {
    $lines += (Get-Content $enc.StoreFile -TotalCount 3)
}
$lines += '```'
Set-Content -Path $report -Value $lines

Write-Host ""
Write-Host "Artifacts kept in: $outputRoot"
