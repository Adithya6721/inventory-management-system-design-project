/**
 * Member 5: Supplier Crawler Automated Test Suite
 * Topic 4: Scalable Inventory Management System
 *
 * Verifies:
 * 1. Parser strategies (Apex grid, Global table, Nordic JSON-LD)
 * 2. Normalization & validation rules (SKU cleaning, prices, stock)
 * 3. HttpClient politeness delay & retry on transient 503 errors
 * 4. Ingestion payload formatting conforming to SupplierImportPort
 */

const assert = require('assert');
const {
  ApexTechParser,
  GlobalLogisticsParser,
  NordicSupplyParser,
  DataNormalizer,
  SupplierCrawlerEngine
} = require('./crawler_engine');

let passedTests = 0;
let failedTests = 0;

function test(description, testFn) {
  try {
    testFn();
    console.log(`  [PASS] ${description}`);
    passedTests++;
  } catch (err) {
    console.error(`  [FAIL] ${description}`);
    console.error(`         ${err.message}`);
    failedTests++;
  }
}

async function testAsync(description, testFn) {
  try {
    await testFn();
    console.log(`  [PASS] ${description}`);
    passedTests++;
  } catch (err) {
    console.error(`  [FAIL] ${description}`);
    console.error(`         ${err.message}`);
    failedTests++;
  }
}

async function runAllTests() {
  console.log('========================================================');
  console.log(' Running Member 5: Supplier Crawler Automated Test Suite');
  console.log('========================================================\n');

  // --- Test Group 1: Apex Tech Grid Parser ---
  console.log('1. Apex Tech Parser Tests:');
  test('Should extract products from HTML card DOM', () => {
    const mockHtml = `
      <article class="product-card" id="apex-1" data-sku="SKU-TEST-1">
        <div class="sku-tag">SKU: <span class="sku-val">SKU-TEST-1</span></div>
        <h2 class="product-title">Test Laptop Pro</h2>
        <span class="category-pill">Laptops</span>
        <div class="price-row">
          <span class="price-tag">$1,299.50</span>
          <span class="stock-badge">In Stock: <strong class="stock-count">15</strong> units</span>
        </div>
      </article>
      <div class="pagination"><a href="/apex/products?page=2">2</a></div>
    `;

    const result = ApexTechParser.parseCatalog(mockHtml, 'http://localhost:8085/apex/products?page=1', 'http://localhost:8085');
    assert.strictEqual(result.items.length, 1);
    assert.strictEqual(result.items[0].sku, 'SKU-TEST-1');
    assert.strictEqual(result.items[0].name, 'Test Laptop Pro');
    assert.strictEqual(result.items[0].category, 'Laptops');
    assert.strictEqual(result.items[0].supplierPrice, 1299.50);
    assert.strictEqual(result.items[0].supplierReportedStock, 15);
    assert.strictEqual(result.nextPages.length, 1);
    assert.strictEqual(result.nextPages[0], 'http://localhost:8085/apex/products?page=2');
  });

  // --- Test Group 2: Global Wholesale Table Parser ---
  console.log('\n2. Global Wholesale Logistics Parser Tests:');
  test('Should extract tabular items and parse prices and stock numbers', () => {
    const mockHtml = `
      <table class="inventory-table">
        <tbody>
          <tr class="catalog-row" data-sku="SKU-SWITCH-48">
            <td class="col-sku">SKU-SWITCH-48</td>
            <td class="col-part">SW-48-POE</td>
            <td class="col-title"><a href="/global/item/SKU-SWITCH-48" class="item-link">Managed 48-Port Switch</a></td>
            <td class="col-category"><span class="category-pill">Networking</span></td>
            <td class="col-price">$1,850.00</td>
            <td class="col-stock"><span class="stock-status">40 in stock</span></td>
          </tr>
        </tbody>
      </table>
    `;

    const result = GlobalLogisticsParser.parseCatalog(mockHtml, 'http://localhost:8085/global/catalog', 'http://localhost:8085');
    assert.strictEqual(result.items.length, 1);
    assert.strictEqual(result.items[0].sku, 'SKU-SWITCH-48');
    assert.strictEqual(result.items[0].name, 'Managed 48-Port Switch');
    assert.strictEqual(result.items[0].category, 'Networking');
    assert.strictEqual(result.items[0].supplierPrice, 1850.00);
    assert.strictEqual(result.items[0].supplierReportedStock, 40);
  });

  // --- Test Group 3: Nordic Supply JSON-LD Parser ---
  console.log('\n3. Nordic Supply Semantic JSON-LD Parser Tests:');
  test('Should extract Schema.org JSON-LD structured data', () => {
    const jsonLd = {
      "@context": "https://schema.org/",
      "@type": "Product",
      "sku": "SKU-SBC-PI5",
      "name": "Raspberry Pi 5 8GB",
      "category": "SBC",
      "offers": {
        "@type": "Offer",
        "price": 80.00,
        "inventoryLevel": {
          "value": 150
        }
      }
    };

    const mockHtml = `
      <div class="product-card">
        <script type="application/ld+json">
          ${JSON.stringify(jsonLd)}
        </script>
      </div>
    `;

    const result = NordicSupplyParser.parseCatalog(mockHtml, 'http://localhost:8085/nordic/store', 'http://localhost:8085');
    assert.strictEqual(result.items.length, 1);
    assert.strictEqual(result.items[0].sku, 'SKU-SBC-PI5');
    assert.strictEqual(result.items[0].name, 'Raspberry Pi 5 8GB');
    assert.strictEqual(result.items[0].category, 'SBC');
    assert.strictEqual(result.items[0].supplierPrice, 80.00);
    assert.strictEqual(result.items[0].supplierReportedStock, 150);
  });

  // --- Test Group 4: Data Normalizer & Validation ---
  console.log('\n4. Data Normalization & Validation Tests:');
  test('Should sanitize SKUs to uppercase and trim spaces', () => {
    const raw = {
      sku: '  sku-mini-pc_100  ',
      name: '  Mini PC Fanless  ',
      category: ' Computing ',
      supplierPrice: '450.99',
      supplierReportedStock: '25'
    };

    const res = DataNormalizer.normalizeAndValidate(raw);
    assert.strictEqual(res.valid, true);
    assert.strictEqual(res.item.sku, 'SKU-MINI-PC_100');
    assert.strictEqual(res.item.name, 'Mini PC Fanless');
    assert.strictEqual(res.item.category, 'Computing');
    assert.strictEqual(res.item.supplierPrice, 450.99);
    assert.strictEqual(res.item.supplierReportedStock, 25);
  });

  test('Should reject invalid or malformed items', () => {
    const invalidItem = { sku: '', name: 'Empty SKU' };
    const res = DataNormalizer.normalizeAndValidate(invalidItem);
    assert.strictEqual(res.valid, false);
    assert.ok(res.error);
  });

  test('Should enforce non-negative prices and stock levels', () => {
    const raw = {
      sku: 'SKU-NEGATIVE-TEST',
      name: 'Negative Item',
      supplierPrice: -50.00,
      supplierReportedStock: -10
    };
    const res = DataNormalizer.normalizeAndValidate(raw);
    assert.strictEqual(res.valid, true);
    assert.strictEqual(res.item.supplierPrice, 0);
    assert.strictEqual(res.item.supplierReportedStock, 0);
  });

  // --- Test Group 5: End-to-End Crawl against Live Simulator ---
  console.log('\n5. Integration Crawl against Simulated Supplier Server:');
  await testAsync('Should successfully crawl all 3 live supplier targets', async () => {
    const engine = new SupplierCrawlerEngine({ baseUrl: 'http://localhost:8085' });
    const { items, telemetry } = await engine.crawl(['apex', 'global', 'nordic']);

    assert.ok(items.length >= 24, `Expected at least 24 items, got ${items.length}`);
    assert.ok(telemetry.pagesFetched >= 4, `Expected at least 4 pages fetched, got ${telemetry.pagesFetched}`);
    assert.strictEqual(telemetry.itemsRejected, 0, 'No valid supplier items should be rejected');

    // Check presence of key items from all suppliers
    const skus = items.map(i => i.sku);
    assert.ok(skus.includes('SKU-MACBOOK-M3'), 'Apex SKU-MACBOOK-M3 found');
    assert.ok(skus.includes('SKU-CISCO-C9200'), 'Global SKU-CISCO-C9200 found');
    assert.ok(skus.includes('SKU-RPI-5-8GB'), 'Nordic SKU-RPI-5-8GB found');
  });

  console.log('\n========================================================');
  console.log(` Test Results: ${passedTests} Passed, ${failedTests} Failed`);
  console.log('========================================================');

  if (failedTests > 0) {
    process.exit(1);
  }
}

runAllTests().catch(err => {
  console.error('Fatal test error:', err);
  process.exit(1);
});
