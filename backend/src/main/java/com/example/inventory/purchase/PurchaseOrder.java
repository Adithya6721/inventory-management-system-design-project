package com.example.inventory.purchase;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Document(collection = "purchase_orders")
public class PurchaseOrder {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderId;

    private String supplierId;

    @Indexed
    private String destinationWarehouseId;

    private List<PurchaseOrderItem> items = new ArrayList<>();

    @Indexed
    private PurchaseOrderStatus status = PurchaseOrderStatus.CREATED;

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    // Hashed receipt key -> canonical request fingerprint, committed with the stock credit.
    private Map<String, String> receiptFingerprints = new LinkedHashMap<>();

    public Map<String, String> getReceiptFingerprints() {
        return receiptFingerprints;
    }

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public PurchaseOrder() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public PurchaseOrder(String orderId, String supplierId, String destinationWarehouseId,
                         List<PurchaseOrderItem> items, PurchaseOrderStatus status,
                         String idempotencyKey) {
        this.orderId = orderId;
        this.supplierId = supplierId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.items = items != null ? items : new ArrayList<>();
        this.status = status != null ? status : PurchaseOrderStatus.CREATED;
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
