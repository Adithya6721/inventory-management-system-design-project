package com.example.inventory.notification;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LowStockAlertRepository extends MongoRepository<LowStockAlert, String> {

    Optional<LowStockAlert> findByProductIdAndWarehouseIdAndStatus(String productId, String warehouseId, AlertStatus status);

    List<LowStockAlert> findByStatus(AlertStatus status);

    List<LowStockAlert> findByWarehouseIdAndStatus(String warehouseId, AlertStatus status);
}
