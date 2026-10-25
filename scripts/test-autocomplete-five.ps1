param(
    [ValidateRange(1,5)][int]$Repeats = 3,
    [ValidateRange(200,10000)][int]$Products = 1000,
    [ValidateRange(8,10000)][int]$Initial = 100,
    [ValidateRange(0,1000)][int]$Debounce = 250
)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & mvn.cmd -f backend/pom.xml -Pbenchmark package -DskipTests -q
    $buildExitCode = $LASTEXITCODE
    $ErrorActionPreference = $oldPreference
    if ($buildExitCode -ne 0) { throw 'Benchmark build failed' }
    & python scripts/run-autocomplete-five.py --repeats $Repeats --products $Products --initial $Initial --debounce $Debounce
    if ($LASTEXITCODE -ne 0) { throw 'Autocomplete experiment failed; inspect failure.json' }
} finally { Pop-Location }
