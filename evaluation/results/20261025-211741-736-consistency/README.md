# Correctness test results

Branch: `experiment/autocomplete-five-modes`. Base revision: `3bb610d3942dff79dffb5b1f69b9d741f71cd8e5`.
Working tree changes are included; consult the branch diff for the tested implementation.

Tests: **90**; passed: **90**; failures: **0**; errors: **0**; skipped: **0**.

| Test class | Tests | Failures | Errors | Skipped | Suite seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| CacheSelectionTest | 2 | 0 | 0 | 0 | 4.031 |
| RedisInventoryCacheAdapterTest | 7 | 0 | 0 | 0 | 0.484 |
| TransactionErrorsTest | 2 | 0 | 0 | 0 | 0.025 |
| MongoConfigTest | 2 | 0 | 0 | 0 | 1.396 |
| ConsistencyMongoIntegrationTest | 20 | 0 | 0 | 0 | 30.387 |
| AutocompleteExperimentServiceTest | 5 | 0 | 0 | 0 | 0.465 |
| SnowflakeIdGeneratorTest | 5 | 0 | 0 | 0 | 0.093 |
| InventoryIntegrationTest | 1 | 0 | 0 | 0 | 8.152 |
| InventoryServiceTest | 2 | 0 | 0 | 0 | 0.426 |
| LowStockServiceTest | 3 | 0 | 0 | 0 | 0.174 |
| ProductServiceTest | 4 | 0 | 0 | 0 | 0.152 |
| PurchaseOrderServiceTest | 5 | 0 | 0 | 0 | 0.040 |
| TokenBucketTest | 3 | 0 | 0 | 0 | 0.462 |
| SalesOrderConcurrencyTest | 1 | 0 | 0 | 0 | 5.222 |
| SalesOrderServiceTest | 7 | 0 | 0 | 0 | 0.065 |
| ConsistentHashRingTest | 4 | 0 | 0 | 0 | 0.253 |
| ShardedInventoryPersistenceRouterTest | 6 | 0 | 0 | 0 | 0.500 |
| ShardingMongoIntegrationTest | 3 | 0 | 0 | 0 | 9.065 |
| StockTransferServiceTest | 5 | 0 | 0 | 0 | 0.034 |
| WarehouseServiceTest | 3 | 0 | 0 | 0 | 0.016 |

These are correctness results, not the three-workload performance study. CPU, RSS, HTTP latency and throughput were not measured.
The new consistency tests mock Redis and use a real one-member MongoDB replica set.
Database concurrency tests retry labelled aborted transactions; this is not a measured client rejection rate.

Selected measured console observations:

```text
Consistency: 100 real orders, 10 reservations, 90 insufficient-stock conflicts, no orphan stock
[Snowflake Benchmark] Generated 25000 IDs across 50 threads in 62 ms (~403226 IDs/sec). Collisions: 0
Mongo migration: added=RebalanceResult[previousShards=[shard-1, shard-2, shard-3], activeShards=[shard-1, shard-2, shard-3, shard-4], recordsScanned=200, recordsMoved=51, remappedPercent=25.5, durationMs=870], removed=RebalanceResult[previousShards=[shard-1, shard-2, shard-3, shard-4], activeShards=[shard-1, shard-3, shard-4], recordsScanned=200, recordsMoved=40, remappedPercent=20.0, durationMs=654]
Mongo reservations: 100 attempts, 10 succeeded, 90 rejected
```
