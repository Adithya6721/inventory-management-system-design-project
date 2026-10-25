# Run the simulated supplier portals and HTML crawler endpoint.
Write-Host "Starting Simulated Supplier Websites Server on port 8085..." -ForegroundColor Cyan
node (Join-Path $PSScriptRoot 'crawler/supplier_server.js')
exit $LASTEXITCODE
