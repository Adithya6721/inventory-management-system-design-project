param([ValidateRange(1024, 65535)][int]$Port = 27019)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$testRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot ".verification\consistency-mongo-$Port"))
if (-not $testRoot.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Test MongoDB path must stay inside the project.'
}
$probe = [Net.Sockets.TcpClient]::new()
try {
    if ($probe.ConnectAsync('127.0.0.1', $Port).Wait(500) -and $probe.Connected) {
        throw "Port $Port is already occupied. Reuse the known test instance or choose another port."
    }
} catch [AggregateException] {
    # Connection refused means this test port is available.
} finally { $probe.Dispose() }
New-Item -ItemType Directory -Path (Join-Path $testRoot 'data') -Force | Out-Null
$mongodPath = (Get-Command mongod -ErrorAction Stop).Source
$mongoshPath = (Get-Command mongosh -ErrorAction Stop).Source
$mongodArgs = @('--bind_ip', '127.0.0.1', '--port', "$Port", '--replSet', 'inventory-rs',
    '--dbpath', ('"' + (Join-Path $testRoot 'data') + '"'),
    '--logpath', ('"' + (Join-Path $testRoot 'mongod.log') + '"'), '--logappend')
$testProcess = Start-Process -FilePath $mongodPath -ArgumentList $mongodArgs -WindowStyle Hidden -PassThru
$testProcess.Id | Set-Content -LiteralPath (Join-Path $testRoot 'mongod.pid')
# Reopening an existing data directory can require recovery before mongosh connects.
$listening = $false
for ($attempt = 0; $attempt -lt 100; $attempt++) {
    $testProcess.Refresh()
    if ($testProcess.HasExited) { throw "MongoDB exited during startup. Inspect $testRoot\mongod.log" }
    $startupProbe = [Net.Sockets.TcpClient]::new()
    try {
        if ($startupProbe.ConnectAsync('127.0.0.1', $Port).Wait(100) -and $startupProbe.Connected) {
            $listening = $true
            break
        }
    } catch [AggregateException] {
        # A connection refusal is normal during database recovery.
    } finally { $startupProbe.Dispose() }
    Start-Sleep -Milliseconds 200
}
if (-not $listening) { throw "MongoDB did not open port $Port. Inspect $testRoot\mongod.log" }
$uri = "mongodb://127.0.0.1:$Port/?directConnection=true&serverSelectionTimeoutMS=15000"
$init = "try { rs.status(); } catch(e) { if(e.code !== 94) throw e; rs.initiate({_id:'inventory-rs',members:[{_id:0,host:'127.0.0.1:$Port'}]}); } let ok=false; for(let i=0;i<60;i++){ if(db.hello().isWritablePrimary){ok=true;break;} sleep(500); } if(!ok) throw new Error('No primary'); print('Test replica set ready');"
& $mongoshPath $uri --quiet --eval $init
if ($LASTEXITCODE -ne 0) { throw "Replica initialization failed. Inspect $testRoot\mongod.log (PID $($testProcess.Id))." }
Write-Output "MongoDB test PID: $($testProcess.Id). Data: $testRoot"
Write-Output "For tests set MONGO_PORT=$Port. Stop this PID when finished; the data is retained."
