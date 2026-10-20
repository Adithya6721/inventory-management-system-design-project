package com.example.inventory.idgen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("Should generate IDs with correct prefix and non-null values")
    void shouldGeneratePrefixedIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L, 1L);

        String prodId = generator.generateId("prod");
        String ordId = generator.generateId("ord");
        String rawId = generator.generateId("");

        assertNotNull(prodId);
        assertTrue(prodId.startsWith("prod_"), "Product ID must start with 'prod_'");
        assertTrue(ordId.startsWith("ord_"), "Order ID must start with 'ord_'");
        assertFalse(rawId.contains("_"), "Raw ID should not have prefix underscore");
    }

    @Test
    @DisplayName("Generated IDs must be strictly monotonically increasing")
    void shouldBeMonotonicallyIncreasing() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L, 1L);

        long id1 = generator.nextId();
        long id2 = generator.nextId();
        long id3 = generator.nextId();

        assertTrue(id2 > id1, "Subsequent ID must be greater than previous ID");
        assertTrue(id3 > id2, "Subsequent ID must be greater than previous ID");
    }

    @Test
    @DisplayName("Should correctly parse timestamp from snowflake ID")
    void shouldParseTimestamp() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(2L, 5L);

        long before = System.currentTimeMillis();
        long id = generator.nextId();
        long after = System.currentTimeMillis();

        Instant parsed = SnowflakeIdGenerator.parseInstant(id);
        long parsedEpoch = parsed.toEpochMilli();

        assertTrue(parsedEpoch >= before - 100 && parsedEpoch <= after + 100,
                "Parsed instant must match the generation timestamp window");
    }

    @Test
    @DisplayName("High Concurrency Stress Test: 50 threads x 500 IDs = 25,000 unique IDs with 0 collisions")
    void shouldGuaranteeZeroCollisionsUnderHighConcurrency() throws InterruptedException {
        int threadCount = 50;
        int idsPerThread = 500;
        int totalExpectedIds = threadCount * idsPerThread;

        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3L, 7L);
        Set<Long> generatedIds = ConcurrentHashMap.newKeySet(totalExpectedIds);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);

        long startNs = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startSignal.await(); // Synchronize all threads to fire simultaneously
                    for (int j = 0; j < idsPerThread; j++) {
                        long id = generator.nextId();
                        generatedIds.add(id);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // Fire all threads at once
        startSignal.countDown();
        boolean completed = doneSignal.await(10, TimeUnit.SECONDS);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000;

        executor.shutdown();

        assertTrue(completed, "Benchmark should complete within 10 seconds");
        assertEquals(totalExpectedIds, generatedIds.size(),
                String.format("Expected %d unique IDs, but got %d (Collisions detected!)",
                        totalExpectedIds, generatedIds.size()));

        double throughputOpsPerSec = (totalExpectedIds / (double) Math.max(1, durationMs)) * 1000;
        System.out.printf("[Snowflake Benchmark] Generated %d IDs across %d threads in %d ms (~%.0f IDs/sec). Collisions: 0%n",
                totalExpectedIds, threadCount, durationMs, throughputOpsPerSec);
    }

    @Test
    @DisplayName("Should reject invalid node or datacenter configuration")
    void shouldRejectInvalidConfig() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32L, 1L)); // Max is 31
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1L, 32L)); // Max is 31
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1L, 1L));
    }
}
