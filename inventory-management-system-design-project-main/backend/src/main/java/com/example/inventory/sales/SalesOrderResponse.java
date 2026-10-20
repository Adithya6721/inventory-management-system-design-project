package com.example.inventory.sales;

import java.time.Instant;
import java.util.List;

public class SalesOrderResponse {

    private String orderId;
    private List<SalesOrderItem> items;
    private SalesOrderStatus status;
    private String idempotencyKey;
    private Instant createdAt;
    private Instant updatedAt;

    public SalesOrderResponse() {
    }

    public SalesOrderResponse(String orderId, List<SalesOrderItem> items,
                              SalesOrderStatus status, String idempotencyKey,
                              Instant createdAt, Instant updatedAt) {
        this.orderId = orderId;
        this.items = items;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static SalesOrderResponse fromDomain(SalesOrder order) {
        if (order == null) {
            return null;
        }
        return new SalesOrderResponse(
                order.getOrderId(),
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

    public List<SalesOrderItem> getItems() {
        return items;
    }

    public void setItems(List<SalesOrderItem> items) {
        this.items = items;
    }

    public SalesOrderStatus getStatus() {
        return status;
    }

    public void setStatus(SalesOrderStatus status) {
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
