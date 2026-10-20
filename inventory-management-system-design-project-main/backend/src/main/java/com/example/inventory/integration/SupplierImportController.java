package com.example.inventory.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierImportController {

    private final SupplierImportService supplierImportService;

    public SupplierImportController(SupplierImportService supplierImportService) {
        this.supplierImportService = supplierImportService;
    }

    @PostMapping("/import")
    public ResponseEntity<SupplierImportPort.SupplierImportResponse> importSupplierFeed(
            @RequestBody SupplierImportPort.SupplierImportRequest request) {
        return ResponseEntity.ok(supplierImportService.processSupplierFeed(request));
    }
}
