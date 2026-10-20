package com.example.inventory.transfer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class StockTransferController {

    private final StockTransferService stockTransferService;

    public StockTransferController(StockTransferService stockTransferService) {
        this.stockTransferService = stockTransferService;
    }

    @PostMapping
    public ResponseEntity<StockTransferResponse> initiateTransfer(@Valid @RequestBody StockTransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockTransferService.initiateTransfer(request));
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<StockTransferResponse> getTransfer(@PathVariable String transferId) {
        return ResponseEntity.ok(stockTransferService.getTransfer(transferId));
    }

    @PostMapping("/{transferId}/complete")
    public ResponseEntity<StockTransferResponse> completeTransfer(@PathVariable String transferId) {
        return ResponseEntity.ok(stockTransferService.completeTransfer(transferId));
    }

    @PostMapping("/{transferId}/cancel")
    public ResponseEntity<StockTransferResponse> cancelTransfer(@PathVariable String transferId) {
        return ResponseEntity.ok(stockTransferService.cancelTransfer(transferId));
    }
}
