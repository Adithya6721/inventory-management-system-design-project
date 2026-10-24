package com.example.inventory.transfer;

import com.example.inventory.common.BadRequestException;
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
class StockTransferServiceTest {

    @Mock
    private StockTransferRepository stockTransferRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private ProductService productService;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private IdGeneratorPort idGeneratorPort;

    private StockTransferService stockTransferService;

    @BeforeEach
    void setUp() {
        stockTransferService = new StockTransferService(
                stockTransferRepository, inventoryService, productService, warehouseService, idGeneratorPort);
    }

    @Test
    @DisplayName("Initiate Transfer: Rejects transfer between identical source and destination")
    void testInitiateTransfer_SameWarehouseRejected() {
        StockTransferRequest request = new StockTransferRequest(
                "wh_1", "wh_1",
                List.of(new TransferItemRequest("prod_1", 10)),
                null
        );

        assertThrows(BadRequestException.class, () -> stockTransferService.initiateTransfer(request));
    }

    @Test
    @DisplayName("Initiate Transfer: Deducts source stock and marks IN_TRANSIT")
    void testInitiateTransfer_Success() {
        StockTransferRequest request = new StockTransferRequest(
                "wh_src", "wh_dest",
                List.of(new TransferItemRequest("prod_1", 10)),
                null
        );

        when(productService.getProductEntity("prod_1"))
                .thenReturn(new Product("prod_1", "SKU1", "Item", null, null, BigDecimal.TEN, 5, true));
        when(idGeneratorPort.generateId("trn")).thenReturn("trn_99");
        when(inventoryService.deductAvailableStockDirect("prod_1", "wh_src", 10, MovementType.TRANSFER_OUT, "trn_99"))
                .thenReturn(true);
        when(stockTransferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockTransferResponse response = stockTransferService.initiateTransfer(request);

        assertNotNull(response);
        assertEquals("trn_99", response.getTransferId());
        assertEquals(TransferStatus.IN_TRANSIT, response.getStatus());
        verify(inventoryService).deductAvailableStockDirect("prod_1", "wh_src", 10, MovementType.TRANSFER_OUT, "trn_99");
    }

    @Test
    @DisplayName("Complete Transfer: Credits destination warehouse and marks COMPLETED")
    void testCompleteTransfer_Success() {
        StockTransfer transfer = new StockTransfer(
                "trn_99", "wh_src", "wh_dest",
                List.of(new TransferItem("prod_1", 10)),
                TransferStatus.IN_TRANSIT, null
        );
        when(stockTransferRepository.findByTransferId("trn_99")).thenReturn(Optional.of(transfer));
        when(stockTransferRepository.save(any(StockTransfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockTransferResponse response = stockTransferService.completeTransfer("trn_99");

        assertEquals(TransferStatus.COMPLETED, response.getStatus());
        verify(inventoryService).creditStock("prod_1", "wh_dest", 10, MovementType.TRANSFER_IN, "trn_99");
    }

    @Test
    @DisplayName("Complete Transfer: Duplicate completion is idempotent and does not double-credit")
    void testCompleteTransfer_IdempotentOnDuplicate() {
        StockTransfer completedTransfer = new StockTransfer(
                "trn_99", "wh_src", "wh_dest",
                List.of(new TransferItem("prod_1", 10)),
                TransferStatus.COMPLETED, null
        );
        when(stockTransferRepository.findByTransferId("trn_99")).thenReturn(Optional.of(completedTransfer));

        StockTransferResponse response = stockTransferService.completeTransfer("trn_99");

        assertEquals(TransferStatus.COMPLETED, response.getStatus());
        verify(inventoryService, never()).creditStock(any(), any(), anyLong(), any(), any());
    }

    @Test
    void mustNotDeliverTransferThatHasNotBeenDispatched() {
        StockTransfer transfer = new StockTransfer("trn_not_sent", "source", "dest",
                List.of(new TransferItem("p", 3)), TransferStatus.INITIATED, null);
        when(stockTransferRepository.findByTransferId("trn_not_sent")).thenReturn(Optional.of(transfer));
        assertThrows(com.example.inventory.common.ConflictException.class,
                () -> stockTransferService.completeTransfer("trn_not_sent"));
        verifyNoInteractions(inventoryService);
    }
}
