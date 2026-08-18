$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot "build.ps1")
java -cp (Join-Path $projectRoot "out/test") corekv.CoreKVStoreTest
