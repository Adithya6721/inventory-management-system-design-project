# Requirements audit and experiment plan

Audit date: 2026-10-23. This document separates implemented behaviour from
measurements still required. Existing report tables are not treated as reproduced
measurements without raw observations and a reproducible command.

Update 2026-10-24: the consistency branch now implements database transactions,
receipt replay protection and authoritative stock reads. See `consistency.md` for
the current design, required replica-set setup, executed tests and remaining
failure scenarios. The audit below records the pre-hardening state; its baseline
matrix and workload protocol remain the next evaluation phase.

Update 2026-10-24 (JMeter): explicit cache on/off selection, isolated cache
namespaces, read/failure counters, a GUI-readable JMX plan and a CLI experiment
runner are now available under `evaluation/jmeter`. The initial cache experiment
uses a 5-second ramp and 30-second steady window, three repetitions and a separate
Redis outage/recovery scenario. See that README for reproducible commands and
timestamped raw evidence. Stock availability still bypasses the cache. The
historical gaps below regarding non-transactional stock and cached availability
have been addressed on this branch; other scope limits remain.

## Current position

The repository is a working single-backend inventory prototype with logical
inventory sharding, consistent hashing, Redis cache-aside reads, per-process
token-bucket limiting, Snowflake IDs, and browser-side Trie autocomplete. Core
product, warehouse, purchase, sales, transfer and low-stock workflows exist.

Important gaps and limits:

- Only inventory records are sharded. Orders and movement/audit documents remain
  in the core database. The requirement explicitly includes transaction data;
  partition order and/or movement data and demonstrate routing and retrieval.
- All shard databases use one MongoClient and MongoDB instance. This demonstrates
  partitioning and remapping, not additional machine capacity or shard fault
  isolation. Use separately addressable instances for an actual node-failure or
  physical scale-out experiment, or label results as logical-shard experiments.
- Shard migration takes a JVM-wide inventory write lock. It is journaled and
  resumable, but pauses stock operations, scans records, and loads documents into
  memory. Report migration pause and resource costs. Graceful removal requires
  the source data to remain reachable; it is not failover.
- Migration coordination and several order/transfer locks are local to one JVM.
  Rate-limit buckets are local too. Multiple backend replicas are outside the
  current correctness guarantees.
- Stock updates are atomic per inventory document, but stock, audit, alerts and
  order state are separate writes. Failures between them can leave partial work.
  Concurrent duplicate idempotency keys can reserve stock before one order save
  loses a unique-index race. Completion/cancellation paths also need failure and
  retry tests, including checks of stock-operation return values.
- Cached stock is a display optimisation, not the authority for reservations.
  Failed invalidation or a read/fill race can leave stale values. Define and test
  a freshness target rather than claiming strict real-time cached reads.
- The active branch has supplier-feed ingestion, not a crawler implementation.
  Inspect/integrate the supplier-crawler branch if selecting that component.
- URL shortening currently generates a random-looking URL in the frontend; there
  is no mapping store or redirect service. Exclude it from selected components
  unless a real use case and implementation are added. The brief does not require
  every available component.

## Measurement issues to fix first

- The UI concurrency test adds ten units to an existing record instead of setting
  available stock to ten. It groups 409, 429 and other failures together and shows
  success text without checking persisted invariants.
- The autocomplete lab fabricates a 24.5 ms server latency on errors, floors Trie
  timing, assigns accuracy without comparing product IDs, and displays fixed
  debounce savings. Remove these as report evidence.
- The Trie traverses the entire matching subtree before applying the result limit;
  total lookup cost is not simply O(prefix length). Measure broad and selective
  prefixes, build time, and memory. The frontend initially loads only 100 products;
  an index of that subset is not a full-catalog accuracy test.
- Product search/list requests do not use the product-by-ID Redis cache. Benchmark
  the endpoints that actually use Redis; do not attribute search speed to Redis.
- The existing sales concurrency unit test uses an in-memory synchronised adapter.
  The opt-in MongoDB tests must run separately to establish database behaviour.
- Cache and limiter metric getters exist, but need accessible experiment telemetry.
  Count cache timeouts/failures separately from ordinary misses.

## Experiment controls

| Component | Current control | Preparation needed |
| --- | --- | --- |
| Sharding | `SHARDING_ENABLED=false/true` | Seed each mode independently; switching does not migrate data |
| Rate limiter | `RATELIMIT_ENABLED`, capacity/refill environment variables | Record admitted/rejected traffic and distinguish clients |
| Cache | Redis adapter is always selected | Add explicit enabled flag and no-op mode; stopping Redis is a failure experiment, not a no-cache baseline |
| ID generation | Snowflake is always primary | Add explicit selection; use full UUIDs as baseline, not the existing truncated 48-bit fallback |
| Autocomplete | Client Trie plus REST fallback | Add repeatable same-dataset Trie and linear-scan runners with equivalent matching semantics |
| Shard membership | Add/remove configured shard APIs | Fresh core and shard databases per run; persist before/after placement |

Use one component change per comparison. Keep atomic stock guards enabled for
every normal experiment. A deliberately unsafe read-then-write demonstration, if
used, belongs only in disposable test storage.

Separate an isolated experiment deployment from existing application data. Give
each run its own core database, all four shard database names, and cache namespace
or dedicated Redis database. Existing `MONGO_DB` alone does not isolate shards or
Redis keys. Do not flush or reset the user's normal databases.

## Workload protocol

Initial levels: 5, 25 and 100 concurrent clients. These are test settings, not
performance claims. Use a pilot to check that they span idle, moderate and
saturated behaviour, and record any adjustments.

For each configuration and workload, warm up for 15 seconds, measure for 60
seconds, and run three independent repetitions. Record exact command, Git commit,
settings, seed, machine specifications, process topology and dataset size. Use the
same generated dataset and request sequence for each paired comparison. Alternate
configuration order to reduce warm-cache/order bias.

Start with 1,000 products and 5 warehouses; add 10,000 products for a dataset-growth
experiment. Separate seeding and warm-up from measured work. Use unique namespaces
or isolated database resets to prevent reservations and past orders changing the
next run's available stock.

Use separate workload families:

1. Point product/stock reads, with uniform and hot-key access distributions.
2. Broad product/warehouse stock queries, to expose scatter-query costs.
3. Sales writes with enough stock, to measure successful processing throughput.
4. Hot-stock contention with exactly known stock, to test overselling prevention.
5. Purchase receipts, sales fulfilment/cancellation and transfers, to test complete
   lifecycle consistency and retries.

Closed-loop concurrency tests send the next request after completion. For overload
and rate limiting, also drive a fixed offered request rate and record scheduling
lag, so slower replies do not silently reduce the offered load.

Disable the limiter for cache, shard and stock comparisons. Enable it explicitly
for the overload experiment. Use one client and multiple clients as separate rate
limiter cases; all localhost requests otherwise share a bucket.

## Comparison matrix

| Experiment | Baseline | Proposed / changed configuration | Extra metrics |
| --- | --- | --- | --- |
| Redis | Explicit no-cache adapter | Cache on, cold then warm | Hit ratio, hit/miss/failure latency, DB work, freshness |
| Limiter | Limiter off | Limiter on, same offered traffic | 429 rate, admitted goodput, admitted p95, recovery |
| Sharding | One database | Three logical shards | Per-shard record and request counts, scatter overhead |
| Consistent hashing | `hash(key) % shardCount` placement simulator | Same keys on consistent-hash ring, 3 to 4 to 3 shards | Key remapping %, imbalance, migration time, pause |
| Virtual nodes | Low virtual-node count | 32 and 128 on fresh datasets | Record/request imbalance, lookup overhead |
| IDs | Full UUID generator | Snowflake with distinct node IDs | IDs/sec, collisions, CPU, duplicate-node misconfiguration |
| Autocomplete | In-memory linear token-prefix scan | Trie over the exact same catalogue | Mean/p95, build time, memory, precision/recall |
| REST search | Direct server search | Browser Trie + actual fallback | End-to-end latency and coverage; report separately from algorithm-only timing |
| Crawler, if selected | Sequential crawl of local fixture sites | Bounded parallel crawl | Pages/items per second, failures, duplicates, import correctness |

Modulo hashing is a routing/remapping baseline; it must not be used to change
ownership of existing live records without migration. Virtual-node changes also
require fresh datasets or an explicit migration, because persisted topology
rejects incompatible configuration.

Do not require sharding to improve local latency. Same-machine shards share CPU,
RAM and I/O; extra routing/scatter work may slow queries. This is a valid measured
trade-off. Demonstrating independent capacity requires independent resources.

## Metrics and correctness

For each run save per-request observations to CSV/JSONL: request type, start time,
latency, status, outcome, and run identifier. Report mean, p50, p95 and p99 latency;
attempted/admitted/successful throughput; and separate 2xx, expected stock 409,
duplicate/conflict 409, limiter 429, timeout and 5xx rates. Fast rejections must not
make successful-request latency appear better. Report CPU and RSS for backend,
database, cache and load generator where measurable, plus memory high-water marks.

Measure record balance and traffic balance independently. A hot product can create
skewed requests even with evenly distributed records. Save per-shard counts before
and after membership changes and compute moved keys / original keys.

Read authoritative database state after workloads; cached responses alone are not
correctness evidence. Check:

- `0 <= reservedQuantity <= quantity` for every inventory record.
- Successful one-unit reservations never exceed initially available stock.
- Reserved quantities reconcile with outstanding reserved orders.
- Receipt quantities are not applied twice and completed sales are deducted once.
- Transfers conserve total units including in-transit quantities; destination
  credits and source restoration happen at most once per operation.
- Audit records and order state agree with committed stock changes.
- Each inventory key has one authoritative owner after migration, with quantity,
  reservations, version and timestamps preserved.
- Repeated/concurrent requests with the same idempotency key have one effect.
- Low-stock alerts activate/resolve correctly without duplicate active alerts.

## Scalability and failure scenarios

First run a graceful 3 -> 4 -> 3 shard change while requests continue. Measure
latency/pause, failed requests, remapped records and final stock reconciliation.
This meets a membership-change scenario but does not demonstrate node failover.

Next stop only the dedicated experiment Redis instance under read/write load and
restore it. Measure timeout costs, DB fallback, recovery, stale reads and stock
correctness. Do not call this a cache-off baseline.

If claiming database-node reliability, extend configuration to per-shard MongoDB
URIs and run isolated instances. Abruptly fail one instance and report which keys
are unavailable. Replication/recovery must be implemented before claiming failover;
rehashing inaccessible stock to an empty server loses state.

## Execution order and deliverables

1. Reproduce MongoDB correctness/migration tests. Mark them as correctness tests,
   not end-to-end throughput benchmarks.
2. Add explicit cache/ID controls, experiment telemetry, dataset isolation and a
   command-line HTTP runner with raw outputs and reconciliation.
3. Fix misleading UI measurements and correctness issues exposed by retry/failure
   tests. Add transaction-data partitioning to close the brief's explicit gap.
4. Run baseline and component comparisons at three workload levels; investigate
   failures before aggregating results.
5. Run membership and cache-failure scenarios, then optional physical scale-out.
6. Generate tables/graphs from saved observations. Explain both improvements and
   regressions, workload-specific bottlenecks, consistency scope and limits.

Suggested output: `evaluation/results/<run-id>/` containing configuration,
environment, request samples, resource samples, before/after state, correctness
checks and summary. Keep charts and the final report traceable to those files.

PowerShell command for the existing real MongoDB sharding tests (uses dedicated
`inventory_member2_test*` databases and clears their inventory collections):

```powershell
cd backend
$env:RATELIMIT_ENABLED = 'false'
mvn test '-Dsharding.mongo.tests=true' '-Dtest=ShardingMongoIntegrationTest'
Remove-Item Env:RATELIMIT_ENABLED
```

The command needs a running MongoDB. Shell environment changes in the agent's
individual command process do not persist into the user's terminal.
