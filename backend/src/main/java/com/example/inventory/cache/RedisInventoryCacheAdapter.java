package com.example.inventory.cache;

import com.example.inventory.integration.InventoryCachePort;
import com.example.inventory.inventory.InventoryResponse;
import com.example.inventory.product.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Member 3: Redis-backed Key-Value Store implementation of InventoryCachePort.
 * Implements the Cache-Aside pattern for read-heavy product and stock lookups,
 * and handles automated cache invalidation upon stock mutations.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisInventoryCacheAdapter implements InventoryCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisInventoryCacheAdapter.class);

    public static final String KEY_PREFIX_PRODUCT = "cache:product:";
    public static final String KEY_PREFIX_STOCK = "cache:stock:";

    private final RedisOperations<String, Object> redisTemplate;
    private final Duration productTtl;
    private final Duration stockTtl;
    private final String namespace;
    private final AtomicLong lookupFailures = new AtomicLong();
    private final AtomicLong writeFailures = new AtomicLong();
    private final AtomicLong invalidationFailures = new AtomicLong();

    // Telemetry & metrics for Deliverable 4
    private final AtomicLong hitCount = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();
    private final AtomicLong evictionCount = new AtomicLong();
    private final AtomicLong totalLookupTimeNs = new AtomicLong();

    @Autowired
    public RedisInventoryCacheAdapter(
            RedisOperations<String, Object> redisTemplate,
            @Value("${app.cache.product-ttl-minutes:10}") long productTtlMinutes,
            @Value("${app.cache.stock-ttl-seconds:60}") long stockTtlSeconds,
            @Value("${app.cache.namespace:cache}") String namespace) {
        if (!namespace.matches("[a-zA-Z0-9:_-]+")) throw new IllegalArgumentException("Invalid cache namespace");
        this.namespace = namespace;
        this.redisTemplate = redisTemplate;
        this.productTtl = Duration.ofMinutes(productTtlMinutes);
        this.stockTtl = Duration.ofSeconds(stockTtlSeconds);
        log.info("Initialized RedisInventoryCacheAdapter with product TTL={}m, stock TTL={}s",
                productTtlMinutes, stockTtlSeconds);
    }

    public RedisInventoryCacheAdapter(RedisOperations<String, Object> redisTemplate,
                                       long productTtlMinutes, long stockTtlSeconds) {
        this(redisTemplate, productTtlMinutes, stockTtlSeconds, "cache");
    }

    @Override
    public Optional<ProductResponse> getCachedProduct(String productId) {
        long start = System.nanoTime();
        String key = namespace + ":product:" + productId;
        try {
            Object val = redisTemplate.opsForValue().get(key);
            if (val instanceof ProductResponse response) {
                hitCount.incrementAndGet();
                log.debug("[Redis Cache HIT] Product: {}", productId);
                return Optional.of(response);
            }
        } catch (Exception ex) {
            lookupFailures.incrementAndGet();
            log.warn("[Redis Cache Unavailable] Failed to read key {}: {}", key, ex.getMessage());
        } finally {
            recordLatency(start);
        }
        missCount.incrementAndGet();
        log.debug("[Redis Cache MISS] Product: {}", productId);
        return Optional.empty();
    }

    @Override
    public Optional<InventoryResponse> getCachedInventory(String productId, String warehouseId) {
        long start = System.nanoTime();
        String key = buildStockKey(productId, warehouseId);
        try {
            Object val = redisTemplate.opsForValue().get(key);
            if (val instanceof InventoryResponse response) {
                hitCount.incrementAndGet();
                log.debug("[Redis Cache HIT] Stock: {}:{}", productId, warehouseId);
                return Optional.of(response);
            }
        } catch (Exception ex) {
            lookupFailures.incrementAndGet();
            log.warn("[Redis Cache Unavailable] Failed to read key {}: {}", key, ex.getMessage());
        } finally {
            recordLatency(start);
        }
        missCount.incrementAndGet();
        log.debug("[Redis Cache MISS] Stock: {}:{}", productId, warehouseId);
        return Optional.empty();
    }

    @Override
    public void cacheProduct(String productId, ProductResponse product) {
        String key = namespace + ":product:" + productId;
        try {
            redisTemplate.opsForValue().set(key, product, productTtl);
            log.debug("[Redis Cache PUT] Product: {} with TTL {}", productId, productTtl);
        } catch (Exception ex) {
            writeFailures.incrementAndGet();
            log.warn("[Redis Cache Put Failed] Product {}: {}", productId, ex.getMessage());
        }
    }

    @Override
    public void cacheInventory(String productId, String warehouseId, InventoryResponse inventory) {
        String key = buildStockKey(productId, warehouseId);
        try {
            redisTemplate.opsForValue().set(key, inventory, stockTtl);
            log.debug("[Redis Cache PUT] Stock: {}:{} with TTL {}", productId, warehouseId, stockTtl);
        } catch (Exception ex) {
            writeFailures.incrementAndGet();
            log.warn("[Redis Cache Put Failed] Stock {}:{}: {}", productId, warehouseId, ex.getMessage());
        }
    }

    @Override
    public void evictProduct(String productId) {
        String key = namespace + ":product:" + productId;
        try {
            Boolean deleted = redisTemplate.delete(key);
            evictionCount.incrementAndGet();
            log.debug("[Redis Cache EVICT] Product: {} (deleted={})", productId, deleted);
        } catch (Exception ex) {
            invalidationFailures.incrementAndGet();
            log.warn("[Redis Cache Evict Failed] Product {}: {}", productId, ex.getMessage());
        }
    }

    @Override
    public void evictInventory(String productId, String warehouseId) {
        String key = buildStockKey(productId, warehouseId);
        try {
            Boolean deleted = redisTemplate.delete(key);
            evictionCount.incrementAndGet();
            log.debug("[Redis Cache EVICT] Stock: {}:{} (deleted={})", productId, warehouseId, deleted);
        } catch (Exception ex) {
            invalidationFailures.incrementAndGet();
            log.warn("[Redis Cache Evict Failed] Stock {}:{}: {}", productId, warehouseId, ex.getMessage());
        }
    }

    @Override
    public void evictAllProductInventory(String productId) {
        String pattern = namespace + ":stock:" + productId + ":*";
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keys);
                evictionCount.addAndGet(keys.size());
                log.debug("[Redis Cache EVICT] Purged {} inventory records for pattern {}", deletedCount, pattern);
            }
        } catch (Exception ex) {
            invalidationFailures.incrementAndGet();
            log.warn("[Redis Cache Purge Failed] Pattern {}: {}", pattern, ex.getMessage());
        }
    }

    @Override
    public CacheMetrics getMetrics() {
        long hits = hitCount.get();
        long misses = missCount.get();
        long evictions = evictionCount.get();
        long total = hits + misses;
        double hitRatio = total == 0 ? 0.0 : ((double) hits / total) * 100.0;
        double avgLookupMs = total == 0 ? 0.0 : (totalLookupTimeNs.get() / (double) total) / 1_000_000.0;
        return new CacheMetrics(hits, misses, evictions, hitRatio, avgLookupMs);
    }

    private String buildStockKey(String productId, String warehouseId) {
        return namespace + ":stock:" + productId + ":" + warehouseId;
    }

    public Diagnostics getDiagnostics() {
        return new Diagnostics(namespace, lookupFailures.get(), writeFailures.get(),
                invalidationFailures.get(), totalLookupTimeNs.get());
    }

    public record Diagnostics(String namespace, long lookupFailures, long writeFailures,
                              long invalidationFailures, long totalLookupTimeNs) { }

    private void recordLatency(long startNs) {
        totalLookupTimeNs.addAndGet(System.nanoTime() - startNs);
    }
}
