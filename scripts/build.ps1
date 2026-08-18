$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$mainOut = Join-Path $projectRoot "out/main"
$testOut = Join-Path $projectRoot "out/test"

New-Item -ItemType Directory -Force -Path $mainOut, $testOut | Out-Null

$mainSources = Get-ChildItem -Path (Join-Path $projectRoot "src/main/java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$testSources = Get-ChildItem -Path (Join-Path $projectRoot "src/test/java") -Recurse -Filter *.java | ForEach-Object { $_.FullName }

javac --release 17 -d $mainOut $mainSources
if ($LASTEXITCODE -ne 0) {
    throw "Main source compilation failed."
}

if ($testSources.Count -gt 0) {
    $allTestCompileSources = @($mainSources + $testSources)
    javac --release 17 -d $testOut $allTestCompileSources
    if ($LASTEXITCODE -ne 0) {
        throw "Test source compilation failed."
    }
}

Write-Host "Build complete."
