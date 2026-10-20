/**
 * Member 5: Simulated Supplier Websites Server
 * Topic 4: Scalable Inventory Management System
 *
 * Simulates three independent wholesale supplier web portals:
 * 1. Apex Tech Distribution (/apex) - Modern e-commerce cards DOM layout with pagination & detail pages
 * 2. Global Wholesale Logistics (/global) - Enterprise tabular layout with volume tiers
 * 3. Nordic Component Supply (/nordic) - Semantic JSON-LD markup and IoT/electronics inventory
 *
 * Built with zero external dependencies (pure Node.js http/url modules).
 */

const http = require('http');
const url = require('url');

const PORT = process.env.PORT || 8085;

// ==========================================
// 1. Apex Tech Distribution Data (/apex)
// ==========================================
const APEX_PRODUCTS = [
  {
    id: 'apex-101',
    sku: 'SKU-MACBOOK-M3',
    name: 'Apple MacBook Pro 14 M3 (16GB, 512GB)',
    category: 'Laptops',
    wholesalePrice: 1749.00,
    listPrice: 1999.00,
    availableStock: 45,
    leadTimeDays: 2,
    rating: 4.9,
    description: 'Next-generation Apple silicon M3 chip with 8-core CPU and 10-core GPU. Liquid Retina XDR display.'
  },
  {
    id: 'apex-102',
    sku: 'SKU-DELL-XPS15',
    name: 'Dell XPS 15 9530 OLED (32GB, 1TB)',
    category: 'Laptops',
    wholesalePrice: 1599.00,
    listPrice: 1899.00,
    availableStock: 28,
    leadTimeDays: 3,
    rating: 4.7,
    description: '15.6 inch 3.5K OLED InfinityEdge touch display, Intel Core i7-13700H, NVIDIA RTX 4060.'
  },
  {
    id: 'apex-103',
    sku: 'SKU-MONITOR-4K',
    name: 'Dell UltraSharp 27 4K USB-C Hub Monitor (U2723QE)',
    category: 'Monitors',
    wholesalePrice: 469.00,
    listPrice: 579.00,
    availableStock: 65,
    leadTimeDays: 1,
    rating: 4.8,
    description: 'IPS Black technology with 2000:1 contrast ratio, 90W power delivery over USB-C, built-in RJ45 hub.'
  },
  {
    id: 'apex-104',
    sku: 'SKU-KEYCHRON-Q1',
    name: 'Keychron Q1 Pro Wireless Custom Mechanical Keyboard',
    category: 'Peripherals',
    wholesalePrice: 165.00,
    listPrice: 199.00,
    availableStock: 80,
    leadTimeDays: 2,
    rating: 4.9,
    description: 'Full CNC aluminum body, QMK/VIA programmable, hot-swappable switches, double-gasket mount.'
  },
  {
    id: 'apex-105',
    sku: 'SKU-SAMSUNG-990PRO',
    name: 'Samsung 990 PRO 2TB PCIe Gen4 NVMe M.2 SSD',
    category: 'Storage',
    wholesalePrice: 154.50,
    listPrice: 189.99,
    availableStock: 110,
    leadTimeDays: 1,
    rating: 4.9,
    description: 'Sequential read speeds up to 7,450 MB/s, proprietary V-NAND technology, heatsink thermal control.'
  },
  {
    id: 'apex-106',
    sku: 'SKU-LOGI-MXMASTER',
    name: 'Logitech MX Master 3S Wireless Performance Mouse',
    category: 'Peripherals',
    wholesalePrice: 79.00,
    listPrice: 99.99,
    availableStock: 135,
    leadTimeDays: 1,
    rating: 4.8,
    description: 'Quiet clicks, 8K DPI any-surface tracking, MagSpeed electromagnetic scroll wheel, USB-C fast charging.'
  },
  {
    id: 'apex-107',
    sku: 'SKU-SONY-WH1000XM5',
    name: 'Sony WH-1000XM5 Wireless Noise Canceling Headphones',
    category: 'Audio',
    wholesalePrice: 320.00,
    listPrice: 399.99,
    availableStock: 50,
    leadTimeDays: 2,
    rating: 4.8,
    description: 'Industry-leading noise cancellation with 8 microphones and Auto NC Optimizer. 30-hour battery life.'
  },
  {
    id: 'apex-108',
    sku: 'SKU-ANKER-DOCK-778',
    name: 'Anker 778 Thunderbolt 4 Docking Station (12-in-1)',
    category: 'Accessories',
    wholesalePrice: 219.00,
    listPrice: 279.99,
    availableStock: 42,
    leadTimeDays: 3,
    rating: 4.6,
    description: 'Thunderbolt 4 upstream port, 100W Max charging, 8K@30Hz or dual 4K@60Hz display support, 2.5GbE Ethernet.'
  }
];

// ==========================================
// 2. Global Wholesale Logistics Data (/global)
// ==========================================
const GLOBAL_PRODUCTS = [
  {
    sku: 'SKU-CISCO-C9200',
    partNumber: 'C9200L-48P-4G-E',
    title: 'Cisco Catalyst 9200L 48-Port PoE+ Gigabit Switch with 4x1G Uplinks',
    category: 'Networking',
    tier1Price: 2380.00,
    stockCount: 22,
    warehouseLoc: 'US-CENTRAL-01',
    minOrderQty: 1
  },
  {
    sku: 'SKU-UBIQUITI-U6PRO',
    partNumber: 'U6-Pro-US',
    title: 'Ubiquiti UniFi 6 Pro Dual-Band WiFi 6 Access Point',
    category: 'Networking',
    tier1Price: 139.50,
    stockCount: 88,
    warehouseLoc: 'US-EAST-02',
    minOrderQty: 2
  },
  {
    sku: 'SKU-SYNOLOGY-DS923',
    partNumber: 'DS923+',
    title: 'Synology DiskStation DS923+ 4-Bay Network Attached Storage',
    category: 'Storage',
    tier1Price: 529.00,
    stockCount: 34,
    warehouseLoc: 'US-WEST-01',
    minOrderQty: 1
  },
  {
    sku: 'SKU-SEAGATE-EXOS16',
    partNumber: 'ST16000NM001G',
    title: 'Seagate Exos X18 16TB Enterprise SATA 6Gb/s 7200 RPM 3.5-Inch HDD',
    category: 'Storage',
    tier1Price: 249.00,
    stockCount: 75,
    warehouseLoc: 'US-CENTRAL-01',
    minOrderQty: 4
  },
  {
    sku: 'SKU-CORSAIR-RM850X',
    partNumber: 'CP-9020200-NA',
    title: 'Corsair RM850x 850W Fully Modular 80 Plus Gold ATX Power Supply',
    category: 'Components',
    tier1Price: 119.50,
    stockCount: 60,
    warehouseLoc: 'US-EAST-02',
    minOrderQty: 2
  },
  {
    sku: 'SKU-KINGSTON-SERVER-RAM',
    partNumber: 'KSM48E40BD8KM-32HM',
    title: 'Kingston 64GB DDR5 4800MHz ECC Unbuffered DIMM Server Memory',
    category: 'Components',
    tier1Price: 198.00,
    stockCount: 48,
    warehouseLoc: 'US-WEST-01',
    minOrderQty: 2
  },
  {
    sku: 'SKU-APC-SMART-UPS',
    partNumber: 'SMT1500C',
    title: 'APC Smart-UPS 1500VA LCD 120V with SmartConnect Battery Backup',
    category: 'Power',
    tier1Price: 549.00,
    stockCount: 16,
    warehouseLoc: 'US-CENTRAL-01',
    minOrderQty: 1
  },
  {
    sku: 'SKU-CAT6A-1000FT',
    partNumber: 'MP-CAT6A-1000-BLU',
    title: 'Monoprice Cat6A Bulk Pure Bare Copper Cable 1000ft 550MHz Blue',
    category: 'Cabling',
    tier1Price: 172.00,
    stockCount: 115,
    warehouseLoc: 'US-CENTRAL-01',
    minOrderQty: 1
  }
];

// ==========================================
// 3. Nordic Component Supply Data (/nordic)
// ==========================================
const NORDIC_PRODUCTS = [
  {
    id: 'nordic-501',
    sku: 'SKU-RPI-5-8GB',
    name: 'Raspberry Pi 5 Single Board Computer (8GB RAM)',
    category: 'SBC',
    currency: 'USD',
    price: 74.50,
    stockStatus: 'InStock',
    inventoryLevel: 160,
    manufacturer: 'Raspberry Pi Foundation',
    gtin: '0617588405587'
  },
  {
    id: 'nordic-502',
    sku: 'SKU-ARDUINO-GIGA',
    name: 'Arduino Giga R1 WiFi Dual-Core Cortex-M7/M4 Board',
    category: 'Microcontrollers',
    currency: 'USD',
    price: 64.00,
    stockStatus: 'InStock',
    inventoryLevel: 82,
    manufacturer: 'Arduino',
    gtin: '7630049203914'
  },
  {
    id: 'nordic-503',
    sku: 'SKU-BOSCH-BME688',
    name: 'Bosch Sensortec BME688 4-in-1 Gas & Environmental Sensor',
    category: 'Sensors',
    currency: 'USD',
    price: 19.80,
    stockStatus: 'InStock',
    inventoryLevel: 320,
    manufacturer: 'Bosch Sensortec',
    gtin: '4057749118742'
  },
  {
    id: 'nordic-504',
    sku: 'SKU-NORDIC-NRF5340',
    name: 'Nordic Semiconductor nRF5340 Dual-Core BLE Development Kit',
    category: 'Wireless',
    currency: 'USD',
    price: 45.00,
    stockStatus: 'InStock',
    inventoryLevel: 58,
    manufacturer: 'Nordic Semiconductor',
    gtin: '7072465000543'
  },
  {
    id: 'nordic-505',
    sku: 'SKU-JBL-FLIP6',
    name: 'JBL Flip 6 Portable Waterproof Bluetooth Speaker',
    category: 'Audio',
    currency: 'USD',
    price: 98.00,
    stockStatus: 'InStock',
    inventoryLevel: 70,
    manufacturer: 'JBL',
    gtin: '0500363842187'
  },
  {
    id: 'nordic-506',
    sku: 'SKU-SONY-INZONE-H9',
    name: 'Sony INZONE H9 Wireless Gaming Noise Canceling Headset',
    category: 'Audio',
    currency: 'USD',
    price: 249.00,
    stockStatus: 'InStock',
    inventoryLevel: 38,
    manufacturer: 'Sony',
    gtin: '0272429243763'
  },
  {
    id: 'nordic-507',
    sku: 'SKU-LG-C3-OLED42',
    name: 'LG C3 42-Inch OLED 4K UHD 120Hz Smart Monitor/TV',
    category: 'Monitors',
    currency: 'USD',
    price: 869.00,
    stockStatus: 'LowStock',
    inventoryLevel: 12,
    manufacturer: 'LG Electronics',
    gtin: '0195174052020'
  },
  {
    id: 'nordic-508',
    sku: 'SKU-ANKER-737',
    name: 'Anker 737 Power Bank (PowerCore 24K, 140W Multi-Port)',
    category: 'Accessories',
    currency: 'USD',
    price: 109.00,
    stockStatus: 'InStock',
    inventoryLevel: 94,
    manufacturer: 'Anker Innovations',
    gtin: '0194644098674'
  }
];

// Helper to wrap in sleek dark HTML layout
function renderHtmlPage(title, supplierBadge, bodyContent) {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${title}</title>
  <style>
    :root {
      --bg: #0f172a;
      --card-bg: #1e293b;
      --border: #334155;
      --text: #f8fafc;
      --muted: #94a3b8;
      --primary: #38bdf8;
      --accent: #10b981;
      --warning: #f59e0b;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      background: var(--bg);
      color: var(--text);
      line-height: 1.5;
      padding: 24px;
    }
    header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding-bottom: 20px;
      border-bottom: 1px solid var(--border);
      margin-bottom: 24px;
    }
    .badge {
      display: inline-block;
      padding: 4px 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      background: rgba(56, 189, 248, 0.15);
      color: var(--primary);
      border: 1px solid rgba(56, 189, 248, 0.3);
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 20px;
    }
    .product-card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 16px;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      transition: transform 0.15s ease, border-color 0.15s ease;
    }
    .product-card:hover {
      border-color: var(--primary);
      transform: translateY(-2px);
    }
    .sku-tag {
      font-family: monospace;
      font-size: 12px;
      color: var(--muted);
      margin-bottom: 6px;
    }
    .product-title {
      font-size: 16px;
      font-weight: 600;
      margin-bottom: 8px;
      color: #fff;
    }
    .category-pill {
      font-size: 11px;
      background: #334155;
      color: #cbd5e1;
      padding: 2px 8px;
      border-radius: 4px;
      display: inline-block;
      margin-bottom: 12px;
    }
    .price-row {
      display: flex;
      justify-content: space-between;
      align-items: baseline;
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid rgba(255,255,255,0.08);
    }
    .price-tag {
      font-size: 20px;
      font-weight: 700;
      color: var(--accent);
    }
    .stock-badge {
      font-size: 13px;
      font-weight: 500;
      color: #38bdf8;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      background: var(--card-bg);
      border-radius: 8px;
      overflow: hidden;
      border: 1px solid var(--border);
    }
    th, td {
      padding: 12px 16px;
      text-align: left;
      border-bottom: 1px solid var(--border);
    }
    th {
      background: #1e293b;
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--muted);
    }
    tr:hover td {
      background: rgba(255, 255, 255, 0.03);
    }
    .pagination {
      margin-top: 24px;
      display: flex;
      gap: 10px;
      align-items: center;
    }
    .pagination a {
      color: var(--primary);
      text-decoration: none;
      padding: 6px 12px;
      border: 1px solid var(--border);
      border-radius: 4px;
    }
    .pagination a.active {
      background: var(--primary);
      color: #0f172a;
      font-weight: bold;
    }
    nav a {
      color: var(--primary);
      text-decoration: none;
      margin-right: 16px;
    }
    nav a:hover { text-decoration: underline; }
  </style>
</head>
<body>
  <header>
    <div>
      <h1 style="font-size: 22px; margin-bottom: 4px;">${title}</h1>
      <span class="badge">${supplierBadge}</span>
    </div>
    <nav>
      <a href="/">Hub Index</a>
      <a href="/apex/products">Apex Tech</a>
      <a href="/global/catalog">Global Wholesale</a>
      <a href="/nordic/store">Nordic Supply</a>
    </nav>
  </header>
  <main>
    ${bodyContent}
  </main>
</body>
</html>`;
}

// Request Router
const server = http.createServer((req, res) => {
  const reqUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = reqUrl.pathname;
  const searchParams = reqUrl.searchParams;
  const query = Object.fromEntries(searchParams.entries());

  // Add CORS headers for direct browser preview
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(200);
    res.end();
    return;
  }

  // ==========================================
  // Root Hub Page (Simulator Navigation)
  // ==========================================
  if (pathname === '/') {
    const hubHtml = `
      <div style="max-width: 800px; margin: 0 auto; padding-top: 20px;">
        <h2 style="font-size: 24px; margin-bottom: 12px; color: #38bdf8;">Simulated Wholesale Supplier Portal Hub</h2>
        <p style="color: #94a3b8; margin-bottom: 24px;">
          This mock server hosts 3 distinct wholesale supplier websites for <strong>Member 5 (Supplier Crawler & Integration)</strong>.
          The Web Crawler traverses these simulated pages, parses various DOM layouts, normalizes catalogs, and feeds data into the Core Inventory API.
        </p>

        <div class="grid" style="grid-template-columns: 1fr; gap: 16px;">
          <div class="product-card">
            <h3 style="color: #fff; margin-bottom: 6px;">1. Apex Tech Distribution</h3>
            <p style="color: #94a3b8; font-size: 14px; margin-bottom: 12px;">
              Consumer computing & accessories. Features an HTML product cards layout with CSS classes (<code>.product-card</code>, <code>.sku-tag</code>, <code>.price-tag</code>, <code>.stock-badge</code>) across multiple paginated pages.
            </p>
            <div>
              <a href="/apex/products?page=1" style="color: #38bdf8; font-weight: 600; text-decoration: none;">View Apex Catalog (Page 1) &rarr;</a> |
              <a href="/apex/robots.txt" style="color: #94a3b8; font-size: 13px; text-decoration: none; margin-left: 8px;">robots.txt</a>
            </div>
          </div>

          <div class="product-card">
            <h3 style="color: #fff; margin-bottom: 6px;">2. Global Wholesale Logistics</h3>
            <p style="color: #94a3b8; font-size: 14px; margin-bottom: 12px;">
              Enterprise IT & networking gear. Uses an industrial HTML table structure (<code>&lt;table&gt;&lt;tr class="catalog-row"&gt;</code>) with embedded SKUs, stock counts, and wholesale tier prices.
            </p>
            <div>
              <a href="/global/catalog" style="color: #38bdf8; font-weight: 600; text-decoration: none;">View Global Catalog Table &rarr;</a>
            </div>
          </div>

          <div class="product-card">
            <h3 style="color: #fff; margin-bottom: 6px;">3. Nordic Component Supply</h3>
            <p style="color: #94a3b8; font-size: 14px; margin-bottom: 12px;">
              Smart sensors, IoT & SBC devices. Employs modern semantic web markup: structured <strong>Schema.org JSON-LD</strong> embedded directly into the HTML <code>&lt;script type="application/ld+json"&gt;</code> tags.
            </p>
            <div>
              <a href="/nordic/store" style="color: #38bdf8; font-weight: 600; text-decoration: none;">View Nordic Storefront &rarr;</a>
            </div>
          </div>
        </div>

        <div style="margin-top: 32px; padding: 16px; background: rgba(56, 189, 248, 0.05); border: 1px solid rgba(56, 189, 248, 0.2); border-radius: 8px;">
          <h4 style="color: #38bdf8; margin-bottom: 6px;">Crawler Integration Ready</h4>
          <p style="font-size: 13px; color: #cbd5e1;">
            Target API: <code>POST http://localhost:8080/api/suppliers/import</code><br/>
            Running Port: <code>http://localhost:${PORT}</code>
          </p>
        </div>
      </div>
    `;
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(renderHtmlPage('Simulated Supplier Hub - Member 5', 'Simulator Gateway', hubHtml));
    return;
  }

  // ==========================================
  // Apex Tech Distribution (/apex)
  // ==========================================
  if (pathname === '/apex/robots.txt') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end("User-agent: *\nAllow: /apex/products\nCrawl-delay: 0.1\nSitemap: http://localhost:8085/apex/sitemap.xml\n");
    return;
  }

  if (pathname === '/apex/products') {
    const page = parseInt(query.page || '1', 10);
    const pageSize = 4;
    const startIndex = (page - 1) * pageSize;
    const pagedItems = APEX_PRODUCTS.slice(startIndex, startIndex + pageSize);
    const totalPages = Math.ceil(APEX_PRODUCTS.length / pageSize);

    let cardsHtml = `<div class="grid">`;
    for (const item of pagedItems) {
      cardsHtml += `
        <article class="product-card" id="${item.id}" data-sku="${item.sku}">
          <div>
            <div class="sku-tag">SKU: <span class="sku-val">${item.sku}</span></div>
            <h2 class="product-title">${item.name}</h2>
            <span class="category-pill">${item.category}</span>
            <p style="font-size: 13px; color: #94a3b8; margin-bottom: 12px;">${item.description}</p>
          </div>
          <div>
            <div class="price-row">
              <span class="price-tag">$${item.wholesalePrice.toFixed(2)}</span>
              <span class="stock-badge">In Stock: <strong class="stock-count">${item.availableStock}</strong> units</span>
            </div>
            <div style="margin-top: 10px; font-size: 12px; color: #64748b; display: flex; justify-content: space-between;">
              <span>Lead time: ${item.leadTimeDays}d</span>
              <a href="/apex/product/${item.id}" class="details-link" style="color: #38bdf8; text-decoration: none;">View Detail &rarr;</a>
            </div>
          </div>
        </article>
      `;
    }
    cardsHtml += `</div>`;

    // Pagination links
    cardsHtml += `<div class="pagination"><span>Page:</span>`;
    for (let p = 1; p <= totalPages; p++) {
      cardsHtml += `<a href="/apex/products?page=${p}" class="${p === page ? 'active' : ''}">${p}</a>`;
    }
    cardsHtml += `</div>`;

    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(renderHtmlPage('Apex Tech Distribution - Catalog', 'Apex Tech Portal (Grid DOM)', cardsHtml));
    return;
  }

  // Apex Product Detail Page
  if (pathname.startsWith('/apex/product/')) {
    const productId = pathname.split('/').pop();
    const item = APEX_PRODUCTS.find(p => p.id === productId);
    if (!item) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Product not found in Apex catalog.');
      return;
    }
    const detailHtml = `
      <div style="max-width: 600px; margin: 0 auto; background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 24px;">
        <div class="sku-tag" style="font-size: 14px;">SKU: <span class="sku-val">${item.sku}</span></div>
        <h1 style="font-size: 22px; margin: 8px 0 16px 0;">${item.name}</h1>
        <span class="category-pill">${item.category}</span>
        <p style="color: #94a3b8; margin: 16px 0;">${item.description}</p>
        <div class="price-row" style="margin-top: 24px;">
          <div>
            <div style="font-size: 12px; color: #94a3b8;">Wholesale Price</div>
            <span class="price-tag">$${item.wholesalePrice.toFixed(2)}</span>
            <span style="font-size: 13px; color: #64748b; text-decoration: line-through; margin-left: 8px;">MSRP $${item.listPrice.toFixed(2)}</span>
          </div>
          <div>
            <div style="font-size: 12px; color: #94a3b8;">Wholesale Availability</div>
            <span class="stock-badge" style="font-size: 16px;"><strong class="stock-count">${item.availableStock}</strong> units</span>
          </div>
        </div>
        <div style="margin-top: 24px; padding-top: 16px; border-top: 1px solid #334155;">
          <a href="/apex/products" style="color: #38bdf8; text-decoration: none;">&larr; Back to Catalog</a>
        </div>
      </div>
    `;
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(renderHtmlPage(item.name + ' - Apex Tech', 'Apex Tech Portal', detailHtml));
    return;
  }

  // ==========================================
  // Global Wholesale Logistics (/global)
  // ==========================================
  if (pathname === '/global/catalog') {
    let tableHtml = `
      <div style="margin-bottom: 16px;">
        <h2 style="font-size: 18px; margin-bottom: 6px;">Enterprise Equipment & Logistics Master Feed</h2>
        <p style="font-size: 13px; color: #94a3b8;">Industrial B2B wholesale pricing table. Verified live stock counts across central fulfillment centers.</p>
      </div>
      <table class="inventory-table">
        <thead>
          <tr>
            <th>SKU</th>
            <th>Part Number</th>
            <th>Product Description</th>
            <th>Category</th>
            <th>Tier 1 Price</th>
            <th>Availability</th>
            <th>Location</th>
          </tr>
        </thead>
        <tbody>
    `;

    for (const item of GLOBAL_PRODUCTS) {
      tableHtml += `
        <tr class="catalog-row" data-sku="${item.sku}">
          <td class="col-sku" style="font-family: monospace; font-weight: bold; color: #38bdf8;">${item.sku}</td>
          <td class="col-part" style="font-family: monospace; font-size: 12px; color: #94a3b8;">${item.partNumber}</td>
          <td class="col-title" style="font-weight: 500;">
            <a href="/global/item/${item.sku}" class="item-link" style="color: #f8fafc; text-decoration: none;">${item.title}</a>
          </td>
          <td class="col-category"><span class="category-pill" style="margin: 0;">${item.category}</span></td>
          <td class="col-price" style="font-weight: bold; color: #10b981;">$${item.tier1Price.toFixed(2)}</td>
          <td class="col-stock">
            <span class="stock-status" style="color: #38bdf8; font-weight: 600;">${item.stockCount} in stock</span>
          </td>
          <td class="col-loc" style="font-size: 12px; color: #94a3b8;">${item.warehouseLoc}</td>
        </tr>
      `;
    }

    tableHtml += `
        </tbody>
      </table>
      <div style="margin-top: 16px; font-size: 12px; color: #64748b;">
        * Note: Tier 1 pricing applies to standard purchase orders. Orders over 100 units qualify for an additional 4% rebate.
      </div>
    `;

    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(renderHtmlPage('Global Wholesale Logistics - Master Table', 'Global Logistics (Tabular Layout)', tableHtml));
    return;
  }

  if (pathname.startsWith('/global/item/')) {
    const sku = pathname.split('/').pop();
    const item = GLOBAL_PRODUCTS.find(p => p.sku === sku);
    if (!item) {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('Item not found in Global Wholesale catalog.');
      return;
    }
    const itemHtml = `
      <div style="max-width: 600px; margin: 0 auto; background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 24px;">
        <span class="category-pill">${item.category}</span>
        <h1 style="font-size: 20px; margin: 12px 0;">${item.title}</h1>
        <p style="font-family: monospace; color: #94a3b8; font-size: 13px;">SKU: <strong style="color: #38bdf8;">${item.sku}</strong> | Part #: ${item.partNumber}</p>
        <div class="price-row" style="margin: 24px 0;">
          <div>
            <div style="font-size: 12px; color: #94a3b8;">Tier 1 Wholesale Price</div>
            <div style="font-size: 22px; font-weight: bold; color: #10b981;">$${item.tier1Price.toFixed(2)}</div>
          </div>
          <div>
            <div style="font-size: 12px; color: #94a3b8;">Warehouse Stock</div>
            <div style="font-size: 18px; font-weight: bold; color: #38bdf8;">${item.stockCount} units</div>
          </div>
        </div>
        <a href="/global/catalog" style="color: #38bdf8; text-decoration: none;">&larr; Back to Wholesale Table</a>
      </div>
    `;
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(renderHtmlPage(item.sku + ' - Global Wholesale', 'Global Logistics', itemHtml));
    return;
  }

  // ==========================================
  // Nordic Component Supply (/nordic) - Semantic JSON-LD
  // ==========================================
  if (pathname === '/nordic/store') {
    let storeHtml = `
      <div style="margin-bottom: 20px;">
        <h2 style="font-size: 20px; margin-bottom: 6px;">Nordic Component Supply - Smart IoT & Developer Inventory</h2>
        <p style="font-size: 13px; color: #94a3b8;">
          This storefront embeds structured <strong>Schema.org JSON-LD</strong> metadata inside <code>&lt;script type="application/ld+json"&gt;</code> tags.
          The crawler extracts structured product data directly from standard schema definitions.
        </p>
      </div>
      <div class="grid">
    `;

    for (const item of NORDIC_PRODUCTS) {
      const jsonLd = {
        "@context": "https://schema.org/",
        "@type": "Product",
        "sku": item.sku,
        "name": item.name,
        "category": item.category,
        "gtin": item.gtin,
        "brand": {
          "@type": "Brand",
          "name": item.manufacturer
        },
        "offers": {
          "@type": "Offer",
          "priceCurrency": item.currency,
          "price": item.price,
          "availability": `https://schema.org/${item.stockStatus}`,
          "inventoryLevel": {
            "@type": "QuantitativeValue",
            "value": item.inventoryLevel
          }
        }
      };

      storeHtml += `
        <div class="product-card" id="${item.id}">
          <!-- Embedded Schema.org JSON-LD metadata for semantic crawlers -->
          <script type="application/ld+json">
${JSON.stringify(jsonLd, null, 2)}
          </script>
          <div>
            <div class="sku-tag">SKU: <span class="sku-value">${item.sku}</span></div>
            <h3 class="product-name" style="font-size: 16px; margin-bottom: 8px;">${item.name}</h3>
            <span class="category-pill">${item.category}</span>
            <div style="font-size: 12px; color: #94a3b8; margin-top: 4px;">Brand: ${item.manufacturer}</div>
          </div>
          <div class="price-row">
            <span class="price-display" style="font-size: 20px; font-weight: bold; color: #10b981;">$${item.price.toFixed(2)}</span>
            <span class="stock-display" style="color: #38bdf8; font-weight: 600;">Stock: ${item.inventoryLevel}</span>
          </div>
        </div>
      `;
    }

    storeHtml += `</div>`;

    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(renderHtmlPage('Nordic Component Supply - IoT Storefront', 'Nordic Supply (JSON-LD Semantic)', storeHtml));
    return;
  }

  // ==========================================
  // Direct JSON API endpoints (convenience / health checks)
  // ==========================================
  if (pathname === '/api/suppliers/apex') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(APEX_PRODUCTS));
    return;
  }
  if (pathname === '/api/suppliers/global') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(GLOBAL_PRODUCTS));
    return;
  }
  if (pathname === '/api/suppliers/nordic') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(NORDIC_PRODUCTS));
    return;
  }

  // Health check endpoint
  if (pathname === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'UP', service: 'Simulated Supplier Portal Server', port: PORT }));
    return;
  }

  // Fallback 404
  res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(renderHtmlPage('404 Not Found', 'Error', '<p style="color: #ef4444;">Requested supplier page was not found.</p><p><a href="/" style="color: #38bdf8;">Return to Hub &rarr;</a></p>'));
});

server.listen(PORT, () => {
  console.log(`=======================================================`);
  console.log(` simulated Supplier Websites Server Running on port ${PORT}`);
  console.log(`=======================================================`);
  console.log(`  * Portal Hub:      http://localhost:${PORT}/`);
  console.log(`  * Apex Tech:       http://localhost:${PORT}/apex/products`);
  console.log(`  * Global Wholesale:http://localhost:${PORT}/global/catalog`);
  console.log(`  * Nordic Supply:   http://localhost:${PORT}/nordic/store`);
  console.log(`=======================================================`);
});

// Export server instance for testing
module.exports = server;
