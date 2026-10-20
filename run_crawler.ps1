# Run Supplier Crawler CLI (Member 5)
param (
    [string]$Suppliers = "apex,global,nordic",
    [string]$TargetWarehouse = "wh_central",
    [switch]$ImportToBackend,
    [switch]$NoPO
)

$argsList = @("crawler/crawler_engine.js", "--suppliers", $Suppliers, "--target-wh", $TargetWarehouse)
if ($ImportToBackend) { $argsList += "--import" }
if ($NoPO) { $argsList += "--no-po" }

Write-Host "Executing Member 5 Supplier Web Crawler..." -ForegroundColor Cyan
node $argsList
