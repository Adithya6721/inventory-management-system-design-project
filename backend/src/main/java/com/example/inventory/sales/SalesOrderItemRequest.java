package com.example.inventory.sales;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class SalesOrderItemRequest {

    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "Warehouse ID is required")
    private String warehouseId;

    @Min(value = 1, message = "Quantity must be at least 1")
    private long quantity;

    public SalesOrderItemRequest() {
    }

    public SalesOrderItemRequest(String productId, String warehouseId, long quantity) {
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
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
}
