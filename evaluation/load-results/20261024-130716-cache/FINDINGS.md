# Findings from the first JMeter experiment

This is the completed product-detail cache comparison, not the complete project
evaluation. [Measured tables and chart](README.md), [CSV](summary.csv),
[run settings](manifest.json), and [per-run evidence](points.json) are saved here.
The implementation/plan revision was `943488a`; the manifest records the exact
backend JAR, runner and JMX hashes. The working tree was marked dirty because
generated evidence was already present.

JMeter generated all measured requests. Python coordinated processes, fixtures,
counters and reports. Both configurations served the same 1,000-product cyclic
CSV workload. Each point used a 5-second ramp and 30-second steady window, with
three repetitions; configuration/workload order was alternated. Each backend had
an initial 15-second warm-up. The load generator started a fresh JVM per point;
excluding startup/ramp does not establish that every JIT optimisation had finished.
The reported means and percentiles are means of the three per-run statistics,
not pooled request percentiles. Chart error bars are between-run standard
deviations, not confidence intervals. RSS columns in summary.csv are means of
per-run peaks; individual peaks are retained in each point's resource summary.

## Observations

There were **2,355,213 measured steady-window requests**, all HTTP 200 and all
passing product ID/SKU assertions. Entire runs, including ramps, also had zero
JMeter errors and matched the backend's product-request counters. Warm-cache
points had 100% cache hits, zero product database loads, and no Redis failures.
Every cache-off request performed exactly one product database load.

| Users | Mean response: off / on (ms) | Successful throughput: off / on (req/s) |
| ---: | ---: | ---: |
| 5 | 0.935 / 1.246 | 4,941 / 3,799 |
| 25 | 8.107 / 5.644 | 3,057 / 4,346 |
| 100 | 15.779 / 26.364 | 6,269 / 3,757 |

Caching improved mean response time by about 30% at 25 users, but was slower at
5 and 100 users. It substantially reduced MongoDB work in every workload. At
100 users, MongoDB process CPU averaged 26.40% of total host logical-CPU capacity
with cache off versus 0.48% with cache on. These process observations also include
any background clients of the shared MongoDB instance.

The cache lookup path itself averaged 0.406, 3.666 and 22.596 ms at 5, 25 and 100
users respectively (counters include the ramp). Its increasing latency warrants
profiling before tuning; these measurements do not establish whether client
decoding, connection scheduling, networking or another factor caused the delay.
The hot local MongoDB dataset and same-host deployment limit any general claim
that Redis improves latency. Throughput is also non-monotonic across concurrency
levels, so these three points do not establish a universal saturation threshold.

## Failure and read correctness

Only this experiment's dedicated Redis process on 6381 was stopped. During the
outage all 15 product requests returned correct HTTP 200 responses via MongoDB.
However, mean latency increased to **4,049 ms** and throughput fell to **1.23 req/s**.
Counters recorded 15 lookup failures and 15 write failures: after the roughly
two-second failed lookup, each fallback attempted to refill the unavailable
cache and waited roughly two more seconds. The scheduler ran for 10 seconds;
including final in-flight completions, the observed failure window was 12.155
seconds. This is a reliability success with a serious response-time cost, not
acceptable evidence of fast failover. Avoiding repeated waits during a known
outage is a candidate improvement for a separately measured follow-up.

After restart and a separate warm-up, recovery requests averaged 1.22 ms, had
100% hits, zero database loads and zero errors. A subsequent product-name update
and reread passed the sequential metadata invalidation check. This does not prove
correctness for concurrent cache fills/updates or writes during an outage.

Stock availability was never cached in either mode. This run makes no claim
about cached stock freshness, purchase/sales throughput, or stock-write behaviour
during a Redis outage. The separate correctness suite passed 85 backend tests,
including real MongoDB contention, rollback and migration checks. Three synthetic
trace tests also passed for the measurement-window and error/goodput calculations.
Reservation expiry remains deferred.

## Scope of the remaining evaluation

Next compare limiter off/on with the same offered rate, reporting admitted
throughput and 429 rejection separately. Then compare single-store and logical
shard point/scatter and stock-write workloads, perform shard addition/removal
under traffic, and reconcile persisted stock/audit/order state. Snowflake/UUID
and Trie/linear-scan comparisons need direct algorithm benchmarks alongside
HTTP tests; JMeter alone does not time an in-browser Trie lookup accurately.

All benchmark services and existing applications shared one host. Logical shard
databases still share one MongoDB deployment; this is not physical scale-out or
database failover evidence. Transaction/order-data partitioning also remains an
explicit requirement gap. See [the evaluation plan](../../../docs/evaluation-plan.md).
Passing this cache experiment does not close those remaining requirements.

The runner stopped only its two backend JVMs and dedicated Redis process after
completion. Raw JTL files are retained locally as lossless gzip files; generated
HTML and large raw data are Git-ignored. Local HTML reports are available in
[cache off](steady-off-u100-r1/html/index.html) and
[cache on](steady-on-u100-r1/html/index.html). Reproduce the run using
[the JMeter instructions](../../jmeter/README.md).
