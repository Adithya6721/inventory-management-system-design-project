package com.example.inventory.purchase;

public class PurchaseOrderItem {

    private String productId;
    private long quantity;
    private long receivedQuantity = 0;

    public PurchaseOrderItem() {
    }

    public PurchaseOrderItem(String productId, long quantity, long receivedQuantity) {
        this.productId = productId;
        this.quantity = quantity;
        this.receivedQuantity = receivedQuantity;
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

    public long getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(long receivedQuantity) {
        this.receivedQuantity = receivedQuantity;
    }
}
