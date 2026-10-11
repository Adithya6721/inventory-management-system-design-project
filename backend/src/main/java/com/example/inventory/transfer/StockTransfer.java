package com.example.inventory.transfer;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "transfers")
public class StockTransfer {

    @Id
    private String id;

    @Indexed(unique = true)
    private String transferId;

    @Indexed
    private String sourceWarehouseId;

    @Indexed
    private String destinationWarehouseId;

    private List<TransferItem> items = new ArrayList<>();

    @Indexed
    private TransferStatus status = TransferStatus.INITIATED;

    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public StockTransfer() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public StockTransfer(String transferId, String sourceWarehouseId, String destinationWarehouseId,
                         List<TransferItem> items, TransferStatus status, String idempotencyKey) {
        this.transferId = transferId;
        this.sourceWarehouseId = sourceWarehouseId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.items = items != null ? items : new ArrayList<>();
        this.status = status != null ? status : TransferStatus.INITIATED;
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
