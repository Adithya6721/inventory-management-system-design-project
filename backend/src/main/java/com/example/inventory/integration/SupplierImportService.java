package com.example.inventory.integration;

import com.example.inventory.common.BadRequestException;
import com.example.inventory.product.Product;
import com.example.inventory.product.ProductRepository;
import com.example.inventory.product.ProductRequest;
import com.example.inventory.product.ProductService;
import com.example.inventory.purchase.PurchaseOrderItemRequest;
import com.example.inventory.purchase.PurchaseOrderRequest;
import com.example.inventory.purchase.PurchaseOrderResponse;
import com.example.inventory.purchase.PurchaseOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SupplierImportService implements SupplierImportPort {

    private static final Logger log = LoggerFactory.getLogger(SupplierImportService.class);

    private final ProductRepository productRepository;
    private final ProductService productService;
    private final PurchaseOrderService purchaseOrderService;
    private final IdGeneratorPort idGeneratorPort;

    public SupplierImportService(ProductRepository productRepository,
                                 ProductService productService,
                                 PurchaseOrderService purchaseOrderService,
                                 IdGeneratorPort idGeneratorPort) {
        this.productRepository = productRepository;
        this.productService = productService;
        this.purchaseOrderService = purchaseOrderService;
        this.idGeneratorPort = idGeneratorPort;
    }

    @Override
    public SupplierImportResponse processSupplierFeed(SupplierImportRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestException("Supplier import request must contain at least one item.");
        }

        String importBatchId = idGeneratorPort.generateId("batch");
        log.info("Processing supplier import batch {} from supplier {}", importBatchId, request.supplierId());

        int processed = 0;
        int catalogUpdated = 0;
        List<PurchaseOrderItemRequest> poItems = new ArrayList<>();

        for (SupplierImportItem item : request.items()) {
            processed++;
            String sku = item.sku().trim().toUpperCase();
            Optional<Product> existingOpt = productRepository.findBySku(sku);

            String resolvedProductId;
            if (existingOpt.isPresent()) {
                Product product = existingOpt.get();
                resolvedProductId = product.getProductId();
                if (item.category() != null && !item.category().isBlank()) {
                    // Use the normal update path so cached product details are evicted.
                    productService.updateProduct(resolvedProductId, new ProductRequest(
                            product.getSku(), product.getName(), product.getDescription(),
                            item.category().trim(), product.getPrice(), product.getReorderLevel(),
                            product.isActive()));
                    catalogUpdated++;
                }
            } else {
                // Register newly discovered product
                ProductRequest createReq = new ProductRequest(
                        sku,
                        item.name() != null ? item.name() : "Supplier Item " + sku,
                        "Discovered via supplier crawler from " + request.supplierName(),
                        item.category(),
                        item.supplierPrice() != null ? item.supplierPrice() : java.math.BigDecimal.ZERO,
                        10,
                        true
                );
                var created = productService.registerProduct(createReq);
                resolvedProductId = created.getProductId();
                catalogUpdated++;
            }

            if (request.autoCreatePurchaseOrder() && item.supplierReportedStock() > 0) {
                poItems.add(new PurchaseOrderItemRequest(resolvedProductId, item.supplierReportedStock()));
            }
        }

        String proposedPoId = null;
        if (request.autoCreatePurchaseOrder() && !poItems.isEmpty() && request.targetWarehouseId() != null) {
            PurchaseOrderRequest poReq = new PurchaseOrderRequest(
                    request.supplierId(),
                    request.targetWarehouseId(),
                    poItems,
                    "IMPORT_" + importBatchId
            );
            PurchaseOrderResponse poResp = purchaseOrderService.createPurchaseOrder(poReq);
            proposedPoId = poResp.getOrderId();
            log.info("Proposed purchase order {} created for supplier import batch {}", proposedPoId, importBatchId);
        }

        return new SupplierImportResponse(
                importBatchId,
                request.supplierId(),
                processed,
                catalogUpdated,
                proposedPoId,
                "SUCCESS",
                "Supplier feed processed. Actual physical stock remains untouched until purchase order receipt."
        );
    }
}
