package com.example.inventory.purchase;

import com.example.inventory.common.BadRequestException;
import com.example.inventory.common.ConflictException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.IdGeneratorPort;
import com.example.inventory.inventory.InventoryService;
import com.example.inventory.inventory.MovementType;
import com.example.inventory.product.ProductService;
import com.example.inventory.warehouse.WarehouseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.inventory.common.RequestFingerprint;
import java.util.TreeMap;
import java.util.HashSet;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InventoryService inventoryService;
    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final IdGeneratorPort idGeneratorPort;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                InventoryService inventoryService,
                                ProductService productService,
                                WarehouseService warehouseService,
                                IdGeneratorPort idGeneratorPort) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.inventoryService = inventoryService;
        this.productService = productService;
        this.warehouseService = warehouseService;
        this.idGeneratorPort = idGeneratorPort;
    }

    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request) {
        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<PurchaseOrder> existing = purchaseOrderRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                PurchaseOrder prior = existing.get();
                RequestFingerprint.requireSame(
                        RequestFingerprint.of(prior.getSupplierId(), prior.getDestinationWarehouseId(),
                                RequestFingerprint.lines(prior.getItems(), PurchaseOrderItem::getProductId,
                                        i -> "", PurchaseOrderItem::getQuantity)),
                        RequestFingerprint.of(request.getSupplierId(), request.getDestinationWarehouseId(),
                                RequestFingerprint.lines(request.getItems(), PurchaseOrderItemRequest::getProductId,
                                        i -> "", PurchaseOrderItemRequest::getQuantity)));
                return PurchaseOrderResponse.fromDomain(existing.get());
            }
        }

        // Validate destination warehouse exists and is active
        warehouseService.validateWarehouseActive(request.getDestinationWarehouseId());

        // Validate products
        List<PurchaseOrderItem> items = new ArrayList<>();
        var seenProducts = new HashSet<String>();
        for (PurchaseOrderItemRequest itemReq : request.getItems()) {
            if (!seenProducts.add(itemReq.getProductId())) {
                throw new BadRequestException("Purchase order must contain one line per product.");
            }
            productService.getProductEntity(itemReq.getProductId());
            if (itemReq.getQuantity() <= 0) {
                throw new BadRequestException("Quantity for product " + itemReq.getProductId() + " must be > 0");
            }
            items.add(new PurchaseOrderItem(itemReq.getProductId(), itemReq.getQuantity(), 0));
        }

        String orderId = idGeneratorPort.generateId("po");
        PurchaseOrder order = new PurchaseOrder(
                orderId,
                request.getSupplierId(),
                request.getDestinationWarehouseId(),
                items,
                PurchaseOrderStatus.CREATED,
                RequestFingerprint.key(request.getIdempotencyKey())
        );

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return PurchaseOrderResponse.fromDomain(saved);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PurchaseOrderResponse> list(org.springframework.data.domain.Pageable pageable) {
        return purchaseOrderRepository.findAll(pageable).map(PurchaseOrderResponse::fromDomain);
    }

    public PurchaseOrderResponse getPurchaseOrder(String orderId) {
        return PurchaseOrderResponse.fromDomain(getPurchaseOrderEntity(orderId));
    }

    public PurchaseOrder getPurchaseOrderEntity(String orderId) {
        return purchaseOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Purchase order not found with ID: " + orderId));
    }

    public PurchaseOrderResponse receiveStock(String orderId, PurchaseReceiptRequest request) {
        PurchaseOrder order = getPurchaseOrderEntity(orderId);

        String receiptKey = RequestFingerprint.key(request == null ? null : request.getIdempotencyKey());
        Map<String, Long> received = new TreeMap<>();
        if (request != null && request.getItems() != null) {
            for (PurchaseReceiptItemRequest item : request.getItems()) {
                if (item == null || item.getProductId() == null || item.getProductId().isBlank()
                        || item.getQuantityReceived() <= 0) {
                    throw new BadRequestException("Receipt items require a product and a positive quantity.");
                }
                try {
                    received.merge(item.getProductId(), item.getQuantityReceived(), Math::addExact);
                } catch (ArithmeticException ex) {
                    throw new BadRequestException("Receipt quantity exceeds supported range.");
                }
            }
        }
        // A partial receipt needs a stable identity to distinguish retries from a new delivery.
        if (!received.isEmpty() && receiptKey == null) {
            throw new BadRequestException("Partial/itemised receipts require an idempotencyKey.");
        }
        String fingerprint = RequestFingerprint.of(received.isEmpty() ? "ALL_REMAINING" : received);
        String keyHash = RequestFingerprint.hash(receiptKey == null ? "implicit-all-remaining" : "explicit:" + receiptKey);
        String priorReceipt = order.getReceiptFingerprints().get(keyHash);
        if (priorReceipt != null) {
            RequestFingerprint.requireSame(priorReceipt, fingerprint);
            return PurchaseOrderResponse.fromDomain(order);
        }

        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new ConflictException("Cannot receive stock for CANCELLED purchase order: " + orderId);
        }
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new ConflictException("Purchase order " + orderId + " has already been fully received.");
        }

        String destinationWarehouseId = order.getDestinationWarehouseId();
        warehouseService.validateWarehouseActive(destinationWarehouseId);

        Map<String, PurchaseOrderItem> itemMap = order.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getProductId, i -> i));

        if (!received.isEmpty()) {
            // Validate first so no partial mutations happen if validation fails
            for (var entry : received.entrySet()) {
                PurchaseOrderItem orderItem = itemMap.get(entry.getKey());
                if (orderItem == null) {
                    throw new BadRequestException("Product " + entry.getKey() + " is not in purchase order " + orderId);
                }
                long remaining = orderItem.getQuantity() - orderItem.getReceivedQuantity();
                if (entry.getValue() > remaining) {
                    throw new ConflictException("Received quantity exceeds remaining ordered quantity for " + entry.getKey());
                }
            }

            // Apply stock credits and update order items
            for (var entry : received.entrySet()) {
                inventoryService.creditStock(
                        entry.getKey(),
                        destinationWarehouseId,
                        entry.getValue(),
                        MovementType.PURCHASE_RECEIPT,
                        orderId
                );
                PurchaseOrderItem orderItem = itemMap.get(entry.getKey());
                orderItem.setReceivedQuantity(orderItem.getReceivedQuantity() + entry.getValue());
            }
        } else {
            // Receive all remaining items
            for (PurchaseOrderItem item : order.getItems()) {
                long remaining = item.getQuantity() - item.getReceivedQuantity();
                if (remaining > 0) {
                    inventoryService.creditStock(
                            item.getProductId(),
                            destinationWarehouseId,
                            remaining,
                            MovementType.PURCHASE_RECEIPT,
                            orderId
                    );
                    item.setReceivedQuantity(item.getQuantity());
                }
            }
        }

        boolean allComplete = order.getItems().stream().allMatch(i -> i.getReceivedQuantity() >= i.getQuantity());
        order.setStatus(allComplete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        order.setUpdatedAt(Instant.now());
        order.getReceiptFingerprints().put(keyHash, fingerprint);

        PurchaseOrder updated = purchaseOrderRepository.save(order);
        return PurchaseOrderResponse.fromDomain(updated);
    }
}
