package com.example.inventory.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Default no-op cache adapter that logs invalidation events.
 * Member 3 will provide the Redis-backed cache adapter.
 */
@Component
@ConditionalOnMissingBean(value = InventoryCachePort.class, ignored = NoOpInventoryCachePort.class)
public class NoOpInventoryCachePort implements InventoryCachePort {

    private static final Logger log = LoggerFactory.getLogger(NoOpInventoryCachePort.class);

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
}
