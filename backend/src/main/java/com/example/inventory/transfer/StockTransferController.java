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

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<StockTransferResponse>> list(
            @org.springframework.data.web.PageableDefault(size = 100, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(stockTransferService.list(pageable));
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
