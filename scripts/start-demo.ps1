param([ValidateSet('services','backend','frontend','suppliers')][string]$Part = 'backend')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent

function Test-DemoPort([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try { return $client.ConnectAsync('127.0.0.1', $Port).Wait(1000) -and $client.Connected }
    catch [AggregateException] { return $false }
    finally { $client.Dispose() }
}

Push-Location $projectRoot
try {
    if ($Part -eq 'suppliers') {
        if (Test-DemoPort 8085) { throw 'Port 8085 is occupied; reuse the existing supplier service or stop it first.' }
        & node crawler/supplier_server.js
        return
    }
    if ($Part -eq 'services') {
        if (-not (Test-DemoPort 6379)) {
            $redisService = Get-Service Memurai -ErrorAction Stop
            if ($redisService.Status -ne 'Running') {
                try { Start-Service Memurai -ErrorAction Stop }
                catch { throw 'Start Memurai from an Administrator PowerShell: Start-Service Memurai' }
            }
        }
        if (-not (Test-DemoPort 27019)) {
            & powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-consistency-mongo.ps1
            if ($LASTEXITCODE -ne 0) { throw 'Test MongoDB startup failed' }
        }
        if (-not (Test-DemoPort 6379)) { throw 'Redis is not listening on port 6379' }
        Write-Host 'Services ready: MongoDB replica set on 27019; Redis on 6379.'
        return
    }
    $settings = @{}
    if ($Part -eq 'backend') {
        if (Test-DemoPort 8081) { throw 'Port 8081 is occupied; reuse the existing backend or stop it first.' }
        if (-not (Test-DemoPort 27019)) { throw 'Start services first: .\scripts\start-demo.ps1 -Part services' }
        $settings = @{
            MONGO_URI = 'mongodb://127.0.0.1:27019/inventory_walkthrough?replicaSet=inventory-rs'
            SHARD_DB_1 = 'inventory_walkthrough_shard_1'
            SHARD_DB_2 = 'inventory_walkthrough_shard_2'
            SHARD_DB_3 = 'inventory_walkthrough_shard_3'
            SHARD_DB_4 = 'inventory_walkthrough_shard_4'
            SHARDING_ENABLED = 'true'
            CACHE_ENABLED = 'true'
            CACHE_NAMESPACE = 'demo:walkthrough'
            EVALUATION_ENABLED = 'false'
            RATELIMIT_ENABLED = 'false'
            SNOWFLAKE_DATACENTER_ID = '1'
            SNOWFLAKE_NODE_ID = '2'
            REDIS_HOST = '127.0.0.1'
            REDIS_PORT = '6379'
            PORT = '8081'
            SERVER_PORT = '8081'
            SERVER_ADDRESS = '127.0.0.1'
            LOGGING_LEVEL_COM_EXAMPLE_INVENTORY = 'INFO'
            LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_DATA_MONGODB_CORE_MONGOTEMPLATE = 'WARN'
        }
    } else {
        if (Test-DemoPort 3000) { throw 'Port 3000 is occupied; reuse the existing frontend or stop it first.' }
        $settings = @{ VITE_API_BASE_URL = 'http://127.0.0.1:8081/api' }
    }
    $previous = @{}
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    $oldPreference = $ErrorActionPreference
    try {
        Write-Host "Starting $Part. Keep this terminal open; Ctrl+C stops it."
        $ErrorActionPreference = 'Continue'
        if ($Part -eq 'backend') { & mvn.cmd -f backend/pom.xml spring-boot:run }
        else { & npm.cmd --prefix frontend run dev -- --host 127.0.0.1 --port 3000 --strictPort }
    } finally {
        $ErrorActionPreference = $oldPreference
        foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    }
} finally { Pop-Location }
