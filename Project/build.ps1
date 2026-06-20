$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$homeworkDir = Join-Path $repoRoot "Homework"

$homeworkBin = Join-Path $homeworkDir "bin"
$projectBin = Join-Path $scriptDir "bin"
$protobufJar = Join-Path $scriptDir "lib\protobuf-java-4.28.3.jar"

if (-not (Test-Path $protobufJar)) {
    throw "Missing protobuf jar: $protobufJar"
}

New-Item -ItemType Directory -Force -Path $homeworkBin | Out-Null
New-Item -ItemType Directory -Force -Path $projectBin | Out-Null

$homeworkSources = @(Get-ChildItem -Path (Join-Path $homeworkDir "src") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })
$projectSources = @(Get-ChildItem -Path (Join-Path $scriptDir "src") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })
$generatedSources = @(Get-ChildItem -Path (Join-Path $scriptDir "src-gen") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName })

if ($homeworkSources.Count -eq 0) {
    throw "No Homework Java sources found."
}
if ($projectSources.Count -eq 0) {
    throw "No Project Java sources found."
}
if ($generatedSources.Count -eq 0) {
    throw "No generated Java sources found in src-gen. Run protoc first."
}

javac -d $homeworkBin @homeworkSources
if ($LASTEXITCODE -ne 0) {
    throw "Homework compilation failed with exit code $LASTEXITCODE."
}

# Keep Project/bin self-contained for Windows builds. Protobuf-java is on the
# classpath because the generated sources depend on it.
$allSources = @($homeworkSources + $projectSources + $generatedSources)
javac -cp $protobufJar -d $projectBin @allSources
if ($LASTEXITCODE -ne 0) {
    throw "Project compilation failed with exit code $LASTEXITCODE."
}

Write-Host "Compiled."
Write-Host 'Run with: powershell -ExecutionPolicy Bypass -File Project\run-eval.ps1'
