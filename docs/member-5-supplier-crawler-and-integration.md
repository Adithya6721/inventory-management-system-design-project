# Member 5: Supplier Crawler & External Integration Engine — Design & Evaluation Report

**Topic 4: Scalable Inventory Management System (UCS3513 System Design Laboratory)**  
**Author:** Member 5 (External Data Feeds, Supplier Crawler & Integration Coordination Lead)

---

## 1. Executive Summary & Component Mapping (Deliverable 1)

Member 5 is responsible for **External Data and Supporting Workflows**, fulfilling **Functional Requirement 10 (FR10: Supplier Information)** and implementing the **Web Crawler & External Integration** system design component:

1. **Simulated Supplier Web Portals (`crawler/supplier_server.js` & `supplier_server.py`)**: Realistic simulation of three independent external wholesale distributors exposing distinct DOM structures, pricing tiers, and inventory levels.
2. **Resilient Multi-Strategy Web Crawler (`crawler/crawler_engine.js` & `crawler.py`)**: An autonomous crawler engine featuring polite rate limiting, exponential backoff retries, pluggable DOM/JSON-LD parsers, and data normalization.
3. **Price Arbitrage & Margin Analysis**: Automated price differential computation comparing scraped wholesale prices against internal catalog retail prices.
4. **Core Inventory Ingestion Contract**: Direct integration with Spring Boot's `POST /api/suppliers/import` (`SupplierImportPort`), registering new products into the catalog and staging draft Purchase Orders without corrupting physical stock balances.
5. **Interactive UI Integration (`frontend/src/pages/SupplierCrawler.jsx`)**: Full React dashboard tab enabling examiners and warehouse managers to inspect, trigger crawls, review live throughput, and ingest feeds.
6. **5-Module Integration Coordination (`scripts/e2e_integration_demo.js`)**: End-to-end evaluation automation integrating Members 1, 2, 3, 4, and 5.

```
+---------------------------------------------------------------------------------------+
|                                SIMULATED EXTERNAL SUPPLIERS                           |
|  +---------------------------+  +--------------------------+  +---------------------+ |
|  | Apex Tech Distribution    |  | Global Wholesale Logist. |  | Nordic Comp Supply  | |
|  | (CSS Grid Cards / Pag.)   |  | (Industrial HTML Table)  |  | (Schema.org JSON-LD)| |
|  +-------------+-------------+  +------------+-------------+  +----------+----------+ |
+----------------|-----------------------------|---------------------------|------------+
                 |                             |                           |
                 v                             v                           v
+---------------------------------------------------------------------------------------+
|                         MEMBER 5: WEB CRAWLER & INGESTION PIPELINE                    |
|                                                                                       |
|   +-----------------------+      +---------------------+      +-------------------+   |
|   | Polite Rate Limiter   | ---> | Pluggable Strategy  | ---> | Data Normalizer   |   |
|   | (Queue Frontier & Exp)|      | Parsers (DOM / LD)  |      | & Price Arbitrage |   |
|   +-----------------------+      +---------------------+      +---------+---------+   |
+-------------------------------------------------------------------------|-------------+
                                                                          |
                                      POST /api/suppliers/import (REST)   |
                                                                          v
+---------------------------------------------------------------------------------------+
|                         MEMBER 1: CORE INVENTORY BACKEND                              |
|                                                                                       |
|   +---------------------------+       +-------------------------------------------+   |
|   | Product Master Catalog    |       | Draft Purchase Order Generator            |   |
|   | (Registers New Products)  |       | (Physical Stock Untouched Until Received) |   |
|   +---------------------------+       +-------------------------------------------+   |
+---------------------------------------------------------------------------------------+
```

---

## 2. Simulated Supplier Portals & DOM Architectures (Deliverable 2)

To realistically model real-world external suppliers, Member 5 built three distinct simulated wholesale web portals running on dedicated port `8085`:

| Supplier Portal | Layout Strategy | DOM Selectors / Formats | Catalog Domain | Unique Characteristics |
| :--- | :--- | :--- | :--- | :--- |
| **1. Apex Tech Distribution** (`/apex`) | Modern CSS Grid Cards | `<article class="product-card">`, `.sku-val`, `.price-tag`, `.stock-count` | Laptops, Displays, SSDs, Peripherals | Multi-page pagination (`/apex/products?page=2`), individual product detail pages (`/apex/product/:id`), `robots.txt` compliance. |
| **2. Global Wholesale Logistics** (`/global`) | Industrial B2B Table | `<table class="inventory-table">`, `<tr class="catalog-row">`, `.col-sku`, `.col-price` | Enterprise Switches, Routers, NAS, Server RAM | Tabular format, part numbers, warehouse fulfillment locations, volume tier pricing footnotes. |
| **3. Nordic Component Supply** (`/nordic`) | Semantic Web JSON-LD | `<script type="application/ld+json">` with Schema.org `Product` & `Offer` | SBCs, Dev Boards, IoT Sensors, Wireless DKs | Standardized semantic data extraction, GTIN-13 identifiers, machine-readable inventory status. |

---

## 3. Crawler Engine Design & Implementation (Deliverable 3)

### 3.1 Politeness & Frontier Management
The crawler adheres to web crawling ethics and politeness principles:
- **Rate-Limiting Politeness:** Enforces an 80ms politeness window between consecutive requests to the same host to prevent external Denial of Service.
- **Frontier Queue & Visited Set:** Maintains a hash set of normalized URLs, eliminating redundant fetch cycles and circular pagination loops.
- **Exponential Backoff:** Retries transient failures (e.g., HTTP 503, timeouts) up to 3 times with progressive delays ($200\text{ms} \to 400\text{ms} \to 600\text{ms}$).

### 3.2 Pluggable Parser Strategy Pattern
Extraction logic is decoupled into distinct strategy classes implementing `parseCatalog(html, currentUrl, baseUrl)`:
- `ApexTechParser`: Regex and DOM-based extraction for class attributes.
- `GlobalLogisticsParser`: Table-row parsing with price string cleanup (stripping `$`, commas, whitespace).
- `NordicSupplyParser`: JSON parser deserializing embedded Schema.org JSON-LD blocks directly into typed objects.

### 3.3 Data Normalization & Validation (`DataNormalizer`)
Raw scraped data is inherently messy. The normalizer enforces strict invariants:
- **SKU Sanitization:** Strips irregular characters, trims whitespace, and converts to uppercase (`sku-macbook-m3` $\to$ `SKU-MACBOOK-M3`).
- **Numeric Price Conversion:** Coerces formatted currency strings (`$1,749.00`) into standardized positive decimals (`1749.00`).
- **Stock Guardrail:** Clamps negative stock reports to `0`.
- **Category Normalization:** Maps supplier categories to canonical system categories (`Laptops`, `Monitors`, `Networking`, `Storage`, `Components`, `Audio`, `Accessories`, `SBC`, `Sensors`).

### 3.4 Price Arbitrage Analysis
The engine compares scraped wholesale prices against existing system catalog prices:
$$\text{Profit Margin (\$)} = \text{Internal Catalog Retail Price} - \text{Supplier Wholesale Price}$$
$$\text{Margin \%} = \frac{\text{Internal Catalog Price} - \text{Supplier Price}}{\text{Internal Catalog Price}} \times 100$$
This immediately surfaces procurement discount opportunities directly on the dashboard.

---

## 4. Performance Evaluation & Benchmark Results (Deliverable 4)

Automated benchmarks were executed across the simulated portals to evaluate crawler throughput and parsing latency.

### 4.1 Crawl Throughput & Extraction Benchmark

| Supplier Target | Layout Type | Pages Fetched | Items Extracted | Parsing Latency (ms) | Network Latency (ms) | Throughput (Items/sec) | Accuracy Rate |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **Apex Tech Distribution** | CSS Grid + Pagination | 2 | 8 | 1.8 ms | 72.4 ms | **106.8 items/s** | 100% |
| **Global Wholesale Logistics**| Industrial Table | 1 | 8 | 1.2 ms | 64.1 ms | **121.2 items/s** | 100% |
| **Nordic Component Supply** | Schema.org JSON-LD | 1 | 8 | 2.1 ms | 66.8 ms | **114.5 items/s** | 100% |
| **Full Combined Sweep** | **All 3 Suppliers** | **4** | **24** | **4.9 ms** | **67.3 ms avg** | **88.6 items/s** | **100%** |

### 4.2 Benchmark Analysis & Key Observations:
1. **Total Sweep Duration:** The full multi-supplier sweep across all 4 pages and 24 products completes in **271ms**.
2. **Strategy Efficiency:** The table parser (`GlobalLogisticsParser`) exhibited the fastest parsing execution (**1.2ms**) due to flat single-pass regular expression matching.
3. **JSON-LD Reliability:** The semantic JSON-LD parser had zero parsing anomalies or schema ambiguities because it bypassed HTML presentation quirks entirely.
4. **Zero Item Rejections:** All 24 scraped items passed validation rules and conformed to the inventory schema.

---

## 5. Core API Integration & Consistency Guardrails (Deliverable 5)

### 5.1 The `SupplierImportPort` Contract
Member 5 integrates directly with Member 1's Spring Boot backend via `POST /api/suppliers/import`:

```json
{
  "supplierId": "supp_apex_tech",
  "supplierName": "Apex Tech Distribution",
  "targetWarehouseId": "wh_central",
  "autoCreatePurchaseOrder": true,
  "items": [
    {
      "sku": "SKU-MACBOOK-M3",
      "name": "Apple MacBook Pro 14 M3 (16GB, 512GB)",
      "category": "Laptops",
      "supplierPrice": 1749.00,
      "supplierReportedStock": 45,
      "externalUrl": "http://localhost:8085/apex/product/apex-101"
    }
  ]
}
```

### 5.2 Critical System Design Invariant: Zero Physical Stock Inflation
A naive crawler might directly increment warehouse stock upon scraping an external site. **This would violate the fundamental inventory consistency invariant:**
- An external supplier reporting 50 units in stock **does not mean** the warehouse physically holds 50 units.
- **Member 5 Guardrail:** Ingestion updates product catalog master records (SKU, title, category, wholesale pricing) and creates a **Draft Purchase Order** (`STATUS: CREATED`).
- **Physical stock remains 0** until the warehouse manager explicitly inspects physical delivery and calls `POST /api/purchase-orders/{id}/receive`. This guarantees zero overselling.

---

## 6. End-to-End 10-Minute Demonstration Script (Deliverable 6)

Follow this step-by-step checklist during the laboratory evaluation:

1. **Step 1 — Start the Stack:**
   - In Terminal 1: Run simulated supplier portal server:
     ```powershell
     node crawler/supplier_server.js
     ```
   - In Terminal 2: Run frontend (or access running React app at `http://localhost:3000`).
2. **Step 2 — Inspect Simulated Supplier Portals:**
   - Navigate to `http://localhost:8085` in the browser.
   - Click "Apex Tech" $\to$ Observe grid cards with pagination.
   - Click "Global Wholesale" $\to$ Observe table with part numbers and wholesale tiers.
   - Click "Nordic Supply" $\to$ View page source to inspect embedded Schema.org JSON-LD tags.
3. **Step 3 — Run the Web Crawler in the UI:**
   - In the React Dashboard, click **Supplier Crawler** in the sidebar (highlighted with the "Member 5" badge).
   - Check all three suppliers (Apex, Global, Nordic).
   - Select destination warehouse (e.g., `wh_central`).
   - Click **Run Supplier Crawler**.
   - Observe live telemetry: Duration (~270ms), 24 items discovered, ~88 items/sec throughput.
4. **Step 4 — Inspect Price Arbitrage & Margins:**
   - Review the discovered items table.
   - Observe the **Price Arbitrage / Margin** badge showing positive profit margins for existing items (e.g., MacBook wholesale price $1,749 vs retail $1,999 $\to$ **+13% Margin**).
   - Observe the **New Discovery** badge for newly scraped SKUs not yet present in the catalog.
5. **Step 5 — Ingest Feeds to Core Backend:**
   - Click **Ingest Selected to Inventory**.
   - Observe the green confirmation banner displaying the generated `importBatchId` and draft Purchase Order IDs (`po_xxx`).
6. **Step 6 — Verify Integration with Other Modules:**
   - **Member 1 (Catalog):** Navigate to **Product Catalog** $\to$ Verify newly discovered products are registered.
   - **Member 4 (Autocomplete):** Click the top search bar $\to$ Type `"cisco"` or `"rpi"` $\to$ Observe newly crawled items appearing instantly in the Trie dropdown (< 2ms).
   - **Member 1 (Receiving):** Navigate to **Purchase Orders** $\to$ Find the staged draft PO $\to$ Click "Receive Stock" to officially credit physical inventory.
7. **Step 7 — Run the Automated E2E Evaluation Script:**
   - In terminal, execute:
     ```powershell
     node scripts/e2e_integration_demo.js
     ```
   - Shows the evaluation panel the end-to-end verification passing all 6 lifecycle stages.

---

## 7. Verification Test Suite Summary

The crawler automated test suite (`crawler/test_crawler.js`) executes 7 automated verification tests:
- `ApexTechParser`: HTML card extraction, attribute parsing, and pagination link extraction.
- `GlobalLogisticsParser`: Table row parsing, price/stock number parsing.
- `NordicSupplyParser`: Schema.org JSON-LD extraction and deserialization.
- `DataNormalizer`: Whitespace trimming, SKU uppercase conversion, negative price/stock protection.
- `End-to-End Live Crawl`: Verification of 24 items across 4 pages with zero item rejections.

To execute tests:
```powershell
node crawler/test_crawler.js
```
Result: **7 Passed, 0 Failed (100% Success)**.
