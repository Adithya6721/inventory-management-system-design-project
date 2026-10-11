package com.example.inventory.inventory;

import com.example.inventory.integration.IdGeneratorPort;
import com.example.inventory.integration.InventoryCachePort;
import com.example.inventory.integration.InventoryPersistencePort;
import com.example.inventory.notification.LowStockService;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductService;
import com.example.inventory.warehouse.Warehouse;
import com.example.inventory.warehouse.WarehouseService;
import com.example.inventory.warehouse.WarehouseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryPersistencePort persistencePort;

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private ProductService productService;

    @Mock
    private WarehouseService warehouseService;

    @Mock
    private LowStockService lowStockService;

    @Mock
    private InventoryCachePort inventoryCachePort;

    @Mock
    private IdGeneratorPort idGeneratorPort;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(
                persistencePort,
                movementRepository,
                productService,
                warehouseService,
                lowStockService,
                inventoryCachePort,
                idGeneratorPort
        );
    }

    @Test
    @DisplayName("Reserve Stock: Routes through PersistencePort and creates SALE_RESERVE movement")
    void testReserveStock_CreatesMovementAndEvictsCache() {
        when(idGeneratorPort.generateId("mov")).thenReturn("mov_1");
        when(persistencePort.reserveStock("prod_1", "wh_1", 5)).thenReturn(true);
        when(productService.getProductEntity("prod_1"))
                .thenReturn(new Product("prod_1", "SKU1", "Item", null, null, BigDecimal.TEN, 10, true));
        when(persistencePort.findByProductIdAndWarehouseId("prod_1", "wh_1"))
                .thenReturn(Optional.of(new InventoryRecord("prod_1", "wh_1", 20, 5)));

        boolean reserved = inventoryService.reserveStock("prod_1", "wh_1", 5, "ref_order_1");

        assertTrue(reserved);
        verify(persistencePort).reserveStock("prod_1", "wh_1", 5);

        ArgumentCaptor<InventoryMovement> movementCaptor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(movementCaptor.capture());
        InventoryMovement movement = movementCaptor.getValue();
        assertEquals(MovementType.SALE_RESERVE, movement.getMovementType());
        assertEquals(-5, movement.getQuantityDelta());
        assertEquals("ref_order_1", movement.getReferenceId());

        verify(inventoryCachePort).evictInventory("prod_1", "wh_1");
        verify(inventoryCachePort).evictAllProductInventory("prod_1");
    }

    @Test
    @DisplayName("Credit Stock: Invokes persistence port, records movement and checks low stock")
    void testCreditStock_Success() {
        when(idGeneratorPort.generateId("mov")).thenReturn("mov_2");
        when(productService.getProductEntity("prod_1"))
                .thenReturn(new Product("prod_1", "SKU1", "Item", null, null, BigDecimal.TEN, 10, true));
        doNothing().when(warehouseService).validateWarehouseActive("wh_1");

        InventoryRecord updatedRecord = new InventoryRecord("prod_1", "wh_1", 50, 0);
        when(persistencePort.creditStock("prod_1", "wh_1", 50)).thenReturn(updatedRecord);
        when(persistencePort.findByProductIdAndWarehouseId("prod_1", "wh_1"))
                .thenReturn(Optional.of(updatedRecord));

        InventoryRecord result = inventoryService.creditStock("prod_1", "wh_1", 50, MovementType.PURCHASE_RECEIPT, "po_123");

        assertNotNull(result);
        assertEquals(50, result.getQuantity());
        verify(persistencePort).creditStock("prod_1", "wh_1", 50);
        verify(inventoryCachePort).evictInventory("prod_1", "wh_1");
        verify(lowStockService).evaluateStock("prod_1", "wh_1", 50, 10);
    }
}
