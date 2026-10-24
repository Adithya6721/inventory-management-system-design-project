package com.example.inventory.sales;

import com.example.inventory.common.BadRequestException;
import com.example.inventory.common.ConflictException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.IdGeneratorPort;
import com.example.inventory.inventory.InventoryService;
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
public class SalesOrderService {

    private static final Logger log = LoggerFactory.getLogger(SalesOrderService.class);

    private final SalesOrderRepository salesOrderRepository;
    private final InventoryService inventoryService;
    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final IdGeneratorPort idGeneratorPort;

    public SalesOrderService(SalesOrderRepository salesOrderRepository,
                             InventoryService inventoryService,
                             ProductService productService,
                             WarehouseService warehouseService,
                             IdGeneratorPort idGeneratorPort) {
        this.salesOrderRepository = salesOrderRepository;
        this.inventoryService = inventoryService;
        this.productService = productService;
        this.warehouseService = warehouseService;
        this.idGeneratorPort = idGeneratorPort;
    }

    public SalesOrderResponse placeOrder(SalesOrderRequest request) {
        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<SalesOrder> existing = salesOrderRepository.findByIdempotencyKey(request.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                RequestFingerprint.requireSame(
                        RequestFingerprint.of(RequestFingerprint.lines(existing.get().getItems(),
                                SalesOrderItem::getProductId, SalesOrderItem::getWarehouseId, SalesOrderItem::getQuantity)),
                        RequestFingerprint.of(RequestFingerprint.lines(request.getItems(),
                                SalesOrderItemRequest::getProductId, SalesOrderItemRequest::getWarehouseId, SalesOrderItemRequest::getQuantity)));
                log.info("Returning existing sales order for idempotencyKey: {}", request.getIdempotencyKey());
                return SalesOrderResponse.fromDomain(existing.get());
            }
        }

        // Validate all products and warehouses first
        for (SalesOrderItemRequest itemReq : request.getItems()) {
            productService.getProductEntity(itemReq.getProductId());
            warehouseService.validateWarehouseActive(itemReq.getWarehouseId());
            if (itemReq.getQuantity() <= 0) {
                throw new BadRequestException("Quantity for product " + itemReq.getProductId() + " must be > 0");
            }
        }

        String orderId = idGeneratorPort.generateId("so");
        List<SalesOrderItem> reservedItems = new ArrayList<>();

        // Atomic reservation with Saga compensation rollback if any item fails
        for (SalesOrderItemRequest itemReq : request.getItems()) {
            boolean reserved = inventoryService.reserveStock(
                    itemReq.getProductId(),
                    itemReq.getWarehouseId(),
                    itemReq.getQuantity(),
                    orderId
            );

            if (!reserved) {
                // Compensate previously reserved items in this transaction
                log.warn("Insufficient stock for product {} at warehouse {}. Rolling back {} reserved items.",
                        itemReq.getProductId(), itemReq.getWarehouseId(), reservedItems.size());
                for (SalesOrderItem prior : reservedItems) {
                    try {
                        inventoryService.releaseReservedStock(
                                prior.getProductId(),
                                prior.getWarehouseId(),
                                prior.getQuantity(),
                                orderId
                        );
                    } catch (Exception ex) {
                        log.error("Failed to release reservation during compensation for product {}", prior.getProductId(), ex);
                    }
                }

                throw new ConflictException("Insufficient available stock to reserve item: "
                        + itemReq.getProductId() + " at warehouse: " + itemReq.getWarehouseId()
                        + " (requested: " + itemReq.getQuantity() + ")");
            }

            reservedItems.add(new SalesOrderItem(itemReq.getProductId(), itemReq.getWarehouseId(), itemReq.getQuantity()));
        }

        SalesOrder order = new SalesOrder(
                orderId,
                reservedItems,
                SalesOrderStatus.RESERVED,
                RequestFingerprint.key(request.getIdempotencyKey())
        );

        SalesOrder saved = salesOrderRepository.save(order);
        return SalesOrderResponse.fromDomain(saved);
    }

    public SalesOrderResponse getOrder(String orderId) {
        return SalesOrderResponse.fromDomain(getOrderEntity(orderId));
    }

    public SalesOrder getOrderEntity(String orderId) {
        return salesOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Sales order not found with ID: " + orderId));
    }

    public SalesOrderResponse cancelOrder(String orderId) {
        SalesOrder order = getOrderEntity(orderId);

        if (order.getStatus() == SalesOrderStatus.CANCELLED) {
            return SalesOrderResponse.fromDomain(order);
        }

        if (order.getStatus() == SalesOrderStatus.COMPLETED) {
            throw new ConflictException("Cannot cancel an already COMPLETED sales order: " + orderId);
        }

        if (order.getStatus() == SalesOrderStatus.RESERVED) {
            for (SalesOrderItem item : order.getItems()) {
                boolean released = inventoryService.releaseReservedStock(
                        item.getProductId(),
                        item.getWarehouseId(),
                        item.getQuantity(),
                        orderId
                );
                if (!released) throw new ConflictException("Reservation release failed; order was not cancelled.");
            }
        }

        order.setStatus(SalesOrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());

        SalesOrder saved = salesOrderRepository.save(order);
        return SalesOrderResponse.fromDomain(saved);
    }

    public SalesOrderResponse completeOrder(String orderId) {
        SalesOrder order = getOrderEntity(orderId);

        if (order.getStatus() == SalesOrderStatus.COMPLETED) {
            return SalesOrderResponse.fromDomain(order);
        }

        if (order.getStatus() != SalesOrderStatus.RESERVED) {
            throw new ConflictException("Cannot complete sales order in status " + order.getStatus() + ". Expected RESERVED.");
        }

        for (SalesOrderItem item : order.getItems()) {
            boolean deducted = inventoryService.deductReservedStock(
                    item.getProductId(),
                    item.getWarehouseId(),
                    item.getQuantity(),
                    orderId
            );
            if (!deducted) throw new ConflictException("Reserved stock deduction failed; order was not completed.");
        }

        order.setStatus(SalesOrderStatus.COMPLETED);
        order.setUpdatedAt(Instant.now());

        SalesOrder saved = salesOrderRepository.save(order);
        return SalesOrderResponse.fromDomain(saved);
    }
}
