# Windows PowerShell 용:  powershell -ExecutionPolicy Bypass -File scripts\gen-datasets.ps1
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$seed = 42
function Gen($name, $size) {
    Write-Host "== $name ($size) =="
    & .\gradlew.bat --quiet generateSample "-Psize=$size" "-Pseed=$seed" "-PoutDir=data/$name"
    if ($LASTEXITCODE -ne 0) { throw "데이터셋 생성 실패: $name" }
}
Gen "10k" 10000
Gen "100k" 100000
Gen "1m" 1000000
Write-Host "완료. 기대값 확인: type data\100k\expected.properties"
