package com.example.inventory.inventory;

import java.time.Instant;

public class InventoryResponse {

    private String productId;
    private String warehouseId;
    private long quantity;
    private long reservedQuantity;
    private long availableQuantity;
    private Instant updatedAt;

    public InventoryResponse() {
    }

    public InventoryResponse(String productId, String warehouseId, long quantity,
                             long reservedQuantity, long availableQuantity, Instant updatedAt) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.reservedQuantity = reservedQuantity;
        this.availableQuantity = availableQuantity;
        this.updatedAt = updatedAt;
    }

    public static InventoryResponse fromDomain(InventoryRecord record) {
        if (record == null) {
            return null;
        }
        return new InventoryResponse(
                record.getProductId(),
                record.getWarehouseId(),
                record.getQuantity(),
                record.getReservedQuantity(),
                record.getAvailableQuantity(),
                record.getUpdatedAt()
        );
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(long reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public long getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(long availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
