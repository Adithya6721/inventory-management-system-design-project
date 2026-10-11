package com.example.inventory.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class LowStockController {

    private final LowStockService lowStockService;

    public LowStockController(LowStockService lowStockService) {
        this.lowStockService = lowStockService;
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<LowStockAlert>> getLowStockAlerts(
            @RequestParam(required = false) String warehouseId) {
        if (warehouseId != null && !warehouseId.isBlank()) {
            return ResponseEntity.ok(lowStockService.getActiveAlertsByWarehouse(warehouseId.trim()));
        }
        return ResponseEntity.ok(lowStockService.getActiveAlerts());
    }
}
