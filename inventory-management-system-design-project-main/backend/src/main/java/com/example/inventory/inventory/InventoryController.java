package com.example.inventory.inventory;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<List<InventoryResponse>> getStockAcrossWarehouses(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getInventoryByProduct(productId));
    }

    @GetMapping("/{productId}/{warehouseId}")
    public ResponseEntity<InventoryResponse> getStockAtWarehouse(
            @PathVariable String productId,
            @PathVariable String warehouseId) {
        return ResponseEntity.ok(inventoryService.getInventory(productId, warehouseId));
    }

    @PostMapping("/adjustments")
    public ResponseEntity<InventoryResponse> adjustStock(@Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(inventoryService.adjustStock(request));
    }

    @GetMapping("/{productId}/movements")
    public ResponseEntity<List<InventoryMovement>> getInventoryMovements(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getMovements(productId));
    }
}
