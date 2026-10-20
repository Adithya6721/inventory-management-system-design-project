package com.example.inventory.sales;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class SalesOrderRequest {

    @NotEmpty(message = "Sales order must contain at least one item")
    @Valid
    private List<SalesOrderItemRequest> items;

    private String idempotencyKey;

    public SalesOrderRequest() {
    }

    public SalesOrderRequest(List<SalesOrderItemRequest> items, String idempotencyKey) {
        this.items = items;
        this.idempotencyKey = idempotencyKey;
    }

    public List<SalesOrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<SalesOrderItemRequest> items) {
        this.items = items;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
