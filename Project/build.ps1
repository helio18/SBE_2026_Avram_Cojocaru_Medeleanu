$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$homeworkDir = Join-Path $repoRoot "Homework"

$homeworkBin = Join-Path $homeworkDir "bin"
$projectBin = Join-Path $scriptDir "bin"

New-Item -ItemType Directory -Force -Path $homeworkBin | Out-Null
New-Item -ItemType Directory -Force -Path $projectBin | Out-Null

$homeworkSources = @(Get-ChildItem -Path (Join-Path $homeworkDir "src") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })
$projectSources = @(Get-ChildItem -Path (Join-Path $scriptDir "src") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })

if ($homeworkSources.Count -eq 0) {
    throw "No Homework Java sources found."
}
if ($projectSources.Count -eq 0) {
    throw "No Project Java sources found."
}

javac -d $homeworkBin @homeworkSources

# Keep Project/bin self-contained for Windows builds.
$allSources = @($homeworkSources + $projectSources)
javac -d $projectBin @allSources

Write-Host "Compiled."
Write-Host 'Run with: powershell -ExecutionPolicy Bypass -File Project\run-eval.ps1'
