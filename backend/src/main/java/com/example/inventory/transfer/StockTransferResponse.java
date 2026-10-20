package com.example.inventory.transfer;

import java.time.Instant;
import java.util.List;

public class StockTransferResponse {

    private String transferId;
    private String sourceWarehouseId;
    private String destinationWarehouseId;
    private List<TransferItem> items;
    private TransferStatus status;
    private String idempotencyKey;
    private Instant createdAt;
    private Instant updatedAt;

    public StockTransferResponse() {
    }

    public StockTransferResponse(String transferId, String sourceWarehouseId, String destinationWarehouseId,
                                 List<TransferItem> items, TransferStatus status,
                                 String idempotencyKey, Instant createdAt, Instant updatedAt) {
        this.transferId = transferId;
        this.sourceWarehouseId = sourceWarehouseId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.items = items;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static StockTransferResponse fromDomain(StockTransfer transfer) {
        if (transfer == null) {
            return null;
        }
        return new StockTransferResponse(
                transfer.getTransferId(),
                transfer.getSourceWarehouseId(),
                transfer.getDestinationWarehouseId(),
                transfer.getItems(),
                transfer.getStatus(),
                transfer.getIdempotencyKey(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
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

    public List<TransferItem> getItems() {
        return items;
    }

    public void setItems(List<TransferItem> items) {
        this.items = items;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
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
