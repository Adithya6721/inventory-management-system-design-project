# Correctness test results

Branch: `consistency/hardening`. Base revision: `5bdb14bf5a910d4a0b4d9943fa570763d5c50f86`.
Working tree changes are included; consult the branch diff for the tested implementation.

Tests: **85**; passed: **85**; failures: **0**; errors: **0**; skipped: **0**.

| Test class | Tests | Failures | Errors | Skipped | Suite seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| CacheSelectionTest | 2 | 0 | 0 | 0 | 3.205 |
| RedisInventoryCacheAdapterTest | 7 | 0 | 0 | 0 | 0.492 |
| TransactionErrorsTest | 2 | 0 | 0 | 0 | 0.026 |
| MongoConfigTest | 2 | 0 | 0 | 0 | 1.221 |
| ConsistencyMongoIntegrationTest | 20 | 0 | 0 | 0 | 26.274 |
| SnowflakeIdGeneratorTest | 5 | 0 | 0 | 0 | 0.106 |
| InventoryIntegrationTest | 1 | 0 | 0 | 0 | 6.805 |
| InventoryServiceTest | 2 | 0 | 0 | 0 | 0.453 |
| LowStockServiceTest | 3 | 0 | 0 | 0 | 0.188 |
| ProductServiceTest | 4 | 0 | 0 | 0 | 0.174 |
| PurchaseOrderServiceTest | 5 | 0 | 0 | 0 | 0.062 |
| TokenBucketTest | 3 | 0 | 0 | 0 | 0.471 |
| SalesOrderConcurrencyTest | 1 | 0 | 0 | 0 | 5.232 |
| SalesOrderServiceTest | 7 | 0 | 0 | 0 | 0.071 |
| ConsistentHashRingTest | 4 | 0 | 0 | 0 | 0.212 |
| ShardedInventoryPersistenceRouterTest | 6 | 0 | 0 | 0 | 0.487 |
| ShardingMongoIntegrationTest | 3 | 0 | 0 | 0 | 8.467 |
| StockTransferServiceTest | 5 | 0 | 0 | 0 | 0.042 |
| WarehouseServiceTest | 3 | 0 | 0 | 0 | 0.020 |

These are correctness results, not the three-workload performance study. CPU, RSS, HTTP latency and throughput were not measured.
The new consistency tests mock Redis and use a real one-member MongoDB replica set.
Database concurrency tests retry labelled aborted transactions; this is not a measured client rejection rate.

Selected measured console observations:

```text
Consistency: 100 real orders, 10 reservations, 90 insufficient-stock conflicts, no orphan stock
[Snowflake Benchmark] Generated 25000 IDs across 50 threads in 73 ms (~342466 IDs/sec). Collisions: 0
Mongo migration: added=RebalanceResult[previousShards=[shard-1, shard-2, shard-3], activeShards=[shard-1, shard-2, shard-3, shard-4], recordsScanned=200, recordsMoved=51, remappedPercent=25.5, durationMs=784], removed=RebalanceResult[previousShards=[shard-1, shard-2, shard-3, shard-4], activeShards=[shard-1, shard-3, shard-4], recordsScanned=200, recordsMoved=40, remappedPercent=20.0, durationMs=643]
Mongo reservations: 100 attempts, 10 succeeded, 90 rejected
```
