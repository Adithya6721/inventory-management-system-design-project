package com.example.inventory.purchase;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<PurchaseOrderResponse>> list(
            @org.springframework.data.web.PageableDefault(size = 100, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(purchaseOrderService.list(pageable));
    }

    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> createPurchaseOrder(@Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseOrderService.createPurchaseOrder(request));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<PurchaseOrderResponse> getPurchaseOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(purchaseOrderService.getPurchaseOrder(orderId));
    }

    @PostMapping("/{orderId}/receive")
    public ResponseEntity<PurchaseOrderResponse> receiveStock(
            @PathVariable String orderId,
            @Valid @RequestBody(required = false) PurchaseReceiptRequest request) {
        return ResponseEntity.ok(purchaseOrderService.receiveStock(orderId, request));
    }
}
