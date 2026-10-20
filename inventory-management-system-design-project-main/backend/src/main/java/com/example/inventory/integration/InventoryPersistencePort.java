package com.example.inventory.integration;

import com.example.inventory.inventory.InventoryRecord;

import java.util.List;
import java.util.Optional;

/**
 * Port defining persistence contracts for Inventory records.
 * Member 2 will connect this port to their consistent-hashing shard router.
 * All core inventory business services interact strictly via this interface.
 */
public interface InventoryPersistencePort {

    Optional<InventoryRecord> findByProductIdAndWarehouseId(String productId, String warehouseId);

    List<InventoryRecord> findByProductId(String productId);

    List<InventoryRecord> findByWarehouseId(String warehouseId);

    InventoryRecord save(InventoryRecord record);

    /**
     * Atomically reserves stock if availableStock (quantity - reservedQuantity) >= delta.
     * @return true if reservation succeeded, false if insufficient available stock.
     */
    boolean reserveStock(String productId, String warehouseId, long quantity);

    /**
     * Atomically releases reserved stock (reservedQuantity = reservedQuantity - delta).
     */
    boolean releaseReservedStock(String productId, String warehouseId, long quantity);

    /**
     * Atomically finalizes a sale deduction from reserved stock:
     * quantity = quantity - delta, reservedQuantity = reservedQuantity - delta.
     */
    boolean deductReservedStock(String productId, String warehouseId, long quantity);

    /**
     * Atomically deducts stock directly from available quantity without prior reservation:
     * quantity = quantity - delta if (quantity - reservedQuantity) >= delta.
     */
    boolean deductAvailableStockDirect(String productId, String warehouseId, long quantity);

    /**
     * Atomically credits physical stock (e.g. from purchase receipt or completed transfer).
     * If the record does not exist, it will be initialized.
     */
    InventoryRecord creditStock(String productId, String warehouseId, long quantity);

    /**
     * Applies manual physical or reserved adjustments with atomic check to avoid negative stock.
     */
    boolean adjustStock(String productId, String warehouseId, long physicalDelta, long reservedDelta);
}
