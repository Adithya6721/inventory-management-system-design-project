package com.example.inventory.inventory;

import com.example.inventory.common.ConflictException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.IdGeneratorPort;
import com.example.inventory.integration.InventoryCachePort;
import com.example.inventory.integration.InventoryPersistencePort;
import com.example.inventory.notification.LowStockService;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductService;
import com.example.inventory.warehouse.WarehouseService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private final InventoryPersistencePort persistencePort;
    private final InventoryMovementRepository movementRepository;
    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final LowStockService lowStockService;
    private final InventoryCachePort inventoryCachePort;
    private final IdGeneratorPort idGeneratorPort;

    public InventoryService(InventoryPersistencePort persistencePort,
                            InventoryMovementRepository movementRepository,
                            ProductService productService,
                            WarehouseService warehouseService,
                            LowStockService lowStockService,
                            InventoryCachePort inventoryCachePort,
                            IdGeneratorPort idGeneratorPort) {
        this.persistencePort = persistencePort;
        this.movementRepository = movementRepository;
        this.productService = productService;
        this.warehouseService = warehouseService;
        this.lowStockService = lowStockService;
        this.inventoryCachePort = inventoryCachePort;
        this.idGeneratorPort = idGeneratorPort;
    }

    public InventoryResponse getInventory(String productId, String warehouseId) {
        // Ensure product & warehouse exist
        productService.getProductEntity(productId);
        warehouseService.getWarehouseEntity(warehouseId);

        return persistencePort.findByProductIdAndWarehouseId(productId, warehouseId)
                .map(InventoryResponse::fromDomain)
                .orElseGet(() -> new InventoryResponse(productId, warehouseId, 0, 0, 0, null));
    }

    public List<InventoryResponse> getInventoryByProduct(String productId) {
        productService.getProductEntity(productId);
        return persistencePort.findByProductId(productId).stream()
                .map(InventoryResponse::fromDomain)
                .collect(Collectors.toList());
    }

    public List<InventoryResponse> getInventoryByWarehouse(String warehouseId) {
        warehouseService.getWarehouseEntity(warehouseId);
        return persistencePort.findByWarehouseId(warehouseId).stream()
                .map(InventoryResponse::fromDomain)
                .collect(Collectors.toList());
    }

    public boolean reserveStock(String productId, String warehouseId, long quantity, String referenceId) {
        boolean success = persistencePort.reserveStock(productId, warehouseId, quantity);
        if (success) {
            recordMovement(productId, warehouseId, MovementType.SALE_RESERVE, -quantity, referenceId);
            onStockMutated(productId, warehouseId);
        }
        return success;
    }

    public boolean releaseReservedStock(String productId, String warehouseId, long quantity, String referenceId) {
        boolean success = persistencePort.releaseReservedStock(productId, warehouseId, quantity);
        if (success) {
            recordMovement(productId, warehouseId, MovementType.SALE_RELEASE, quantity, referenceId);
            onStockMutated(productId, warehouseId);
        }
        return success;
    }

    public boolean deductReservedStock(String productId, String warehouseId, long quantity, String referenceId) {
        boolean success = persistencePort.deductReservedStock(productId, warehouseId, quantity);
        if (success) {
            recordMovement(productId, warehouseId, MovementType.SALE_DEDUCT, -quantity, referenceId);
            onStockMutated(productId, warehouseId);
        }
        return success;
    }

    public boolean deductAvailableStockDirect(String productId, String warehouseId, long quantity,
                                             MovementType movementType, String referenceId) {
        boolean success = persistencePort.deductAvailableStockDirect(productId, warehouseId, quantity);
        if (success) {
            recordMovement(productId, warehouseId, movementType, -quantity, referenceId);
            onStockMutated(productId, warehouseId);
        }
        return success;
    }

    public InventoryRecord creditStock(String productId, String warehouseId, long quantity,
                                      MovementType movementType, String referenceId) {
        productService.getProductEntity(productId);
        warehouseService.validateWarehouseActive(warehouseId);

        InventoryRecord updated = persistencePort.creditStock(productId, warehouseId, quantity);
        recordMovement(productId, warehouseId, movementType, quantity, referenceId);
        onStockMutated(productId, warehouseId);
        return updated;
    }

    public InventoryResponse adjustStock(StockAdjustmentRequest request) {
        Product product = productService.getProductEntity(request.getProductId());
        warehouseService.validateWarehouseActive(request.getWarehouseId());

        boolean adjusted = persistencePort.adjustStock(
                request.getProductId(),
                request.getWarehouseId(),
                request.getPhysicalDelta(),
                request.getReservedDelta()
        );

        if (!adjusted) {
            throw new ConflictException("Stock adjustment failed: would violate non-negative stock invariants.");
        }

        recordMovement(
                request.getProductId(),
                request.getWarehouseId(),
                MovementType.ADJUSTMENT,
                request.getPhysicalDelta(),
                request.getReason() != null ? request.getReason() : "MANUAL_ADJUSTMENT"
        );

        onStockMutated(request.getProductId(), request.getWarehouseId());

        return persistencePort.findByProductIdAndWarehouseId(request.getProductId(), request.getWarehouseId())
                .map(InventoryResponse::fromDomain)
                .orElseThrow(() -> new NotFoundException("Inventory record not found after adjustment"));
    }

    public List<InventoryMovement> getMovements(String productId) {
        productService.getProductEntity(productId);
        return movementRepository.findByProductIdOrderByTimestampDesc(productId);
    }

    private void recordMovement(String productId, String warehouseId, MovementType type, long delta, String referenceId) {
        String movementId = idGeneratorPort.generateId("mov");
        InventoryMovement movement = new InventoryMovement(
                movementId,
                productId,
                warehouseId,
                type,
                delta,
                referenceId
        );
        movementRepository.save(movement);
    }

    private void onStockMutated(String productId, String warehouseId) {
        inventoryCachePort.evictInventory(productId, warehouseId);
        inventoryCachePort.evictAllProductInventory(productId);

        // Low stock evaluation
        Optional<InventoryRecord> recordOpt = persistencePort.findByProductIdAndWarehouseId(productId, warehouseId);
        if (recordOpt.isPresent()) {
            Product product = productService.getProductEntity(productId);
            lowStockService.evaluateStock(
                    productId,
                    warehouseId,
                    recordOpt.get().getAvailableQuantity(),
                    product.getReorderLevel()
            );
        }
    }
}
