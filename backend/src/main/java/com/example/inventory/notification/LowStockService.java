package com.example.inventory.notification;

import com.example.inventory.integration.IdGeneratorPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class LowStockService {

    private static final Logger log = LoggerFactory.getLogger(LowStockService.class);

    private final LowStockAlertRepository lowStockAlertRepository;
    private final IdGeneratorPort idGeneratorPort;

    public LowStockService(LowStockAlertRepository lowStockAlertRepository,
                           IdGeneratorPort idGeneratorPort) {
        this.lowStockAlertRepository = lowStockAlertRepository;
        this.idGeneratorPort = idGeneratorPort;
    }

    public synchronized void evaluateStock(String productId, String warehouseId, long availableQuantity, int reorderLevel) {
        Optional<LowStockAlert> activeAlertOpt =
                lowStockAlertRepository.findByProductIdAndWarehouseIdAndStatus(productId, warehouseId, AlertStatus.ACTIVE);

        if (availableQuantity <= reorderLevel) {
            if (activeAlertOpt.isPresent()) {
                // Deduplicate: update current available quantity on existing active alert without creating a duplicate record
                LowStockAlert alert = activeAlertOpt.get();
                alert.setAvailableQuantity(availableQuantity);
                alert.setThreshold(reorderLevel);
                alert.setUpdatedAt(Instant.now());
                lowStockAlertRepository.save(alert);
                log.info("[LowStockAlert Updated] Product: {}, Warehouse: {}, Available: {}, Threshold: {}",
                        productId, warehouseId, availableQuantity, reorderLevel);
            } else {
                // Create new active alert
                String alertId = idGeneratorPort.generateId("alert");
                LowStockAlert newAlert = new LowStockAlert(
                        alertId,
                        productId,
                        warehouseId,
                        reorderLevel,
                        availableQuantity,
                        AlertStatus.ACTIVE
                );
                lowStockAlertRepository.save(newAlert);
                log.warn("[LowStockAlert Triggered] Product: {}, Warehouse: {}, Available: {}, Threshold: {}",
                        productId, warehouseId, availableQuantity, reorderLevel);
            }
        } else {
            // Stock is safely above threshold: resolve any existing active alert
            if (activeAlertOpt.isPresent()) {
                LowStockAlert alert = activeAlertOpt.get();
                alert.setStatus(AlertStatus.RESOLVED);
                alert.setAvailableQuantity(availableQuantity);
                alert.setUpdatedAt(Instant.now());
                lowStockAlertRepository.save(alert);
                log.info("[LowStockAlert Resolved] Product: {}, Warehouse: {}, Available: {}, Threshold: {}",
                        productId, warehouseId, availableQuantity, reorderLevel);
            }
        }
    }

    public List<LowStockAlert> getActiveAlerts() {
        return lowStockAlertRepository.findByStatus(AlertStatus.ACTIVE);
    }

    public List<LowStockAlert> getActiveAlertsByWarehouse(String warehouseId) {
        return lowStockAlertRepository.findByWarehouseIdAndStatus(warehouseId, AlertStatus.ACTIVE);
    }
}
