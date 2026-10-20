package com.example.inventory.purchase;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends MongoRepository<PurchaseOrder, String> {

    Optional<PurchaseOrder> findByOrderId(String orderId);

    Optional<PurchaseOrder> findByIdempotencyKey(String idempotencyKey);
}
