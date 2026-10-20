package com.example.inventory.sharding;

import com.example.inventory.common.BadRequestException;
import com.example.inventory.common.ConflictException;
import com.example.inventory.integration.InventoryPersistencePort;
import com.example.inventory.inventory.InventoryRecord;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShardedInventoryPersistenceRouterTest {
    static class Fixture {
        final ShardingProperties properties = new ShardingProperties();
        final MongoTemplate metadata = mock(MongoTemplate.class);
        final AtomicReference<ShardTopology> durable = new AtomicReference<>();
        final Map<String, MongoShard> shards = new LinkedHashMap<>();
        final Map<String, Map<String, Document>> data = new LinkedHashMap<>();
        final Map<String, InventoryPersistencePort> ports = new LinkedHashMap<>();
        final Map<String, Document> original = new LinkedHashMap<>();
        ShardedInventoryPersistenceRouter router;

        Fixture() {
            properties.getDatabases().forEach((id, database) -> {
                MongoShard shard = mock(MongoShard.class);
                InventoryPersistencePort port = mock(InventoryPersistencePort.class);
                Map<String, Document> records = new LinkedHashMap<>();
                shards.put(id, shard);
                ports.put(id, port);
                data.put(id, records);
                when(shard.database()).thenReturn(database);
                when(shard.persistence()).thenReturn(port);
                when(shard.documents()).thenAnswer(call -> new ArrayList<>(records.values()));
                when(shard.count()).thenAnswer(call -> (long) records.size());
                doAnswer(call -> {
                    Document document = call.getArgument(0);
                    records.put(document.getString("_id"), new Document(document));
                    return null;
                }).when(shard).copy(any());
                doAnswer(call -> {
                    records.remove(((Document) call.getArgument(0)).getString("_id"));
                    return null;
                }).when(shard).delete(any());
            });
            when(metadata.findById(ShardTopology.ID, ShardTopology.class)).thenAnswer(call -> durable.get());
            when(metadata.insert(any(ShardTopology.class))).thenAnswer(call -> {
                durable.set(call.getArgument(0));
                return call.getArgument(0);
            });
            when(metadata.save(any(ShardTopology.class))).thenAnswer(call -> {
                durable.set(call.getArgument(0));
                return call.getArgument(0);
            });
            router = restart();
        }

        ShardedInventoryPersistenceRouter restart() {
            var instance = new ShardedInventoryPersistenceRouter(shards, metadata, properties);
            instance.initialize();
            return instance;
        }

        void seed(int count) {
            for (int i = 0; i < count; i++) {
                String p = "p" + i;
                var document = new Document("_id", p + "_w").append("productId", p)
                        .append("warehouseId", "w").append("quantity", 27L)
                        .append("reservedQuantity", 7L).append("version", 31L)
                        .append("updatedAt", new java.util.Date(1234567890L)).append("futureField", "preserve");
                original.put(p + "_w", new Document(document));
                data.get(router.route(p, "w").shardId()).put(p + "_w", document);
            }
        }

        void assertPlacement(ShardedInventoryPersistenceRouter instance) {
            assertEquals(original.size(), data.values().stream().mapToInt(Map::size).sum());
            original.forEach((id, document) -> {
                String owner = instance.route(document.getString("productId"), "w").shardId();
                assertEquals(document, data.get(owner).get(id));
                assertEquals(1, data.values().stream().filter(records -> records.containsKey(id)).count());
            });
        }
    }

    @Test
    void movesOnlyChangedOwnersAndPreservesAllFieldsThroughAdditionRemovalAndRestart() {
        var f = new Fixture();
        f.seed(500);
        var report = f.router.addShard("shard-4");
        assertEquals(500, report.recordsScanned());
        assertEquals(f.data.get("shard-4").size(), report.recordsMoved());
        assertTrue(report.recordsMoved() > 0 && report.recordsMoved() < 250);
        f.assertPlacement(f.router);
        f.router.removeShard("shard-2");
        assertTrue(f.data.get("shard-2").isEmpty());
        var restarted = f.restart();
        assertEquals(List.of("shard-1", "shard-3", "shard-4"), restarted.status().activeShards());
        f.assertPlacement(restarted);
    }

    @Test
    void routesEveryMutationAndPointReadToSameOwnerAndFansOutPartialQueries() {
        var f = new Fixture();
        var r = f.router;
        String owner = r.route("p", "w").shardId();
        var record = new InventoryRecord("p", "w", 20, 3);
        r.save(record);
        r.findByProductIdAndWarehouseId("p", "w");
        r.reserveStock("p", "w", 1);
        r.releaseReservedStock("p", "w", 1);
        r.deductReservedStock("p", "w", 1);
        r.deductAvailableStockDirect("p", "w", 1);
        r.creditStock("p", "w", 1);
        r.adjustStock("p", "w", 1, 0);
        var port = f.ports.get(owner);
        verify(port).save(record);
        verify(port).findByProductIdAndWarehouseId("p", "w");
        verify(port).reserveStock("p", "w", 1);
        verify(port).releaseReservedStock("p", "w", 1);
        verify(port).deductReservedStock("p", "w", 1);
        verify(port).deductAvailableStockDirect("p", "w", 1);
        verify(port).creditStock("p", "w", 1);
        verify(port).adjustStock("p", "w", 1, 0);
        f.ports.forEach((id, other) -> { if (!id.equals(owner)) verifyNoInteractions(other); });
        assertEquals(8, r.status().shards().stream().filter(s -> s.shardId().equals(owner))
                .findFirst().orElseThrow().requests());
        for (String active : r.status().activeShards()) {
            when(f.ports.get(active).findByProductId("p")).thenReturn(List.of(record));
            when(f.ports.get(active).findByWarehouseId("w")).thenReturn(List.of(record));
        }
        assertEquals(3, r.findByProductId("p").size());
        assertEquals(3, r.findByWarehouseId("w").size());
        verifyNoInteractions(f.ports.get("shard-4"));
    }

    @Test
    void interruptedCopyBlocksInventoryAndRestartResumesWithoutLossOrDuplicates() {
        var f = new Fixture();
        f.seed(100);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        doAnswer(call -> {
            Document document = call.getArgument(0);
            f.data.get("shard-4").put(document.getString("_id"), new Document(document));
            if (failOnce.getAndSet(false)) throw new IllegalStateException("Lost write acknowledgement");
            return null;
        }).when(f.shards.get("shard-4")).copy(any());
        assertThrows(ShardingUnavailableException.class, () -> f.router.addShard("shard-4"));
        assertThrows(ShardingUnavailableException.class, () -> f.router.reserveStock("p", "w", 1));
        assertEquals("RECOVERY_REQUIRED", f.router.status().state());
        assertNotNull(f.durable.get().targetShards());
        f.router = f.restart();
        assertEquals("READY", f.router.status().state());
        assertNull(f.durable.get().targetShards());
        f.assertPlacement(f.router);
    }

    @Test
    void uncertainCommitAndInterruptedCleanupCanBeRetriedSafely() {
        var f = new Fixture();
        f.seed(100);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        when(f.metadata.save(any(ShardTopology.class))).thenAnswer(call -> {
            ShardTopology value = call.getArgument(0);
            f.durable.set(value);
            if (value.activeShards().contains("shard-4") && failOnce.getAndSet(false)) {
                throw new IllegalStateException("Commit acknowledgement lost");
            }
            return value;
        });
        assertThrows(ShardingUnavailableException.class, () -> f.router.addShard("shard-4"));
        f.router.rebalance();
        f.assertPlacement(f.router);

        AtomicBoolean failDelete = new AtomicBoolean(true);
        doAnswer(call -> {
            if (failDelete.getAndSet(false)) throw new IllegalStateException("Delete failed");
            f.data.get("shard-4").remove(((Document) call.getArgument(0)).getString("_id"));
            return null;
        }).when(f.shards.get("shard-4")).delete(any());
        assertThrows(ShardingUnavailableException.class, () -> f.router.removeShard("shard-4"));
        f.router = f.restart();
        f.assertPlacement(f.router);
        assertTrue(f.data.get("shard-4").isEmpty());
    }

    @Test
    void inventoryMutationWaitsUntilMigrationCompletesThenUsesNewOwner() throws Exception {
        var f = new Fixture();
        f.seed(100);
        var nextRing = new ConsistentHashRing(List.of("shard-1", "shard-2", "shard-3", "shard-4"),
                f.properties.getVirtualNodes());
        String p = f.original.values().stream().map(d -> d.getString("productId"))
                .filter(id -> nextRing.route(id + ":w").equals("shard-4")).findFirst().orElseThrow();
        CountDownLatch copying = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            copying.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            Document document = call.getArgument(0);
            f.data.get("shard-4").put(document.getString("_id"), new Document(document));
            return null;
        }).when(f.shards.get("shard-4")).copy(any());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> migration = pool.submit(() -> f.router.addShard("shard-4"));
            assertTrue(copying.await(5, TimeUnit.SECONDS));
            CountDownLatch attempted = new CountDownLatch(1);
            Future<?> mutation = pool.submit(() -> { attempted.countDown(); return f.router.reserveStock(p, "w", 1); });
            assertTrue(attempted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> mutation.get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            migration.get(5, TimeUnit.SECONDS);
            mutation.get(5, TimeUnit.SECONDS);
            verify(f.ports.get("shard-4")).reserveStock(p, "w", 1);
            f.assertPlacement(f.router);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidMembershipAndConfigurationChanges() {
        var f = new Fixture();
        assertThrows(BadRequestException.class, () -> f.router.addShard("unknown"));
        assertThrows(ConflictException.class, () -> f.router.addShard("shard-1"));
        f.router.removeShard("shard-2");
        f.router.removeShard("shard-3");
        assertThrows(ConflictException.class, () -> f.router.removeShard("shard-1"));
        f.properties.setVirtualNodes(256);
        assertThrows(IllegalStateException.class, f::restart);
    }
}
