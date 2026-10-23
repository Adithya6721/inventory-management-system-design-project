package com.example.inventory.integration;

import com.example.inventory.inventory.InventoryResponse;
import com.example.inventory.product.ProductResponse;

import java.util.Optional;

/**
 * Port for Redis key-value cache operations and invalidation hooks.
 * Supports the Cache-Aside pattern for hot product master data and stock availability.
 */
public interface InventoryCachePort {

    // Cache-Aside reads
    Optional<ProductResponse> getCachedProduct(String productId);
    Optional<InventoryResponse> getCachedInventory(String productId, String warehouseId);

    // Cache-Aside writes (with TTL)
    void cacheProduct(String productId, ProductResponse product);
    void cacheInventory(String productId, String warehouseId, InventoryResponse inventory);

    // Cache Invalidation hooks (called by Member 1 on stock mutations)
    void evictProduct(String productId);
    void evictInventory(String productId, String warehouseId);
    void evictAllProductInventory(String productId);

    // Telemetry & metrics for Deliverable 4
    CacheMetrics getMetrics();

    record CacheMetrics(long hits, long misses, long evictions, double hitRatio, double avgLookupLatencyMs) {}
}
