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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
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
                return PurchaseOrderResponse.fromDomain(existing.get());
            }
        }

        // Validate destination warehouse exists and is active
        warehouseService.validateWarehouseActive(request.getDestinationWarehouseId());

        // Validate products
        List<PurchaseOrderItem> items = new ArrayList<>();
        for (PurchaseOrderItemRequest itemReq : request.getItems()) {
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
                request.getIdempotencyKey() != null ? request.getIdempotencyKey().trim() : null
        );

        PurchaseOrder saved = purchaseOrderRepository.save(order);
        return PurchaseOrderResponse.fromDomain(saved);
    }

    public PurchaseOrderResponse getPurchaseOrder(String orderId) {
        return PurchaseOrderResponse.fromDomain(getPurchaseOrderEntity(orderId));
    }

    public PurchaseOrder getPurchaseOrderEntity(String orderId) {
        return purchaseOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Purchase order not found with ID: " + orderId));
    }

    public synchronized PurchaseOrderResponse receiveStock(String orderId, PurchaseReceiptRequest request) {
        PurchaseOrder order = getPurchaseOrderEntity(orderId);

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

        if (request != null && request.getItems() != null && !request.getItems().isEmpty()) {
            // Validate first so no partial mutations happen if validation fails
            for (PurchaseReceiptItemRequest itemReq : request.getItems()) {
                PurchaseOrderItem orderItem = itemMap.get(itemReq.getProductId());
                if (orderItem == null) {
                    throw new BadRequestException("Product " + itemReq.getProductId() + " is not in purchase order " + orderId);
                }
                long remaining = orderItem.getQuantity() - orderItem.getReceivedQuantity();
                if (itemReq.getQuantityReceived() > remaining) {
                    throw new ConflictException("Received quantity " + itemReq.getQuantityReceived() +
                            " exceeds remaining ordered quantity " + remaining + " for product " + itemReq.getProductId());
                }
            }

            // Apply stock credits and update order items
            for (PurchaseReceiptItemRequest itemReq : request.getItems()) {
                inventoryService.creditStock(
                        itemReq.getProductId(),
                        destinationWarehouseId,
                        itemReq.getQuantityReceived(),
                        MovementType.PURCHASE_RECEIPT,
                        orderId
                );
                PurchaseOrderItem orderItem = itemMap.get(itemReq.getProductId());
                orderItem.setReceivedQuantity(orderItem.getReceivedQuantity() + itemReq.getQuantityReceived());
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

        PurchaseOrder updated = purchaseOrderRepository.save(order);
        return PurchaseOrderResponse.fromDomain(updated);
    }
}
