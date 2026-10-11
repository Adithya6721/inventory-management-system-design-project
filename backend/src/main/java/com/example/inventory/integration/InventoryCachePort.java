package com.example.inventory.integration;

/**
 * Port for Redis cache operations and invalidation hooks.
 * Member 3 will supply the Redis cache implementation.
 */
public interface InventoryCachePort {
    void evictProduct(String productId);
    void evictInventory(String productId, String warehouseId);
    void evictAllProductInventory(String productId);
}
