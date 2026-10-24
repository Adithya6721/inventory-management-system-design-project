param([ValidateRange(1024, 65535)][int]$Port = 27019)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runId = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$logRoot = Join-Path $projectRoot '.verification'
New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
$consoleLog = Join-Path $logRoot "consistency-$runId.log"
$settings = @{
    MONGO_PORT = "$Port"
    MONGO_DB = 'inventory_consistency_e2e'
    MONGO_URI = "mongodb://127.0.0.1:$Port/inventory_consistency_e2e?replicaSet=inventory-rs"
    SHARD_DB_1 = 'inventory_consistency_e2e_shard_1'
    SHARD_DB_2 = 'inventory_consistency_e2e_shard_2'
    SHARD_DB_3 = 'inventory_consistency_e2e_shard_3'
    SHARD_DB_4 = 'inventory_consistency_e2e_shard_4'
    RATELIMIT_ENABLED = 'false'
}
$previous = @{}
foreach ($name in $settings.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
}
Push-Location (Join-Path $projectRoot 'backend')
try {
    & mvn.cmd test '-Dconsistency.mongo.tests=true' '-Dsharding.mongo.tests=true' -q *> $consoleLog
    $testExitCode = $LASTEXITCODE
} finally {
    Pop-Location
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}
Get-Content -LiteralPath $consoleLog -Tail 8
& python (Join-Path $PSScriptRoot 'collect-test-results.py') --output "evaluation/results/$runId-consistency" --console-log $consoleLog --mongo-port $Port
if ($LASTEXITCODE -ne 0) { throw 'Failed to export test results.' }
exit $testExitCode
