package com.example.inventory.warehouse;

import com.example.inventory.inventory.InventoryResponse;
import com.example.inventory.inventory.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final InventoryService inventoryService;

    public WarehouseController(WarehouseService warehouseService,
                               InventoryService inventoryService) {
        this.warehouseService = warehouseService;
        this.inventoryService = inventoryService;
    }

    @PostMapping
    public ResponseEntity<WarehouseResponse> createWarehouse(@Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(warehouseService.createWarehouse(request));
    }

    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> listWarehouses() {
        return ResponseEntity.ok(warehouseService.listWarehouses());
    }

    @GetMapping("/{warehouseId}")
    public ResponseEntity<WarehouseResponse> getWarehouse(@PathVariable String warehouseId) {
        return ResponseEntity.ok(warehouseService.getWarehouse(warehouseId));
    }

    @PutMapping("/{warehouseId}")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @PathVariable String warehouseId,
            @Valid @RequestBody WarehouseRequest request) {
        return ResponseEntity.ok(warehouseService.updateWarehouse(warehouseId, request));
    }

    @GetMapping("/{warehouseId}/inventory")
    public ResponseEntity<List<InventoryResponse>> getWarehouseInventory(@PathVariable String warehouseId) {
        return ResponseEntity.ok(inventoryService.getInventoryByWarehouse(warehouseId));
    }
}
