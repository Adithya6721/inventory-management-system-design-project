# Run Supplier Crawler CLI (Member 5)
param (
    [string]$Suppliers = "apex,global,nordic",
    [string]$TargetWarehouse = "wh_central",
    [switch]$ImportToBackend,
    [switch]$NoPO,
    [string]$BaseUrl = 'http://127.0.0.1:8085',
    [string]$BackendUrl = 'http://127.0.0.1:8081'
)

$argsList = @((Join-Path $PSScriptRoot 'crawler/crawler_engine.js'), '--base-url', $BaseUrl, '--backend-url', $BackendUrl, "--suppliers", $Suppliers, "--target-wh", $TargetWarehouse)
if ($ImportToBackend) { $argsList += "--import" }
if ($NoPO) { $argsList += "--no-po" }

Write-Host "Executing supplier web crawler..."
node $argsList
exit $LASTEXITCODE
