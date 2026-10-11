package com.example.inventory.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class PurchaseOrderRequest {

    private String supplierId;

    @NotBlank(message = "Destination warehouse ID is required")
    private String destinationWarehouseId;

    @NotEmpty(message = "Order must contain at least one item")
    @Valid
    private List<PurchaseOrderItemRequest> items;

    private String idempotencyKey;

    public PurchaseOrderRequest() {
    }

    public PurchaseOrderRequest(String supplierId, String destinationWarehouseId,
                                List<PurchaseOrderItemRequest> items, String idempotencyKey) {
        this.supplierId = supplierId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.items = items;
        this.idempotencyKey = idempotencyKey;
    }

    public String getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(String supplierId) {
        this.supplierId = supplierId;
    }

    public String getDestinationWarehouseId() {
        return destinationWarehouseId;
    }

    public void setDestinationWarehouseId(String destinationWarehouseId) {
        this.destinationWarehouseId = destinationWarehouseId;
    }

    public List<PurchaseOrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<PurchaseOrderItemRequest> items) {
        this.items = items;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
