package com.example.inventory.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class StockTransferRequest {

    @NotBlank(message = "Source warehouse ID is required")
    private String sourceWarehouseId;

    @NotBlank(message = "Destination warehouse ID is required")
    private String destinationWarehouseId;

    @NotEmpty(message = "Transfer must contain at least one item")
    @Valid
    private List<TransferItemRequest> items;

    private String idempotencyKey;

    public StockTransferRequest() {
    }

    public StockTransferRequest(String sourceWarehouseId, String destinationWarehouseId,
                                List<TransferItemRequest> items, String idempotencyKey) {
        this.sourceWarehouseId = sourceWarehouseId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.items = items;
        this.idempotencyKey = idempotencyKey;
    }

    public String getSourceWarehouseId() {
        return sourceWarehouseId;
    }

    public void setSourceWarehouseId(String sourceWarehouseId) {
        this.sourceWarehouseId = sourceWarehouseId;
    }

    public String getDestinationWarehouseId() {
        return destinationWarehouseId;
    }

    public void setDestinationWarehouseId(String destinationWarehouseId) {
        this.destinationWarehouseId = destinationWarehouseId;
    }

    public List<TransferItemRequest> getItems() {
        return items;
    }

    public void setItems(List<TransferItemRequest> items) {
        this.items = items;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
