package com.example.inventory.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findByProductId(String productId);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Product> findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(String name, String sku, Pageable pageable);

    Page<Product> findByCategoryIgnoreCase(String category, Pageable pageable);

    Page<Product> findByCategoryIgnoreCaseAndNameContainingIgnoreCase(String category, String name, Pageable pageable);
}
