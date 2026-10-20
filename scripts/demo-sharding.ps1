param(
    [string]$BaseUrl = 'http://localhost:8080',
    [ValidateRange(1, 1000)][int]$ProductCount = 30
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

function Invoke-InventoryApi {
    param([string]$Method, [string]$Path, $Body)
    $arguments = @{ Method = $Method; Uri = "$BaseUrl/api$Path"; TimeoutSec = 120 }
    if ($null -ne $Body) {
        $arguments.ContentType = 'application/json'
        $arguments.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    Invoke-RestMethod @arguments
}

function Show-Distribution {
    param([string]$Stage)
    Write-Host "`n$Stage"
    $state = Invoke-InventoryApi GET '/shards'
    $state.shards | Format-Table shardId, database, active, records, requests | Out-Host
}

function Assert-Inventory {
    param($Records, [switch]$OriginalOwners)
    foreach ($record in $Records) {
        $p = $record.productId
        $w = $record.warehouseId
        $stock = Invoke-InventoryApi GET "/inventory/$p/$w"
        if ($stock.quantity -ne 100 -or $stock.reservedQuantity -ne 10) {
            throw "Inventory changed during reassignment for ${p}:${w}"
        }
        $route = Invoke-InventoryApi GET "/shards/route?productId=$p&warehouseId=$w"
        if ($OriginalOwners -and $route.shardId -ne $record.originalShard) {
            throw "Original ownership was not restored for ${p}:${w}"
        }
    }
}

$initial = Invoke-InventoryApi GET '/shards'
if ($initial.state -ne 'READY' -or ($initial.activeShards -join ',') -ne 'shard-1,shard-2,shard-3') {
    throw 'This demo needs shards 1, 2 and 3 active and shard 4 inactive. Check GET /api/shards first.'
}

$runId = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$warehouses = @(1..3 | ForEach-Object {
    Invoke-InventoryApi POST '/warehouses' @{
        name = "Sharding Demo $runId Warehouse $_"; location = 'Campus'; status = 'ACTIVE'
    }
})
$records = [System.Collections.Generic.List[object]]::new()
for ($i = 1; $i -le $ProductCount; $i++) {
    $product = Invoke-InventoryApi POST '/products' @{
        sku = "SHARD-$runId-$i"; name = "Sharding Demo Product $i"
        price = 100; reorderLevel = 5; active = $true
    }
    foreach ($warehouse in $warehouses) {
        $p = $product.productId
        $w = $warehouse.warehouseId
        $null = Invoke-InventoryApi POST '/inventory/adjustments' @{
            productId = $p; warehouseId = $w; physicalDelta = 100
            reservedDelta = 10; reason = "Sharding demo $runId"
        }
        $route = Invoke-InventoryApi GET "/shards/route?productId=$p&warehouseId=$w"
        $records.Add([pscustomobject]@{ productId = $p; warehouseId = $w; originalShard = $route.shardId })
    }
}

Show-Distribution 'Before adding shard 4'
$added = Invoke-InventoryApi POST '/shards/shard-4'
$added | Format-List | Out-Host
Assert-Inventory $records
Show-Distribution 'After adding shard 4'

$removed = Invoke-InventoryApi DELETE '/shards/shard-4'
$removed | Format-List | Out-Host
Assert-Inventory $records -OriginalOwners
Show-Distribution 'After removing shard 4'
Write-Host "Verified $($records.Count) records after addition and removal. Quantities and reservations are preserved."
Write-Host 'The demo products, warehouses and inventory remain available for inspection.'
