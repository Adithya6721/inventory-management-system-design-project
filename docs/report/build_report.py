"""Build the academic AetherStock PDF from the repository's saved evidence.

Run from any directory: python docs/report/build_report.py
Dependencies: reportlab, matplotlib, Pillow. No running application is required.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
from dataclasses import dataclass, field
from pathlib import Path
from xml.sax.saxutils import escape

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from reportlab.graphics.shapes import Circle, Drawing, Line, Polygon, Rect, String
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_JUSTIFY, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import Image, Paragraph, Table, TableStyle


ROOT = Path(__file__).resolve().parents[2]
ASSETS = Path(__file__).resolve().parent / "assets"
COMPONENTS = ROOT / "evaluation/results/20261024-145210-components"
CACHE = ROOT / "evaluation/load-results/20261024-130716-cache"
PAGE_W, PAGE_H = A4
LEFT = RIGHT = 58
WIDTH = PAGE_W - LEFT - RIGHT
TOP = PAGE_H - 80
BOTTOM = 57
BODY_HEIGHT = TOP - BOTTOM
NAVY = colors.HexColor("#173753")
BLUE = colors.HexColor("#0066a6")
TEAL = colors.HexColor("#16786f")
GREY = colors.HexColor("#52616b")
LIGHT = colors.HexColor("#f0f5f8")
RULE = colors.HexColor("#ced8df")
INK = colors.HexColor("#17222c")


def register_fonts():
    font_dir = Path("C:/Windows/Fonts")
    if (font_dir / "times.ttf").exists():
        for name, filename in [
            ("ReportSerif", "times.ttf"),
            ("ReportSerif-Bold", "timesbd.ttf"),
            ("ReportSerif-Italic", "timesi.ttf"),
            ("ReportSerif-BoldItalic", "timesbi.ttf"),
        ]:
            pdfmetrics.registerFont(TTFont(name, str(font_dir / filename)))
        pdfmetrics.registerFontFamily(
            "ReportSerif", normal="ReportSerif", bold="ReportSerif-Bold",
            italic="ReportSerif-Italic", boldItalic="ReportSerif-BoldItalic",
        )
        return "ReportSerif", "ReportSerif-Bold", "ReportSerif-Italic"
    return "Times-Roman", "Times-Bold", "Times-Italic"


SERIF, SERIF_BOLD, SERIF_ITALIC = register_fonts()
STYLES = {
    "body": ParagraphStyle(
        "Body", fontName=SERIF, fontSize=11.2, leading=15.4,
        textColor=INK, alignment=TA_JUSTIFY, spaceAfter=8,
    ),
    "small": ParagraphStyle(
        "Small", fontName=SERIF, fontSize=10, leading=13.4,
        textColor=GREY, spaceAfter=7,
    ),
    "title": ParagraphStyle(
        "Title", fontName="Helvetica-Bold", fontSize=18, leading=22.5,
        textColor=NAVY, spaceAfter=17,
    ),
    "heading": ParagraphStyle(
        "Heading", fontName="Helvetica-Bold", fontSize=11.3, leading=15,
        textColor=NAVY, spaceBefore=8, spaceAfter=6,
    ),
    "caption": ParagraphStyle(
        "Caption", fontName=SERIF_ITALIC, fontSize=9.5, leading=12.2,
        textColor=GREY, alignment=TA_CENTER, spaceBefore=4, spaceAfter=9,
    ),
    "note": ParagraphStyle(
        "Note", fontName=SERIF, fontSize=10.3, leading=14, textColor=NAVY,
    ),
    "formula": ParagraphStyle(
        "Formula", fontName="Helvetica", fontSize=10.2, leading=16,
        alignment=TA_CENTER, textColor=NAVY,
    ),
    "reference": ParagraphStyle(
        "Reference", fontName=SERIF, fontSize=9.7, leading=12.5,
        textColor=INK, spaceAfter=6, splitLongWords=True,
    ),
}


def p(text, kind="body"):
    return Paragraph(text, STYLES[kind])


def h(text):
    return p(text, "heading")


def caption(text):
    return p(text, "caption")


def box(text, formula=False):
    result = Table([[p(text, "formula" if formula else "note")]], colWidths=[WIDTH])
    result.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), LIGHT),
        ("BOX", (0, 0), (-1, -1), 0.6, RULE),
        ("LEFTPADDING", (0, 0), (-1, -1), 12),
        ("RIGHTPADDING", (0, 0), (-1, -1), 12),
        ("TOPPADDING", (0, 0), (-1, -1), 9),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 9),
    ]))
    result.spaceAfter = 9
    return result


def table(headers, rows, weights=None, size=9.1):
    weights = weights or [1] * len(headers)
    widths = [WIDTH * w / sum(weights) for w in weights]
    normal = ParagraphStyle(
        "Cell", fontName="Helvetica", fontSize=size, leading=size * 1.3,
        textColor=INK, alignment=TA_LEFT, splitLongWords=True,
    )
    head = ParagraphStyle(
        "CellHead", parent=normal, fontName="Helvetica-Bold", textColor=colors.white,
    )

    def cell(value, style):
        return Paragraph(str(value), style)

    data = [[cell(x, head) for x in headers]]
    data.extend([[cell(x, normal) for x in row] for row in rows])
    result = Table(data, colWidths=widths, hAlign="LEFT")
    result.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), NAVY),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, LIGHT]),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LINEBELOW", (0, 0), (-1, 0), 0.7, NAVY),
        ("LINEBELOW", (0, -1), (-1, -1), 0.6, RULE),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    result.spaceAfter = 7
    return result


def graphic(name, height):
    return Image(str(ASSETS / name), width=WIDTH, height=height)


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def n(value, decimals=2):
    return f"{value:,.{decimals}f}"


def shape_text(drawing, x, y, text, size=9, bold=False, color=INK, anchor="middle"):
    for index, line in enumerate(text.split("\n")):
        drawing.add(String(
            x, y - index * (size + 3), line,
            fontName="Helvetica-Bold" if bold else "Helvetica", fontSize=size,
            fillColor=color, textAnchor=anchor,
        ))


def node(drawing, x, y, width, height, text, fill=LIGHT, size=9):
    drawing.add(Rect(x, y, width, height, rx=5, ry=5, fillColor=fill, strokeColor=RULE, strokeWidth=0.8))
    lines = len(text.split("\n"))
    text_y = y + height / 2 + (lines - 1) * (size + 3) / 2 - size / 3
    shape_text(drawing, x + width / 2, text_y, text, size=size)


def arrow(drawing, x1, y1, x2, y2, color=GREY):
    drawing.add(Line(x1, y1, x2, y2, strokeColor=color, strokeWidth=1))
    theta = math.atan2(y2 - y1, x2 - x1)
    length, half = 5, 2.4
    points = [x2, y2,
              x2 - length * math.cos(theta) + half * math.sin(theta),
              y2 - length * math.sin(theta) - half * math.cos(theta),
              x2 - length * math.cos(theta) - half * math.sin(theta),
              y2 - length * math.sin(theta) + half * math.cos(theta)]
    drawing.add(Polygon(points, fillColor=color, strokeColor=color))


def architecture():
    d = Drawing(WIDTH, 326)
    node(d, 20, 279, 265, 43, "React dashboard / operational workflows\nBrowser-side product Trie", size=10)
    node(d, 322, 279, 157, 43, "Simulated supplier portals\nApex / Global / Nordic", size=9)
    node(d, 20, 217, 265, 38, "REST API / per-client token bucket", size=10)
    node(d, 322, 217, 157, 38, "Node.js crawler\nParse, validate, import", size=9)
    arrow(d, 153, 279, 153, 255)
    arrow(d, 400, 279, 400, 255)
    node(d, 0, 145, WIDTH, 49,
         "Spring Boot domain services (one backend JVM)\nProducts | stock | warehouses | orders | transfers | alerts\nSnowflake ID generator | short-link service", size=9.4)
    arrow(d, 153, 217, 153, 194)
    arrow(d, 322, 236, 285, 236)
    node(d, 173, 94, 177, 31, "Inventory router / SHA-256 ring", size=9)
    arrow(d, 261, 145, 261, 125)
    node(d, 0, 8, 158, 64,
         "Core MongoDB database\nProducts, warehouses, orders\nMovements, alerts, short URLs\nPersisted shard topology", size=8.8)
    node(d, 173, 8, 177, 64,
         "Logical inventory databases\nShard 1 | Shard 2 | Shard 3\nShard 4: configured standby\nShared replica-set deployment", size=8.8)
    node(d, 365, 8, 114, 64,
         "Redis key-value store\nProduct metadata\nCache-aside reads\n10-minute TTL", size=8.5)
    arrow(d, 75, 145, 75, 72)
    arrow(d, 261, 94, 261, 72)
    arrow(d, 423, 145, 423, 72)
    shape_text(d, 77, 104, "core data", size=8, color=GREY)
    shape_text(d, 421, 104, "metadata", size=8, color=GREY)
    return d


def ring_diagram():
    d = Drawing(WIDTH, 167)
    cx, cy, radius = 90, 80, 62
    d.add(Circle(cx, cy, radius, fillColor=None, strokeColor=RULE, strokeWidth=2))
    palette = [BLUE, TEAL, colors.HexColor("#b27928")]
    for i in range(12):
        theta = math.pi / 2 - i * math.pi / 6
        x, y = cx + radius * math.cos(theta), cy + radius * math.sin(theta)
        d.add(Circle(x, y, 4, fillColor=palette[i % 3], strokeColor=colors.white))
    shape_text(d, cx, 86, "SHA-256", size=11, bold=True)
    shape_text(d, cx, 68, "clockwise ownership", size=8)
    arrow(d, 59, 145, 98, 145, color=BLUE)
    node(d, 198, 102, 272, 41, "Key = productId:warehouseId\nHash once; select the next ring position", size=10)
    arrow(d, 198, 123, 152, 103, color=BLUE)
    for i, text in enumerate(["Shard 1", "Shard 2", "Shard 3"]):
        d.add(Circle(210 + i * 87, 78, 4, fillColor=palette[i], strokeColor=None))
        shape_text(d, 220 + i * 87, 75, text, size=9, anchor="start")
    shape_text(d, 331, 48, "Configured: 32 virtual nodes per shard", size=10)
    shape_text(d, 331, 30, "3 active shards = 96 routing positions", size=10)
    shape_text(d, 331, 12, "12 representative tokens illustrated above", size=8, color=GREY)
    return d


def lifecycle_diagram():
    d = Drawing(WIDTH, 151)
    shape_text(d, 0, 138, "SALES ORDER", size=9, bold=True, color=NAVY, anchor="start")
    node(d, 0, 89, 114, 35, "Validated request", size=9)
    node(d, 157, 89, 143, 35, "RESERVED\nHold stock", size=9)
    node(d, 345, 103, 134, 32, "COMPLETED: deduct", size=8.8)
    node(d, 345, 62, 134, 32, "CANCELLED: release", size=8.8)
    arrow(d, 114, 107, 157, 107)
    arrow(d, 300, 109, 345, 119)
    arrow(d, 300, 99, 345, 78)
    shape_text(d, 0, 59, "STOCK TRANSFER", size=9, bold=True, color=NAVY, anchor="start")
    node(d, 0, 4, 114, 39, "Source validated", size=9)
    node(d, 157, 4, 143, 39, "IN_TRANSIT\nDeduct source", size=9)
    node(d, 345, 4, 134, 39, "Complete: credit target\nCancel: restore source", size=8.4)
    arrow(d, 114, 23, 157, 23)
    arrow(d, 300, 23, 345, 23)
    return d


def make_charts(cache_rows, http, hashing):
    ASSETS.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({
        "font.family": "DejaVu Sans", "font.size": 10.5,
        "axes.spines.top": False, "axes.spines.right": False,
        "axes.labelcolor": "#17222c", "text.color": "#17222c",
        "axes.titleweight": "bold", "savefig.facecolor": "white",
    })
    blue, teal, amber = "#0066a6", "#16786f", "#b27928"
    users = [5, 25, 100]

    def save(fig, name):
        fig.savefig(ASSETS / name, dpi=210, bbox_inches=None)
        plt.close(fig)

    fig, axes = plt.subplots(1, 2, figsize=(9, 3.0), layout="constrained")
    for mode, color in [("off", blue), ("on", teal)]:
        data = [r for r in cache_rows if r["cache"] == mode]
        xs = range(3)
        axes[0].errorbar(xs, [float(r["mean_ms"]) for r in data],
                         yerr=[float(r["mean_ms_stddev"]) for r in data],
                         color=color, marker="o", capsize=3, label=f"Cache {mode}")
        axes[1].errorbar(xs, [float(r["successful_rps"]) for r in data],
                         yerr=[float(r["successful_rps_stddev"]) for r in data],
                         color=color, marker="o", capsize=3, label=f"Cache {mode}")
    for ax in axes:
        ax.set_xticks(range(3), users)
        ax.set_xlabel("Concurrent users")
        ax.grid(axis="y", alpha=0.2)
        ax.set_ylim(bottom=0)
    axes[0].set_ylabel("Mean HTTP latency (ms)")
    axes[1].set_ylabel("Successful requests / second")
    axes[0].legend(frameon=False, loc="upper left")
    save(fig, "cache-results.png")

    fig, axes = plt.subplots(1, 2, figsize=(9, 2.7), layout="constrained")
    for mode, color, label in [("single", blue, "Limiter off"), ("limited", teal, "Limiter on")]:
        data = [next(r for r in http if r["experiment"] == "limiter" and r["mode"] == mode and r["users"] == u) for u in users]
        axes[0].plot(range(3), [r["accepted_rps"] for r in data], "o-", color=color, label=label)
    limited = [next(r for r in http if r["experiment"] == "limiter" and r["mode"] == "limited" and r["users"] == u) for u in users]
    axes[1].bar(range(3), [r["rejection_pct"] for r in limited], color=amber, width=0.55)
    for ax in axes:
        ax.set_xticks(range(3), users)
        ax.set_xlabel("Concurrent users")
        ax.grid(axis="y", alpha=0.2)
        ax.set_axisbelow(True)
    axes[0].set_ylabel("Admitted reads / second")
    axes[0].legend(frameon=False)
    axes[1].set_ylabel("Rejected requests (%)")
    axes[1].set_ylim(0, 105)
    save(fig, "limiter-results.png")

    fig, axes = plt.subplots(1, 2, figsize=(9, 2.8), layout="constrained")
    for mode, color, label in [("single", blue, "Single database"), ("sharded", teal, "3 logical shards")]:
        data = [next(r for r in http if r["experiment"] == "inventory-reads" and r["mode"] == mode and r["users"] == u) for u in users]
        axes[0].plot(range(3), [r["by_request"]["scatter"]["mean_ms"] for r in data], "o-", color=color, label=label)
        axes[1].plot(range(3), [r["accepted_rps"] for r in data], "o-", color=color, label=label)
    for ax in axes:
        ax.set_xticks(range(3), users)
        ax.set_xlabel("Concurrent users")
        ax.grid(axis="y", alpha=0.2)
        ax.set_ylim(bottom=0)
    axes[0].set_ylabel("Product-wide mean latency (ms)")
    axes[1].set_ylabel("Accepted reads / second")
    axes[0].legend(frameon=False)
    save(fig, "shard-results.png")

    fig, axes = plt.subplots(1, 2, figsize=(9, 2.65), layout="constrained")
    data = [r for r in hashing if r["keys"] == 100000]
    for index, (key, color) in enumerate(zip(["shard-1", "shard-2", "shard-3"], [blue, teal, amber])):
        axes[0].bar([i + (index - 1) * 0.24 for i in range(3)],
                    [r["record_distribution"][key] / 1000 for r in data],
                    width=0.24, color=color, label=key)
    axes[0].set_xticks(range(3), [1, 32, 128])
    axes[0].set_xlabel("Virtual nodes per shard")
    axes[0].set_ylabel("Records (thousands)")
    axes[0].legend(frameon=False, fontsize=9)
    selected = next(r for r in data if r["virtual_nodes"] == 32)
    axes[1].bar(["Consistent ring", "Modulo"],
                [selected["ring_add_remapped_pct"], selected["modulo_add_remapped_pct"]],
                color=[teal, amber], width=0.52)
    axes[1].set_ylabel("Keys remapped (%)")
    axes[1].set_ylim(0, 100)
    for ax in axes:
        ax.grid(axis="y", alpha=0.2)
        ax.set_axisbelow(True)
    save(fig, "hash-results.png")


@dataclass
class ReportPage:
    title: str
    key: str
    items: list = field(default_factory=list)
    level: int = 0


def build_pages():
    with (CACHE / "summary.csv").open(newline="", encoding="utf-8") as handle:
        cache_rows = list(csv.DictReader(handle))
    http = load_json(COMPONENTS / "http-results.json")
    hashing = load_json(COMPONENTS / "hashing.json")["results"]
    ids = load_json(COMPONENTS / "ids.json")["results"]
    autocomplete = load_json(COMPONENTS / "autocomplete.json")
    failure = load_json(CACHE / "redis-failure-check.json")
    migration = load_json(COMPONENTS / "migration-check.json")
    make_charts(cache_rows, http, hashing)
    pages = []

    def add(title, key, items, level=0):
        pages.append(ReportPage(title, key, [p(title, "title")] + items, level))

    add("Abstract", "abstract", [
        p("AetherStock is a working inventory-management prototype developed for UCS3513 System Design Laboratory. "
          "It integrates product registration, warehouse-wise stock tracking, purchase receipts, sales reservations, "
          "inter-warehouse transfers, low-stock alerts and supplier-product discovery. A React interface communicates "
          "with a Spring Boot backend, MongoDB persistence and a Redis product-metadata cache."),
        p("The design incorporates eight system-design components: a token-bucket rate limiter, logical database "
          "sharding, consistent hashing, a key-value cache, Snowflake identifiers, a URL shortener, a supplier web "
          "crawler and Trie-based autocomplete. Inventory is routed by a composite product/warehouse key to three "
          "active logical databases. A fourth configured shard supports controlled membership changes. Stock "
          "availability remains authoritative in MongoDB; transactions jointly commit stock changes, movement "
          "records, alerts and business-document state."),
        p("Evaluation uses the repository's recorded experiments from 24 October 2026 at 5, 25 and 100 concurrent "
          "users. Cache comparisons use three 30-second steady-state repetitions; component HTTP comparisons "
          "use short 10-second windows with one repetition. Additional direct benchmarks examine identifier "
          "generation, hash distribution and autocomplete. Correctness evidence records 85 passing backend tests."),
        p("The results demonstrate workload-dependent trade-offs. Warm Redis eliminates measured product-detail "
          "database loads, but improves HTTP performance only at the medium workload in this experiment. Logical "
          "sharding adds routing and fan-out overhead on the shared host. A three-to-four-to-three shard change "
          "preserves all 803 inventory documents, moving 26.53% in each direction. Hot-stock bursts maintain "
          "stock safety while experiencing substantial retryable write conflicts. Redis failure preserves correct "
          "product responses through database fallback, with a marked latency penalty."),
        p("AetherStock therefore provides an integrated academic demonstration of component selection, transaction "
          "safety and measurable scaling trade-offs. Its current boundary is a single backend and one MongoDB "
          "deployment: inventory is logically partitioned, while orders and audit data remain centralized. "
          "Physical scale-out, coordinated multi-backend operation and several component-specific verification "
          "gaps remain future work."),
        box("<b>Keywords:</b> inventory management; MongoDB transactions; consistent hashing; logical sharding; "
            "Redis; token bucket; Snowflake; supplier crawler; Trie; performance evaluation."),
        h("Report basis"),
        p("Assignment: Topic 4, <i>Mini Project Problem Statements.pdf</i>, pp. 8-9 [1]. "
          "Implementation and recorded evidence are distinguished throughout. Numerical results reproduce "
          "saved observations; explanatory stock examples and architectural diagrams are illustrative.", "small"),
    ])
    pages.append(ReportPage("Contents", "contents"))

    add("1  Introduction and project scope", "introduction", [
        h("1.1  Problem statement"),
        p("An inventory system must answer where products are stored, how many units are available and how "
          "each purchase, sale or transfer changes that stock. As warehouses and concurrent requests increase, "
          "a simple read-then-write implementation can oversell products, repeat deliveries or lose the link "
          "between stock and its audit trail. A system-design solution must address both operational usability "
          "and the correctness of concurrent state changes."),
        p("The laboratory problem requests an integrated prototype that supports registration, inventory "
          "search, warehouse management, orders, transfers and low-stock notifications. It also requires "
          "justified component selection, at least three workload levels, baseline comparisons where relevant, "
          "resource measurements and a scalability or failure scenario [1]."),
        h("1.2  Project objectives"),
        table(["Objective", "Observable outcome"], [
            ["Reliable stock operations", "Reservations and deductions obey stock bounds; failed business transactions roll back."],
            ["Partition-aware persistence", "Composite keys select inventory owners; shard membership can change with recorded migration results."],
            ["Responsive product access", "Product metadata caching and prefix search support catalogue browsing and selection."],
            ["Controlled request handling", "Client buckets admit bounded traffic and expose explicit HTTP 429 rejections."],
            ["Evidence-based assessment", "Latency, useful throughput, outcomes, resources and correctness are reported together."],
        ], [1.05, 2.65], size=9.5),
        h("1.3  Prototype scope"),
        p("The implemented system combines an operational React dashboard with component demonstrations. "
          "Its business modules include catalogue management, warehouses, purchase and sales orders, transfers, "
          "movement history and low-stock alerts. Simulated supplier portals provide reproducible external data. "
          "The System Design Lab and Autocomplete Lab expose supporting mechanisms for demonstration."),
        h("1.4  Success criteria and boundary"),
        p("Success means that documented workflows execute through the integrated API, stock and business "
          "records reconcile after concurrent activity, and measurements explain the benefit or cost of each "
          "tested component. Three logical databases on one MongoDB process demonstrate partition routing; "
          "they do not establish additional machine capacity. The assignment's transaction-data partitioning "
          "requirement is only partially covered because orders and movement records remain in the core database."),
    ])

    add("2  Requirements and component mapping", "requirements", [
        p("Component choices follow the inventory access pattern and the state each workflow must protect. "
          "The mapping below identifies all eight incorporated components and their specific roles."),
        caption("Table 1. Functional requirements, selected components and justification."),
        table(["Requirement / challenge", "Component", "Implementation and justification"], [
            ["Protect APIs during bursts", "Rate limiter", "Per-client token buckets allow short bursts and bound sustained admission to the backend."],
            ["Partition warehouse stock", "Sharding", "Separate logical MongoDB databases store inventory records; a persistence port keeps business services storage-independent."],
            ["Keep placement stable as membership changes", "Consistent hashing", "SHA-256 ring with virtual nodes routes productId:warehouseId and limits reassignment on shard addition/removal."],
            ["Repeated catalogue detail reads", "Key-value store", "Redis caches product metadata with expiry and invalidation, reducing repeated database reads."],
            ["Identify products and business events", "Unique ID generator", "Snowflake creates prefixed identifiers locally for products, warehouses, orders, movements and alerts."],
            ["Share product or related links", "URL shortener", "Base62 aliases map long URLs to HTTP redirects and record redirect counts."],
            ["Discover supplier catalogue information", "Web crawler", "Strategy-specific parsers collect supplier products, normalize records and submit an import feed."],
            ["Search by product name, SKU or category", "Autocomplete", "A browser Trie performs token-prefix lookup, with a debounced API fallback for missing local matches."],
        ], [1.2, 0.88, 2.35], size=9.3),
        h("2.1  Cross-cutting requirements"),
        p("Purchase receipts, sales and transfers also require transactional stock updates, idempotent request "
          "handling and movement auditing. A shard router or cache cannot independently provide these "
          "properties. Snapshot transactions and conditional stock mutations therefore form the correctness "
          "foundation underneath the selected components."),
        box("<b>Requirement status:</b> the eight components are present. Inventory partitioning is implemented; "
            "partitioning of transaction/order and audit data remains an explicit gap. Component presence "
            "does not imply that every possible performance or failure case has been measured."),
    ])

    add("3  System architecture", "architecture", [
        p("AetherStock uses a layered, single-backend architecture. REST controllers validate requests; domain "
          "services coordinate workflows; integration ports select cache, identifier and persistence implementations. "
          "Figure 1 shows the implemented deployment and data paths."),
        architecture(),
        caption("Figure 1. Implemented architecture. All inventory databases share one MongoDB deployment."),
        h("3.1  Interaction and transaction boundaries"),
        p("Product-detail requests use Redis cache-aside access. Inventory availability and mutations use the "
          "authoritative persistence router. All logical shard templates share the core MongoDB factory and "
          "transaction session, so a business transaction may update inventory and its core order/audit records "
          "atomically. The crawler imports catalogue data and may propose a purchase order; goods become "
          "physical warehouse stock only on receipt."),
        table(["Layer", "Technology / configuration"], [
            ["Client", "React 19 / Vite; catalogue, stock and component demonstration pages"],
            ["Application", "Java 17; Spring Boot 3.3.4; REST API on port 8080 by default"],
            ["Persistence", "MongoDB transaction-capable deployment; Redis metadata cache"],
            ["External-data simulation", "Node.js supplier portal and crawler; portal port 8085 by default"],
        ], [1, 3], size=9.2),
    ])

    add("3.2  Data model and consistency", "data-model", [
        caption("Table 2. Principal records and storage responsibilities."),
        table(["Record", "Important fields / relations", "Location"], [
            ["Product / warehouse", "Product ID, unique SKU, category, price, reorder level; warehouse ID and active state", "Core database"],
            ["Inventory", "Product + warehouse pair, physical quantity, reserved quantity, version, timestamps", "Owning logical shard"],
            ["Purchase order", "Supplier, destination, ordered/received items, receipt fingerprints, status", "Core database"],
            ["Sales order / transfer", "Item references, quantity, lifecycle status, creation idempotency key", "Core database"],
            ["Movement / alert", "Product and warehouse references, movement type and reference ID; threshold and alert state", "Core database"],
            ["Short URL / topology", "Unique code, target, clicks and expiry; active/pending shards and ring configuration", "Core database"],
        ], [1, 2.6, 1], size=9.3),
        h("Stock invariants"),
        box("A = P - R &nbsp;&nbsp; | &nbsp;&nbsp; P &gt;= 0 &nbsp;&nbsp; | &nbsp;&nbsp; 0 &lt;= R &lt;= P<br/>"
            "P: physical units &nbsp;&nbsp; R: reserved units &nbsp;&nbsp; A: available units", formula=True),
        p("A unique product/warehouse index prevents duplicate stock records at an owner. Guarded database "
          "updates reserve only available units, release only existing reservations and prevent negative stock. "
          "Stock, movements, alert changes, receipt identities and order/transfer state commit or roll back "
          "together. Transactions use snapshot reads and majority write concern [2]."),
        h("Request identity and retries"),
        p("Creation requests accept stable idempotency keys. An existing key with the same canonical payload "
          "returns the previous result; changing the payload produces a conflict. Partial receipts require an "
          "explicit identity, and repeated receipt lines are aggregated before remaining quantities are checked. "
          "The supplied creation forms retain pending keys in session storage across failed requests and reloads."),
        p("Recognized aborted transaction conflicts return HTTP 409. An uncertain commit outcome returns 503 "
          "and requires reconciliation or retry of the whole request with the same key. A successful status "
          "transition must not survive a failed stock operation. The router holds membership stable until "
          "the surrounding inventory transaction completes."),
        p("A transaction-capable replica set or compatible MongoDB cluster is required. The local one-member "
          "replica set provides transaction semantics without machine-failure redundancy.", "small"),
    ], level=1)

    add("4  Algorithms: rate limiting and caching", "limiter-cache-design", [
        h("4.1  Token-bucket admission control"),
        p("The rate limiter maintains an in-process bucket for each resolved client identity. The default "
          "capacity is 10 tokens and the refill rate is 5 tokens per second. A synchronized token operation "
          "refills according to elapsed monotonic time, caps the balance at capacity and consumes one token "
          "when a request is admitted."),
        box("tokens = min(capacity, tokens + elapsed_seconds x refill_rate)<br/>"
            "Admit when tokens &gt;= 1; otherwise return HTTP 429.", formula=True),
        p("Rate-limit response headers report the limit and remaining allowance; a rejected request provides "
          "retry information. The interceptor protects API traffic. The redirect route is outside the "
          "API path, so a short-link click does not traverse that interceptor. A local bucket is inexpensive "
          "but applies to one backend process: adding replicas would require shared admission state or an "
          "explicitly partitioned quota policy."),
        h("4.2  Redis cache-aside product lookup"),
        table(["Step", "Product-detail read or update"], [
            ["1. Lookup", "Read product metadata from the configured Redis namespace."],
            ["2. Miss", "Load the product from MongoDB and populate the cache with a 10-minute default TTL."],
            ["3. Change", "The normal product update path evicts affected metadata."],
            ["4. Cache failure", "Record the cache failure and fall back to MongoDB; request latency may rise."],
        ], [0.75, 3.25], size=9.7),
        p("Inventory availability bypasses Redis. This avoids making sales decisions from an old cached "
          "quantity while concurrent transactions reserve or deduct stock. Legacy stock-cache entries are "
          "invalidated after a committed mutation but are not an authority for availability. The product "
          "cache is a performance optimization with separate freshness concerns."),
        h("Complexity and measurement"),
        p("Bucket updates require constant work per request; the bucket registry grows with active clients. "
          "Cache lookup adds a network exchange and serialization. A cache hit can remove a database read "
          "without reducing end-to-end latency when the database working set is already warm. The evaluation "
          "therefore measures cache hit ratio, lookup time, database loads, HTTP latency and resource usage "
          "instead of assuming that caching is always faster."),
    ])

    add("4.3  Sharding and consistent hashing", "hash-design", [
        p("The router hashes the exact composite key <b>productId:warehouseId</b> with UTF-8 SHA-256. Each "
          "virtual node hashes <b>shardId#virtualNodeIndex</b>. The first ring position at or clockwise after "
          "the key hash selects the owner, with wraparound at the end of the ring [3]."),
        ring_diagram(),
        caption("Figure 2. Consistent-hash routing with virtual nodes; schematic token positions."),
        h("Lookup and query cost"),
        p("With S shards and V virtual nodes per shard, a sorted-ring lookup takes O(log(SV)) comparisons. "
          "A point read or stock mutation visits the owner. A product-wide or warehouse-wide query visits "
          "all active shards and combines results, adding fan-out work. Virtual nodes improve the opportunity "
          "for balanced placement but do not create replicas or divide a single hot stock key."),
        h("Journaled membership change"),
        table(["Phase", "Action and recovery property"], [
            ["Prepare", "Acquire the router write lock; persist pending membership and build the target ring."],
            ["Copy", "Copy records whose owner changes, preserving fields and retaining original copies."],
            ["Commit authority", "Persist target membership as authoritative while keeping the pending marker."],
            ["Clean up", "Delete non-owner copies and clear pending state; inventory requests resume."],
            ["Resume", "Reload persisted topology and repeat the idempotent copy/cleanup stages after interruption."],
        ], [1, 3.5], size=9.3),
        p("The local read/write lock spans complete inventory transactions. Migration waits for in-flight "
          "work and pauses subsequent inventory operations. A transaction with a pre-change snapshot cannot "
          "safely use new routing and receives a retryable conflict. An incomplete migration gates inventory "
          "with 503 until recovery. Active membership persists across restarts; ring settings cannot be "
          "silently changed for an existing dataset."),
    ], level=1)

    add("4.4  Unique identifiers and short links", "ids-links-design", [
        h("Snowflake identifiers"),
        p("The primary identifier generator combines a millisecond timestamp relative to 1 January 2024, "
          "a datacenter identifier, a node identifier and a per-millisecond sequence. Prefixes such as "
          "prod_, po_, so_ and mov_ keep identifiers recognizable at API boundaries."),
        caption("Table 3. Implemented 64-bit Snowflake layout."),
        table(["Sign", "Timestamp", "Datacenter", "Node", "Sequence"], [
            ["1 bit", "41 bits", "5 bits", "5 bits", "12 bits"],
            ["Sign = 0", "Milliseconds since epoch", "0-31", "0-31", "0-4095 per ms"],
        ], [0.8, 1.75, 1.15, 0.8, 1.65], size=9.3),
        box("id = ((timestamp - epoch) &lt;&lt; 22) | (datacenter &lt;&lt; 17)<br/>"
            "&nbsp;&nbsp;&nbsp;&nbsp; | (node &lt;&lt; 12) | sequence", formula=True),
        p("Generation is synchronized within the JVM. Sequence rollover waits for the next millisecond. "
          "Small backward clock movements are waited out and checked again; larger regressions fail "
          "generation. Distinct active generators require distinct datacenter/node identities. Time-sortable "
          "numeric values and local generation are useful, but the measured ID rate is not database "
          "insertion throughput and does not prove deployment-wide collision freedom."),
        h("Base62 short-link mapping"),
        p("The shortener generates a random six-character code from a 62-character alphabet using "
          "SecureRandom. The nominal six-character namespace contains 62<super>6</super> = 56,800,235,584 "
          "codes. Existing long URLs are looked up before creation. A pre-detected code collision increases "
          "the candidate length, with up to five attempts; a unique MongoDB code index guards persistence."),
        p("POST /api/shorten creates a mapping. GET /r/{code} resolves it with an atomic click-counter "
          "increment and returns HTTP 302. An information endpoint inspects a mapping without adding a "
          "click. The controller exposes X-Redirect-Latency-Ms and component counters for evaluation."),
        h("Verification boundary"),
        p("An expiry timestamp is stored with a nominal 90-day default. Lifetime/index behavior, parallel "
          "same-URL requests and an insertion-time collision race need dedicated verification. The service "
          "checks a code before saving; its retry loop does not catch a duplicate-key race at save time. "
          "No saved redirect-load measurements are available in the evaluated evidence; this report therefore "
          "does not assign a measured redirect latency to the component."),
    ], level=1)

    add("4.5  Supplier crawler and autocomplete", "crawler-trie-design", [
        h("Supplier discovery pipeline"),
        p("The Node.js crawler traverses a URL frontier with a visited set. Each queued URL selects a "
          "parser strategy: Apex Tech uses product cards with pagination, Global Wholesale uses table rows, "
          "and Nordic Supply exposes Product/Offer JSON-LD. The implemented frontier processes fetches "
          "sequentially. It normalizes SKU text and numeric fields, validates items and records crawl telemetry."),
        table(["Pipeline stage", "Implemented behavior"], [
            ["Fetch", "An 80 ms default client politeness interval, 5 s request timeout and bounded retries."],
            ["Retry", "Increasing delays of 200, 400 and 600 ms; the current implementation uses linear backoff."],
            ["Extract", "Supplier-specific card/table parsing or embedded JSON deserialization; discovered pagination is queued."],
            ["Normalize", "Trim and uppercase SKU, validate required data, normalize categories and non-negative stock."],
            ["Integrate", "Group feed items by supplier and call POST /api/suppliers/import."],
        ], [1, 3.7], size=9.4),
        p("The import service registers newly discovered SKUs, may update the category of an existing "
          "product, and can create a purchase order for a chosen warehouse. Supplier-reported stock is "
          "procurement information; the import itself does not credit physical stock. Import batches are "
          "not one atomic, replay-safe transaction across all catalogue changes and purchase creation."),
        h("Trie token-prefix search"),
        p("The browser indexes tokens from SKU, product name and category, plus a compact SKU form. "
          "A prefix lookup walks child maps, then collects product references from the matching subtree, "
          "deduplicates by product ID and returns up to eight suggestions. Keyboard navigation and a "
          "debounced server query support the search interaction."),
        box("Current search work = prefix traversal + matching-subtree traversal + deduplication.<br/>"
            "Returning eight suggestions does not bound the traversal to eight candidates."),
        p("Selective prefixes visit small subtrees; broad prefixes may traverse most of the catalogue. "
          "The application initially builds from its fetched product page, so local coverage is bounded "
          "by loaded records. The complete-catalogue algorithm benchmark measures the data structure "
          "under controlled input and is not a measurement of browser rendering or full application search coverage."),
    ], level=1)

    add("5  Prototype workflows", "workflows", [
        h("5.1  Product registration and warehouse setup"),
        p("A user registers a product with SKU, name, category, price and reorder level, then creates "
          "warehouses to represent storage locations. Product and warehouse identifiers come from the "
          "Snowflake generator. Registration does not create physical stock. Catalogue search and "
          "autocomplete help select existing products while warehouse views show the stock associated "
          "with a chosen location."),
        h("5.2  Purchase order and receipt"),
        p("A purchase order identifies a supplier, destination warehouse and positive per-product "
          "quantities. Creation validates active warehouse state and duplicate product lines, then stores "
          "a CREATED order. A receipt either names delivered quantities with a stable receipt key or "
          "receives all remaining items. The service validates all receipt lines before crediting stock "
          "and updates the order to PARTIALLY_RECEIVED or RECEIVED."),
        caption("Table 4. Worked receipt example; illustrative quantities, not benchmark data."),
        table(["Action", "Physical", "Reserved", "Available", "Order result"], [
            ["Initial stock", "0", "0", "0", "No receipt"],
            ["Create PO for 40", "0", "0", "0", "CREATED"],
            ["Receive 15 with key R1", "15", "0", "15", "PARTIALLY_RECEIVED"],
            ["Replay the same R1", "15", "0", "15", "Same effect returned"],
            ["Receive all remaining", "40", "0", "40", "RECEIVED"],
        ], [1.8, 0.75, 0.75, 0.85, 1.8], size=9.1),
        h("5.3  Adjustment, audit and low-stock alerts"),
        p("A manual adjustment specifies physical/reserved deltas and a reason. Conditional updates "
          "reject a result that would violate stock bounds. Successful mutations append movement "
          "records and reevaluate availability against the product reorder level within the transaction. "
          "Availability at or below the threshold creates or updates an ACTIVE alert; recovery above "
          "the threshold resolves it. A unique partial index limits active alerts to one per pair."),
        h("5.4  Operational interface"),
        p("The dashboard links Product Catalog, Warehouse Inventory, Purchase Orders, Sales Orders, "
          "Stock Transfers and Low Stock Alerts. Supplier Crawler, Autocomplete Lab and System Design "
          "Lab provide component demonstrations. API failures are surfaced to the user, including "
          "rate-limit and stock/conflict responses. Product information and inventory quantities use "
          "their respective metadata and authoritative-stock access paths."),
    ])

    add("5.5  Sales and transfer execution", "sales-transfers", [
        lifecycle_diagram(),
        caption("Figure 3. Implemented sales and transfer lifecycles, with stock effects at each transition."),
        h("Sales reservation, completion and cancellation"),
        p("Placing an order validates every product/warehouse line and conditionally reserves its units. "
          "Successful creation stores a RESERVED order. Completion deducts both physical and reserved "
          "quantities; cancellation releases reservations while leaving physical stock unchanged. "
          "The service refuses invalid transitions and must observe successful stock operations before "
          "saving a terminal state."),
        caption("Table 5. Sales example for a 4-unit order; each terminal row is an alternative."),
        table(["State", "Physical P", "Reserved R", "Available P - R"], [
            ["Before order", "10", "0", "10"],
            ["RESERVED", "10", "4", "6"],
            ["Then COMPLETED", "6", "0", "6"],
            ["Or CANCELLED", "10", "0", "10"],
        ], [1.5, 1, 1, 1.5], size=9.5),
        h("Inter-warehouse stock conservation"),
        p("Initiating a transfer deducts available stock from the source and creates an IN_TRANSIT "
          "record. Completion credits the destination; cancellation restores the source. Repeating an "
          "already acknowledged terminal action returns its result without applying stock again. "
          "Completing an undisbursed transfer is rejected."),
        box("For a transfer without external receipts or sales:<br/>"
            "source physical + destination physical + in-transit units = constant", formula=True),
        p("In-transit units are represented by the transfer document rather than by warehouse physical "
          "stock. A source/destination sum alone temporarily falls during transit and is therefore an "
          "incomplete conservation check. Failure-injection and concurrency tests reconcile business "
          "records with stock and movements, including cross-logical-shard operations."),
    ], level=1)

    add("6  Evaluation methodology", "methodology", [
        p("All numerical experiments below use saved repository evidence dated 24 October 2026 [4-6]. "
          "The low, medium and high HTTP workloads are 5, 25 and 100 concurrent users. Separate "
          "experiments isolate each mechanism and use an appropriate baseline."),
        caption("Table 6. Experimental matrix and recorded conditions."),
        table(["Study", "Workload / duration", "Comparison or check"], [
            ["Product metadata", "1,000 products; 5/25/100 users; 30 s steady window; 3 repeats; 5 s ramp excluded", "Redis off vs on; limiter off; same endpoint and product assertions"],
            ["Inventory reads", "100 products x 5 warehouses; 1,000 units/record; 10 s; 1 repeat per user level", "Single store vs 3 logical shards; alternating point and product-wide queries"],
            ["Rate limiting", "5/25/100 users; paced 10/50/200 requests/s; 10 s after warm-up", "Off vs one shared bucket, capacity 10 and refill 5/s"],
            ["Stock contention", "100 one-unit orders per case; 5/25/100 users; finite bursts", "Distinct stock records vs one hot record with 10 units; first attempts only"],
            ["Membership / failure", "25-user shard-change run; separate 5-user Redis-outage run", "Add/remove shard and reconcile records; unavailable cache then recovery"],
            ["Direct algorithms", "250,000 IDs/point; 1k/10k/100k keys; 100/1k/10k products", "Snowflake vs UUID; ring vs modulo; Trie vs token-prefix linear scan"],
        ], [1, 2.2, 2.1], size=9.1),
        h("Environment and metric definitions"),
        p("Recorded host: Windows 11, 12 logical CPUs, 8 physical cores and approximately 15.65 GiB "
          "RAM. The HTTP driver is Apache JMeter 5.6.3 and the backend runs on Microsoft JDK 17.0.17. "
          "Benchmark backends use port 8082 and an isolated test MongoDB on 27019; the cache study "
          "uses Redis on 6381. Applications, database and load generator share the host."),
        box("Successful throughput = admitted successful operations / measured seconds.<br/>"
            "Success rate = successful operations / attempts x 100.<br/>"
            "Expected 409/429 rejections are reported separately from unexpected errors."),
        p("Mean, p95 and throughput describe the specified windows. Cache table p95 values are means "
          "of per-run p95s, not a pooled percentile. CPU is normalized to total logical-CPU host capacity; "
          "RSS is observed process resident memory. Manifests retain settings, revisions and artifact "
          "hashes. Short, same-host runs support local comparisons, not a production-capacity estimate.", "small"),
    ])

    cache_table = []
    for r in cache_rows:
        cache_table.append([
            r["users"], r["cache"], n(float(r["mean_ms"]), 3), n(float(r["p95_ms"]), 3),
            n(float(r["successful_rps"]), 1), n(float(r["error_pct"]), 2),
            n(float(r["db_loads_per_request"]), 1),
        ])
    add("6.1  Product-cache comparison", "cache-results", [
        p("Warm product-detail reads were measured with cache off and on at all three user levels. "
          "Every measured response passed status and product-field assertions. Cache-on hit ratio was "
          "100%; measured database loads per request fell from 1 to 0."),
        caption("Table 7. Cache baseline results: three 30-second repetitions per point [4]."),
        table(["Users", "Cache", "Mean ms", "p95 ms", "Success/s", "Error %", "DB reads / req"],
              cache_table, [0.58, 0.65, 0.91, 0.91, 1.08, 0.78, 1.06], size=9.1),
        graphic("cache-results.png", 171),
        caption("Figure 4. Product-cache latency and throughput; error bars show one between-run standard deviation."),
        h("Interpretation"),
        p("At 25 users, cache-on mean latency fell from 8.107 to 5.644 ms, approximately 30.4%, "
          "and successful throughput rose from 3,057.4 to 4,346.1 requests/s, approximately 42.1%. "
          "At 5 and 100 users the cached path was slower than the baseline. At 100 users, for "
          "example, mean latency rose from 15.779 to 26.364 ms."),
        p("The local MongoDB working set was already warm, and Redis introduced another service "
          "exchange and serialization path on the same machine. The evidence supports a database-load "
          "reduction at every level and a latency benefit only at the medium point. It does not support "
          "a general claim that caching always improves response time."),
        p("All rows have 100% request success. Cache hit/load counters include the complete run "
          "including ramp; HTTP timings use the steady measurement window. Cold-Redis population and "
          "JVM warm-up were recorded separately from the reported warm comparisons.", "small"),
    ], level=1)

    resource_rows = []
    for r in cache_rows:
        resource_rows.append([
            f"{r['users']} / {r['cache']}",
            n(float(r["backend_mean_cpu_host_pct"])), n(float(r["backend_peak_rss_mib"]), 1),
            n(float(r["mongodb_mean_cpu_host_pct"])), n(float(r["mongodb_peak_rss_mib"]), 1),
            n(float(r["redis_mean_cpu_host_pct"])), n(float(r["redis_peak_rss_mib"]), 1),
        ])
    add("6.2  Resources and Redis failure", "cache-resources", [
        caption("Table 8. Cache-study CPU (%) and RSS (MiB), aggregated over three repetitions [4]."),
        table(["Users / cache", "App CPU", "App RSS", "DB CPU", "DB RSS", "Redis CPU", "Redis RSS"],
              resource_rows, [1.04, 0.93, 0.98, 0.88, 0.9, 0.96, 0.97], size=8.9),
        p("CPU columns are means of normalized host-capacity percentages. RSS columns are the "
          "means of per-run observed peaks from summary.csv. They describe process observations, "
          "not per-request allocations. At 100 users, MongoDB mean CPU fell from 26.40% to 0.48% "
          "with cache enabled, while application CPU remained substantial.", "small"),
        h("Key-value lookup cost"),
        table(["Cache-on users", "Mean cache lookup ms", "JMeter CPU %", "JMeter RSS MiB"], [
            [r["users"], n(float(r["cache_lookup_mean_ms"]), 3),
             n(float(r["jmeter_mean_cpu_host_pct"])), n(float(r["jmeter_peak_rss_mib"]), 1)]
            for r in cache_rows if r["cache"] == "on"
        ], [1.1, 1.55, 1.15, 1.45], size=9.2),
        p("The recorded mean cache lookup rises from 0.406 ms at 5 users to 22.596 ms at 100 users. "
          "This is compatible with contention or queueing along the cached path, but the observations "
          "do not isolate a single cause. Two backend JVMs were resident in the cache experiment, "
          "with traffic sent to one at a time."),
        h("Cache outage and recovery"),
        caption("Table 9. Separate 5-user Redis failure scenario; excluded from cache baselines."),
        table(["State", "Requests", "Mean ms", "p95 ms", "Success/s", "Error %", "DB reads / req"], [
            ["Outage", "15", "4,049.07", "4,082.00", "1.2", "0.00", "1.0"],
            ["Recovery", "36,943", "1.22", "2.00", "3,792.5", "0.00", "0.0"],
        ], [1.05, 1, 1.05, 1, 1, 0.86, 1.05], size=9),
        p("Product responses remained correct through MongoDB fallback. During outage the "
          "configured cache timeouts produced approximately four-second responses and very low "
          "throughput. After recovery and separate warm-up, responses returned to roughly 1.22 ms "
          "mean. A circuit breaker or shorter bounded timeout is a future improvement; the observed "
          "scenario validates cache fallback, not database failover."),
    ], level=1)

    limiter_rows = []
    for mode in ["single", "limited"]:
        for u in [5, 25, 100]:
            r = next(r for r in http if r["experiment"] == "limiter" and r["mode"] == mode and r["users"] == u)
            limiter_rows.append([
                "Off" if mode == "single" else "On", u, n(r["offered_rps"], 0),
                n(r["accepted_rps"], 1), n(r["accepted_p95_ms"]), n(r["rejection_pct"]),
                n(r["p95_scheduling_lag_ms"]),
            ])
    add("6.3  Rate-limiter evaluation", "limiter-results", [
        p("Fixed pacing offered 10, 50 and 200 requests/s with 5, 25 and 100 users. All threads "
          "shared one client bucket, intentionally measuring enforcement of one quota. A five-second "
          "warm-up exhausted the initial burst allowance before the ten-second measured window."),
        caption("Table 10. Admission, rejection and pacing under the token bucket [5]."),
        table(["Limiter", "Users", "Offered / s", "Admit / s", "Admit p95 ms", "Reject %", "Pacing p95 ms"],
              limiter_rows, [0.78, 0.65, 0.95, 0.96, 1.17, 0.95, 1.17], size=9),
        graphic("limiter-results.png", 162),
        caption("Figure 5. Steady admission remains at 5 reads/s while the rejection fraction grows."),
        h("Observed behavior"),
        p("With the limiter disabled, actual and admitted rates matched each offered level. "
          "With it enabled, admission stayed at 5/s and rejection rose to 50%, 90% and 97.5%. "
          "The corresponding successful-admission fractions were 50%, 10% and 2.5%. All six "
          "runs reported zero unexpected errors, and a cooldown check admitted the same client again."),
        p("Pacing p95 lag stayed below 51 ms, providing evidence that the load generator reached "
          "its offered rates. HTTP 429 is an intentional rejection and is not counted as completed "
          "product work. Rejected requests still consume some interceptor and networking resources."),
        box("<b>Trade-off:</b> the limiter enforces a stable per-client admission bound and makes overload "
            "visible. This experiment does not measure fairness across many clients or aggregate "
            "enforcement across multiple backend processes."),
    ], level=1)

    read_rows = []
    for mode in ["single", "sharded"]:
        for u in [5, 25, 100]:
            r = next(r for r in http if r["experiment"] == "inventory-reads" and r["mode"] == mode and r["users"] == u)
            point, scatter = r["by_request"]["point"], r["by_request"]["scatter"]
            read_rows.append([
                "Single" if mode == "single" else "3 shards", u,
                n(point["mean_ms"]), n(point["p95_ms"]), n(scatter["mean_ms"]),
                n(scatter["p95_ms"]), n(r["accepted_rps"], 1),
            ])
    add("6.4  Logical-shard read evaluation", "shard-results", [
        p("Both modes contained 100 products across five warehouses, giving 500 inventory "
          "records with 1,000 units each. Requests alternated a product/warehouse point read and "
          "a product-wide query. Cache and rate limiting were disabled."),
        caption("Table 11. One-store versus three-logical-shard inventory reads [5]."),
        table(["Mode", "Users", "Point mean", "Point p95", "Wide mean", "Wide p95", "Reads / s"],
              read_rows, [0.95, 0.64, 1, 0.92, 1, 0.93, 1], size=9),
        p("All latency columns are milliseconds. All rows report 100% successful reads and zero "
          "unexpected errors; each point uses one ten-second measurement window.", "small"),
        graphic("shard-results.png", 161),
        caption("Figure 6. Product-wide latency and total read throughput on the shared MongoDB host."),
        p("At 100 users, point-read mean latency rose from 103.57 to 120.38 ms with sharding. "
          "Product-wide reads rose from 80.03 to 142.83 ms, approximately 78.5%, and accepted "
          "throughput fell from 1,067.6 to 743.8 reads/s. The wide query must consult all owners. "
          "Routing and repeated database work do not add physical CPU or storage capacity."),
        h("Load distribution and resource context"),
        p("For the 100-user sharded run, persistence-call deltas were 7,608 / 7,310 / 8,138 "
          "across shards 1-3, approximately 33.0% / 31.7% / 35.3%. These counters include ramp "
          "and fan-out operations and must not be equated with steady HTTP request counts. "
          "Backend CPU was 15.54% with 453.56 MiB peak RSS; MongoDB used 28.35% CPU and "
          "172.09 MiB peak RSS. Small low-load differences are insufficient for a general speedup claim."),
    ], level=1)

    stock_rows = []
    for mode in ["single", "sharded"]:
        for u in [5, 25, 100]:
            r = next(r for r in http if r["experiment"] == "hot-stock" and r["mode"] == mode and r["users"] == u)
            check = load_json(COMPONENTS / r["folder"] / "stock-check.json")
            outcomes = r["outcomes"]
            stock_rows.append([
                "Single" if mode == "single" else "3 shards", u,
                check["reserved_orders"], outcomes.get("insufficient_stock", 0),
                outcomes.get("write_conflict", 0),
                n(r["accepted_rps"]), check["persisted_reserved_units"],
            ])
    add("6.5  Stock correctness and contention", "stock-results", [
        p("Each burst submitted 100 one-unit orders. Distributed-stock cases used 100 distinct "
          "records with 1,000 units each. Hot-stock cases targeted one record containing ten units. "
          "No automatic retries are hidden in these first-attempt HTTP outcomes."),
        caption("Table 12. Hot-stock burst outcomes; final reserved stock agrees with successful orders [5]."),
        table(["Mode", "Users", "Reserved orders", "No stock", "Retryable writes", "Orders / s", "Final R"],
              stock_rows, [0.95, 0.65, 1.02, 0.9, 1.07, 0.92, 0.8], size=9),
        p("At 5 users both modes reserved ten orders, rejected 50 for insufficient stock and "
          "returned 40 retryable write conflicts. At 100 users only four single-store and two "
          "sharded orders reserved; the other first attempts encountered write conflicts. Correct "
          "rejection prevented overselling but did not consume all available stock."),
        h("Distributed-stock throughput"),
        table(["Concurrent users", "Single-store orders/s", "Sharded orders/s", "Successful orders per case"], [
            ["5", "72.15", "80.13", "100 / 100"],
            ["25", "183.49", "164.20", "100 / 100"],
            ["100", "212.31", "196.85", "100 / 100"],
        ], [1.1, 1.35, 1.35, 1.7], size=9.2),
        p("All six distributed cases completed 100 reservations. All twelve stock reconciliations "
          "passed: stored quantities, reserved orders and reservation audits agreed, with no "
          "oversold units, orphan orders/audits or duplicate/wrong stock owners. Correctness is "
          "supported by persisted-state checks, not just HTTP response codes."),
        h("Success, errors and resources"),
        p("For each hot-stock row, the reserved-order count is also its success percentage because "
          "there are 100 attempts. Expected conflicts remain rejected work; unexpected error "
          "counts were zero. At 100 users, distributed single/sharded sales used 10.18% / 10.03% "
          "backend CPU with 457.96 / 454.85 MiB peak RSS. Very short hot bursts have too few "
          "resource samples for reliable CPU conclusions."),
        box("<b>Safety and progress differ:</b> stock bounds held. Bounded whole-request retries with "
            "stable idempotency keys need a separate evaluation of eventual fulfillment."),
    ], level=1)

    hash_rows = []
    for r in hashing:
        if r["keys"] == 100000:
            hash_rows.append([
                r["virtual_nodes"], " / ".join(f"{v:,}" for v in r["record_distribution"].values()),
                n(r["coefficient_of_variation"], 3), n(r["ring_add_remapped_pct"]),
                n(r["modulo_add_remapped_pct"]),
            ])
    add("6.6  Hash distribution and migration", "migration-results", [
        caption("Table 13. Distribution of 100,000 deterministic keys across three shards [5]."),
        table(["Virtual nodes", "Records: shard 1 / 2 / 3", "CV", "Ring remap %", "Modulo remap %"],
              hash_rows, [0.94, 2.4, 0.7, 1.1, 1.15], size=9),
        graphic("hash-results.png", 153),
        caption("Figure 7. Record balance and three-to-four-shard key movement; CV = standard deviation / mean."),
        p("At the configured 32 virtual nodes, adding a fourth shard moved 26.775% of keys "
          "versus 74.959% for modulo placement. Increasing virtual nodes improved the sample's "
          "record balance, though 128 nodes were still not perfectly balanced. An 80%-hot-key "
          "simulation sent 87.51% of traffic to one owner at 32 virtual nodes, showing that "
          "balanced record counts cannot eliminate a hot key."),
        h("Live three-to-four-to-three membership change"),
        caption("Table 14. Graceful membership change during read traffic [5]."),
        table(["Change", "Scanned", "Moved", "Remapped %", "Server time ms"], [
            [e["action"].capitalize(), e["result"]["recordsScanned"], e["result"]["recordsMoved"],
             n(e["result"]["remappedPercent"]), e["result"]["durationMs"]]
            for e in migration["events"]
        ], [1.05, 0.95, 0.9, 1.25, 1.45], size=9.4),
        p("All 803 full documents were preserved, including quantities, reservations, IDs, "
          "versions and timestamps; duplicate copies after completion were zero. The 25-user "
          "measurement recorded 7,162 accepted reads and 50 explicit snapshot/routing conflicts "
          "out of 7,212 attempts: 99.31% success. Maximum observed HTTP latency was 3,026 ms."),
        p("The migration lock explains the pauses. Source data stayed reachable during removal, "
          "so this experiment demonstrates controlled rebalancing and state preservation rather "
          "than failover after losing a machine. Orders and audits matched their pre-change snapshot.", "small"),
    ], level=1)

    id_rows = [[r["mode"].capitalize(), r["threads"], n(r["ids_per_second"], 0), r["collisions"],
                n(r["cpu_milliseconds"]), n(r["mean_id_characters"], 0)] for r in ids]
    trie_rows = []
    for count in [100, 1000, 10000]:
        for kind in ["selective", "broad"]:
            linear = next(r for r in autocomplete["results"] if r["products"] == count and r["kind"] == kind and r["mode"] == "linear")
            trie = next(r for r in autocomplete["results"] if r["products"] == count and r["kind"] == kind and r["mode"] == "trie")
            trie_rows.append([f"{count:,}", kind, n(linear["mean_ms"], 4), n(trie["mean_ms"], 4),
                              n(trie["p95_ms"], 4), "100%"])
    add("6.7  Snowflake and Trie benchmarks", "algorithm-results", [
        caption("Table 15. Direct Java ID generation: 250,000 IDs per point, one repeat [5]."),
        table(["Generator", "Threads", "IDs / second", "Collisions", "CPU ms", "ID chars"],
              id_rows, [1.13, 0.82, 1.5, 1.01, 1.04, 0.96], size=9),
        p("Snowflake generated 3.40-4.05 million IDs/s versus 1.06-1.40 million for full UUIDs. "
          "All 1.5 million normal benchmark IDs passed collision checks. A fixed-clock "
          "misconfiguration test intentionally produced 1,000 collisions with duplicated node "
          "identities; distinct node IDs produced none. ID lengths include the benchmark prefix. "
          "The whole benchmark JVM peaked at 325.76 MiB RSS.", "small"),
        caption("Table 16. Actual ProductTrie versus pre-tokenized linear search, 500 queries per point [5]."),
        table(["Products", "Prefix", "Linear mean ms", "Trie mean ms", "Trie p95 ms", "Set accuracy"],
              trie_rows, [1.05, 1.12, 1.25, 1.18, 1.16, 1.12], size=9),
        p("For 10,000 products, selective lookup averaged 0.0011 ms with the Trie versus "
          "0.3798 ms with linear scanning. Broad prefixes reversed the result: 1.4315 ms "
          "versus 0.0003 ms. The linear top-eight baseline stops early, whereas the Trie "
          "collects all matching candidates before slicing the result."),
        h("Accuracy, build cost and scope"),
        p("Full candidate-set comparisons and top-eight count/validity checks passed, including "
          "missing-prefix cases. Returned order has no equivalence contract. At 10,000 products, "
          "Trie construction took 1,348.00 ms versus 21.39 ms for the linear index. Approximate "
          "heap growth was 12.73 versus 8.74 MiB, including temporary allocations and garbage "
          "collection effects. The Node process peaked at 156.56 MiB RSS."),
        p("These algorithm timings exclude HTTP and browser rendering. Sub-microsecond "
          "observations are sensitive to timer, warm-up and loop effects; a single repeat is "
          "an initial comparison rather than a stable application response-time guarantee.", "small"),
    ], level=1)

    add("6.8  Verification and evidence coverage", "coverage", [
        h("Backend correctness evidence"),
        p("The latest saved correctness run reports <b>85 tests passed, 0 failures, 0 errors "
          "and 0 skipped</b> across 19 test classes [6]. It includes 20 real replica-set "
          "consistency integration tests. Unit and integration coverage is summarized below."),
        caption("Table 17. Selected executed correctness areas and their assertions."),
        table(["Area", "What the saved tests check"], [
            ["Stock and orders", "Concurrent reservations, non-negative stock, order/audit reconciliation and multi-item failure rollback."],
            ["Idempotency / receipts", "Same-key behavior, replay-safe receipt credit, duplicate-line aggregation and receipt-save rollback."],
            ["Transfers / lifecycle", "Valid dispatch-before-completion, failure rollback and concurrent terminal actions that conserve stock."],
            ["Sharding", "Deterministic routing, raw-document preservation, add/remove/restart and interrupted migration recovery."],
            ["Cache / alerts / IDs", "Authoritative availability, cache adapter behavior, alert uniqueness and Snowflake checks."],
        ], [1.15, 3.5], size=9.3),
        h("Supplier-crawler observations"),
        p("The existing supplier-component document reports a combined sweep of four pages "
          "and 24 valid items in 271 ms: 88.6 items/s, zero rejected items and 100% reported "
          "extraction accuracy [7]. It also reports seven passing crawler tests. These are "
          "document-reported observations; the referenced report does not provide the same "
          "raw-run manifest, repetitions or resource series as the October 24 benchmark bundles."),
        h("Unmeasured or incompletely verified points"),
        p("The shortener exposes redirect timing and collision counters, but no saved "
          "redirect workload series is included. Source inspection also finds zero-offset TTL "
          "index annotations on both createdAt and expiresAt. The createdAt index can make "
          "a mapping eligible for early deletion; the nominal 90-day lifetime must not be "
          "treated as verified until index configuration and expiry behavior are corrected and tested."),
        p("Real multi-member primary step-down, unknown-commit network failures, multi-backend "
          "membership coordination, catalogue coverage in the browser, and supplier-import "
          "atomicity/replay remain outside the measured evidence. Correctness-suite timings "
          "are not throughput measurements. Some database tests retry aborted transactions; "
          "that explains why their outcomes differ from first-attempt HTTP contention results."),
        box("<b>Evidence conclusion:</b> the report covers all three workload levels, multiple baselines, "
            "resource observations, stock reconciliation, shard changes and a cache outage. "
            "Missing component metrics and untested failure modes are explicit rather than inferred."),
    ], level=1)

    add("7  Scalability, reliability and trade-offs", "analysis", [
        caption("Table 18. Observed bottlenecks and justified next steps."),
        table(["Observation", "Design trade-off", "Next engineering step"], [
            ["Logical shards share resources", "Partition routing is demonstrated, but one MongoDB deployment supplies capacity.", "Evaluate native physical sharding or replicated placement with explicit transaction semantics."],
            ["Product-wide reads fan out", "Point locality is gained at the cost of multi-owner queries.", "Batch and parallelize bounded reads, paginate results and evaluate access-pattern-specific indexes."],
            ["Hot stock causes many conflicts", "Conditional writes and transactions favor stock safety over first-attempt admission.", "Measure bounded backoff/retry and fair admission using stable request identities."],
            ["Migration pauses inventory", "A local lock simplifies preservation and recovery.", "Stream/batch copies and design versioned online routing with coordination and fencing."],
            ["Cache outage is slow", "Database fallback preserves responses while cache timeouts dominate latency.", "Add a measured circuit-breaker policy and bound fallback latency."],
            ["Broad Trie prefixes are expensive", "Subtree collection precedes top-eight truncation.", "Maintain bounded suggestions or stop traversal early under a defined ranking contract."],
        ], [1.13, 1.65, 1.95], size=9.15),
        h("7.1  Reliability boundaries"),
        p("The local router lock and token buckets are process-scoped. Multiple application "
          "instances would need coordinated membership, unique Snowflake node identities and "
          "a deliberate rate-limit policy. A one-member replica set does not tolerate loss "
          "of its host. Graceful shard removal requires accessible source data; replication "
          "and failover must be designed separately."),
        h("7.2  Validity of the evaluation"),
        p("All services and JMeter share one Windows host with background applications. "
          "The component suite uses short runs and one repetition; cache comparisons have "
          "three repetitions but still share resources and a warm database working set. "
          "Algorithm tests exclude network and UI costs. These conditions explain why larger "
          "concurrency does not yield a smooth or universally beneficial performance trend."),
        h("7.3  Remaining functional design work"),
        p("Orders and movement data need a partitioning strategy to complete the assignment's "
          "transaction-data requirement. Reservation expiry is deferred. Large receipt histories "
          "would benefit from a bounded receipt ledger, while supplier imports need a durable "
          "batch identity and clearer atomicity. Short-link lifetime behavior needs correction "
          "and dedicated measurement before a retention guarantee is made."),
    ])

    add("8  End-to-end demonstration", "demonstration", [
        p("The following sequence demonstrates the integrated prototype and connects each "
          "visible action to a correctness or system-design observation. Example quantities "
          "are illustrative and can be entered through the UI after the environment is ready."),
        caption("Table 19. Suggested demonstration sequence (approximately 10-15 minutes)."),
        table(["Step", "Action", "What to show"], [
            ["1", "Register one product and two warehouses.", "Generated IDs, unique SKU validation and initially empty warehouse stock."],
            ["2", "Create a 40-unit purchase order and receive it.", "CREATED to RECEIVED; 40 physical/available units and a purchase movement."],
            ["3", "Search the product by SKU/name prefix.", "Trie suggestions, product selection and authoritative warehouse quantities."],
            ["4", "Reserve a 4-unit sales order, then complete it.", "P=40, R=4, A=36 while reserved; P=36, R=0 after completion."],
            ["5", "Transfer 5 units to the second warehouse.", "Source falls to 31 while 5 are in transit; completion credits destination to 5."],
            ["6", "Take availability to the reorder threshold.", "One active low-stock alert; replenishment above threshold resolves it."],
            ["7", "Inspect routes and activate/deactivate shard 4.", "Record/request distribution, movement percentage and preserved stock after both changes."],
            ["8", "Run supplier discovery and review/import a feed.", "Different portal formats, crawl telemetry and no physical-stock credit before receipt."],
            ["9", "Demonstrate short-link creation and resolution.", "302 response, target, click counter and timing header; validate current expiry behavior."],
            ["10", "Open the saved evaluation evidence.", "Three workload levels, expected rejections, stock checks, migration and Redis recovery."],
        ], [0.42, 1.8, 2.9], size=9.1),
        h("Useful API and script entry points"),
        p("<b>Core:</b> /api/products, /api/warehouses, /api/inventory, /api/purchase-orders, "
          "/api/sales-orders, /api/transfers and /api/alerts/low-stock.<br/>"
          "<b>Components:</b> /api/shards, /api/shards/route, /api/shorten, /r/{code} and "
          "/api/suppliers/import.<br/>"
          "<b>Scripts:</b> scripts/demo-sharding.ps1; scripts/e2e_integration_demo.js; "
          "scripts/test-consistency.ps1; scripts/test-components.ps1.", "small"),
        p("Use transaction-capable MongoDB for the backend and the supplier portal on "
          "port 8085 for crawler demonstrations. The sharding demo creates sample records "
          "and changes membership, so run it against the selected demonstration dataset. "
          "Saved benchmark results can be presented directly alongside live workflow observations.", "small"),
    ])

    add("9  Conclusion and references", "conclusion", [
        p("AetherStock integrates the principal inventory workflows with eight system-design "
          "components and an explicit stock-consistency model. Shared MongoDB transactions "
          "and guarded mutations protect business state, while consistent hashing supports "
          "controlled logical partitioning. The saved tests and reconciliations demonstrate "
          "stock safety and document preservation in the executed scenarios."),
        p("The evaluation's main lesson is that component benefits depend on workload and "
          "deployment. Redis removes product database reads but does not always improve "
          "latency; logical sharding adds overhead without new physical resources; hot-stock "
          "transactions preserve safety while limiting immediate progress. The prototype "
          "therefore meets the goal of an evidence-based micro-project demonstration, with "
          "transaction-data partitioning, physical scale-out and remaining verification gaps "
          "clearly identified as future work."),
        h("References and evidence index"),
        p("<b>[1]</b> Sri Sivasubramaniya Nadar College of Engineering. <i>Mini Project Problem "
          "Statements</i>, UCS3513 System Design Laboratory, Topic 4, pp. 8-9, 2026-27. "
          "Repository: Mini Project Problem Statements.pdf.", "reference"),
        p("<b>[2]</b> AetherStock implementation and consistency design. "
          "backend/src/main/java/com/example/inventory/; docs/consistency.md; "
          "backend/src/main/resources/application.yml.", "reference"),
        p("<b>[3]</b> AetherStock logical-sharding design and recovery protocol. "
          "docs/sharding.md; backend/src/main/java/com/example/inventory/sharding/.", "reference"),
        p("<b>[4]</b> Product-cache benchmark bundle, 24 October 2026. "
          "evaluation/load-results/20261024-130716-cache/: README.md, manifest.json, "
          "summary.csv, redis-failure-check.json and per-run raw/resource observations.", "reference"),
        p("<b>[5]</b> Component benchmark bundle, 24 October 2026. "
          "evaluation/results/20261024-145210-components/: README.md, FINDINGS.md, "
          "manifest.json, http-results.json, migration-check.json, hashing.json, ids.json, "
          "autocomplete.json and per-run stock checks. Runner: evaluation/QUICK-TESTS.md.", "reference"),
        p("<b>[6]</b> Correctness evidence, 24 October 2026. "
          "evaluation/results/20261024-125728-079-consistency/README.md: "
          "85 passing backend tests and per-class timings.", "reference"),
        p("<b>[7]</b> Supplier integration and reported crawler observations. "
          "docs/member-5-supplier-crawler-and-integration.md; crawler/crawler_engine.js; "
          "crawler/test_crawler.js. Autocomplete implementation: frontend/src/services/trie.js.", "reference"),
        p("<b>[8]</b> MongoDB, <i>Transactions</i>. "
          '<link href="https://www.mongodb.com/docs/manual/core/transactions/" color="#0066a6">'
          "https://www.mongodb.com/docs/manual/core/transactions/</link>. "
          "Spring Data MongoDB transaction support: "
          '<link href="https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html" color="#0066a6">'
          "official session and transaction reference</link>.", "reference"),
        p("<b>[9]</b> Apache JMeter, <i>User Manual</i>. "
          '<link href="https://jmeter.apache.org/usermanual/" color="#0066a6">'
          "https://jmeter.apache.org/usermanual/</link>. "
          "Institutional logo asset: SSN official website, "
          '<link href="https://www.ssn.edu.in/" color="#0066a6">https://www.ssn.edu.in/</link>.'
          " Logo source retrieved 25 October 2026.", "reference"),
    ])

    # The cover is a separate page. Roman preliminary pages precede body page 1.
    toc_rows = [[f'<link href="#abstract" color="#173753">Abstract</link>', "i"]]
    for physical_index, page in enumerate(pages[2:], start=4):
        indent = "&nbsp;&nbsp;&nbsp;" if page.level else ""
        toc_rows.append([
            f'{indent}<link href="#{page.key}" color="#173753">{escape(page.title)}</link>',
            str(physical_index - 3),
        ])
    toc = table(["Section", "Page"], toc_rows, [8, 0.85], size=9.45)
    toc.setStyle(TableStyle([
        ("TOPPADDING", (0, 0), (-1, -1), 3.3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3.3),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white]),
    ]))
    pages[1].items = [
        p("Contents", "title"), toc, h("List of figures"),
        p("1. Implemented architecture ........................................................ 3<br/>"
          "2. Consistent-hash ring ............................................................... 6<br/>"
          "3. Sales and transfer lifecycles .................................................. 10<br/>"
          "4. Product-cache comparison ................................................... 12<br/>"
          "5. Token-bucket admission ....................................................... 14<br/>"
          "6. Logical-shard read comparison ............................................. 15<br/>"
          "7. Hash distribution and remapping .......................................... 17", "small"),
    ]
    assert failure["passed"] and migration["passed"], "Saved failure/migration checks must pass."
    return pages


def measure_page(page):
    placements = []
    used, previous_after = 0.0, 0.0
    for flowable in page.items:
        before = flowable.getSpaceBefore()
        gap = max(previous_after, before) if used else before
        _, height = flowable.wrap(WIDTH, BODY_HEIGHT)
        used += gap
        placements.append((flowable, used, height))
        used += height
        previous_after = flowable.getSpaceAfter()
    if used > BODY_HEIGHT + 0.1:
        raise ValueError(f"Page '{page.title}' overflows by {used - BODY_HEIGHT:.1f} pt (used {used:.1f}).")
    return placements, used


def cover(c):
    c.bookmarkPage("cover")
    c.addOutlineEntry("Cover", "cover", level=0)
    c.setStrokeColor(RULE)
    c.setLineWidth(0.7)
    c.rect(40, 38, PAGE_W - 80, PAGE_H - 76)
    logo_w = 118
    logo_h = logo_w * 196 / 478
    c.drawImage(str(ASSETS / "ssn-logo.png"), (PAGE_W - logo_w) / 2,
                728, logo_w, logo_h, mask="auto")
    c.setFillColor(INK)
    c.setFont(SERIF_BOLD, 14.5)
    c.drawCentredString(PAGE_W / 2, 700, "Sri Sivasubramaniya Nadar College of Engineering")
    c.setFont(SERIF, 10.6)
    c.drawCentredString(PAGE_W / 2, 681, "(An Autonomous Institution, Affiliated to Anna University, Chennai)")
    c.setFont(SERIF_BOLD, 17)
    c.drawCentredString(PAGE_W / 2, 616, "UCS3513 System Design Laboratory")
    c.setFont(SERIF_BOLD, 15)
    c.drawCentredString(PAGE_W / 2, 578, "MICRO PROJECT REPORT")
    c.setFillColor(NAVY)
    c.setFont(SERIF_BOLD, 29)
    c.drawCentredString(PAGE_W / 2, 515, "AetherStock")
    c.setFont(SERIF_BOLD, 17)
    c.drawCentredString(PAGE_W / 2, 486, "Scalable Inventory Management System")
    c.setStrokeColor(BLUE)
    c.line(165, 467, PAGE_W - 165, 467)
    c.setFillColor(INK)
    c.setFont(SERIF, 12)
    c.drawCentredString(PAGE_W / 2, 434, "B.E. Computer Science and Engineering")
    c.setFont(SERIF, 11)
    c.drawCentredString(PAGE_W / 2, 414, "III Year / V Semester  |  Academic Year 2026-2027")
    c.setFont(SERIF_BOLD, 13)
    c.drawCentredString(PAGE_W / 2, 359, "Submitted by")
    c.setFont(SERIF_BOLD, 11)
    c.drawString(80, 332, "S.No.")
    c.drawString(137, 332, "Name")
    c.drawString(367, 332, "Register Number")
    c.setStrokeColor(RULE)
    c.line(78, 322, PAGE_W - 78, 322)
    for index in range(6):
        baseline = 297 - index * 25
        c.setFont(SERIF, 11)
        c.setFillColor(INK)
        c.drawString(89, baseline, str(index + 1))
        # Empty, printable fields allow either handwriting or entry in a PDF reader.
        for kind, x, width in [("name", 134, 210), ("register", 365, 144)]:
            c.acroForm.textfield(
                name=f"team_{index + 1}_{kind}", tooltip=f"Team member {index + 1} {kind}",
                x=x, y=baseline - 3, width=width, height=19,
                value="", fontName="Helvetica", fontSize=10,
                borderWidth=0.5, borderStyle="underlined", borderColor=RULE,
                fillColor=colors.white, textColor=INK, forceBorder=True,
                fieldFlags="", maxlen=65 if kind == "name" else 25,
            )
    c.setFont(SERIF, 11)
    c.drawCentredString(PAGE_W / 2, 112, "Department of Computer Science and Engineering")
    c.drawCentredString(PAGE_W / 2, 91, "October 2026")
    c.showPage()


def generate(output, layout_path=None):
    pages = build_pages()
    measured = [(page, *measure_page(page)) for page in pages]
    if len(pages) + 1 != 25:
        raise ValueError(f"Expected 25 pages, found {len(pages) + 1}.")
    output.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(output), pagesize=A4, pageCompression=1)
    c.setTitle("AetherStock: Scalable Inventory Management System")
    c.setAuthor("Project team")
    c.setSubject("UCS3513 System Design Laboratory - Micro Project Report")
    c.setKeywords("AetherStock, inventory, SSN, UCS3513, micro project, consistent hashing, evaluation")
    c.setViewerPreference("DisplayDocTitle", "true")
    cover(c)
    layout = []
    for physical, (page, placements, used) in enumerate(measured, start=2):
        c.bookmarkPage(page.key)
        c.addOutlineEntry(page.title, page.key, level=page.level)
        label = "i" if physical == 2 else "ii" if physical == 3 else str(physical - 3)
        c.setFillColor(GREY)
        c.setFont("Helvetica", 8)
        c.drawString(LEFT, PAGE_H - 39, "AETHERSTOCK  /  MICRO PROJECT REPORT")
        c.drawRightString(PAGE_W - RIGHT, PAGE_H - 39, "UCS3513 System Design Laboratory")
        c.setStrokeColor(RULE)
        c.setLineWidth(0.5)
        c.line(LEFT, PAGE_H - 49, PAGE_W - RIGHT, PAGE_H - 49)
        c.line(LEFT, 42, PAGE_W - RIGHT, 42)
        c.setFont("Helvetica", 8)
        c.drawString(LEFT, 28, "Sri Sivasubramaniya Nadar College of Engineering")
        c.drawRightString(PAGE_W - RIGHT, 28, label)
        for flowable, offset, height in placements:
            flowable.drawOn(c, LEFT, TOP - offset - height)
        layout.append({"pdf_page": physical, "printed_page": label, "title": page.title,
                       "used_height_pt": round(used, 2), "free_height_pt": round(BODY_HEIGHT - used, 2)})
        c.showPage()
    c.save()
    if layout_path:
        layout_path.parent.mkdir(parents=True, exist_ok=True)
        layout_path.write_text(json.dumps(layout, indent=2), encoding="utf-8")
    print(f"Created {output} ({len(pages) + 1} A4 pages, {output.stat().st_size:,} bytes)")
    print(f"Layout preflight passed; minimum spare body height: {min(r['free_height_pt'] for r in layout):.1f} pt")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "AetherStock_Project_Report.pdf")
    parser.add_argument("--layout-json", type=Path, default=None)
    args = parser.parse_args()
    generate(args.output.resolve(), args.layout_json)
