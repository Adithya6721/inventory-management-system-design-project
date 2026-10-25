# Correctness test results

Branch: `experiment/autocomplete-five-modes`. Base revision: `c4231868b27554c9047a25ff81e7fc335068b64a`.
Working tree changes are included; consult the branch diff for the tested implementation.

Tests: **96**; passed: **96**; failures: **0**; errors: **0**; skipped: **0**.

| Test class | Tests | Failures | Errors | Skipped | Suite seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| CacheSelectionTest | 2 | 0 | 0 | 0 | 4.331 |
| RedisInventoryCacheAdapterTest | 7 | 0 | 0 | 0 | 0.518 |
| TransactionErrorsTest | 2 | 0 | 0 | 0 | 0.059 |
| MongoConfigTest | 2 | 0 | 0 | 0 | 1.272 |
| ConsistencyMongoIntegrationTest | 20 | 0 | 0 | 0 | 32.121 |
| AutocompleteExperimentServiceTest | 5 | 0 | 0 | 0 | 0.464 |
| SnowflakeIdGeneratorTest | 5 | 0 | 0 | 0 | 0.095 |
| InventoryIntegrationTest | 1 | 0 | 0 | 0 | 7.651 |
| InventoryServiceTest | 2 | 0 | 0 | 0 | 0.879 |
| LowStockServiceTest | 3 | 0 | 0 | 0 | 0.204 |
| ProductServiceTest | 4 | 0 | 0 | 0 | 0.245 |
| PurchaseOrderServiceTest | 5 | 0 | 0 | 0 | 0.043 |
| TokenBucketTest | 3 | 0 | 0 | 0 | 0.474 |
| SalesOrderConcurrencyTest | 1 | 0 | 0 | 0 | 5.247 |
| SalesOrderServiceTest | 7 | 0 | 0 | 0 | 0.090 |
| ConsistentHashRingTest | 4 | 0 | 0 | 0 | 0.313 |
| ShardedInventoryPersistenceRouterTest | 6 | 0 | 0 | 0 | 0.528 |
| ShardingMongoIntegrationTest | 3 | 0 | 0 | 0 | 8.971 |
| ShortLinkMongoIntegrationTest | 1 | 0 | 0 | 0 | 4.964 |
| ShortLinkServiceTest | 5 | 0 | 0 | 0 | 0.028 |
| StockTransferServiceTest | 5 | 0 | 0 | 0 | 0.029 |
| WarehouseServiceTest | 3 | 0 | 0 | 0 | 0.015 |

These are correctness results, not the three-workload performance study. CPU, RSS, HTTP latency and throughput were not measured.
The new consistency tests mock Redis and use a real one-member MongoDB replica set.
Database concurrency tests retry labelled aborted transactions; this is not a measured client rejection rate.

Selected measured console observations:

```text
Consistency: 100 real orders, 10 reservations, 90 insufficient-stock conflicts, no orphan stock
[Snowflake Benchmark] Generated 25000 IDs across 50 threads in 66 ms (~378788 IDs/sec). Collisions: 0
Mongo migration: added=RebalanceResult[previousShards=[shard-1, shard-2, shard-3], activeShards=[shard-1, shard-2, shard-3, shard-4], recordsScanned=200, recordsMoved=51, remappedPercent=25.5, durationMs=835], removed=RebalanceResult[previousShards=[shard-1, shard-2, shard-3, shard-4], activeShards=[shard-1, shard-3, shard-4], recordsScanned=200, recordsMoved=40, remappedPercent=20.0, durationMs=545]
Mongo reservations: 100 attempts, 10 succeeded, 90 rejected
```
