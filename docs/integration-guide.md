# Team Integration Guide — Member 1 Contracts & Interfaces

This document explains how team members (Members 2, 3, 4, and 5) integrate with Member 1's **Core Inventory & Database Backend**.

---

## Architecture Overview

```
                      +-----------------------------+
                      | Member 4: React UI / Client |
                      +--------------+--------------+
                                     | REST (JSON)
                                     v
+---------------------+   +--------------------------+   +---------------------+
| Member 3: ID Gen    |-->| Member 1: Core Inventory |<--| Member 3: Redis     |
| (IdGeneratorPort)   |   | Services & Controllers   |   | (InventoryCachePort)|
+---------------------+   +-------------+------------+   +---------------------+
                                        |
               Persistence Port Contract| (InventoryPersistencePort)
                                        v
                          +--------------------------+
                          | Member 2: Sharding       |
                          | Router / Consistent Hash |
                          +-------------+------------+
                                        |
                                        v
                          +--------------------------+
                          | MongoDB Shards / DBs     |
                          +--------------------------+
```

---

## 1. Member 2: Sharding & Consistent Hashing

### Interface to Implement: `InventoryPersistencePort`
**Implemented by Member 2:** `ShardedInventoryPersistenceRouter` is the primary
port implementation when sharding is enabled (the default). The existing service
and API contracts are unchanged. See [sharding.md](sharding.md) for configuration,
membership management, migration recovery and the working demonstration.

- **Location:** `com.example.inventory.integration.InventoryPersistencePort`
- **Responsibility:** Route inventory reads, writes, and atomic reservations to the appropriate shard determined by hashing `productId` or composite key `(productId, warehouseId)`.

```java
package com.example.inventory.integration;

import com.example.inventory.inventory.InventoryRecord;
import java.util.List;
import java.util.Optional;

public interface InventoryPersistencePort {
    Optional<InventoryRecord> findByProductIdAndWarehouseId(String productId, String warehouseId);
    List<InventoryRecord> findByProductId(String productId);
    List<InventoryRecord> findByWarehouseId(String warehouseId);
    InventoryRecord save(InventoryRecord record);

    // Atomic conditional updates per shard
    boolean reserveStock(String productId, String warehouseId, long quantity);
    boolean releaseReservedStock(String productId, String warehouseId, long quantity);
    boolean deductReservedStock(String productId, String warehouseId, long quantity);
    boolean deductAvailableStockDirect(String productId, String warehouseId, long quantity);
    InventoryRecord creditStock(String productId, String warehouseId, long quantity);
    boolean adjustStock(String productId, String warehouseId, long physicalDelta, long reservedDelta);
}
```

### How Member 2 Plugs In:
Create a Spring `@Component` or `@Primary` bean implementing `InventoryPersistencePort`:
```java
@Component
@Primary
public class ShardedInventoryPersistenceRouter implements InventoryPersistencePort {
    private final ConsistentHashRouter hashRing;
    private final Map<String, MongoTemplate> shardTemplates;

    // Route operations using hashRing.route(productId)
}
```
**Zero changes are required inside `InventoryService`, `SalesOrderService`, or `StockTransferService`!**

---

## 2. Member 3: Redis Cache, Rate Limiter & ID Generator

### 2.1 Unique ID Generator: `IdGeneratorPort`
- **Location:** `com.example.inventory.integration.IdGeneratorPort`
- **Default Provided:** `DefaultUuidGeneratorPort` (12-char hex UUID with prefix)
- **Member 3 Task:** Supply a Snowflake or Redis Ticket ID generator by creating a Spring bean:
```java
@Component
@Primary
public class SnowflakeIdGenerator implements IdGeneratorPort {
    @Override
    public String generateId(String prefix) {
        long snowflake = ...; // generate 64-bit ID
        return prefix + "_" + snowflake;
    }
}
```

### 2.2 Redis Cache Invalidation: `InventoryCachePort`
- **Location:** `com.example.inventory.integration.InventoryCachePort`
- **Member 3 Task:** Member 1 invokes these hooks upon any committed mutation. Member 3 provides the Redis cache evictions:
```java
@Component
@Primary
public class RedisInventoryCacheAdapter implements InventoryCachePort {
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void evictProduct(String productId) {
        redisTemplate.delete("cache:product:" + productId);
    }

    @Override
    public void evictInventory(String productId, String warehouseId) {
        redisTemplate.delete("cache:stock:" + productId + ":" + warehouseId);
    }

    @Override
    public void evictAllProductInventory(String productId) {
        // delete keys pattern "cache:stock:" + productId + ":*"
    }
}
```

### 2.3 Rate Limiter
Member 3 can add a Spring Web filter or interceptor returning HTTP `429 Too Many Requests` when token bucket limit is exceeded. Member 1's `ApiError` format is ready for standard error serialization.

---

## 3. Member 4: React Frontend & Autocomplete

### Integration Points:
- CORS is pre-configured in `ApplicationConfig.java` to accept requests from `http://localhost:3000`, `http://localhost:5173`, etc.
- **Product Search & Autocomplete:**
  - `GET /api/products?query={term}&category={cat}&page=0&size=10`
  - Member 4's autocomplete component can query this endpoint on debounce, or build a Trie client-side based on product listings.
- **Warehouse-wise Stock Screen:**
  - `GET /api/warehouses`
  - `GET /api/warehouses/{warehouseId}/inventory`
- **Orders & Transfers:**
  - Full request/response contracts documented in `docs/api-contracts.md`.

---

## 4. Member 5: Supplier Crawler & Integration Coordination

### Integration Points:
- **Supplier Feed Ingestion:**
  - `POST /api/suppliers/import`
  - Member 5's crawler can stream or batch scraped items into this endpoint.
  - Newly discovered items are saved into the catalog.
  - If `autoCreatePurchaseOrder: true`, draft Purchase Orders are staged for warehouse receiving without contaminating physical stock balances.
- **JMeter Load Testing Coordination:**
  - Low concurrency (10 threads): baseline response time
  - Medium concurrency (50 threads): throughput sweet spot
  - High concurrency (100 threads): verifying no negative stock and 409 rejection under concurrency.
