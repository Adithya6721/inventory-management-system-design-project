# API Contracts & Specification — Member 1 (Core Inventory)

This document specifies the REST API endpoints provided by the **Core Inventory & Database** backend. All endpoints adhere to standard HTTP verbs, predictable status codes, and consistent JSON error formats.

---

## Base URL
```
http://localhost:8080/api
```

---

## 1. Product Management (`/api/products`)

### 1.1 Register Product
- **Method:** `POST`
- **Path:** `/api/products`
- **Status:** `201 Created`
- **Request Body:**
```json
{
  "sku": "SKU-MACBOOK-M3",
  "name": "Apple MacBook Pro M3",
  "description": "14-inch Space Gray, 16GB RAM, 512GB SSD",
  "category": "Laptops",
  "price": 1999.00,
  "reorderLevel": 10,
  "active": true
}
```
- **Response (201):**
```json
{
  "productId": "prod_a1b2c3d4e5f6",
  "sku": "SKU-MACBOOK-M3",
  "name": "Apple MacBook Pro M3",
  "description": "14-inch Space Gray, 16GB RAM, 512GB SSD",
  "category": "Laptops",
  "price": 1999.00,
  "reorderLevel": 10,
  "active": true,
  "createdAt": "2026-10-11T11:00:00Z",
  "updatedAt": "2026-10-11T11:00:00Z"
}
```
- **Error (409 Conflict):** If SKU already exists.

---

### 1.2 Get Product by ID
- **Method:** `GET`
- **Path:** `/api/products/{productId}`
- **Status:** `200 OK`
- **Response:**
```json
{
  "productId": "prod_a1b2c3d4e5f6",
  "sku": "SKU-MACBOOK-M3",
  "name": "Apple MacBook Pro M3",
  "description": "14-inch Space Gray, 16GB RAM, 512GB SSD",
  "category": "Laptops",
  "price": 1999.00,
  "reorderLevel": 10,
  "active": true,
  "createdAt": "2026-10-11T11:00:00Z",
  "updatedAt": "2026-10-11T11:00:00Z"
}
```
- **Error (404 Not Found):** If product ID does not exist.

---

### 1.3 Search & List Products (with Pagination)
- **Method:** `GET`
- **Path:** `/api/products?query={query}&category={category}&page=0&size=20`
- **Query Parameters:**
  - `query` (optional): case-insensitive search by product name or SKU.
  - `category` (optional): category filter.
  - `page` (optional, default `0`): zero-indexed page number.
  - `size` (optional, default `20`): items per page.
- **Status:** `200 OK`
- **Response:**
```json
{
  "content": [
    {
      "productId": "prod_a1b2c3d4e5f6",
      "sku": "SKU-MACBOOK-M3",
      "name": "Apple MacBook Pro M3",
      "category": "Laptops",
      "price": 1999.00,
      "reorderLevel": 10,
      "active": true
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### 1.4 Update Product
- **Method:** `PUT`
- **Path:** `/api/products/{productId}`
- **Status:** `200 OK`
- **Request Body:** Same schema as Product Registration.

---

### 1.5 Soft Delete Product
- **Method:** `DELETE`
- **Path:** `/api/products/{productId}`
- **Status:** `204 No Content`

---

## 2. Warehouse Management (`/api/warehouses`)

### 2.1 Create Warehouse
- **Method:** `POST`
- **Path:** `/api/warehouses`
- **Status:** `201 Created`
- **Request Body:**
```json
{
  "name": "Central Distribution Hub",
  "location": "Chicago, IL",
  "status": "ACTIVE"
}
```
- **Response (201):**
```json
{
  "warehouseId": "wh_789abc123def",
  "name": "Central Distribution Hub",
  "location": "Chicago, IL",
  "status": "ACTIVE",
  "createdAt": "2026-10-11T11:00:00Z",
  "updatedAt": "2026-10-11T11:00:00Z"
}
```

---

### 2.2 List Warehouses
- **Method:** `GET`
- **Path:** `/api/warehouses`
- **Status:** `200 OK`

---

### 2.3 Get Warehouse Inventory
- **Method:** `GET`
- **Path:** `/api/warehouses/{warehouseId}/inventory`
- **Status:** `200 OK`
- **Response:** Array of `InventoryResponse` objects for the warehouse.

---

## 3. Inventory Operations (`/api/inventory`)

### 3.1 Get Stock Across All Warehouses
- **Method:** `GET`
- **Path:** `/api/inventory/{productId}`
- **Status:** `200 OK`
- **Response:**
```json
[
  {
    "productId": "prod_a1b2c3d4e5f6",
    "warehouseId": "wh_789abc123def",
    "quantity": 50,
    "reservedQuantity": 10,
    "availableQuantity": 40,
    "updatedAt": "2026-10-11T11:05:00Z"
  }
]
```

---

### 3.2 Get Stock at a Specific Warehouse
- **Method:** `GET`
- **Path:** `/api/inventory/{productId}/{warehouseId}`
- **Status:** `200 OK`
- **Response:** Single `InventoryResponse` object.

---

### 3.3 Manual Inventory Adjustment
- **Method:** `POST`
- **Path:** `/api/inventory/adjustments`
- **Status:** `200 OK`
- **Request Body:**
```json
{
  "productId": "prod_a1b2c3d4e5f6",
  "warehouseId": "wh_789abc123def",
  "physicalDelta": 10,
  "reservedDelta": 0,
  "reason": "Quarterly physical audit stock count correction"
}
```
- **Response (200):** Updated `InventoryResponse`.

---

### 3.4 Inventory Movements Audit Trail
- **Method:** `GET`
- **Path:** `/api/inventory/{productId}/movements`
- **Status:** `200 OK`
- **Response:**
```json
[
  {
    "movementId": "mov_123456789abc",
    "productId": "prod_a1b2c3d4e5f6",
    "warehouseId": "wh_789abc123def",
    "movementType": "PURCHASE_RECEIPT",
    "quantityDelta": 50,
    "referenceId": "po_456def789abc",
    "timestamp": "2026-10-11T11:00:00Z"
  }
]
```

---

## 4. Purchase Orders (`/api/purchase-orders`)

### 4.1 Create Purchase Order
- **Method:** `POST`
- **Path:** `/api/purchase-orders`
- **Status:** `201 Created`
- **Request Body:**
```json
{
  "supplierId": "supp_apple_direct",
  "destinationWarehouseId": "wh_789abc123def",
  "items": [
    {
      "productId": "prod_a1b2c3d4e5f6",
      "quantity": 100
    }
  ],
  "idempotencyKey": "PO-REQ-98765"
}
```

---

### 4.2 Receive Stock for Purchase Order
- **Method:** `POST`
- **Path:** `/api/purchase-orders/{orderId}/receive`
- **Status:** `200 OK`
- **Request Body (Optional itemized receipt):**
```json
{
  "items": [
    {
      "productId": "prod_a1b2c3d4e5f6",
      "quantityReceived": 50
    }
  ],
  "idempotencyKey": "REC-BATCH-1"
}
```
*(If request body is omitted or items empty, all remaining quantities are received).*

---

## 5. Sales Orders (`/api/sales-orders`)

### 5.1 Place Sales Order (Atomic Stock Reservation)
- **Method:** `POST`
- **Path:** `/api/sales-orders`
- **Status:** `201 Created`
- **Request Body:**
```json
{
  "items": [
    {
      "productId": "prod_a1b2c3d4e5f6",
      "warehouseId": "wh_789abc123def",
      "quantity": 2
    }
  ],
  "idempotencyKey": "ORDER-CUST-1001"
}
```
- **Response (201):** Status is set to `RESERVED`.
- **Error (409 Conflict):** If available stock is insufficient (`quantity - reservedQuantity < requested`).

---

### 5.2 Cancel Sales Order (Releases Reservation)
- **Method:** `POST`
- **Path:** `/api/sales-orders/{orderId}/cancel`
- **Status:** `200 OK`
- **Response:** Status set to `CANCELLED`, reserved stock atomically released.

---

### 5.3 Complete / Fulfill Sales Order
- **Method:** `POST`
- **Path:** `/api/sales-orders/{orderId}/complete`
- **Status:** `200 OK`
- **Response:** Status set to `COMPLETED`, stock physically deducted.

---

## 6. Stock Transfers (`/api/transfers`)

### 6.1 Initiate Stock Transfer
- **Method:** `POST`
- **Path:** `/api/transfers`
- **Status:** `201 Created`
- **Request Body:**
```json
{
  "sourceWarehouseId": "wh_789abc123def",
  "destinationWarehouseId": "wh_boston_456",
  "items": [
    {
      "productId": "prod_a1b2c3d4e5f6",
      "quantity": 10
    }
  ],
  "idempotencyKey": "TRN-TRUCK-5432"
}
```
- **Behavior:** Source stock is deducted into `IN_TRANSIT`.

---

### 6.2 Complete Stock Transfer
- **Method:** `POST`
- **Path:** `/api/transfers/{transferId}/complete`
- **Status:** `200 OK`
- **Behavior:** Destination warehouse is credited, status set to `COMPLETED`.

---

## 7. Low Stock Alerts (`/api/alerts`)

### 7.1 List Active Alerts
- **Method:** `GET`
- **Path:** `/api/alerts/low-stock?warehouseId={warehouseId}`
- **Status:** `200 OK`
- **Response:**
```json
[
  {
    "alertId": "alert_987654",
    "productId": "prod_a1b2c3d4e5f6",
    "warehouseId": "wh_789abc123def",
    "threshold": 10,
    "availableQuantity": 4,
    "status": "ACTIVE",
    "createdAt": "2026-10-11T11:15:00Z",
    "updatedAt": "2026-10-11T11:20:00Z"
  }
]
```

---

## 8. Supplier Crawler Import (`/api/suppliers/import`)

### 8.1 Ingest Crawler Feed (Member 5 Integration)
- **Method:** `POST`
- **Path:** `/api/suppliers/import`
- **Status:** `200 OK`
- **Request Body:**
```json
{
  "supplierId": "supp_tech_distributor",
  "supplierName": "Tech Distributor Direct",
  "targetWarehouseId": "wh_789abc123def",
  "autoCreatePurchaseOrder": true,
  "items": [
    {
      "sku": "SKU-MONITOR-4K",
      "name": "Dell UltraSharp 27 4K",
      "category": "Monitors",
      "supplierPrice": 499.00,
      "supplierReportedStock": 50,
      "externalUrl": "https://supplier.example.com/items/4k-monitor"
    }
  ]
}
```
- **Rule:** Supplier reported stock **does NOT inflate physical warehouse stock**. It registers product metadata and optionally proposes a draft Purchase Order!

---

## Standard Error Response Structure
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "Insufficient available stock to reserve item: prod_123 at warehouse: wh_456 (requested: 15)",
  "path": "/api/sales-orders",
  "timestamp": "2026-10-11T11:30:00Z",
  "details": null
}
```
