package com.example.inventory.product;

import com.example.inventory.common.ConflictException;
import com.example.inventory.common.NotFoundException;
import com.example.inventory.integration.IdGeneratorPort;
import com.example.inventory.integration.InventoryCachePort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final IdGeneratorPort idGeneratorPort;
    private final InventoryCachePort inventoryCachePort;

    public ProductService(ProductRepository productRepository,
                          IdGeneratorPort idGeneratorPort,
                          InventoryCachePort inventoryCachePort) {
        this.productRepository = productRepository;
        this.idGeneratorPort = idGeneratorPort;
        this.inventoryCachePort = inventoryCachePort;
    }

    public ProductResponse registerProduct(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new ConflictException("Product with SKU '" + request.getSku() + "' already exists.");
        }

        String productId = idGeneratorPort.generateId("prod");
        Product product = new Product(
                productId,
                request.getSku().trim().toUpperCase(),
                request.getName().trim(),
                request.getDescription(),
                request.getCategory() != null ? request.getCategory().trim() : null,
                request.getPrice(),
                request.getReorderLevel(),
                request.getActive() != null ? request.getActive() : true
        );

        Product saved = productRepository.save(product);
        inventoryCachePort.evictProduct(productId);
        return ProductResponse.fromDomain(saved);
    }

    public ProductResponse getProduct(String productId) {
        return inventoryCachePort.getCachedProduct(productId)
                .orElseGet(() -> {
                    ProductResponse response = ProductResponse.fromDomain(getProductEntity(productId));
                    inventoryCachePort.cacheProduct(productId, response);
                    return response;
                });
    }

    public Product getProductEntity(String productId) {
        return productRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("Product not found with ID: " + productId));
    }

    public ProductResponse updateProduct(String productId, ProductRequest request) {
        Product product = getProductEntity(productId);

        String trimmedSku = request.getSku().trim().toUpperCase();
        if (!product.getSku().equalsIgnoreCase(trimmedSku)) {
            Optional<Product> existing = productRepository.findBySku(trimmedSku);
            if (existing.isPresent() && !existing.get().getProductId().equals(productId)) {
                throw new ConflictException("Another product already exists with SKU '" + trimmedSku + "'.");
            }
            product.setSku(trimmedSku);
        }

        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory() != null ? request.getCategory().trim() : null);
        product.setPrice(request.getPrice());
        product.setReorderLevel(request.getReorderLevel());
        if (request.getActive() != null) {
            product.setActive(request.getActive());
        }
        product.setUpdatedAt(Instant.now());

        Product saved = productRepository.save(product);
        inventoryCachePort.evictProduct(productId);
        return ProductResponse.fromDomain(saved);
    }

    public void deleteProduct(String productId) {
        Product product = getProductEntity(productId);
        product.setActive(false);
        product.setUpdatedAt(Instant.now());
        productRepository.save(product);
        inventoryCachePort.evictProduct(productId);
    }

    public Page<ProductResponse> searchProducts(String query, String category, Pageable pageable) {
        Page<Product> products;
        boolean hasQuery = query != null && !query.trim().isEmpty();
        boolean hasCategory = category != null && !category.trim().isEmpty();

        if (hasQuery && hasCategory) {
            products = productRepository.findByCategoryIgnoreCaseAndNameContainingIgnoreCase(
                    category.trim(), query.trim(), pageable);
        } else if (hasCategory) {
            products = productRepository.findByCategoryIgnoreCase(category.trim(), pageable);
        } else if (hasQuery) {
            products = productRepository.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(
                    query.trim(), query.trim(), pageable);
        } else {
            products = productRepository.findAll(pageable);
        }

        return products.map(ProductResponse::fromDomain);
    }
}
