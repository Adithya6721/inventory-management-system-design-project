package com.example.inventory.purchase;

import jakarta.validation.Valid;

import java.util.List;

public class PurchaseReceiptRequest {

    @Valid
    private List<PurchaseReceiptItemRequest> items;

    private String idempotencyKey;

    public PurchaseReceiptRequest() {
    }

    public PurchaseReceiptRequest(List<PurchaseReceiptItemRequest> items, String idempotencyKey) {
        this.items = items;
        this.idempotencyKey = idempotencyKey;
    }

    public List<PurchaseReceiptItemRequest> getItems() {
        return items;
    }

    public void setItems(List<PurchaseReceiptItemRequest> items) {
        this.items = items;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
