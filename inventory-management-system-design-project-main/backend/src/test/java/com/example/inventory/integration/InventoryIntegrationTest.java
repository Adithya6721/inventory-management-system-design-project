package com.example.inventory.integration;

import com.example.inventory.inventory.InventoryResponse;
import com.example.inventory.notification.LowStockAlert;
import com.example.inventory.product.ProductRequest;
import com.example.inventory.product.ProductResponse;
import com.example.inventory.purchase.PurchaseOrderItemRequest;
import com.example.inventory.purchase.PurchaseOrderRequest;
import com.example.inventory.purchase.PurchaseOrderResponse;
import com.example.inventory.purchase.PurchaseReceiptRequest;
import com.example.inventory.sales.SalesOrderItemRequest;
import com.example.inventory.sales.SalesOrderRequest;
import com.example.inventory.sales.SalesOrderResponse;
import com.example.inventory.transfer.StockTransferRequest;
import com.example.inventory.transfer.StockTransferResponse;
import com.example.inventory.transfer.TransferItemRequest;
import com.example.inventory.warehouse.WarehouseRequest;
import com.example.inventory.warehouse.WarehouseResponse;
import com.example.inventory.warehouse.WarehouseStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api";
    }

    @Test
    @DisplayName("End-to-End Integration Flow: Product, Warehouse, PO Receipt, Sales Order, Alerts, Transfer")
    void testEndToEndInventoryFlow() {
        String testSuffix = String.valueOf(System.currentTimeMillis()).substring(7);

        // 1. Create two warehouses
        WarehouseRequest wh1Req = new WarehouseRequest("Central Hub " + testSuffix, "Chicago", WarehouseStatus.ACTIVE);
        ResponseEntity<WarehouseResponse> wh1Resp = restTemplate.postForEntity(
                getBaseUrl() + "/warehouses", wh1Req, WarehouseResponse.class);
        assertEquals(HttpStatus.CREATED, wh1Resp.getStatusCode());
        assertNotNull(wh1Resp.getBody());
        String wh1Id = wh1Resp.getBody().getWarehouseId();

        WarehouseRequest wh2Req = new WarehouseRequest("East Hub " + testSuffix, "Boston", WarehouseStatus.ACTIVE);
        ResponseEntity<WarehouseResponse> wh2Resp = restTemplate.postForEntity(
                getBaseUrl() + "/warehouses", wh2Req, WarehouseResponse.class);
        assertEquals(HttpStatus.CREATED, wh2Resp.getStatusCode());
        String wh2Id = wh2Resp.getBody().getWarehouseId();

        // 2. Register a product with reorderLevel = 10
        ProductRequest prodReq = new ProductRequest(
                "SKU-E2E-" + testSuffix,
                "Enterprise Server " + testSuffix,
                "High performance compute unit",
                "Hardware",
                new BigDecimal("2499.00"),
                10, // reorderLevel
                true
        );
        ResponseEntity<ProductResponse> prodResp = restTemplate.postForEntity(
                getBaseUrl() + "/products", prodReq, ProductResponse.class);
        assertEquals(HttpStatus.CREATED, prodResp.getStatusCode());
        assertNotNull(prodResp.getBody());
        String prodId = prodResp.getBody().getProductId();

        // 3. Purchase Order to receive 15 units into Central Hub
        PurchaseOrderRequest poReq = new PurchaseOrderRequest(
                "supp_dell",
                wh1Id,
                List.of(new PurchaseOrderItemRequest(prodId, 15)),
                "PO-KEY-" + testSuffix
        );
        ResponseEntity<PurchaseOrderResponse> poResp = restTemplate.postForEntity(
                getBaseUrl() + "/purchase-orders", poReq, PurchaseOrderResponse.class);
        assertEquals(HttpStatus.CREATED, poResp.getStatusCode());
        String orderId = poResp.getBody().getOrderId();

        // Receive the stock
        ResponseEntity<PurchaseOrderResponse> recResp = restTemplate.postForEntity(
                getBaseUrl() + "/purchase-orders/" + orderId + "/receive",
                new PurchaseReceiptRequest(null, "REC-KEY-" + testSuffix),
                PurchaseOrderResponse.class
        );
        assertEquals(HttpStatus.OK, recResp.getStatusCode());

        // Verify stock in Central Hub is now 15
        ResponseEntity<InventoryResponse> invResp1 = restTemplate.getForEntity(
                getBaseUrl() + "/inventory/" + prodId + "/" + wh1Id, InventoryResponse.class);
        assertEquals(HttpStatus.OK, invResp1.getStatusCode());
        assertNotNull(invResp1.getBody());
        assertEquals(15, invResp1.getBody().getAvailableQuantity());
        assertEquals(15, invResp1.getBody().getQuantity());

        // 4. Place a sales order for 6 units (Available: 15 - 6 = 9 <= reorderLevel 10)
        SalesOrderRequest soReq = new SalesOrderRequest(
                List.of(new SalesOrderItemRequest(prodId, wh1Id, 6)),
                "SO-KEY-" + testSuffix
        );
        ResponseEntity<SalesOrderResponse> soResp = restTemplate.postForEntity(
                getBaseUrl() + "/sales-orders", soReq, SalesOrderResponse.class);
        assertEquals(HttpStatus.CREATED, soResp.getStatusCode());
        assertNotNull(soResp.getBody());
        assertEquals(6, soResp.getBody().getItems().get(0).getQuantity());

        // Check low-stock alert triggered (9 <= 10)
        ResponseEntity<List<LowStockAlert>> alertResp = restTemplate.exchange(
                getBaseUrl() + "/alerts/low-stock?warehouseId=" + wh1Id,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, alertResp.getStatusCode());
        assertNotNull(alertResp.getBody());
        assertTrue(alertResp.getBody().stream().anyMatch(a -> a.getProductId().equals(prodId)));

        // 5. Transfer 4 units from Central Hub (wh1Id) to East Hub (wh2Id)
        StockTransferRequest trnReq = new StockTransferRequest(
                wh1Id,
                wh2Id,
                List.of(new TransferItemRequest(prodId, 4)),
                "TRN-KEY-" + testSuffix
        );
        ResponseEntity<StockTransferResponse> trnResp = restTemplate.postForEntity(
                getBaseUrl() + "/transfers", trnReq, StockTransferResponse.class);
        assertEquals(HttpStatus.CREATED, trnResp.getStatusCode());
        String transferId = trnResp.getBody().getTransferId();

        // Complete the transfer
        ResponseEntity<StockTransferResponse> compTrnResp = restTemplate.postForEntity(
                getBaseUrl() + "/transfers/" + transferId + "/complete",
                null,
                StockTransferResponse.class
        );
        assertEquals(HttpStatus.OK, compTrnResp.getStatusCode());

        // Verify East Hub now has 4 units
        ResponseEntity<InventoryResponse> wh2Inv = restTemplate.getForEntity(
                getBaseUrl() + "/inventory/" + prodId + "/" + wh2Id, InventoryResponse.class);
        assertEquals(HttpStatus.OK, wh2Inv.getStatusCode());
        assertEquals(4, wh2Inv.getBody().getAvailableQuantity());

        // 6. Complete sales order
        String soId = soResp.getBody().getOrderId();
        ResponseEntity<SalesOrderResponse> compSoResp = restTemplate.postForEntity(
                getBaseUrl() + "/sales-orders/" + soId + "/complete",
                null,
                SalesOrderResponse.class
        );
        assertEquals(HttpStatus.OK, compSoResp.getStatusCode());
    }
}
