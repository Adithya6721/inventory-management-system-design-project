package com.example.inventory.inventory;

import jakarta.validation.constraints.NotBlank;

public class StockAdjustmentRequest {

    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "Warehouse ID is required")
    private String warehouseId;

    private long physicalDelta = 0;

    private long reservedDelta = 0;

    @NotBlank(message = "Reason for stock adjustment is required")
    private String reason;

    public StockAdjustmentRequest() {
    }

    public StockAdjustmentRequest(String productId, String warehouseId,
                                  long physicalDelta, long reservedDelta, String reason) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.physicalDelta = physicalDelta;
        this.reservedDelta = reservedDelta;
        this.reason = reason;
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

    public long getPhysicalDelta() {
        return physicalDelta;
    }

    public void setPhysicalDelta(long physicalDelta) {
        this.physicalDelta = physicalDelta;
    }

    public long getReservedDelta() {
        return reservedDelta;
    }

    public void setReservedDelta(long reservedDelta) {
        this.reservedDelta = reservedDelta;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
