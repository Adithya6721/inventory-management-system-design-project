/** Real supplier -> catalog -> purchase order -> warehouse receipt checks.
 * Run via python scripts/test-supplier.py to use isolated databases automatically.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { SupplierCrawlerEngine } = require('../crawler/crawler_engine');
const base = process.env.BACKEND_API_URL;
const supplierBase = process.env.SUPPLIER_BASE_URL;
async function api(method, path, body) {
  const response = await fetch(`${base}/api${path}`, {
    method, headers: { 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  assert.ok(response.ok, `${method} ${path}: ${response.status} ${await response.clone().text()}`);
  return response.json();
}
async function main() {
  assert.ok(base && supplierBase, 'Use python scripts/test-supplier.py (isolates test data).');
  const warehouse = await api('POST', '/warehouses', { name: 'Supplier integration receiving', location: 'Integration test', status: 'ACTIVE' });
  const crawler = new SupplierCrawlerEngine({ baseUrl: supplierBase, backendUrl: base });
  const crawl = await crawler.crawl();
  assert.equal(crawl.items.length, 24);
  assert.equal(crawl.telemetry.pagesFetched, 4);
  assert.equal(crawl.telemetry.pagesFailed, 0);
  assert.equal(new Set(crawl.items.map(item => item.sku)).size, 24);
  const imports = await crawler.importToCoreInventory({ targetWarehouseId: warehouse.warehouseId });
  assert.equal(imports.length, 3);
  assert.ok(imports.every(result => result.success && result.proposedPurchaseOrderId));
  const products = (await api('GET', '/products?size=100')).content;
  assert.equal(products.length, 24);
  const orders = (await api('GET', '/purchase-orders')).content;
  assert.equal(orders.length, 3);
  assert.deepEqual(await api('GET', `/warehouses/${warehouse.warehouseId}/inventory`), [], 'Import must not credit physical stock');
  // Warm product-detail cache, update through supplier import, read it again.
  const item = crawl.items[0];
  const product = products.find(product => product.sku === item.sku);
  await api('GET', `/products/${product.productId}`);
  const changedCategory = 'Supplier integration updated category';
  await api('POST', '/suppliers/import', {
    supplierId: item.supplierId, supplierName: item.supplierName, autoCreatePurchaseOrder: false,
    items: [{ ...item, category: changedCategory }],
  });
  const updated = await api('GET', `/products/${product.productId}`);
  assert.equal(updated.category, changedCategory, 'Supplier update must invalidate cached product details');
  assert.equal(updated.price, product.price, 'Existing catalog selling price must be preserved');
  const orderId = imports[0].proposedPurchaseOrderId;
  const receipt = await api('POST', `/purchase-orders/${orderId}/receive`, {});
  const receivedItems = crawl.items.filter(item => item.supplierId === imports[0].supplierId && item.supplierReportedStock > 0);
  const stock = await api('GET', `/warehouses/${warehouse.warehouseId}/inventory`);
  const expected = new Map(receivedItems.map(item => [products.find(product => product.sku === item.sku).productId, item.supplierReportedStock]));
  for (const row of stock) {
    assert.equal(row.quantity, expected.get(row.productId) || 0);
    assert.equal(row.reservedQuantity, 0);
  }
  const total = stock.reduce((sum, row) => sum + row.quantity, 0);
  assert.equal(total, receivedItems.reduce((sum, item) => sum + item.supplierReportedStock, 0));
  const countMovements = async () => {
    let count = 0;
    for (const product of products) count += (await api('GET', `/inventory/${product.productId}/movements`)).length;
    return count;
  };
  const movementsBefore = await countMovements();
  await api('POST', `/purchase-orders/${orderId}/receive`, {});
  const repeatedStock = await api('GET', `/warehouses/${warehouse.warehouseId}/inventory`);
  assert.equal(repeatedStock.reduce((sum, row) => sum + row.quantity, 0), total, 'Repeated receipt must not double credit');
  assert.equal(await countMovements(), movementsBefore, 'Repeated receipt must not create duplicate movements');
  const report = {
    passed: true, crawl: crawl.telemetry, products: products.length, purchaseOrders: orders.length,
    warehouseStockAfterImport: 0, warehouseStockAfterReceipt: total, receivedOrderStatus: receipt.status,
    supplierStockSeparateFromWarehouseStock: true, productCacheInvalidated: true,
    repeatReceiptDoesNotDuplicateStockOrMovements: true, movementCount: movementsBefore,
  };
  if (process.env.SUPPLIER_REPORT) fs.writeFileSync(process.env.SUPPLIER_REPORT, JSON.stringify(report, null, 2) + '\n');
  console.log(JSON.stringify(report, null, 2));
}
main().catch(error => { console.error(error); process.exitCode = 1; });
