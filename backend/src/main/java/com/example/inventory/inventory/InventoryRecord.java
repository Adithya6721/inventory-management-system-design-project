package com.example.inventory.inventory;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "inventory")
@CompoundIndex(name = "prod_wh_unique_idx", def = "{'productId': 1, 'warehouseId': 1}", unique = true)
public class InventoryRecord {

    @Id
    private String id;

    @Indexed
    private String productId;

    @Indexed
    private String warehouseId;

    private long quantity = 0;

    private long reservedQuantity = 0;

    private long version = 0;

    private Instant updatedAt;

    public InventoryRecord() {
        this.updatedAt = Instant.now();
    }

    public InventoryRecord(String productId, String warehouseId, long quantity, long reservedQuantity) {
        this.id = productId + "_" + warehouseId;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.reservedQuantity = reservedQuantity;
        this.version = 0;
        this.updatedAt = Instant.now();
    }

    public long getAvailableQuantity() {
        return Math.max(0, quantity - reservedQuantity);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(long reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
