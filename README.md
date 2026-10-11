# Scalable Inventory Management System — Member 1 Core Backend

This repository houses the core inventory and database backend for the System Design Laboratory project (**Topic 4: Scalable Inventory Management System**).

Developed by **Member 1: Core Inventory & Database**.

---

## Member Responsibilities & Architecture Boundary

| Member | Focus Area | Member 1 Boundary / Integration Point |
| :--- | :--- | :--- |
| **Member 1 (This Module)** | **Core Inventory, Database & Business Logic** | Implements domain models, atomic stock rules, orders, transfers, alerts, and stable integration ports. |
| **Member 2** | Sharding & Consistent Hashing | Member 1 provides `InventoryPersistencePort`. Member 2 plugs in shard routing without touching core services. |
| **Member 3** | Redis, Rate Limiting & Unique IDs | Member 1 provides `InventoryCachePort` and `IdGeneratorPort`. |
| **Member 4** | React Frontend & Autocomplete | Member 1 provides documented REST APIs (`docs/api-contracts.md`) and preconfigured CORS. |
| **Member 5** | Supplier Crawler & Integration | Member 1 provides `POST /api/suppliers/import` preventing artificial warehouse inflation. |

---

## Key Invariants & Consistency Rules

- **Available Stock Formula:**
  $$\text{Available Stock} = \text{Quantity} - \text{Reserved Quantity}$$
- **Invariants Maintained:**
  - `quantity >= 0`
  - `reservedQuantity >= 0`
  - `reservedQuantity <= quantity`
  - `availableStock >= 0`
- **Atomic Operations:** Mongo conditional updates (`findAndModify`) ensure that under high concurrency, overselling and negative inventory are impossible.
- **Saga Pattern / Rollback:** Multi-item orders that fail on subsequent items automatically release reservations on prior items.
- **Audit Trail:** Every committed change logs an immutable `InventoryMovement` record.
- **Deduplication:** Low stock alerts are created once and updated in-place without generating spam duplicates; resolved automatically when stock recovers.

---

## Tech Stack
- **Java:** 17 LTS (configured in `pom.xml`)
- **Framework:** Spring Boot 3.3.4 (Spring Web, Spring Data MongoDB, Validation, Actuator)
- **Database:** MongoDB 7 / 8
- **Containerization:** Docker & Docker Compose
- **Testing:** JUnit 5, Mockito, SpringBootTest, Multi-threaded Concurrency Harness

---

## Project Structure

```
.
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/example/inventory/
│       │   │   ├── config/             # Mongo & CORS configurations
│       │   │   ├── common/             # GlobalExceptionHandler, ApiError, Exceptions
│       │   │   ├── product/            # Products CRUD, search & pagination
│       │   │   ├── warehouse/          # Warehouses & inventory query
│       │   │   ├── inventory/          # Core stock, movements & persistence adapter
│       │   │   ├── purchase/           # Purchase orders & stock receipts
│       │   │   ├── sales/              # Sales orders with atomic reservation
│       │   │   ├── transfer/           # Warehouse-to-warehouse stock transfers
│       │   │   ├── notification/       # Low-stock alerts with deduplication
│       │   │   └── integration/        # Ports for Members 2, 3, and 5
│       │   └── resources/
│       │       └── application.yml
│       └── test/
│           └── java/com/example/inventory/
│               ├── product/ProductServiceTest.java
│               ├── warehouse/WarehouseServiceTest.java
│               ├── inventory/InventoryServiceTest.java
│               ├── purchase/PurchaseOrderServiceTest.java
│               ├── sales/SalesOrderServiceTest.java
│               ├── sales/SalesOrderConcurrencyTest.java
│               ├── transfer/StockTransferServiceTest.java
│               ├── notification/LowStockServiceTest.java
│               └── integration/InventoryIntegrationTest.java
├── docker-compose.yml
├── docs/
│   ├── api-contracts.md                # Full REST API documentation & JSON examples
│   ├── integration-guide.md            # Integration guides for Members 2, 3, 4, 5
│   └── testing.md                      # Test report & 100-thread concurrency results
└── README.md
```

---

## Quickstart & Local Execution

### 1. Build and Run Tests
```powershell
cd backend
mvn clean test
```
All 25 automated tests (unit, integration, and concurrency) will run.

### 2. Run Locally
Ensure MongoDB is running on port 27017:
```powershell
cd backend
mvn spring-boot:run
```
The server will start on `http://localhost:8080`.

### 3. Run with Docker Compose
```powershell
docker compose up -d --build
```
This launches:
- `inventory-mongodb`: MongoDB on `27017`
- `inventory-redis`: Redis on `6379`
- `inventory-backend`: Spring Boot API on `8080`

---

## API Summary

| Verb | Path | Description |
| :--- | :--- | :--- |
| `POST` | `/api/products` | Register product (unique SKU) |
| `GET` | `/api/products/{id}` | Get product details |
| `GET` | `/api/products?query=&category=` | Search products with pagination |
| `PUT` | `/api/products/{id}` | Update product |
| `DELETE` | `/api/products/{id}` | Soft delete product |
| `POST` | `/api/warehouses` | Create warehouse |
| `GET` | `/api/warehouses` | List warehouses |
| `GET` | `/api/warehouses/{id}/inventory` | Get warehouse-wise inventory |
| `GET` | `/api/inventory/{productId}` | Stock across all warehouses |
| `GET` | `/api/inventory/{productId}/{whId}` | Stock at specific warehouse |
| `POST` | `/api/inventory/adjustments` | Manual stock adjustment |
| `GET` | `/api/inventory/{productId}/movements`| Audit trail history |
| `POST` | `/api/purchase-orders` | Create purchase order |
| `POST` | `/api/purchase-orders/{id}/receive` | Receive purchased stock |
| `POST` | `/api/sales-orders` | Place sales order (atomic reservation) |
| `POST` | `/api/sales-orders/{id}/cancel` | Cancel order & release stock |
| `POST` | `/api/sales-orders/{id}/complete` | Fulfill order & deduct stock |
| `POST` | `/api/transfers` | Initiate stock transfer |
| `POST` | `/api/transfers/{id}/complete` | Complete transfer & credit destination |
| `GET` | `/api/alerts/low-stock` | List active low stock alerts |
| `POST` | `/api/suppliers/import` | Ingest crawler data (Member 5) |
