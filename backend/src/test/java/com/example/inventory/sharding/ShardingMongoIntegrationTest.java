package com.example.inventory.sharding;

import com.example.inventory.integration.InventoryPersistencePort;
import com.example.inventory.inventory.InventoryRecord;
import com.example.inventory.inventory.InventoryResponse;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Opt in with -Dsharding.mongo.tests=true; requires a running, disposable test MongoDB. */
@EnabledIfSystemProperty(named = "sharding.mongo.tests", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.data.mongodb.uri=mongodb://${MONGO_HOST:localhost}:${MONGO_PORT:27017}/inventory_member2_test",
        "inventory.sharding.databases.shard-1=inventory_member2_test_shard_1",
        "inventory.sharding.databases.shard-2=inventory_member2_test_shard_2",
        "inventory.sharding.databases.shard-3=inventory_member2_test_shard_3",
        "inventory.sharding.databases.shard-4=inventory_member2_test_shard_4",
        "logging.level.com.example.inventory=INFO",
        "logging.level.org.springframework.data.mongodb.core.MongoTemplate=INFO"
})
class ShardingMongoIntegrationTest {
    @Autowired ShardedInventoryPersistenceRouter router;
    @Autowired InventoryPersistencePort persistence;
    @Autowired MongoClient mongo;
    @Autowired TestRestTemplate http;
    @Autowired MongoTemplate metadata;
    @Autowired ShardingProperties properties;

    @BeforeEach
    void resetTestShardInventory() {
        for (String id : List.of("shard-1", "shard-2", "shard-3")) {
            if (!router.status().activeShards().contains(id)) router.addShard(id);
        }
        if (router.status().activeShards().contains("shard-4")) router.removeShard("shard-4");
        for (int i = 1; i <= 4; i++) {
            mongo.getDatabase("inventory_member2_test_shard_" + i).getCollection("inventory")
                    .deleteMany(new Document());
        }
    }

    @Test
    void migratesRealDocumentsAndPreservesEveryFieldAndScatterQueries() {
        assertSame(router, persistence);
        for (int i = 0; i < 200; i++) {
            var record = new InventoryRecord("p" + i, "w" + (i % 5), 30 + i, 7);
            record.setVersion(15);
            persistence.save(record);
        }
        Map<String, Document> before = allDocuments();
        var added = http.postForEntity("/api/shards/shard-4", null,
                ShardedInventoryPersistenceRouter.RebalanceResult.class);
        assertEquals(HttpStatus.OK, added.getStatusCode());
        assertEquals(200, added.getBody().recordsScanned());
        assertTrue(added.getBody().recordsMoved() > 0);
        assertEquals(before, allDocuments());
        assertPlacement();
        assertEquals(40, persistence.findByWarehouseId("w1").size());
        assertEquals(1, persistence.findByProductId("p1").size());
        var removed = http.exchange("/api/shards/shard-2", HttpMethod.DELETE, null,
                ShardedInventoryPersistenceRouter.RebalanceResult.class);
        assertEquals(HttpStatus.OK, removed.getStatusCode());
        assertEquals(before, allDocuments());
        assertPlacement();
        assertEquals(0, mongo.getDatabase("inventory_member2_test_shard_2")
                .getCollection("inventory").countDocuments());
        assertEquals(31, persistence.findByProductIdAndWarehouseId("p1", "w1").orElseThrow().getQuantity());
        var restarted = new ShardingConfiguration().shardedInventoryPersistenceRouter(mongo, metadata, properties);
        assertEquals(router.status().activeShards(), restarted.status().activeShards());
        assertEquals(31, restarted.findByProductIdAndWarehouseId("p1", "w1").orElseThrow().getQuantity());
        System.out.println("Mongo migration: added=" + added.getBody() + ", removed=" + removed.getBody());
    }

    @Test
    void allAtomicStockOperationsAndOneHundredConcurrentReservations() throws Exception {
        persistence.creditStock("p", "w", 10);
        var executor = Executors.newFixedThreadPool(24);
        try {
            List<Callable<Boolean>> jobs = new ArrayList<>();
            for (int i = 0; i < 100; i++) jobs.add(() -> persistence.reserveStock("p", "w", 1));
            int succeeded = 0;
            for (var result : executor.invokeAll(jobs, 15, TimeUnit.SECONDS)) {
                if (result.get()) succeeded++;
            }
            assertEquals(10, succeeded);
            System.out.println("Mongo reservations: 100 attempts, " + succeeded + " succeeded, 90 rejected");
        } finally {
            executor.shutdownNow();
        }
        assertFalse(persistence.reserveStock("p", "w", 1));
        assertTrue(persistence.releaseReservedStock("p", "w", 2));
        assertTrue(persistence.deductReservedStock("p", "w", 3));
        assertTrue(persistence.deductAvailableStockDirect("p", "w", 2));
        assertFalse(persistence.deductAvailableStockDirect("p", "w", 1));
        assertFalse(persistence.adjustStock("p", "w", -1, 0));
        assertTrue(persistence.adjustStock("p", "w", 5, 0));
        assertFalse(persistence.reserveStock("p", "w", 0));
        assertFalse(persistence.releaseReservedStock("p", "w", 100));
        assertFalse(persistence.deductReservedStock("p", "w", 100));
        assertThrows(IllegalArgumentException.class, () -> persistence.creditStock("p", "w", 0));
        var finalRecord = persistence.findByProductIdAndWarehouseId("p", "w").orElseThrow();
        assertEquals(10, finalRecord.getQuantity());
        assertEquals(5, finalRecord.getReservedQuantity());
        router.addShard("shard-4");
        router.removeShard(router.route("p", "w").shardId());
        var migrated = persistence.findByProductIdAndWarehouseId("p", "w").orElseThrow();
        assertEquals(finalRecord.getQuantity(), migrated.getQuantity());
        assertEquals(finalRecord.getReservedQuantity(), migrated.getReservedQuantity());
        assertEquals(finalRecord.getVersion(), migrated.getVersion());
        assertTrue(persistence.releaseReservedStock("p", "w", 5));
        assertTrue(persistence.deductAvailableStockDirect("p", "w", 10));
        assertEquals(0, persistence.findByProductIdAndWarehouseId("p", "w").orElseThrow().getQuantity());
    }

    @Test
    void memberOneInventoryApisStillReadAndWriteAfterMembershipChanges() {
        String suffix = UUID.randomUUID().toString();
        var warehouse = http.postForEntity("/api/warehouses",
                Map.of("name", "Shard Demo " + suffix, "location", "Campus", "status", "ACTIVE"), Map.class);
        assertEquals(HttpStatus.CREATED, warehouse.getStatusCode());
        String w = (String) warehouse.getBody().get("warehouseId");
        var product = http.postForEntity("/api/products", Map.of("sku", suffix, "name", "Demo item",
                "price", 100, "reorderLevel", 2, "active", true), Map.class);
        assertEquals(HttpStatus.CREATED, product.getStatusCode());
        String p = (String) product.getBody().get("productId");
        var adjusted = http.postForEntity("/api/inventory/adjustments",
                Map.of("productId", p, "warehouseId", w, "physicalDelta", 25,
                        "reservedDelta", 0, "reason", "Sharding integration test"), InventoryResponse.class);
        assertEquals(HttpStatus.OK, adjusted.getStatusCode());
        assertEquals(25, adjusted.getBody().getQuantity());
        var route = http.getForEntity("/api/shards/route?productId=" + p + "&warehouseId=" + w,
                ShardedInventoryPersistenceRouter.Route.class);
        assertEquals(p + ":" + w, route.getBody().key());
        router.addShard("shard-4");
        router.removeShard(router.route(p, w).shardId());
        var inventory = http.getForEntity("/api/inventory/" + p + "/" + w, InventoryResponse.class);
        assertEquals(HttpStatus.OK, inventory.getStatusCode());
        assertEquals(25, inventory.getBody().getQuantity());
        var across = http.getForEntity("/api/inventory/" + p, InventoryResponse[].class);
        assertEquals(1, across.getBody().length);
        var byWarehouse = http.getForEntity("/api/warehouses/" + w + "/inventory", InventoryResponse[].class);
        assertEquals(1, byWarehouse.getBody().length);
        assertEquals(HttpStatus.CONFLICT, http.postForEntity("/api/shards/shard-4", null, Map.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, http.postForEntity("/api/shards/unknown", null, Map.class).getStatusCode());
    }

    private Map<String, Document> allDocuments() {
        Map<String, Document> result = new HashMap<>();
        for (int i = 1; i <= 4; i++) {
            for (Document document : mongo.getDatabase("inventory_member2_test_shard_" + i)
                    .getCollection("inventory").find()) {
                assertNull(result.put(document.getString("_id"), document), "Duplicate inventory document");
            }
        }
        return result;
    }

    private void assertPlacement() {
        for (var shard : router.status().shards()) {
            for (Document document : mongo.getDatabase(shard.database()).getCollection("inventory").find()) {
                assertEquals(shard.shardId(), router.route(document.getString("productId"),
                        document.getString("warehouseId")).shardId());
            }
        }
    }
}
