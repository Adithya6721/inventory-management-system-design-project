# Supplier integration

Start the seeded application using [the startup guide](start-demo.md). The new
**Suppliers** page uses the supplier service on 8085. It calls `POST /api/crawl`,
which runs `crawler/crawler_engine.js` against the local HTML portals:

- Apex: product cards, two pages.
- Global: a product table, one page.
- Nordic: JSON-LD embedded in an HTML page, one page.

The crawler follows pagination sequentially, waits 80 ms between requests, cleans
SKU/price/stock fields, and reports actual page counts and elapsed time. Failed
pages cause the UI to report failure; there is no fallback catalog or invented
timing. `avgNetworkLatencyMs` is the legacy field name for elapsed request time
including the politeness wait. It is not an isolated network measurement.

Selected products are sent to the backend's existing `/api/suppliers/import`
endpoint, grouped by supplier. New SKUs become products; existing products retain
their name, description and selling price, while their category can be updated.
Category updates now use the normal product update path, which evicts cached
product details. A positive supplier quantity can create a purchase order. It
does not change warehouse inventory. Receiving that purchase order adds physical
stock through the existing transaction and audit flow.

Read requests retry with delays of 200, 400 and 600 ms. Import POST requests are
not automatically retried, because each request creates a new batch. An import
with a lost response may still need manual inspection before retrying. Supplier
batches are not one transaction covering the entire catalog and purchase order.

## Reproduce the checks

From the repository root, with services running:

```powershell
mvn.cmd -f backend/pom.xml -Pbenchmark package -DskipTests -q
python scripts/test-supplier.py
```

The runner starts its own backend on 8082 and supplier service on 8086, chooses
new test core/shard databases and a separate cache namespace, then stops only its
own processes. The seeded app on 8081/3000 and its databases are preserved.
Test databases are retained for inspection. Evidence is written to
`evaluation/results/<timestamp>-supplier-integration/`.

It checks the three parsers, normalization, repeated crawl counters, read retries
and absence of automatic POST replay, the browser crawl endpoint, 24 unique
products from four HTML pages, three supplier purchase orders, zero warehouse
stock after import, cache invalidation after category updates, stock credited
only after receipt, and no duplicate stock or audit entries on repeated receipt.

This is an integration check with one measured crawl, not a load benchmark or a
replacement for the existing JMeter and autocomplete experiments. Earlier
supplier-branch documentation describes proposed capabilities and example
metrics; use recorded results from the runner as measured evidence.

For a crawl without importing anything, start the supplier service and run:

```powershell
node crawler/crawler_engine.js --base-url http://127.0.0.1:8085 --json
```

For the existing backend consistency suite:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-consistency.ps1
```
