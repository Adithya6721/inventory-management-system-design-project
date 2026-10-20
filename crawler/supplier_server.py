"""
Member 5: Simulated Wholesale Supplier Server (Python 3 Edition)
Topic 4: Scalable Inventory Management System

Pure Python standard library implementation (zero pip dependencies required).
Hosts 3 simulated wholesale supplier web portals:
- Apex Tech Distribution (/apex/products)
- Global Wholesale Logistics (/global/catalog)
- Nordic Component Supply (/nordic/store)
"""

import http.server
import json
import os
import socketserver
import urllib.parse
from dataclasses import asdict, dataclass

PORT = int(os.environ.get("PORT", 8085))

APEX_PRODUCTS = [
    {"id": "apex-101", "sku": "SKU-MACBOOK-M3", "name": "Apple MacBook Pro 14 M3 (16GB, 512GB)", "category": "Laptops", "wholesalePrice": 1749.00, "availableStock": 45, "description": "Next-generation Apple silicon M3 chip with 8-core CPU and 10-core GPU."},
    {"id": "apex-102", "sku": "SKU-DELL-XPS15", "name": "Dell XPS 15 9530 OLED (32GB, 1TB)", "category": "Laptops", "wholesalePrice": 1599.00, "availableStock": 28, "description": "15.6 inch 3.5K OLED InfinityEdge touch display, Intel Core i7-13700H."},
    {"id": "apex-103", "sku": "SKU-MONITOR-4K", "name": "Dell UltraSharp 27 4K USB-C Hub Monitor (U2723QE)", "category": "Monitors", "wholesalePrice": 469.00, "availableStock": 65, "description": "IPS Black technology with 2000:1 contrast ratio, 90W power delivery."},
    {"id": "apex-104", "sku": "SKU-KEYCHRON-Q1", "name": "Keychron Q1 Pro Wireless Custom Mechanical Keyboard", "category": "Peripherals", "wholesalePrice": 165.00, "availableStock": 80, "description": "Full CNC aluminum body, QMK/VIA programmable, hot-swappable switches."},
    {"id": "apex-105", "sku": "SKU-SAMSUNG-990PRO", "name": "Samsung 990 PRO 2TB PCIe Gen4 NVMe M.2 SSD", "category": "Storage", "wholesalePrice": 154.50, "availableStock": 110, "description": "Sequential read speeds up to 7,450 MB/s, proprietary V-NAND."},
    {"id": "apex-106", "sku": "SKU-LOGI-MXMASTER", "name": "Logitech MX Master 3S Wireless Performance Mouse", "category": "Peripherals", "wholesalePrice": 79.00, "availableStock": 135, "description": "Quiet clicks, 8K DPI any-surface tracking, MagSpeed wheel."},
    {"id": "apex-107", "sku": "SKU-SONY-WH1000XM5", "name": "Sony WH-1000XM5 Wireless Noise Canceling Headphones", "category": "Audio", "wholesalePrice": 320.00, "availableStock": 50, "description": "Industry-leading noise cancellation with 8 microphones. 30-hour battery."},
    {"id": "apex-108", "sku": "SKU-ANKER-DOCK-778", "name": "Anker 778 Thunderbolt 4 Docking Station (12-in-1)", "category": "Accessories", "wholesalePrice": 219.00, "availableStock": 42, "description": "Thunderbolt 4 upstream port, 100W Max charging, dual 4K@60Hz display support."}
]

GLOBAL_PRODUCTS = [
    {"sku": "SKU-CISCO-C9200", "partNumber": "C9200L-48P-4G-E", "title": "Cisco Catalyst 9200L 48-Port PoE+ Gigabit Switch", "category": "Networking", "tier1Price": 2380.00, "stockCount": 22, "warehouseLoc": "US-CENTRAL-01"},
    {"sku": "SKU-UBIQUITI-U6PRO", "partNumber": "U6-Pro-US", "title": "Ubiquiti UniFi 6 Pro Dual-Band WiFi 6 Access Point", "category": "Networking", "tier1Price": 139.50, "stockCount": 88, "warehouseLoc": "US-EAST-02"},
    {"sku": "SKU-SYNOLOGY-DS923", "partNumber": "DS923+", "title": "Synology DiskStation DS923+ 4-Bay Network Attached Storage", "category": "Storage", "tier1Price": 529.00, "stockCount": 34, "warehouseLoc": "US-WEST-01"},
    {"sku": "SKU-SEAGATE-EXOS16", "partNumber": "ST16000NM001G", "title": "Seagate Exos X18 16TB Enterprise SATA 7200 RPM HDD", "category": "Storage", "tier1Price": 249.00, "stockCount": 75, "warehouseLoc": "US-CENTRAL-01"},
    {"sku": "SKU-CORSAIR-RM850X", "partNumber": "CP-9020200-NA", "title": "Corsair RM850x 850W Fully Modular 80 Plus Gold PSU", "category": "Components", "tier1Price": 119.50, "stockCount": 60, "warehouseLoc": "US-EAST-02"},
    {"sku": "SKU-KINGSTON-SERVER-RAM", "partNumber": "KSM48E40BD8KM-32HM", "title": "Kingston 64GB DDR5 4800MHz ECC Server Memory", "category": "Components", "tier1Price": 198.00, "stockCount": 48, "warehouseLoc": "US-WEST-01"},
    {"sku": "SKU-APC-SMART-UPS", "partNumber": "SMT1500C", "title": "APC Smart-UPS 1500VA LCD 120V Battery Backup", "category": "Power", "tier1Price": 549.00, "stockCount": 16, "warehouseLoc": "US-CENTRAL-01"},
    {"sku": "SKU-CAT6A-1000FT", "partNumber": "MP-CAT6A-1000-BLU", "title": "Monoprice Cat6A Bulk Pure Copper Cable 1000ft Blue", "category": "Cabling", "tier1Price": 172.00, "stockCount": 115, "warehouseLoc": "US-CENTRAL-01"}
]

NORDIC_PRODUCTS = [
    {"id": "nordic-501", "sku": "SKU-RPI-5-8GB", "name": "Raspberry Pi 5 Single Board Computer (8GB RAM)", "category": "SBC", "price": 74.50, "inventoryLevel": 160, "manufacturer": "Raspberry Pi Foundation"},
    {"id": "nordic-502", "sku": "SKU-ARDUINO-GIGA", "name": "Arduino Giga R1 WiFi Dual-Core Cortex-M7/M4 Board", "category": "Microcontrollers", "price": 64.00, "inventoryLevel": 82, "manufacturer": "Arduino"},
    {"id": "nordic-503", "sku": "SKU-BOSCH-BME688", "name": "Bosch Sensortec BME688 4-in-1 Gas & Environmental Sensor", "category": "Sensors", "price": 19.80, "inventoryLevel": 320, "manufacturer": "Bosch Sensortec"},
    {"id": "nordic-504", "sku": "SKU-NORDIC-NRF5340", "name": "Nordic Semiconductor nRF5340 Dual-Core BLE Development Kit", "category": "Wireless", "price": 45.00, "inventoryLevel": 58, "manufacturer": "Nordic Semiconductor"},
    {"id": "nordic-505", "sku": "SKU-JBL-FLIP6", "name": "JBL Flip 6 Portable Waterproof Bluetooth Speaker", "category": "Audio", "price": 98.00, "inventoryLevel": 70, "manufacturer": "JBL"},
    {"id": "nordic-506", "sku": "SKU-SONY-INZONE-H9", "name": "Sony INZONE H9 Wireless Gaming Noise Canceling Headset", "category": "Audio", "price": 249.00, "inventoryLevel": 38, "manufacturer": "Sony"},
    {"id": "nordic-507", "sku": "SKU-LG-C3-OLED42", "name": "LG C3 42-Inch OLED 4K UHD 120Hz Smart Monitor/TV", "category": "Monitors", "price": 869.00, "inventoryLevel": 12, "manufacturer": "LG Electronics"},
    {"id": "nordic-508", "sku": "SKU-ANKER-737", "name": "Anker 737 Power Bank (PowerCore 24K, 140W Multi-Port)", "category": "Accessories", "price": 109.00, "inventoryLevel": 94, "manufacturer": "Anker Innovations"}
]

def render_html_wrapper(title: str, badge: str, content: str) -> str:
    return f"""<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>{title}</title>
  <style>
    body {{ background: #0f172a; color: #f8fafc; font-family: sans-serif; padding: 24px; }}
    .badge {{ background: #1e293b; color: #38bdf8; padding: 4px 10px; border-radius: 999px; font-size: 12px; }}
    .product-card {{ background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 16px; margin-bottom: 12px; }}
    .sku-tag {{ color: #94a3b8; font-family: monospace; font-size: 12px; }}
    .price-tag {{ color: #10b981; font-size: 20px; font-weight: bold; }}
    .stock-badge {{ color: #38bdf8; font-size: 14px; font-weight: bold; }}
    table {{ width: 100%; border-collapse: collapse; background: #1e293b; }}
    th, td {{ padding: 12px; border: 1px solid #334155; text-align: left; }}
    th {{ background: #0f172a; color: #94a3b8; font-size: 12px; text-transform: uppercase; }}
    .pagination a {{ color: #38bdf8; padding: 6px 12px; border: 1px solid #334155; text-decoration: none; margin-right: 6px; }}
    .pagination a.active {{ background: #38bdf8; color: #0f172a; font-weight: bold; }}
  </style>
</head>
<body>
  <header style="margin-bottom: 24px; border-bottom: 1px solid #334155; padding-bottom: 16px;">
    <h1>{title}</h1>
    <span class="badge">{badge}</span>
  </header>
  <main>{content}</main>
</body>
</html>"""

class SupplierRequestHandler(http.server.BaseHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, OPTIONS')
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        query = urllib.parse.parse_qs(parsed.query)

        if path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"status": "UP", "service": "Python Simulated Supplier Server", "port": PORT}).encode("utf-8"))
            return

        if path == "/apex/robots.txt":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"User-agent: *\nAllow: /apex/products\nCrawl-delay: 0.1\n")
            return

        if path == "/apex/products":
            page = int(query.get("page", ["1"])[0])
            page_size = 4
            start = (page - 1) * page_size
            items = APEX_PRODUCTS[start:start+page_size]
            cards = ""
            for it in items:
                cards += f"""
                <article class="product-card" id="{it['id']}" data-sku="{it['sku']}">
                    <div class="sku-tag">SKU: <span class="sku-val">{it['sku']}</span></div>
                    <h2 class="product-title">{it['name']}</h2>
                    <span class="category-pill">{it['category']}</span>
                    <p style="color: #94a3b8; font-size: 13px;">{it['description']}</p>
                    <div style="margin-top: 12px; display: flex; justify-content: space-between;">
                        <span class="price-tag">${it['wholesalePrice']:.2f}</span>
                        <span class="stock-badge">In Stock: <strong class="stock-count">{it['availableStock']}</strong> units</span>
                    </div>
                </article>
                """
            cards += f"""<div class="pagination"><span>Page:</span>
                <a href="/apex/products?page=1" class="{'active' if page == 1 else ''}">1</a>
                <a href="/apex/products?page=2" class="{'active' if page == 2 else ''}">2</a>
            </div>"""
            html = render_html_wrapper("Apex Tech Distribution - Catalog", "Apex Tech Grid", cards)
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html.encode("utf-8"))
            return

        if path == "/global/catalog":
            rows = ""
            for it in GLOBAL_PRODUCTS:
                rows += f"""
                <tr class="catalog-row" data-sku="{it['sku']}">
                    <td class="col-sku">{it['sku']}</td>
                    <td class="col-part">{it['partNumber']}</td>
                    <td class="col-title"><a href="/global/item/{it['sku']}" class="item-link">{it['title']}</a></td>
                    <td class="col-category"><span class="category-pill">{it['category']}</span></td>
                    <td class="col-price">${it['tier1Price']:.2f}</td>
                    <td class="col-stock"><span class="stock-status">{it['stockCount']} in stock</span></td>
                    <td class="col-loc">{it['warehouseLoc']}</td>
                </tr>
                """
            table = f"""<table class="inventory-table">
                <thead><tr><th>SKU</th><th>Part #</th><th>Title</th><th>Category</th><th>Price</th><th>Stock</th><th>Location</th></tr></thead>
                <tbody>{rows}</tbody>
            </table>"""
            html = render_html_wrapper("Global Wholesale Logistics", "Global Logistics Table", table)
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html.encode("utf-8"))
            return

        if path == "/nordic/store":
            cards = ""
            for it in NORDIC_PRODUCTS:
                json_ld = {
                    "@context": "https://schema.org/",
                    "@type": "Product",
                    "sku": it["sku"],
                    "name": it["name"],
                    "category": it["category"],
                    "offers": {
                        "@type": "Offer",
                        "price": it["price"],
                        "availability": "https://schema.org/InStock",
                        "inventoryLevel": {"@type": "QuantitativeValue", "value": it["inventoryLevel"]}
                    }
                }
                cards += f"""
                <div class="product-card" id="{it['id']}">
                    <script type="application/ld+json">
{json.dumps(json_ld, indent=2)}
                    </script>
                    <div class="sku-tag">SKU: <span class="sku-value">{it['sku']}</span></div>
                    <h3 class="product-name">{it['name']}</h3>
                    <span class="category-pill">{it['category']}</span>
                    <div style="margin-top: 12px; display: flex; justify-content: space-between;">
                        <span class="price-display" style="color:#10b981; font-weight:bold;">${it['price']:.2f}</span>
                        <span class="stock-display" style="color:#38bdf8; font-weight:bold;">Stock: {it['inventoryLevel']}</span>
                    </div>
                </div>
                """
            html = render_html_wrapper("Nordic Component Supply", "Nordic JSON-LD Storefront", cards)
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html.encode("utf-8"))
            return

        self.send_response(404)
        self.end_headers()

if __name__ == "__main__":
    server_address = ("", PORT)
    with socketserver.TCPServer(server_address, SupplierRequestHandler) as httpd:
        print(f"Simulated Wholesale Supplier Server (Python) on port {PORT}")
        httpd.serve_forever()
