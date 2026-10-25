# Correctness test results

Branch: `experiment/autocomplete-five-modes`. Base revision: `e2d061a1d596a71db3616a6f48e9faa920d667a2`.
Working tree changes are included; consult the branch diff for the tested implementation.

Tests: **90**; passed: **90**; failures: **0**; errors: **0**; skipped: **0**.

| Test class | Tests | Failures | Errors | Skipped | Suite seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| CacheSelectionTest | 2 | 0 | 0 | 0 | 3.979 |
| RedisInventoryCacheAdapterTest | 7 | 0 | 0 | 0 | 0.501 |
| TransactionErrorsTest | 2 | 0 | 0 | 0 | 0.059 |
| MongoConfigTest | 2 | 0 | 0 | 0 | 1.385 |
| ConsistencyMongoIntegrationTest | 20 | 0 | 0 | 0 | 31.667 |
| AutocompleteExperimentServiceTest | 5 | 0 | 0 | 0 | 0.521 |
| SnowflakeIdGeneratorTest | 5 | 0 | 0 | 0 | 0.075 |
| InventoryIntegrationTest | 1 | 0 | 0 | 0 | 8.240 |
| InventoryServiceTest | 2 | 0 | 0 | 0 | 0.524 |
| LowStockServiceTest | 3 | 0 | 0 | 0 | 0.178 |
| ProductServiceTest | 4 | 0 | 0 | 0 | 0.182 |
| PurchaseOrderServiceTest | 5 | 0 | 0 | 0 | 0.049 |
| TokenBucketTest | 3 | 0 | 0 | 0 | 0.476 |
| SalesOrderConcurrencyTest | 1 | 0 | 0 | 0 | 5.248 |
| SalesOrderServiceTest | 7 | 0 | 0 | 0 | 0.086 |
| ConsistentHashRingTest | 4 | 0 | 0 | 0 | 0.585 |
| ShardedInventoryPersistenceRouterTest | 6 | 0 | 0 | 0 | 0.449 |
| ShardingMongoIntegrationTest | 3 | 0 | 0 | 0 | 9.486 |
| StockTransferServiceTest | 5 | 0 | 0 | 0 | 0.042 |
| WarehouseServiceTest | 3 | 0 | 0 | 0 | 0.016 |

These are correctness results, not the three-workload performance study. CPU, RSS, HTTP latency and throughput were not measured.
The new consistency tests mock Redis and use a real one-member MongoDB replica set.
Database concurrency tests retry labelled aborted transactions; this is not a measured client rejection rate.

Selected measured console observations:

```text
Consistency: 100 real orders, 10 reservations, 90 insufficient-stock conflicts, no orphan stock
[Snowflake Benchmark] Generated 25000 IDs across 50 threads in 46 ms (~543478 IDs/sec). Collisions: 0
Mongo migration: added=RebalanceResult[previousShards=[shard-1, shard-2, shard-3], activeShards=[shard-1, shard-2, shard-3, shard-4], recordsScanned=200, recordsMoved=51, remappedPercent=25.5, durationMs=863], removed=RebalanceResult[previousShards=[shard-1, shard-2, shard-3, shard-4], activeShards=[shard-1, shard-3, shard-4], recordsScanned=200, recordsMoved=40, remappedPercent=20.0, durationMs=712]
Mongo reservations: 100 attempts, 10 succeeded, 90 rejected
```
