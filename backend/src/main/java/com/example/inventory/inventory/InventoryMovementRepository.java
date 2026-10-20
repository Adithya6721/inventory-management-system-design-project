package com.example.inventory.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryMovementRepository extends MongoRepository<InventoryMovement, String> {

    List<InventoryMovement> findByProductIdOrderByTimestampDesc(String productId);

    Page<InventoryMovement> findByProductIdOrderByTimestampDesc(String productId, Pageable pageable);

    List<InventoryMovement> findByProductIdAndWarehouseIdOrderByTimestampDesc(String productId, String warehouseId);

    List<InventoryMovement> findByReferenceId(String referenceId);
}
