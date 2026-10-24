package com.example.inventory.cache;

import com.example.inventory.integration.InventoryCachePort;
import com.example.inventory.integration.NoOpInventoryCachePort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.RedisOperations;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CacheSelectionTest {
    private final RedisOperations<String, Object> redis = mock(RedisOperations.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(RedisInventoryCacheAdapter.class, NoOpInventoryCachePort.class)
            .withBean("redisOperations", RedisOperations.class, () -> redis);

    @Test
    void disabledModeHasOneNoOpAdapterAndNeverContactsRedis() {
        context.withPropertyValues("app.cache.enabled=false").run(app -> {
            assertThat(app).hasSingleBean(InventoryCachePort.class);
            var cache = app.getBean(InventoryCachePort.class);
            assertThat(cache).isInstanceOf(NoOpInventoryCachePort.class);
            assertThat(cache.getCachedProduct("p")).isEmpty();
            cache.evictProduct("p");
            verifyNoInteractions(redis);
        });
    }

    @Test
    void defaultModeHasOneRedisAdapter() {
        context.run(app -> {
            assertThat(app).hasSingleBean(InventoryCachePort.class);
            assertThat(app.getBean(InventoryCachePort.class)).isInstanceOf(RedisInventoryCacheAdapter.class);
        });
    }
}
