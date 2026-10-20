package com.example.inventory.integration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port for supplier crawlers and external data feeds.
 * Member 5 will supply crawler inputs into this interface.
 */
public interface SupplierImportPort {

    SupplierImportResponse processSupplierFeed(SupplierImportRequest request);

    record SupplierImportItem(
            String sku,
            String name,
            String category,
            BigDecimal supplierPrice,
            long supplierReportedStock,
            String externalUrl
    ) {}

    record SupplierImportRequest(
            String supplierId,
            String supplierName,
            String targetWarehouseId,
            boolean autoCreatePurchaseOrder,
            List<SupplierImportItem> items
    ) {}

    record SupplierImportResponse(
            String importBatchId,
            String supplierId,
            int productsProcessed,
            int productsCatalogUpdated,
            String proposedPurchaseOrderId,
            String status,
            String message
    ) {}
}
