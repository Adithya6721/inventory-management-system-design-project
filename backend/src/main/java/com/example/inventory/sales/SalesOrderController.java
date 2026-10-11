package com.example.inventory.sales;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales-orders")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @PostMapping
    public ResponseEntity<SalesOrderResponse> placeOrder(@Valid @RequestBody SalesOrderRequest request) {
        SalesOrderResponse response = salesOrderService.placeOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<SalesOrderResponse> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(salesOrderService.getOrder(orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<SalesOrderResponse> cancelOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(salesOrderService.cancelOrder(orderId));
    }

    @PostMapping("/{orderId}/complete")
    public ResponseEntity<SalesOrderResponse> completeOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(salesOrderService.completeOrder(orderId));
    }
}
