package com.example.inventory.notification;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "low_stock_alerts")
@CompoundIndex(name = "prod_wh_status_idx", def = "{'productId': 1, 'warehouseId': 1, 'status': 1}")
public class LowStockAlert {

    @Id
    private String id;

    @Indexed(unique = true)
    private String alertId;

    @Indexed
    private String productId;

    @Indexed
    private String warehouseId;

    private int threshold;

    private long availableQuantity;

    @Indexed
    private AlertStatus status = AlertStatus.ACTIVE;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public LowStockAlert() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public LowStockAlert(String alertId, String productId, String warehouseId,
                         int threshold, long availableQuantity, AlertStatus status) {
        this.alertId = alertId;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.threshold = threshold;
        this.availableQuantity = availableQuantity;
        this.status = status != null ? status : AlertStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public long getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(long availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
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
