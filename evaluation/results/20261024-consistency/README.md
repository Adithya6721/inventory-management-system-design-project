# Correctness test results

Branch: `consistency/hardening`. Base revision: `7a3eb4021785a31c6b0c47fa7a18b1575ce349c3`.
Working tree changes are included; consult the branch diff for the tested implementation.

Tests: **82**; passed: **82**; failures: **0**; errors: **0**; skipped: **0**.

The frontend creation-intent helper also passed four Node tests (see
`frontend-tests.txt`). These check stable keys on retry, distinct payloads/actions,
late acknowledgements and session restoration. Backend and frontend counts are
reported separately.

`npm.cmd run build` also passed with Vite 8.3.4 (see `frontend-build.txt`). Compose
YAML parsed successfully; Docker container startup was not tested because Docker
is not installed. The isolated test MongoDB was stopped after verification, with
its data retained under `.verification/`; the normal MongoDB service was unchanged.

| Test class | Tests | Failures | Errors | Skipped | Suite seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| RedisInventoryCacheAdapterTest | 6 | 0 | 0 | 0 | 1.649 |
| TransactionErrorsTest | 2 | 0 | 0 | 0 | 0.008 |
| MongoConfigTest | 2 | 0 | 0 | 0 | 0.449 |
| ConsistencyMongoIntegrationTest | 20 | 0 | 0 | 0 | 24.328 |
| SnowflakeIdGeneratorTest | 5 | 0 | 0 | 0 | 0.084 |
| InventoryIntegrationTest | 1 | 0 | 0 | 0 | 6.045 |
| InventoryServiceTest | 2 | 0 | 0 | 0 | 0.387 |
| LowStockServiceTest | 3 | 0 | 0 | 0 | 0.159 |
| ProductServiceTest | 4 | 0 | 0 | 0 | 0.124 |
| PurchaseOrderServiceTest | 5 | 0 | 0 | 0 | 0.043 |
| TokenBucketTest | 3 | 0 | 0 | 0 | 0.462 |
| SalesOrderConcurrencyTest | 1 | 0 | 0 | 0 | 5.215 |
| SalesOrderServiceTest | 7 | 0 | 0 | 0 | 0.066 |
| ConsistentHashRingTest | 4 | 0 | 0 | 0 | 0.248 |
| ShardedInventoryPersistenceRouterTest | 6 | 0 | 0 | 0 | 0.469 |
| ShardingMongoIntegrationTest | 3 | 0 | 0 | 0 | 7.370 |
| StockTransferServiceTest | 5 | 0 | 0 | 0 | 0.043 |
| WarehouseServiceTest | 3 | 0 | 0 | 0 | 0.017 |

These are correctness results, not the three-workload performance study. CPU, RSS, HTTP latency and throughput were not measured.
The new consistency tests mock Redis and use a real one-member MongoDB replica set.
Database concurrency tests retry labelled aborted transactions; this is not a measured client rejection rate.

Selected measured console observations:

```text
Consistency: 100 real orders, 10 reservations, 90 insufficient-stock conflicts, no orphan stock
[Snowflake Benchmark] Generated 25000 IDs across 50 threads in 54 ms (~462963 IDs/sec). Collisions: 0
Mongo migration: added=RebalanceResult[previousShards=[shard-1, shard-2, shard-3], activeShards=[shard-1, shard-2, shard-3, shard-4], recordsScanned=200, recordsMoved=51, remappedPercent=25.5, durationMs=708], removed=RebalanceResult[previousShards=[shard-1, shard-2, shard-3, shard-4], activeShards=[shard-1, shard-3, shard-4], recordsScanned=200, recordsMoved=40, remappedPercent=20.0, durationMs=570]
Mongo reservations: 100 attempts, 10 succeeded, 90 rejected
```
