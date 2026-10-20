package com.example.inventory.purchase;

import com.example.inventory.common.ConflictException;
import com.example.inventory.integration.IdGeneratorPort;
import com.example.inventory.inventory.InventoryService;
import com.example.inventory.inventory.MovementType;
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
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ProductService productService;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private IdGeneratorPort idGeneratorPort;

    private PurchaseOrderService purchaseOrderService;

    @BeforeEach
    void setUp() {
        purchaseOrderService = new PurchaseOrderService(
                purchaseOrderRepository, inventoryService, productService, warehouseService, idGeneratorPort);
    }

    @Test
    @DisplayName("Create Purchase Order: Successfully creates in CREATED status")
    void testCreatePurchaseOrder() {
        PurchaseOrderRequest request = new PurchaseOrderRequest(
                "supp_1", "wh_1",
                List.of(new PurchaseOrderItemRequest("prod_1", 50)),
                "IDEMP-PO-1"
        );

        when(purchaseOrderRepository.findByIdempotencyKey("IDEMP-PO-1")).thenReturn(Optional.empty());
        when(productService.getProductEntity("prod_1")).thenReturn(new Product("prod_1", "SKU1", "Item", null, null, BigDecimal.TEN, 5, true));
        when(idGeneratorPort.generateId("po")).thenReturn("po_500");
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrderResponse response = purchaseOrderService.createPurchaseOrder(request);

        assertNotNull(response);
        assertEquals("po_500", response.getOrderId());
        assertEquals(PurchaseOrderStatus.CREATED, response.getStatus());
        assertEquals(1, response.getItems().size());
    }

    @Test
    @DisplayName("Receive Stock: Successfully credits stock and transitions order status")
    void testReceiveStock_Success() {
        PurchaseOrder order = new PurchaseOrder(
                "po_500", "supp_1", "wh_1",
                List.of(new PurchaseOrderItem("prod_1", 50, 0)),
                PurchaseOrderStatus.CREATED, null
        );
        when(purchaseOrderRepository.findByOrderId("po_500")).thenReturn(Optional.of(order));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseReceiptRequest receipt = new PurchaseReceiptRequest(
                List.of(new PurchaseReceiptItemRequest("prod_1", 20)), "REC-1"
        );

        PurchaseOrderResponse response = purchaseOrderService.receiveStock("po_500", receipt);

        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, response.getStatus());
        assertEquals(20, response.getItems().get(0).getReceivedQuantity());
        verify(inventoryService).creditStock("prod_1", "wh_1", 20, MovementType.PURCHASE_RECEIPT, "po_500");
    }

    @Test
    @DisplayName("Receive Stock: Rejects over-receipt with ConflictException")
    void testReceiveStock_OverReceiptRejected() {
        PurchaseOrder order = new PurchaseOrder(
                "po_500", "supp_1", "wh_1",
                List.of(new PurchaseOrderItem("prod_1", 10, 0)),
                PurchaseOrderStatus.CREATED, null
        );
        when(purchaseOrderRepository.findByOrderId("po_500")).thenReturn(Optional.of(order));

        PurchaseReceiptRequest receipt = new PurchaseReceiptRequest(
                List.of(new PurchaseReceiptItemRequest("prod_1", 15)), "REC-OVER"
        );

        assertThrows(ConflictException.class, () -> purchaseOrderService.receiveStock("po_500", receipt));
        verify(inventoryService, never()).creditStock(any(), any(), anyLong(), any(), any());
    }
}
