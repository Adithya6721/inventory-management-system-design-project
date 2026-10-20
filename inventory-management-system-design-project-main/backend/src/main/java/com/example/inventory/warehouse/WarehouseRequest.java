package com.example.inventory.warehouse;

import jakarta.validation.constraints.NotBlank;

public class WarehouseRequest {

    @NotBlank(message = "Warehouse name is required")
    private String name;

    @NotBlank(message = "Warehouse location is required")
    private String location;

    private WarehouseStatus status = WarehouseStatus.ACTIVE;

    public WarehouseRequest() {
    }

    public WarehouseRequest(String name, String location, WarehouseStatus status) {
        this.name = name;
        this.location = location;
        this.status = status != null ? status : WarehouseStatus.ACTIVE;
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
}
