# Run the component tests

From the project root, with the existing test MongoDB replica set on port 27019:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-components.ps1
```

That is the complete quick suite. It builds a separate benchmark JAR, starts
isolated backends on 8082 one at a time, creates fresh data, runs JMeter and the
direct algorithm tests, writes the results path, and stops its own backends.
Your running app and its data stay available. Cache is off throughout these
experiments; stock transactions and stock guards stay on. No expiry changes.

If the test MongoDB is not running, start it once with
`powershell -ExecutionPolicy Bypass -File scripts/start-consistency-mongo.ps1`.
Do not start another copy if 27019 is already in use. Java 17/JDK, Maven, Node,
Python with `requests` and `psutil`, and JMeter 5.6.3 are required. The local
JMeter installation is detected automatically; elsewhere set `JMETER_HOME`.
No JMeter plugins are needed. No Redis service is required for these cache-off
tests. The existing replica set is reused; only fresh run-specific databases
are written, and those databases remain available for inspection afterwards.

## Run just one experiment

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-components.ps1 -Only limiter
powershell -ExecutionPolicy Bypass -File scripts/test-components.ps1 -Only shards
powershell -ExecutionPolicy Bypass -File scripts/test-components.ps1 -Only stock
powershell -ExecutionPolicy Bypass -File scripts/test-components.ps1 -Only ids
powershell -ExecutionPolicy Bypass -File scripts/test-components.ps1 -Only hash
powershell -ExecutionPolicy Bypass -File scripts/test-components.ps1 -Only autocomplete
```

| Experiment | Simple test | What to look for |
| --- | --- | --- |
| Limiter | Same product requests at 10, 50, 200 requests/sec; limiter off then on | Off admits all. On admits about 5/sec after warm-up; remaining responses are explicit 429. Compare admitted latency, rejection rate and pacing lag. |
| Single DB / logical shards | Same catalogue shape: 100 products × 5 warehouses; stock 1,000 each. 5/25/100 users alternate point reads and product-wide reads. | Compare each request type separately. Sharded product-wide queries visit all active shards. Record counts and operation counts are saved. |
| Sales with sufficient stock | 100 one-unit orders spread across 100 stock records at 5/25/100 users, on both storage modes | 100 reserved orders, 100 reserved units, matching audit records. |
| Contention | 100 one-unit orders compete for 10 units at 5/25/100 users, on both storage modes | Never more than 10 reservations. Insufficient stock and retryable concurrent-write conflicts are separate outcomes. Persisted stock/order/audit totals must match. |
| Shard changes | Keep read traffic running while adding shard 4, then removing it | Before/after documents match exactly, no duplicate copies, record movement and maximum HTTP delay recorded. A stale database snapshot can receive a clearly reported retryable 409 during membership change. |
| IDs | Generate 250,000 IDs using full UUIDs and actual Snowflake, at 1/8/32 threads | IDs/sec, CPU time, collisions. Also demonstrate why each Snowflake node needs a distinct node ID with a controlled fixed-clock check. |
| Hashing | Same 1k/10k/100k keys: modulo placement versus actual ring; 1/32/128 virtual nodes | Remapping percentage and record balance. The hot-key simulation shows that balanced records do not imply balanced requests. |
| Autocomplete | Actual frontend Trie versus a linear token-prefix scan on the same 100/1k/10k products | Compare broad, selective and missing prefixes separately. Check full candidate sets and top-8 validity, build time, memory and lookup time. |

The sufficient-stock and contention cases are bounded bursts, not long-running
capacity measurements. Each case uses fresh stock, so earlier reservations do
not alter the next case. Tests do not automatically retry stock conflicts;
the report shows them. A direct MongoDB check also reconciles all outstanding
orders, audits and reservations and checks physical shard ownership.

## Read the results

Open the printed `evaluation/results/<timestamp>-components/README.md`.
It links the metrics and correctness checks. `http-summary.csv` opens directly
in Excel. `http-results.json` includes per-request-type latency, accepted
throughput, expected rejection counts, CPU/RSS and shard-operation deltas.
Each HTTP run has its CSV input, exact JMeter command, resource samples and
compressed raw `raw.jtl.gz`. Raw samples and large generated assets stay local.
`ids.json`, `hashing.json`, and `autocomplete.json` contain the algorithm results.

JMeter performs the HTTP load; Python handles setup and reporting. For a GUI
walkthrough open `evaluation/jmeter/components.jmx`. Look at **Concurrent API
users**, **Requests from CSV**, the HTTP sampler and **Validate business outcome**.
The plan's values come from `-J` properties; each run's `command.json` shows
the exact settings and dataset. Decompress a copied `raw.jtl.gz` to inspect
existing samples. Backends stop after the test, so replaying live requests needs
a running benchmark deployment; rerunning the one-command script handles that.

## Short runs versus final measurements

Defaults deliberately favour a quick check: 10 measured seconds, one repeat per
configuration. Limiter measurements exclude five warm-up seconds and must reach
their offered rate within 10%. Reads exclude a two-second ramp. Extra tail
samples are excluded. Each point starts a new load-generator JVM, so these are
local comparative results with limited warm-up, not production capacity claims.

For a longer final study use the same command with:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-components.ps1 -Seconds 60 -Repeats 3
```

All logical shard databases share one MongoDB process. The single-versus-sharded
test measures routing, query and transaction costs; it does not add machine
capacity or demonstrate physical shard failover. The ID and Trie comparisons
are direct Java/Node measurements, not HTTP or browser rendering timings.
Order/movement transaction data remains in the core database; partitioning it
is still a separate requirement gap.
