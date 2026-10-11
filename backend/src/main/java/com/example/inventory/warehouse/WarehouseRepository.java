package com.example.inventory.warehouse;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WarehouseRepository extends MongoRepository<Warehouse, String> {

    Optional<Warehouse> findByWarehouseId(String warehouseId);

    boolean existsByWarehouseId(String warehouseId);
}
