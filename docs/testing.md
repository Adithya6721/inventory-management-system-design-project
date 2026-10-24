# Testing & Verification Report — Member 1 (Core Inventory)

## Current automated evaluation

The timestamped correctness rerun is in
[`evaluation/results/20261024-125728-079-consistency/README.md`](../evaluation/results/20261024-125728-079-consistency/README.md):
85 backend tests passed without failures or skips. The reproducible JMeter
cache baseline plan and commands are in
[`evaluation/jmeter/README.md`](../evaluation/jmeter/README.md).
The older observations below are retained as historical context; unit/service
test timings are not HTTP load-test performance measurements.

## 1. Historical test suite summary

The backend contains **25 automated tests** covering unit, integration, and high-concurrency scenarios across all core business modules.

```
-------------------------------------------------------
 T E S T S
-------------------------------------------------------
Running com.example.inventory.product.ProductServiceTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
Running com.example.inventory.warehouse.WarehouseServiceTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Running com.example.inventory.notification.LowStockServiceTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Running com.example.inventory.purchase.PurchaseOrderServiceTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
Running com.example.inventory.transfer.StockTransferServiceTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
Running com.example.inventory.sales.SalesOrderServiceTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
Running com.example.inventory.inventory.InventoryServiceTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
Running com.example.inventory.sales.SalesOrderConcurrencyTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Running com.example.inventory.integration.InventoryIntegrationTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

Results:
Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 2. Concurrency Test: 100 Competing Orders for 10 Units

### Scenario Setup
- Product: `prod_conc`
- Initial Physical Quantity: `10`
- Initial Reserved Quantity: `0`
- Initial Available Quantity: `10`
- Competitors: `100 threads` (via `ExecutorService` thread pool of 32 threads)
- Timing: Coordinated release via `CountDownLatch` releasing all 100 requests in the same millisecond.
- Target per order: 1 unit.

### Measured Results:
| Metric | Expected Value | Actual Measured Result | Status |
| :--- | :--- | :--- | :--- |
| **Total Orders Submitted** | 100 | 100 | Passed |
| **Successful Orders (201 Created)** | 10 | 10 | Passed |
| **Rejected Orders (409 Conflict)** | 90 | 90 | Passed |
| **Final Physical Quantity** | 10 | 10 | Passed |
| **Final Reserved Quantity** | 10 | 10 | Passed |
| **Final Available Quantity** | 0 | 0 | Passed |
| **Negative Stock Count** | 0 | 0 | Passed |
| **Movements Recorded** | 10 | 10 | Passed |

### Key Concurrency Invariant:
$$\text{Available Stock} = \text{Quantity} - \text{Reserved Quantity} \ge 0$$
No overselling occurred, no negative stock was produced, and exactly 90 requests received atomic HTTP 409 Conflict rejection.

---

## 3. End-to-End Flow Verification (`InventoryIntegrationTest`)
Executed against local MongoDB on `localhost:27017`:
1. **Warehouse Registration:** Created `wh1` (Central Hub) and `wh2` (East Hub).
2. **Product Registration:** Created `prod1` with SKU `SKU-E2E-...` and `reorderLevel = 10`.
3. **Purchase Order & Receipt:** Created PO for 15 units into `wh1`, received stock; physical stock increased to 15.
4. **Sales Order Placement:** Placed order for 6 units from `wh1`; available stock dropped to `9` (`15 - 6`).
5. **Low-Stock Alert Triggered:** Evaluated `9 <= 10`, verified `LowStockAlert` created with status `ACTIVE`.
6. **Stock Transfer:** Initiated transfer of 4 units from `wh1` to `wh2` (`IN_TRANSIT`), completed transfer; `wh2` credited with 4 units.
7. **Sales Order Fulfillment:** Completed sales order; reserved stock converted into physical deduction.

All steps completed with 100% assertion pass rate.
