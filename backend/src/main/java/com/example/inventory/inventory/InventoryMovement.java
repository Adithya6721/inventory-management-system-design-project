package com.example.inventory.inventory;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "inventory_movements")
public class InventoryMovement {

    @Id
    private String id;

    @Indexed(unique = true)
    private String movementId;

    @Indexed
    private String productId;

    @Indexed
    private String warehouseId;

    private MovementType movementType;

    private long quantityDelta;

    @Indexed
    private String referenceId;

    @CreatedDate
    @Indexed
    private Instant timestamp;

    public InventoryMovement() {
        this.timestamp = Instant.now();
    }

    public InventoryMovement(String movementId, String productId, String warehouseId,
                             MovementType movementType, long quantityDelta,
                             String referenceId) {
        this.movementId = movementId;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.referenceId = referenceId;
        this.timestamp = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMovementId() {
        return movementId;
    }

    public void setMovementId(String movementId) {
        this.movementId = movementId;
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

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public long getQuantityDelta() {
        return quantityDelta;
    }

    public void setQuantityDelta(long quantityDelta) {
        this.quantityDelta = quantityDelta;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
