package com.example.inventory.transfer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockTransferRepository extends MongoRepository<StockTransfer, String> {

    Optional<StockTransfer> findByTransferId(String transferId);

    Optional<StockTransfer> findByIdempotencyKey(String idempotencyKey);
}
