package com.example.inventory.cache;

import com.example.inventory.integration.InventoryCachePort;
import com.example.inventory.inventory.InventoryResponse;
import com.example.inventory.product.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisInventoryCacheAdapterTest {

    @Mock
    private RedisOperations<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RedisInventoryCacheAdapter cacheAdapter;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheAdapter = new RedisInventoryCacheAdapter(redisTemplate, 10L, 60L);
    }

    @Test
    @DisplayName("Should return cached product on cache HIT and update metrics")
    void shouldReturnCachedProductOnHit() {
        ProductResponse mockProduct = new ProductResponse(
                "prod_1001", "SKU-TEST", "MacBook Pro", "Test laptop",
                "Laptops", new BigDecimal("1999.99"), 5, true, Instant.now(), Instant.now()
        );

        when(valueOperations.get(RedisInventoryCacheAdapter.KEY_PREFIX_PRODUCT + "prod_1001"))
                .thenReturn(mockProduct);

        Optional<ProductResponse> result = cacheAdapter.getCachedProduct("prod_1001");

        assertTrue(result.isPresent());
        assertEquals("prod_1001", result.get().getProductId());
        assertEquals("MacBook Pro", result.get().getName());

        InventoryCachePort.CacheMetrics metrics = cacheAdapter.getMetrics();
        assertEquals(1, metrics.hits());
        assertEquals(0, metrics.misses());
        assertEquals(100.0, metrics.hitRatio(), 0.01);
    }

    @Test
    @DisplayName("Should return empty Optional on cache MISS and update metrics")
    void shouldReturnEmptyOnMiss() {
        when(valueOperations.get(anyString())).thenReturn(null);

        Optional<ProductResponse> result = cacheAdapter.getCachedProduct("prod_missing");

        assertTrue(result.isEmpty());

        InventoryCachePort.CacheMetrics metrics = cacheAdapter.getMetrics();
        assertEquals(0, metrics.hits());
        assertEquals(1, metrics.misses());
        assertEquals(0.0, metrics.hitRatio(), 0.01);
    }

    @Test
    @DisplayName("Should cache inventory item with configured stock TTL")
    void shouldCacheInventoryWithTtl() {
        InventoryResponse stock = new InventoryResponse("prod_1001", "wh_mum", 50, 10, 40, Instant.now());

        cacheAdapter.cacheInventory("prod_1001", "wh_mum", stock);

        verify(valueOperations).set(
                eq(RedisInventoryCacheAdapter.KEY_PREFIX_STOCK + "prod_1001:wh_mum"),
                eq(stock),
                eq(Duration.ofSeconds(60))
        );
    }

    @Test
    @DisplayName("Should evict inventory cache on stock update")
    void shouldEvictInventoryOnMutation() {
        when(redisTemplate.delete(anyString())).thenReturn(true);

        cacheAdapter.evictInventory("prod_1001", "wh_mum");

        verify(redisTemplate).delete("cache:stock:prod_1001:wh_mum");
        assertEquals(1, cacheAdapter.getMetrics().evictions());
    }

    @Test
    @DisplayName("Should purge all warehouse stock caches for a product on bulk update")
    void shouldPurgeAllStockCachesForProduct() {
        Set<String> matchingKeys = Set.of(
                "cache:stock:prod_1001:wh_mum",
                "cache:stock:prod_1001:wh_blr",
                "cache:stock:prod_1001:wh_del"
        );

        when(redisTemplate.keys("cache:stock:prod_1001:*")).thenReturn(matchingKeys);
        when(redisTemplate.delete(matchingKeys)).thenReturn(3L);

        cacheAdapter.evictAllProductInventory("prod_1001");

        verify(redisTemplate).delete(matchingKeys);
        assertEquals(3, cacheAdapter.getMetrics().evictions());
    }

    @Test
    @DisplayName("Resiliency Test: Should degrade gracefully on Redis network failure without crashing")
    void shouldDegradeGracefullyOnRedisFailure() {
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        // Does NOT throw exception: gracefully falls back to database lookup!
        assertDoesNotThrow(() -> {
            Optional<ProductResponse> result = cacheAdapter.getCachedProduct("prod_fail");
            assertTrue(result.isEmpty(), "Should treat Redis failure as cache miss");
        });
        assertEquals(1, cacheAdapter.getDiagnostics().lookupFailures());
        assertTrue(cacheAdapter.getDiagnostics().totalLookupTimeNs() > 0);
    }

    @Test
    void namespacesDoNotReadOrEvictAnotherExperimentsKeys() {
        var isolated = new RedisInventoryCacheAdapter(redisTemplate, 10, 60, "experiment-123");
        isolated.getCachedProduct("p");
        isolated.evictInventory("p", "w");
        verify(valueOperations).get("experiment-123:product:p");
        verify(redisTemplate).delete("experiment-123:stock:p:w");
        verify(redisTemplate, never()).delete("cache:stock:p:w");
    }
}
