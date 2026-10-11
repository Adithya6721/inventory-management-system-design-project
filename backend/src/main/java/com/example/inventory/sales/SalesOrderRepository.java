package com.example.inventory.sales;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalesOrderRepository extends MongoRepository<SalesOrder, String> {

    Optional<SalesOrder> findByOrderId(String orderId);

    Optional<SalesOrder> findByIdempotencyKey(String idempotencyKey);
}
