param(
    [ValidateSet('all','limiter','shards','stock','ids','autocomplete','hash')]
    [string]$Only = 'all',
    [ValidateRange(3,300)][int]$Seconds = 10,
    [ValidateRange(1,10)][int]$Repeats = 1
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    # A separate JAR avoids the file lock held by the running walkthrough app.
    if ($Only -ne 'autocomplete') {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & mvn.cmd -f backend/pom.xml -Pbenchmark package -DskipTests -q
        $buildExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousPreference
        if ($buildExitCode -ne 0) { throw "Benchmark build failed ($buildExitCode)" }
    }
    & python scripts/run-components.py --only $Only --seconds $Seconds --repeats $Repeats
    if ($LASTEXITCODE -ne 0) { throw 'Component experiment failed; inspect its failure.json' }
} finally { Pop-Location }
