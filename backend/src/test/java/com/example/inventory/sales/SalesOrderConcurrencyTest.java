package com.example.inventory.sales;

import com.example.inventory.common.ConflictException;
import com.example.inventory.integration.DefaultUuidGeneratorPort;
import com.example.inventory.integration.IdGeneratorPort;
import com.example.inventory.integration.InventoryCachePort;
import com.example.inventory.integration.InventoryPersistencePort;
import com.example.inventory.inventory.*;
import com.example.inventory.notification.LowStockAlertRepository;
import com.example.inventory.notification.LowStockService;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.product.ProductService;
import com.example.inventory.warehouse.Warehouse;
import com.example.inventory.warehouse.WarehouseRepository;
import com.example.inventory.warehouse.WarehouseService;
import com.example.inventory.warehouse.WarehouseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SalesOrderConcurrencyTest {

    /**
     * In-memory thread-safe atomic persistence port simulating MongoDB's single-document atomic update.
     */
    static class ThreadSafeMemoryInventoryPersistencePort implements InventoryPersistencePort {
        private final ConcurrentHashMap<String, InventoryRecord> store = new ConcurrentHashMap<>();

        public void init(String productId, String warehouseId, long quantity, long reserved) {
            store.put(productId + "_" + warehouseId, new InventoryRecord(productId, warehouseId, quantity, reserved));
        }

        @Override
        public Optional<InventoryRecord> findByProductIdAndWarehouseId(String productId, String warehouseId) {
            return Optional.ofNullable(store.get(productId + "_" + warehouseId));
        }

        @Override
        public List<InventoryRecord> findByProductId(String productId) {
            return store.values().stream().filter(r -> r.getProductId().equals(productId)).toList();
        }

        @Override
        public List<InventoryRecord> findByWarehouseId(String warehouseId) {
            return store.values().stream().filter(r -> r.getWarehouseId().equals(warehouseId)).toList();
        }

        @Override
        public InventoryRecord save(InventoryRecord record) {
            store.put(record.getProductId() + "_" + record.getWarehouseId(), record);
            return record;
        }

        @Override
        public synchronized boolean reserveStock(String productId, String warehouseId, long quantity) {
            InventoryRecord record = store.get(productId + "_" + warehouseId);
            if (record == null) return false;

            long available = record.getQuantity() - record.getReservedQuantity();
            if (available >= quantity) {
                record.setReservedQuantity(record.getReservedQuantity() + quantity);
                record.setVersion(record.getVersion() + 1);
                record.setUpdatedAt(Instant.now());
                return true;
            }
            return false;
        }

        @Override
        public synchronized boolean releaseReservedStock(String productId, String warehouseId, long quantity) {
            InventoryRecord record = store.get(productId + "_" + warehouseId);
            if (record == null || record.getReservedQuantity() < quantity) return false;
            record.setReservedQuantity(record.getReservedQuantity() - quantity);
            return true;
        }

        @Override
        public synchronized boolean deductReservedStock(String productId, String warehouseId, long quantity) {
            InventoryRecord record = store.get(productId + "_" + warehouseId);
            if (record == null || record.getReservedQuantity() < quantity || record.getQuantity() < quantity) return false;
            record.setQuantity(record.getQuantity() - quantity);
            record.setReservedQuantity(record.getReservedQuantity() - quantity);
            return true;
        }

        @Override
        public synchronized boolean deductAvailableStockDirect(String productId, String warehouseId, long quantity) {
            InventoryRecord record = store.get(productId + "_" + warehouseId);
            if (record == null) return false;
            long available = record.getQuantity() - record.getReservedQuantity();
            if (available >= quantity) {
                record.setQuantity(record.getQuantity() - quantity);
                return true;
            }
            return false;
        }

        @Override
        public synchronized InventoryRecord creditStock(String productId, String warehouseId, long quantity) {
            return store.compute(productId + "_" + warehouseId, (k, v) -> {
                if (v == null) {
                    return new InventoryRecord(productId, warehouseId, quantity, 0);
                }
                v.setQuantity(v.getQuantity() + quantity);
                return v;
            });
        }

        @Override
        public synchronized boolean adjustStock(String productId, String warehouseId, long physicalDelta, long reservedDelta) {
            InventoryRecord record = store.get(productId + "_" + warehouseId);
            if (record == null) return false;
            long newQ = record.getQuantity() + physicalDelta;
            long newR = record.getReservedQuantity() + reservedDelta;
            if (newQ < 0 || newR < 0 || newQ < newR) return false;
            record.setQuantity(newQ);
            record.setReservedQuantity(newR);
            return true;
        }
    }

    @Test
    @DisplayName("Concurrency Test: 100 concurrent 1-unit orders competing for 10 units of stock")
    void test100ConcurrentOrdersFor10Units() throws InterruptedException {
        String productId = "prod_conc";
        String warehouseId = "wh_main";
        long initialQuantity = 10;
        int totalRequests = 100;

        // Setup persistent mock dependencies
        ThreadSafeMemoryInventoryPersistencePort persistencePort = new ThreadSafeMemoryInventoryPersistencePort();
        persistencePort.init(productId, warehouseId, initialQuantity, 0);

        List<InventoryMovement> movements = Collections.synchronizedList(new ArrayList<>());
        InventoryMovementRepository movementRepository = mock(InventoryMovementRepository.class);
        when(movementRepository.save(any(InventoryMovement.class))).thenAnswer(invocation -> {
            InventoryMovement m = invocation.getArgument(0);
            movements.add(m);
            return m;
        });

        ProductRepository productRepository = mock(ProductRepository.class);
        Product product = new Product(productId, "SKU-CONC", "Concurrency Widget", "", "Test", BigDecimal.TEN, 5, true);
        when(productRepository.findByProductId(productId)).thenReturn(Optional.of(product));

        WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);
        Warehouse warehouse = new Warehouse(warehouseId, "Main Warehouse", "Building 1", WarehouseStatus.ACTIVE);
        when(warehouseRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));

        IdGeneratorPort idGeneratorPort = new DefaultUuidGeneratorPort();
        InventoryCachePort cachePort = mock(InventoryCachePort.class);

        ProductService productService = new ProductService(productRepository, idGeneratorPort, cachePort);
        WarehouseService warehouseService = new WarehouseService(warehouseRepository, idGeneratorPort);
        LowStockAlertRepository alertRepository = mock(LowStockAlertRepository.class);
        LowStockService lowStockService = new LowStockService(alertRepository, idGeneratorPort);

        InventoryService inventoryService = new InventoryService(
                persistencePort,
                movementRepository,
                productService,
                warehouseService,
                lowStockService,
                cachePort,
                idGeneratorPort
        );

        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrderService salesOrderService = new SalesOrderService(
                salesOrderRepository,
                inventoryService,
                productService,
                warehouseService,
                idGeneratorPort
        );

        // Concurrency harness
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch readyLatch = new CountDownLatch(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger successfulOrders = new AtomicInteger(0);
        AtomicInteger rejectedOrders = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Simultaneous trigger
                    SalesOrderRequest req = new SalesOrderRequest(
                            List.of(new SalesOrderItemRequest(productId, warehouseId, 1)),
                            "IDEMP-" + index
                    );
                    salesOrderService.placeOrder(req);
                    successfulOrders.incrementAndGet();
                } catch (ConflictException ex) {
                    rejectedOrders.incrementAndGet();
                } catch (Exception ex) {
                    fail("Unexpected exception: " + ex.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Blast all 100 requests simultaneously!
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verification of Invariants
        System.out.println("================ CONCURRENCY TEST RESULTS ================");
        System.out.println("Total Orders Submitted : " + totalRequests);
        System.out.println("Successful Orders      : " + successfulOrders.get());
        System.out.println("Rejected (409) Orders  : " + rejectedOrders.get());

        assertEquals(10, successfulOrders.get(), "Exactly 10 orders must succeed");
        assertEquals(90, rejectedOrders.get(), "Exactly 90 orders must fail with Insufficient Stock");

        InventoryRecord finalRecord = persistencePort.findByProductIdAndWarehouseId(productId, warehouseId).orElseThrow();
        System.out.println("Final Physical Stock   : " + finalRecord.getQuantity());
        System.out.println("Final Reserved Stock   : " + finalRecord.getReservedQuantity());
        System.out.println("Final Available Stock  : " + finalRecord.getAvailableQuantity());
        System.out.println("Total Movements Logged : " + movements.size());
        System.out.println("==========================================================");

        assertEquals(10, finalRecord.getQuantity(), "Physical quantity should remain 10 before fulfillment");
        assertEquals(10, finalRecord.getReservedQuantity(), "Reserved quantity must be exactly 10");
        assertEquals(0, finalRecord.getAvailableQuantity(), "Available quantity must never be negative, exactly 0");
        assertTrue(finalRecord.getAvailableQuantity() >= 0, "Invariant violation: available stock cannot be negative");
        assertEquals(10, movements.size(), "Exactly 10 movement records should be logged for 10 successful reservations");
    }
}
