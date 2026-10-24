# What these tests show

[Tables and graphs](README.md) · [Run instructions](../../QUICK-TESTS.md)

All 25 HTTP runs and 12 stock reconciliations passed their checks. These are
short initial comparisons: 10-second windows, one repeat, one shared machine.
Expected 409/429 responses remain visible and are not counted as admitted work.

- **Limiter:** off admitted 10/50/200 reads per second. On admitted 5/sec at every
  offered level, rejecting 50%, 90% and 97.5% respectively. Fixed pacing reached
  its targets; measured p95 scheduling lag stayed below 51 ms. A request from
  the same client bucket was admitted again after the cooldown check.
- **Logical sharding:** at 100 users, point reads averaged 103.57 ms with one
  store versus 120.38 ms with shards; product-wide reads averaged 80.03 versus
  142.83 ms. The latter must visit all shards. Partitioning has a measured cost
  here, and shared CPU/storage supplies no additional physical capacity. Small
  low-load differences should not be treated as a general speedup claim.
- **Stock:** every distributed-stock burst reserved all 100 orders. Every
  hot-stock burst stayed within its 10-unit limit, with no orphan orders/audits,
  negative quantities or duplicated stock owners. At 100 concurrent users only
  4 orders reserved in the single-store case and 2 in the sharded case; the
  remaining requests received retryable database write conflicts. Stock was
  conserved, but availability of successful writes suffered. Clients would need
  to retry those conflicts with the same idempotency keys to make further
  progress. This suite deliberately reports the first attempts without retries.
- **Membership changes:** all 803 documents and their fields survived the
  3-to-4-to-3 change. Adding and removing shard 4 each moved 213 records (26.53%).
  Server migration times were 2,934 and 2,774 ms. The longest HTTP response was
  3,026 ms; 50 transactions received explicit retryable snapshot/routing
  conflicts. The router's inventory lock preserves state while causing pauses.
- **IDs:** Snowflake generated about 3.40–4.05 million IDs/sec versus
  1.06–1.40 million for full UUIDs in this direct Java benchmark. All 1.5 million
  normal benchmark IDs passed collision checks. A separate fixed-clock check
  intentionally demonstrates collisions when two generators share a node ID;
  distinct node IDs passed. This does not measure database insertion speed.
- **Hashing:** at 100,000 keys with 32 virtual nodes, adding a fourth shard moved
  26.775% of keys versus 74.959% under modulo placement. The virtual-node tables
  report record imbalance. The hot-key simulation shows why balanced records
  still permit one heavily loaded owner.
- **Autocomplete:** at 10,000 products, selective prefixes averaged 0.0011 ms
  with the Trie versus 0.3798 ms with a linear scan. Broad prefixes reversed the
  result: 1.4315 versus 0.0003 ms. The current Trie collects all matching
  candidates before selecting eight; the linear baseline stops after eight.
  Full candidate-set and top-8 validity checks passed. Both implement token
  prefix matching, with no guarantee of identical ordering. These are generated
  complete-catalogue algorithm tests, not proof of production frontend catalogue
  coverage or browser rendering performance.

CPU, memory, per-request latency and rejection details remain in the report and
raw observations. Longer repeated runs are available through the same command
with `-Seconds 60 -Repeats 3`. Physical shard failure/isolation, multi-backend
coordination, and transaction/order-data partitioning remain separate work.
Reservation expiry remains deferred. No operational UI or production business
logic was changed for these experiments.
