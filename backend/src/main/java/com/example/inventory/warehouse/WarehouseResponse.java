package com.example.inventory.warehouse;

import java.time.Instant;

public class WarehouseResponse {

    private String warehouseId;
    private String name;
    private String location;
    private WarehouseStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public WarehouseResponse() {
    }

    public WarehouseResponse(String warehouseId, String name, String location,
                             WarehouseStatus status, Instant createdAt, Instant updatedAt) {
        this.warehouseId = warehouseId;
        this.name = name;
        this.location = location;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static WarehouseResponse fromDomain(Warehouse warehouse) {
        if (warehouse == null) {
            return null;
        }
        return new WarehouseResponse(
                warehouse.getWarehouseId(),
                warehouse.getName(),
                warehouse.getLocation(),
                warehouse.getStatus(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt()
        );
    }

    public String getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public WarehouseStatus getStatus() {
        return status;
    }

    public void setStatus(WarehouseStatus status) {
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
