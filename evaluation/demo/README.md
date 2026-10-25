# Demo data

UI: http://localhost:3000. Refresh once after seeding.

12 products, 3 warehouses, 36 stock records, 6 active low-stock alerts.

## Ready-to-show actions

- Search **Wireless Mouse** or **USB-C Hub** in global search.
- Open warehouses and compare physical, reserved and available stock.
- Fulfil or cancel **office-equipment-ready** to demonstrate reservations.
- Receive **delhi-low-stock-restock** to resolve monitor/scanner low-stock alerts.
- Receive the remainder of **partial-mumbai-delivery**.
- Complete **mouse-delivery-in-transit** and watch Delhi stock increase.

| Type | Demo label | ID | Current status |
| --- | --- | --- | --- |
| Purchase | opening-bengaluru | po_372728111395119104 | RECEIVED |
| Purchase | opening-mumbai | po_372728113857175552 | RECEIVED |
| Purchase | opening-delhi | po_372728115572645888 | RECEIVED |
| Purchase | office-restock | po_372728116805771264 | CREATED |
| Purchase | delhi-low-stock-restock | po_372728116952571904 | CREATED |
| Purchase | partial-mumbai-delivery | po_372728117090983936 | PARTIALLY_RECEIVED |
| Sales | office-equipment-ready | so_372728117678186496 | RESERVED |
| Sales | packing-supplies-ready | so_372728118017925120 | RESERVED |
| Sales | fulfilled-it-order | so_372728118257000448 | COMPLETED |
| Sales | cancelled-gloves-order | so_372728118819037184 | CANCELLED |
| Sales | low-stock-scanners-reserved | so_372728119209107456 | RESERVED |
| Transfer | mouse-delivery-in-transit | trn_372728119435599872 | IN_TRANSIT |
| Transfer | labels-delivered | trn_372728119687258112 | COMPLETED |
| Transfer | cable-move-cancelled | trn_372728120136048640 | CANCELLED |

Reproduce without resetting existing data:

`python scripts/seed-demo.py --base-url http://127.0.0.1:8081`

The script uses the normal APIs and stable creation/receipt identities. It does not erase stock or reopen completed/cancelled orders. Rerunning verifies current data instead of resetting the demonstration.
