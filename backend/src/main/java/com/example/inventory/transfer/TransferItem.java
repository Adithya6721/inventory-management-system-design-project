package com.example.inventory.transfer;

public class TransferItem {

    private String productId;
    private long quantity;

    public TransferItem() {
    }

    public TransferItem(String productId, long quantity) {
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
