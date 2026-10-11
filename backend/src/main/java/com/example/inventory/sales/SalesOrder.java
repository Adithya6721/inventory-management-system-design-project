package com.example.inventory.sales;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "sales_orders")
public class SalesOrder {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderId;

    private List<SalesOrderItem> items = new ArrayList<>();

    @Indexed
    private SalesOrderStatus status = SalesOrderStatus.CREATED;

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public SalesOrder() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public SalesOrder(String orderId, List<SalesOrderItem> items, SalesOrderStatus status, String idempotencyKey) {
        this.orderId = orderId;
        this.items = items != null ? items : new ArrayList<>();
        this.status = status != null ? status : SalesOrderStatus.CREATED;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
