package com.example.inventory.transfer;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class TransferItemRequest {

    @NotBlank(message = "Product ID is required")
    private String productId;

    @Min(value = 1, message = "Transfer quantity must be at least 1")
    private long quantity;

    public TransferItemRequest() {
    }

    public TransferItemRequest(String productId, long quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }
}
