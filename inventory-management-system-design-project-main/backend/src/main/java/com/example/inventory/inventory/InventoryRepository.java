package com.example.inventory.inventory;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends MongoRepository<InventoryRecord, String> {

    Optional<InventoryRecord> findByProductIdAndWarehouseId(String productId, String warehouseId);

    List<InventoryRecord> findByProductId(String productId);

    List<InventoryRecord> findByWarehouseId(String warehouseId);

    boolean existsByWarehouseId(String warehouseId);
}
