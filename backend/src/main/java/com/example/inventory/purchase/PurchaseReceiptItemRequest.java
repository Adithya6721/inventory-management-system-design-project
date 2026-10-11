package com.example.inventory.purchase;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class PurchaseReceiptItemRequest {

    @NotBlank(message = "Product ID is required")
    private String productId;

    @Min(value = 1, message = "Received quantity must be at least 1")
    private long quantityReceived;

    public PurchaseReceiptItemRequest() {
    }

    public PurchaseReceiptItemRequest(String productId, long quantityReceived) {
        this.productId = productId;
        this.quantityReceived = quantityReceived;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public long getQuantityReceived() {
        return quantityReceived;
    }

    public void setQuantityReceived(long quantityReceived) {
        this.quantityReceived = quantityReceived;
    }
}
