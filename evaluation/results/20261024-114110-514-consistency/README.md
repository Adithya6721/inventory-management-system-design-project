# Correctness test results

Branch: `consistency/hardening`. Base revision: `a10d2202737ab60a14d3db18a514b2f5717e9557`.
Working tree changes are included; consult the branch diff for the tested implementation.

Tests: **82**; passed: **82**; failures: **0**; errors: **0**; skipped: **0**.

| Test class | Tests | Failures | Errors | Skipped | Suite seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| RedisInventoryCacheAdapterTest | 6 | 0 | 0 | 0 | 1.686 |
| TransactionErrorsTest | 2 | 0 | 0 | 0 | 0.007 |
| MongoConfigTest | 2 | 0 | 0 | 0 | 0.442 |
| ConsistencyMongoIntegrationTest | 20 | 0 | 0 | 0 | 22.674 |
| SnowflakeIdGeneratorTest | 5 | 0 | 0 | 0 | 0.093 |
| InventoryIntegrationTest | 1 | 0 | 0 | 0 | 6.216 |
| InventoryServiceTest | 2 | 0 | 0 | 0 | 0.409 |
| LowStockServiceTest | 3 | 0 | 0 | 0 | 0.114 |
| ProductServiceTest | 4 | 0 | 0 | 0 | 0.133 |
| PurchaseOrderServiceTest | 5 | 0 | 0 | 0 | 0.044 |
| TokenBucketTest | 3 | 0 | 0 | 0 | 0.462 |
| SalesOrderConcurrencyTest | 1 | 0 | 0 | 0 | 5.213 |
| SalesOrderServiceTest | 7 | 0 | 0 | 0 | 0.061 |
| ConsistentHashRingTest | 4 | 0 | 0 | 0 | 0.234 |
| ShardedInventoryPersistenceRouterTest | 6 | 0 | 0 | 0 | 0.447 |
| ShardingMongoIntegrationTest | 3 | 0 | 0 | 0 | 7.354 |
| StockTransferServiceTest | 5 | 0 | 0 | 0 | 0.037 |
| WarehouseServiceTest | 3 | 0 | 0 | 0 | 0.019 |

These are correctness results, not the three-workload performance study. CPU, RSS, HTTP latency and throughput were not measured.
The new consistency tests mock Redis and use a real one-member MongoDB replica set.
Database concurrency tests retry labelled aborted transactions; this is not a measured client rejection rate.

Selected measured console observations:

```text
Consistency: 100 real orders, 10 reservations, 90 insufficient-stock conflicts, no orphan stock
[Snowflake Benchmark] Generated 25000 IDs across 50 threads in 53 ms (~471698 IDs/sec). Collisions: 0
Mongo migration: added=RebalanceResult[previousShards=[shard-1, shard-2, shard-3], activeShards=[shard-1, shard-2, shard-3, shard-4], recordsScanned=200, recordsMoved=51, remappedPercent=25.5, durationMs=744], removed=RebalanceResult[previousShards=[shard-1, shard-2, shard-3, shard-4], activeShards=[shard-1, shard-3, shard-4], recordsScanned=200, recordsMoved=40, remappedPercent=20.0, durationMs=520]
Mongo reservations: 100 attempts, 10 succeeded, 90 rejected
```
