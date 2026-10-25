package com.example.inventory.transfer;

import com.example.inventory.common.BadRequestException;
import com.example.inventory.common.ConflictException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.IdGeneratorPort;
import com.example.inventory.inventory.InventoryService;
import com.example.inventory.inventory.MovementType;
import com.example.inventory.product.ProductService;
import com.example.inventory.warehouse.WarehouseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.inventory.common.RequestFingerprint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StockTransferService {

    private static final Logger log = LoggerFactory.getLogger(StockTransferService.class);

    private final StockTransferRepository stockTransferRepository;
    private final InventoryService inventoryService;
    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final IdGeneratorPort idGeneratorPort;

    public StockTransferService(StockTransferRepository stockTransferRepository,
                                InventoryService inventoryService,
                                ProductService productService,
                                WarehouseService warehouseService,
                                IdGeneratorPort idGeneratorPort) {
        this.stockTransferRepository = stockTransferRepository;
        this.inventoryService = inventoryService;
        this.productService = productService;
        this.warehouseService = warehouseService;
        this.idGeneratorPort = idGeneratorPort;
    }

    public StockTransferResponse initiateTransfer(StockTransferRequest request) {
        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<StockTransfer> existing = stockTransferRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                StockTransfer prior = existing.get();
                RequestFingerprint.requireSame(
                        RequestFingerprint.of(prior.getSourceWarehouseId(), prior.getDestinationWarehouseId(),
                                RequestFingerprint.lines(prior.getItems(), TransferItem::getProductId,
                                        i -> "", TransferItem::getQuantity)),
                        RequestFingerprint.of(request.getSourceWarehouseId(), request.getDestinationWarehouseId(),
                                RequestFingerprint.lines(request.getItems(), TransferItemRequest::getProductId,
                                        i -> "", TransferItemRequest::getQuantity)));
                log.info("Returning existing transfer for idempotencyKey: {}", request.getIdempotencyKey());
                return StockTransferResponse.fromDomain(existing.get());
            }
        }

        // Validate distinct warehouses
        if (request.getSourceWarehouseId().equalsIgnoreCase(request.getDestinationWarehouseId())) {
            throw new BadRequestException("Source warehouse and destination warehouse cannot be the same.");
        }

        // Validate both warehouses are active
        warehouseService.validateWarehouseActive(request.getSourceWarehouseId());
        warehouseService.validateWarehouseActive(request.getDestinationWarehouseId());

        // Validate products
        for (TransferItemRequest itemReq : request.getItems()) {
            productService.getProductEntity(itemReq.getProductId());
            if (itemReq.getQuantity() <= 0) {
                throw new BadRequestException("Transfer quantity must be > 0 for product: " + itemReq.getProductId());
            }
        }

        String transferId = idGeneratorPort.generateId("trn");
        List<TransferItem> deductedItems = new ArrayList<>();

        // Deduct from source warehouse, rolling back if any product has insufficient stock
        for (TransferItemRequest itemReq : request.getItems()) {
            boolean deducted = inventoryService.deductAvailableStockDirect(
                    itemReq.getProductId(),
                    request.getSourceWarehouseId(),
                    itemReq.getQuantity(),
                    MovementType.TRANSFER_OUT,
                    transferId
            );

            if (!deducted) {
                log.warn("Transfer failed: Insufficient stock for product {} at source warehouse {}. Rolling back {} items.",
                        itemReq.getProductId(), request.getSourceWarehouseId(), deductedItems.size());
                for (TransferItem prior : deductedItems) {
                    try {
                        inventoryService.creditStock(
                                prior.getProductId(),
                                request.getSourceWarehouseId(),
                                prior.getQuantity(),
                                MovementType.ADJUSTMENT,
                                transferId
                        );
                    } catch (Exception ex) {
                        log.error("Failed to restore stock during transfer compensation for product {}", prior.getProductId(), ex);
                    }
                }

                throw new ConflictException("Insufficient available stock in source warehouse '"
                        + request.getSourceWarehouseId() + "' for product '" + itemReq.getProductId() + "'");
            }

            deductedItems.add(new TransferItem(itemReq.getProductId(), itemReq.getQuantity()));
        }

        StockTransfer transfer = new StockTransfer(
                transferId,
                request.getSourceWarehouseId(),
                request.getDestinationWarehouseId(),
                deductedItems,
                TransferStatus.IN_TRANSIT,
                RequestFingerprint.key(request.getIdempotencyKey())
        );

        StockTransfer saved = stockTransferRepository.save(transfer);
        return StockTransferResponse.fromDomain(saved);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<StockTransferResponse> list(org.springframework.data.domain.Pageable pageable) {
        return stockTransferRepository.findAll(pageable).map(StockTransferResponse::fromDomain);
    }

    public StockTransferResponse getTransfer(String transferId) {
        return StockTransferResponse.fromDomain(getTransferEntity(transferId));
    }

    public StockTransfer getTransferEntity(String transferId) {
        return stockTransferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new NotFoundException("Stock transfer not found with ID: " + transferId));
    }

    public StockTransferResponse completeTransfer(String transferId) {
        StockTransfer transfer = getTransferEntity(transferId);

        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            log.info("Transfer {} is already COMPLETED. Returning idempotently.", transferId);
            return StockTransferResponse.fromDomain(transfer);
        }

        if (transfer.getStatus() != TransferStatus.IN_TRANSIT) {
            throw new ConflictException("Cannot complete stock transfer from status: " + transfer.getStatus());
        }

        // Credit destination warehouse
        for (TransferItem item : transfer.getItems()) {
            inventoryService.creditStock(
                    item.getProductId(),
                    transfer.getDestinationWarehouseId(),
                    item.getQuantity(),
                    MovementType.TRANSFER_IN,
                    transferId
            );
        }

        transfer.setStatus(TransferStatus.COMPLETED);
        transfer.setUpdatedAt(Instant.now());

        StockTransfer saved = stockTransferRepository.save(transfer);
        return StockTransferResponse.fromDomain(saved);
    }

    public StockTransferResponse cancelTransfer(String transferId) {
        StockTransfer transfer = getTransferEntity(transferId);

        if (transfer.getStatus() == TransferStatus.CANCELLED) {
            return StockTransferResponse.fromDomain(transfer);
        }

        if (transfer.getStatus() == TransferStatus.COMPLETED) {
            throw new ConflictException("Cannot cancel an already COMPLETED transfer: " + transferId);
        }

        if (transfer.getStatus() != TransferStatus.IN_TRANSIT) {
            throw new ConflictException("Cannot restore stock for a transfer that is not IN_TRANSIT.");
        }

        // Restore stock to source warehouse
        for (TransferItem item : transfer.getItems()) {
            inventoryService.creditStock(
                    item.getProductId(),
                    transfer.getSourceWarehouseId(),
                    item.getQuantity(),
                    MovementType.ADJUSTMENT,
                    transferId
            );
        }

        transfer.setStatus(TransferStatus.CANCELLED);
        transfer.setUpdatedAt(Instant.now());

        StockTransfer saved = stockTransferRepository.save(transfer);
        return StockTransferResponse.fromDomain(saved);
    }
}
