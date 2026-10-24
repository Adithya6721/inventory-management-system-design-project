package com.example.inventory.consistency;

import com.example.inventory.common.ConflictException;
import com.example.inventory.integration.InventoryCachePort;
import com.example.inventory.integration.InventoryPersistencePort;
import com.example.inventory.inventory.*;
import com.example.inventory.product.Product;
import com.example.inventory.purchase.*;
import com.example.inventory.sales.*;
import com.example.inventory.sharding.ShardedInventoryPersistenceRouter;
import com.example.inventory.transfer.*;
import com.example.inventory.warehouse.Warehouse;
import com.example.inventory.warehouse.WarehouseStatus;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Opt in on a disposable replica set: -Dconsistency.mongo.tests=true. */
@EnabledIfSystemProperty(named = "consistency.mongo.tests", matches = "true")
@SpringBootTest(properties = {
        "spring.data.mongodb.uri=mongodb://127.0.0.1:${MONGO_PORT:27019}/inventory_consistency_test?replicaSet=inventory-rs",
        "inventory.sharding.databases.shard-1=inventory_consistency_test_shard_1",
        "inventory.sharding.databases.shard-2=inventory_consistency_test_shard_2",
        "inventory.sharding.databases.shard-3=inventory_consistency_test_shard_3",
        "inventory.sharding.databases.shard-4=inventory_consistency_test_shard_4",
        "app.ratelimit.enabled=false", "logging.level.root=WARN",
        "logging.level.com.example.inventory=ERROR",
        "logging.level.org.springframework.data.mongodb.core.MongoTemplate=WARN"
})
class ConsistencyMongoIntegrationTest {
    @Autowired MongoTemplate core;
    @Autowired MongoClient client;
    @SpyBean InventoryService inventory;
    @Autowired InventoryPersistencePort stock;
    @Autowired SalesOrderService sales;
    @Autowired PurchaseOrderService purchases;
    @Autowired StockTransferService transfers;
    @Autowired ShardedInventoryPersistenceRouter router;
    @Autowired PlatformTransactionManager transactionManager;
    @SpyBean SalesOrderRepository saleRepository;
    @SpyBean PurchaseOrderRepository purchaseRepository;
    @SpyBean StockTransferRepository transferRepository;
    @SpyBean InventoryMovementRepository movementRepository;
    @MockBean InventoryCachePort cache;

    @BeforeEach
    void isolateTestData() {
        for (String shard : List.of("shard-1", "shard-2", "shard-3")) {
            if (!router.status().activeShards().contains(shard)) router.addShard(shard);
        }
        if (router.status().activeShards().contains("shard-4")) router.removeShard("shard-4");
        for (int i = 1; i <= 4; i++) {
            client.getDatabase("inventory_consistency_test_shard_" + i)
                    .getCollection("inventory").deleteMany(new Document());
        }
        for (String collection : List.of("products", "warehouses", "sales_orders", "purchase_orders",
                "transfers", "inventory_movements", "low_stock_alerts")) {
            core.getCollection(collection).deleteMany(new Document());
        }
        for (String w : List.of("source", "dest")) {
            core.insert(new Warehouse(w, w, "Test", WarehouseStatus.ACTIVE));
        }
        for (int i = 0; i < 12; i++) {
            core.insert(new Product("p" + i, "SKU-" + i, "Product " + i, "", "test", BigDecimal.ONE, 2, true));
        }
    }

    void seed(String p, String w, long quantity) {
        inventory.creditStock(p, w, quantity, MovementType.PURCHASE_RECEIPT, "seed");
    }

    InventoryRecord record(String p, String w) {
        return stock.findByProductIdAndWarehouseId(p, w).orElseThrow();
    }

    SalesOrderRequest sale(String key, String p, long quantity) {
        return new SalesOrderRequest(List.of(new SalesOrderItemRequest(p, "source", quantity)), key);
    }

    PurchaseOrderResponse purchase(long quantity) {
        return purchases.createPurchaseOrder(new PurchaseOrderRequest("supplier", "source",
                List.of(new PurchaseOrderItemRequest("p0", quantity)), "purchase-key"));
    }

    StockTransferResponse dispatch() {
        return transfers.initiateTransfer(new StockTransferRequest("source", "dest",
                List.of(new TransferItemRequest("p0", 4)), "transfer-key"));
    }

    @Test
    void auditFailureRollsBackStockAndAlert() {
        seed("p0", "source", 5);
        long auditCount = movementRepository.count();
        doThrow(new IllegalStateException("injected audit outage")).when(movementRepository).save(any(InventoryMovement.class));
        assertThrows(IllegalStateException.class, () -> sales.placeOrder(sale("audit-fail", "p0", 4)));
        assertEquals(0, record("p0", "source").getReservedQuantity());
        assertEquals(auditCount, movementRepository.count());
        assertEquals(0, saleRepository.count());
        assertEquals(0, core.getCollection("low_stock_alerts").countDocuments());
    }

    @Test
    void orderSaveFailureRollsBackReservationAuditAndAlertAcrossDatabases() {
        seed("p0", "source", 5);
        long auditCount = movementRepository.count();
        doThrow(new IllegalStateException("injected order outage")).when(saleRepository).save(any(SalesOrder.class));
        assertThrows(IllegalStateException.class, () -> sales.placeOrder(sale("order-fail", "p0", 4)));
        assertEquals(0, record("p0", "source").getReservedQuantity());
        assertEquals(auditCount, movementRepository.count());
        assertEquals(0, saleRepository.count());
        assertEquals(0, core.getCollection("low_stock_alerts").countDocuments());
    }

    @Test
    void insufficientSecondItemRollsBackFirstItemOnAnotherShard() {
        String other = null;
        for (int i = 1; i < 12; i++) {
            if (!router.route("p0", "source").shardId().equals(router.route("p" + i, "source").shardId())) {
                other = "p" + i; break;
            }
        }
        assertNotNull(other, "Test must exercise different logical shard databases");
        seed("p0", "source", 10);
        seed(other, "source", 1);
        long auditCount = movementRepository.count();
        var request = new SalesOrderRequest(List.of(new SalesOrderItemRequest("p0", "source", 3),
                new SalesOrderItemRequest(other, "source", 2)), "cross-shard");
        assertThrows(ConflictException.class, () -> sales.placeOrder(request));
        assertEquals(0, record("p0", "source").getReservedQuantity());
        assertEquals(auditCount, movementRepository.count());
        assertEquals(0, saleRepository.count());
    }

    @Test
    void receiptReplayAndChangedPayloadHaveOneEffect() {
        var po = purchase(10);
        var receipt = new PurchaseReceiptRequest(List.of(new PurchaseReceiptItemRequest("p0", 3)), "delivery.1");
        purchases.receiveStock(po.getOrderId(), receipt);
        purchases.receiveStock(po.getOrderId(), receipt);
        assertEquals(3, record("p0", "source").getQuantity());
        assertEquals(1, movementRepository.findByReferenceId(po.getOrderId()).size());
        assertThrows(ConflictException.class, () -> purchases.receiveStock(po.getOrderId(),
                new PurchaseReceiptRequest(List.of(new PurchaseReceiptItemRequest("p0", 4)), "delivery.1")));
        assertEquals(3, record("p0", "source").getQuantity());
    }

    @Test
    void receiptSaveFailureRollsBackCreditAndAllowsSafeRetry() {
        var po = purchase(10);
        var receipt = new PurchaseReceiptRequest(List.of(new PurchaseReceiptItemRequest("p0", 3)), "delivery");
        doThrow(new IllegalStateException("injected receipt save failure")).when(purchaseRepository).save(any(PurchaseOrder.class));
        assertThrows(IllegalStateException.class, () -> purchases.receiveStock(po.getOrderId(), receipt));
        assertTrue(stock.findByProductIdAndWarehouseId("p0", "source").isEmpty());
        assertEquals(0, movementRepository.count());
        reset(purchaseRepository);
        purchases.receiveStock(po.getOrderId(), receipt);
        purchases.receiveStock(po.getOrderId(), receipt);
        assertEquals(3, record("p0", "source").getQuantity());
    }

    @Test
    void failedTransferCompletionAndRetryCannotDuplicateDestinationStock() {
        seed("p0", "source", 10);
        var transfer = dispatch();
        doThrow(new IllegalStateException("injected transfer save failure")).when(transferRepository).save(any(StockTransfer.class));
        assertThrows(IllegalStateException.class, () -> transfers.completeTransfer(transfer.getTransferId()));
        assertTrue(stock.findByProductIdAndWarehouseId("p0", "dest").isEmpty());
        assertEquals(TransferStatus.IN_TRANSIT, transfers.getTransfer(transfer.getTransferId()).getStatus());
        reset(transferRepository);
        transfers.completeTransfer(transfer.getTransferId());
        transfers.completeTransfer(transfer.getTransferId());
        assertEquals(6, record("p0", "source").getQuantity());
        assertEquals(4, record("p0", "dest").getQuantity());
    }

    @Test
    void concurrentSameKeyCreatesOnlyOneReservation() throws Exception {
        seed("p0", "source", 100);
        List<String> ids = parallel(20, () -> retry(() -> sales.placeOrder(sale("shared-key", "p0", 3)).getOrderId()));
        assertEquals(1, ids.stream().distinct().count());
        assertEquals(1, saleRepository.count());
        assertEquals(3, record("p0", "source").getReservedQuantity());
        assertEquals(1, movementRepository.findByReferenceId(ids.get(0)).size());
    }

    @Test
    void concurrentReceiptsWithSameKeyCreditOnce() throws Exception {
        var po = purchase(100);
        var receipt = new PurchaseReceiptRequest(List.of(new PurchaseReceiptItemRequest("p0", 3)), "shared-delivery");
        parallel(20, () -> retry(() -> purchases.receiveStock(po.getOrderId(), receipt)));
        assertEquals(3, record("p0", "source").getQuantity());
        assertEquals(3, purchases.getPurchaseOrder(po.getOrderId()).getItems().get(0).getReceivedQuantity());
        assertEquals(1, movementRepository.findByReferenceId(po.getOrderId()).size());
    }

    @Test
    void hundredRealOrdersForTenUnitsReconcileWithPersistedReservations() throws Exception {
        seed("p0", "source", 10);
        var outcomes = parallel(100, () -> {
            try { retry(() -> sales.placeOrder(sale(null, "p0", 1))); return true; }
            catch (ConflictException insufficient) { return false; }
        });
        assertEquals(10, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(10, saleRepository.count());
        assertEquals(10, record("p0", "source").getReservedQuantity());
        assertEquals(10, record("p0", "source").getQuantity());
        assertEquals(1, core.getCollection("low_stock_alerts").countDocuments(new Document("status", "ACTIVE")));
        System.out.println("Consistency: 100 real orders, 10 reservations, 90 insufficient-stock conflicts, no orphan stock");
    }

    @Test
    void completionVersusCancellationConservesStock() throws Exception {
        seed("p0", "source", 10);
        var transfer = dispatch();
        parallel(2, new java.util.concurrent.atomic.AtomicInteger()::getAndIncrement, index -> {
            try {
                return retry(() -> index == 0 ? transfers.completeTransfer(transfer.getTransferId())
                        : transfers.cancelTransfer(transfer.getTransferId()));
            } catch (ConflictException terminalState) { return null; }
        });
        long dest = stock.findByProductIdAndWarehouseId("p0", "dest").map(InventoryRecord::getQuantity).orElse(0L);
        assertEquals(10, record("p0", "source").getQuantity() + dest);
        var state = transfers.getTransfer(transfer.getTransferId()).getStatus();
        assertTrue(state == TransferStatus.COMPLETED || state == TransferStatus.CANCELLED);
    }

    @Test
    void migrationWaitsForBusinessCommitThenCopiesCommittedState() throws Exception {
        seed("p0", "source", 10);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> business = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                assertTrue(stock.reserveStock("p0", "source", 2));
                entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return null;
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var attempted = new CountDownLatch(1);
            Future<?> migration = pool.submit(() -> { attempted.countDown(); return router.addShard("shard-4"); });
            assertTrue(attempted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> migration.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            business.get(5, TimeUnit.SECONDS);
            migration.get(5, TimeUnit.SECONDS);
            assertEquals(2, record("p0", "source").getReservedQuantity());
            assertEquals(10, record("p0", "source").getQuantity());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void stockReadIgnoresStaleRedisValue() {
        seed("p0", "source", 7);
        // Mock cache would return null; an authoritative read must not consult it.
        clearInvocations(cache);
        assertEquals(7, inventory.getInventory("p0", "source").getQuantity());
        verify(cache, never()).getCachedInventory(any(), any());
    }

    @Test
    void snapshotTakenBeforeMigrationMustNotReadUsingNewRouting() throws Exception {
        seed("p0", "source", 10);
        var snapshotTaken = new CountDownLatch(1);
        var migrated = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> read = pool.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                core.findOne(new org.springframework.data.mongodb.core.query.Query(), Product.class);
                snapshotTaken.countDown();
                try { assertTrue(migrated.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return inventory.getInventory("p0", "source");
            }));
            assertTrue(snapshotTaken.await(5, TimeUnit.SECONDS));
            router.addShard("shard-4");
            migrated.countDown();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> read.get(5, TimeUnit.SECONDS));
            assertInstanceOf(ConflictException.class, failure.getCause());
            assertEquals(10, inventory.getInventory("p0", "source").getQuantity());
        } finally { migrated.countDown(); pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test
    void failedSecondDeductionRollsBackEarlierDeductionAndOrderState() {
        seed("p0", "source", 10);
        seed("p1", "source", 10);
        var order = sales.placeOrder(new SalesOrderRequest(List.of(
                new SalesOrderItemRequest("p0", "source", 2), new SalesOrderItemRequest("p1", "source", 2)), "multi"));
        long auditCount = movementRepository.count();
        doReturn(false).when(inventory).deductReservedStock("p1", "source", 2, order.getOrderId());
        assertThrows(ConflictException.class, () -> sales.completeOrder(order.getOrderId()));
        assertEquals(10, record("p0", "source").getQuantity());
        assertEquals(2, record("p0", "source").getReservedQuantity());
        assertEquals(SalesOrderStatus.RESERVED, sales.getOrder(order.getOrderId()).getStatus());
        assertEquals(auditCount, movementRepository.count());
        reset(inventory);
        sales.completeOrder(order.getOrderId());
        sales.completeOrder(order.getOrderId());
        assertEquals(8, record("p0", "source").getQuantity());
        assertEquals(8, record("p1", "source").getQuantity());
        assertEquals(0, record("p0", "source").getReservedQuantity());
    }

    @Test
    void concurrentSalesCompletionAndCancellationHaveOneTerminalEffect() throws Exception {
        seed("p0", "source", 10);
        var order = sales.placeOrder(sale("lifecycle", "p0", 3));
        parallel(2, new java.util.concurrent.atomic.AtomicInteger()::getAndIncrement, index -> {
            try { return retry(() -> index == 0 ? sales.completeOrder(order.getOrderId()) : sales.cancelOrder(order.getOrderId())); }
            catch (ConflictException terminalState) { return null; }
        });
        var state = sales.getOrder(order.getOrderId()).getStatus();
        assertTrue(state == SalesOrderStatus.COMPLETED || state == SalesOrderStatus.CANCELLED);
        assertEquals(state == SalesOrderStatus.COMPLETED ? 7 : 10, record("p0", "source").getQuantity());
        assertEquals(0, record("p0", "source").getReservedQuantity());
    }

    @Test
    void concurrentTransferCreationWithSameKeyDeductsOnce() throws Exception {
        seed("p0", "source", 100);
        var ids = parallel(20, () -> retry(() -> dispatch().getTransferId()));
        assertEquals(1, ids.stream().distinct().count());
        assertEquals(96, record("p0", "source").getQuantity());
        assertEquals(1, transferRepository.count());
        assertThrows(ConflictException.class, () -> transfers.initiateTransfer(new StockTransferRequest(
                "source", "dest", List.of(new TransferItemRequest("p0", 5)), "transfer-key")));
    }

    @Test
    void fullReceiptWithoutBodyCanBeReplayedAfterResponseLoss() {
        var po = purchase(10);
        purchases.receiveStock(po.getOrderId(), null);
        purchases.receiveStock(po.getOrderId(), null);
        assertEquals(10, record("p0", "source").getQuantity());
        assertEquals(1, movementRepository.findByReferenceId(po.getOrderId()).size());
    }

    @Test
    void blankCreationKeysAreAbsentRatherThanOneSharedUniqueKey() {
        seed("p0", "source", 10);
        sales.placeOrder(sale(" ", "p0", 1));
        sales.placeOrder(sale("", "p0", 1));
        assertEquals(2, saleRepository.count());
        assertEquals(2, record("p0", "source").getReservedQuantity());
    }

    @Test
    void overflowingAdjustmentCannotWrapIntoAValidLookingQuantity() {
        seed("p0", "source", Long.MAX_VALUE);
        assertThrows(ConflictException.class, () -> inventory.adjustStock(
                new StockAdjustmentRequest("p0", "source", 1, 0, "overflow regression")));
        assertEquals(Long.MAX_VALUE, record("p0", "source").getQuantity());
    }

    @Test
    void differentProductWarehousePairsCannotCollideOnConcatenatedId() {
        core.insert(new Product("p_a", "COLLISION-SKU", "Collision test", "", "test", BigDecimal.ONE, 2, true));
        core.insert(new Warehouse("a_source", "other", "Test", WarehouseStatus.ACTIVE));
        core.insert(new Product("p", "COLLISION-SKU-2", "Collision test 2", "", "test", BigDecimal.ONE, 2, true));
        seed("p_a", "source", 7);
        seed("p", "a_source", 9);
        assertNotEquals(record("p_a", "source").getId(), record("p", "a_source").getId());
        assertEquals(7, record("p_a", "source").getQuantity());
        assertEquals(9, record("p", "a_source").getQuantity());
    }

    static <T> T retry(Supplier<T> work) {
        for (int i = 0; ; i++) {
            try { return work.get(); }
            catch (RuntimeException ex) {
                if (!(ex instanceof DuplicateKeyException) && !com.example.inventory.common.TransactionErrors.isTransient(ex)) throw ex;
                if (i >= 40) throw ex;
                try { Thread.sleep(3L + i); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }
        }
    }

    static <T> List<T> parallel(int count, Supplier<T> work) throws Exception {
        return parallel(count, () -> 0, ignored -> work.get());
    }

    static <T> List<T> parallel(int count, Supplier<Integer> index, java.util.function.Function<Integer, T> work) throws Exception {
        var pool = Executors.newFixedThreadPool(Math.min(count, 24));
        var start = new CountDownLatch(1);
        try {
            List<Future<T>> jobs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                jobs.add(pool.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return work.apply(index.get()); }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (var job : jobs) results.add(job.get(45, TimeUnit.SECONDS));
            return results;
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }
}
