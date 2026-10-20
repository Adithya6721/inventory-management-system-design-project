package com.example.inventory.sales;

import com.example.inventory.common.ConflictException;
import com.example.inventory.integration.IdGeneratorPort;
import com.example.inventory.inventory.InventoryService;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductService;
import com.example.inventory.warehouse.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SalesOrderServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ProductService productService;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private IdGeneratorPort idGeneratorPort;

    private SalesOrderService salesOrderService;

    @BeforeEach
    void setUp() {
        salesOrderService = new SalesOrderService(
                salesOrderRepository, inventoryService, productService, warehouseService, idGeneratorPort);
    }

    @Test
    @DisplayName("Place Order: Successfully reserves stock and creates order in RESERVED status")
    void testPlaceOrder_Success() {
        SalesOrderRequest request = new SalesOrderRequest(
                List.of(new SalesOrderItemRequest("prod_1", "wh_1", 2)),
                "IDEMP-SO-1"
        );

        when(salesOrderRepository.findByIdempotencyKey("IDEMP-SO-1")).thenReturn(Optional.empty());
        when(productService.getProductEntity("prod_1"))
                .thenReturn(new Product("prod_1", "SKU1", "Item", null, null, BigDecimal.TEN, 5, true));
        when(idGeneratorPort.generateId("so")).thenReturn("so_101");
        when(inventoryService.reserveStock("prod_1", "wh_1", 2, "so_101")).thenReturn(true);
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrderResponse response = salesOrderService.placeOrder(request);

        assertNotNull(response);
        assertEquals("so_101", response.getOrderId());
        assertEquals(SalesOrderStatus.RESERVED, response.getStatus());
        verify(inventoryService).reserveStock("prod_1", "wh_1", 2, "so_101");
    }

    @Test
    @DisplayName("Place Order: Insufficient stock throws ConflictException")
    void testPlaceOrder_InsufficientStock() {
        SalesOrderRequest request = new SalesOrderRequest(
                List.of(new SalesOrderItemRequest("prod_1", "wh_1", 20)),
                null
        );

        when(productService.getProductEntity("prod_1"))
                .thenReturn(new Product("prod_1", "SKU1", "Item", null, null, BigDecimal.TEN, 5, true));
        when(idGeneratorPort.generateId("so")).thenReturn("so_102");
        when(inventoryService.reserveStock("prod_1", "wh_1", 20, "so_102")).thenReturn(false);

        assertThrows(ConflictException.class, () -> salesOrderService.placeOrder(request));
        verify(salesOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Place Multi-item Order: Compensates/rolls back prior reservations if any subsequent item fails")
    void testPlaceOrder_SagaRollbackOnFailure() {
        SalesOrderRequest request = new SalesOrderRequest(
                List.of(
                        new SalesOrderItemRequest("prod_1", "wh_1", 2),
                        new SalesOrderItemRequest("prod_2", "wh_1", 10)
                ),
                null
        );

        when(productService.getProductEntity(anyString()))
                .thenReturn(new Product("prod_x", "SKUx", "Item", null, null, BigDecimal.TEN, 5, true));
        when(idGeneratorPort.generateId("so")).thenReturn("so_103");

        // Item 1 succeeds, Item 2 fails
        when(inventoryService.reserveStock("prod_1", "wh_1", 2, "so_103")).thenReturn(true);
        when(inventoryService.reserveStock("prod_2", "wh_1", 10, "so_103")).thenReturn(false);

        assertThrows(ConflictException.class, () -> salesOrderService.placeOrder(request));

        // Verifies item 1 reservation was compensated!
        verify(inventoryService).releaseReservedStock("prod_1", "wh_1", 2, "so_103");
        verify(salesOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancel Order: Successfully releases reservations")
    void testCancelOrder_ReleasesReservation() {
        SalesOrder order = new SalesOrder(
                "so_101",
                List.of(new SalesOrderItem("prod_1", "wh_1", 2)),
                SalesOrderStatus.RESERVED, null
        );
        when(salesOrderRepository.findByOrderId("so_101")).thenReturn(Optional.of(order));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrderResponse response = salesOrderService.cancelOrder("so_101");

        assertEquals(SalesOrderStatus.CANCELLED, response.getStatus());
        verify(inventoryService).releaseReservedStock("prod_1", "wh_1", 2, "so_101");
    }
}
