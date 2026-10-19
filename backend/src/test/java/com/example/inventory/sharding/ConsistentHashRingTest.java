package com.example.inventory.sharding;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsistentHashRingTest {
    @Test
    void stableAcrossConstructionOrderAndRestart() {
        var first = new ConsistentHashRing(List.of("shard-1", "shard-2", "shard-3"), 32);
        var second = new ConsistentHashRing(List.of("shard-3", "shard-1", "shard-2"), 32);
        assertEquals(96, first.size());
        for (int i = 0; i < 10_000; i++) {
            assertEquals(first.route("p" + i + ":w1"), second.route("p" + i + ":w1"));
        }
    }

    @Test
    void onlyNewServerAcquiresKeysAndOnlyRemovedServerLosesKeys() {
        var before = new ConsistentHashRing(List.of("shard-1", "shard-2", "shard-3"), 32);
        var after = new ConsistentHashRing(List.of("shard-1", "shard-2", "shard-3", "shard-4"), 32);
        var removed = new ConsistentHashRing(List.of("shard-1", "shard-3", "shard-4"), 32);
        var counts = new HashMap<String, Integer>();
        int remapped = 0;
        for (int i = 0; i < 20_000; i++) {
            String key = "p" + i + ":w" + (i % 7);
            String owner = after.route(key);
            counts.merge(owner, 1, Integer::sum);
            if (!before.route(key).equals(owner)) {
                assertEquals("shard-4", owner);
                remapped++;
            }
            if (!owner.equals(removed.route(key))) assertEquals("shard-2", owner);
        }
        assertTrue(remapped > 3_000 && remapped < 7_000);
        counts.values().forEach(count -> assertTrue(count > 3_000 && count < 7_000));
        System.out.println("Ring sample: 20000 keys, counts=" + counts + ", remapped=" + remapped);
    }

    @Test
    void usesClockwiseSuccessorAndWrapsPastLastToken() {
        var ring = new ConsistentHashRing(List.of("a", "b"), 1);
        var a = ConsistentHashRing.hash("a#0");
        var b = ConsistentHashRing.hash("b#0");
        String first = a.compareTo(b) < 0 ? "a" : "b";
        var maximum = a.max(b);
        String wrapKey = java.util.stream.IntStream.range(0, 10_000).mapToObj(i -> "key" + i)
                .filter(key -> ConsistentHashRing.hash(key).compareTo(maximum) > 0).findFirst().orElseThrow();
        assertEquals(first, ring.route(wrapKey));
        assertEquals("a", ring.route("a#0"));
        assertEquals("b", ring.route("b#0"));
    }

    @Test
    void validatesRingAndCompositeKey() {
        assertThrows(IllegalArgumentException.class, () -> new ConsistentHashRing(List.of("a"), 0));
        assertThrows(IllegalArgumentException.class, () -> new ConsistentHashRing(List.of("a", "a"), 1));
        assertThrows(IllegalStateException.class, () -> new ConsistentHashRing(List.of(), 1).route("p:w"));
        assertEquals("p:w", ShardedInventoryPersistenceRouter.inventoryKey("p", "w"));
        assertThrows(com.example.inventory.common.BadRequestException.class,
                () -> ShardedInventoryPersistenceRouter.inventoryKey("p:w", "x"));
    }
}
