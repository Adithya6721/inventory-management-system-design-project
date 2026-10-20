package com.example.inventory.purchase;

import java.time.Instant;
import java.util.List;

public class PurchaseOrderResponse {

    private String orderId;
    private String supplierId;
    private String destinationWarehouseId;
    private List<PurchaseOrderItem> items;
    private PurchaseOrderStatus status;
    private String idempotencyKey;
    private Instant createdAt;
    private Instant updatedAt;

    public PurchaseOrderResponse() {
    }

    public PurchaseOrderResponse(String orderId, String supplierId, String destinationWarehouseId,
                                 List<PurchaseOrderItem> items, PurchaseOrderStatus status,
                                 String idempotencyKey, Instant createdAt, Instant updatedAt) {
        this.orderId = orderId;
        this.supplierId = supplierId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.items = items;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PurchaseOrderResponse fromDomain(PurchaseOrder order) {
        if (order == null) {
            return null;
        }
        return new PurchaseOrderResponse(
                order.getOrderId(),
                order.getSupplierId(),
                order.getDestinationWarehouseId(),
                order.getItems(),
                order.getStatus(),
                order.getIdempotencyKey(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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

    public List<PurchaseOrderItem> getItems() {
        return items;
    }

    public void setItems(List<PurchaseOrderItem> items) {
        this.items = items;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseOrderStatus status) {
        this.status = status;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
