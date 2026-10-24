package com.example.inventory.integration;

import com.example.inventory.inventory.InventoryResponse;
import com.example.inventory.product.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default no-op cache adapter that logs invalidation events.
 * Explicit no-cache baseline. Redis outages use the Redis adapter's failure path.
 */
@Component
@ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "false")
public class NoOpInventoryCachePort implements InventoryCachePort {

    private static final Logger log = LoggerFactory.getLogger(NoOpInventoryCachePort.class);

    @Override
    public Optional<ProductResponse> getCachedProduct(String productId) {
        return Optional.empty();
    }

    @Override
    public Optional<InventoryResponse> getCachedInventory(String productId, String warehouseId) {
        return Optional.empty();
    }

    @Override
    public void cacheProduct(String productId, ProductResponse product) {
        // no-op
    }

    @Override
    public void cacheInventory(String productId, String warehouseId, InventoryResponse inventory) {
        // no-op
    }

    @Override
    public void evictProduct(String productId) {
        log.debug("[Cache Invalidation Hook] Evicted product: {}", productId);
    }

    @Override
    public void evictInventory(String productId, String warehouseId) {
        log.debug("[Cache Invalidation Hook] Evicted inventory for product {} at warehouse {}", productId, warehouseId);
    }

    @Override
    public void evictAllProductInventory(String productId) {
        log.debug("[Cache Invalidation Hook] Evicted all inventory records for product {}", productId);
    }

    @Override
    public CacheMetrics getMetrics() {
        return new CacheMetrics(0, 0, 0, 0.0, 0.0);
    }
}
