# JMeter experiments

JMeter 5.6.3 is available locally. No additional JMeter plugins are required.
`product-cache.jmx` can be opened in its GUI. Measured traffic is generated with
JMeter's command-line mode, so GUI listeners do not consume load-generator CPU.

The first comparison targets **GET /api/products/{productId}**, which uses Redis.
Search/list endpoints and authoritative stock reads are not product-cache hits.
Both configurations use identical data, validation and HTTP response assertions:

- `CACHE_ENABLED=false`: an explicit no-op adapter; no cache network calls.
- `CACHE_ENABLED=true`: cache-aside product reads, with an isolated namespace.

Stopping Redis is a separate failure scenario, not the no-cache baseline.
Rate limiting is disabled for these comparisons. Stock writes and reads retain
their consistency protection. Reservation expiry is outside this experiment.

The runner uses isolated database and cache names and records its settings, raw
JMeter samples, resource samples, counters, charts and summaries. Follow the
runner's generated manifest for dataset paths and target ports when opening the
plan in the GUI. `users`, `ramp`, `duration`, `loops`, `host`, `port` and `dataFile`
are JMeter properties configured with `-Jname=value` on the command line.

## Run the cache experiment

Prerequisites: Java 17, Maven, JMeter 5.6.3, MongoDB replica set `inventory-rs`
on port 27019, and Memurai/Redis. The Python coordinator uses `requests`,
`psutil` and `matplotlib` (versions are in `requirements.txt`). JMeter generates
all measured HTTP traffic; Python starts isolated services, seeds data and
collects counters/resources.

From the repository root in PowerShell:

```powershell
# If the separate test replica set is not already running:
powershell -ExecutionPolicy Bypass -File scripts/start-consistency-mongo.ps1

# Verify correctness and build a separate executable JAR:
powershell -ExecutionPolicy Bypass -File scripts/test-consistency.ps1
mvn.cmd -f backend/pom.xml -Pbenchmark package -DskipTests
python -m unittest discover -s scripts/tests -v

# 18 measured runs: 2 modes x 3 workloads x 3 repeats, plus warm-up/failure checks:
python scripts/run-jmeter-cache.py --redis-failure `
  --jmeter-home 'C:\Program Files\apache-jmeter-5.6.3' `
  --redis-exe 'C:\Program Files\Memurai\memurai.exe'
```

Default workloads are 5/25/100 concurrent users, with a 5-second ramp followed by
30 seconds of measurement per run, 1,000 products and three repeats. Use
`--duration 60` for a longer run. There is no think time, so these users represent
continuous load, not a count of human operators. A pilot can use
`--users 2 5 10 --duration 3 --ramp 1 --warmup 2 --repeats 1 --products 100`.

The default isolated backends use ports 8082 (off) and 8083 (on); the dedicated
Redis uses 6381. Occupied ports cause an abort. Fresh database names are recorded
in `manifest.json`; they remain available for inspection. The runner stops only
the services it started. `--keep-services` leaves them running for GUI exploration.
Existing apps, MongoDB and Redis on 6379 are not stopped or flushed.

Results go to `evaluation/load-results/<timestamp>-cache/`: `README.md`, chart,
`summary.csv/json`, per-run metrics and process resource samples, dataset, command
arguments and compressed raw JTL files. Representative JMeter HTML reports are
generated after measurement in the two highest-load first-repeat folders.
Large JTL files and generated HTML assets remain local and are Git-ignored.
Check that `manifest.json` has `finished_utc` and no `failure.json`; a failed run
must not be presented as a completed experiment. Pilot results are smoke checks.

## Open the plan in JMeter

For a retained deployment, use the dataset path and port in its manifest:

```powershell
& 'C:\Program Files\apache-jmeter-5.6.3\bin\jmeter.bat' `
  -t evaluation/jmeter/product-cache.jmx `
  -JdataFile=C:/absolute/path/to/run/products.csv -Jport=8083 `
  -Jusers=5 -Jramp=5 -Jduration=35
```

The GUI exposes the Thread Group, CSV Data Set, HTTP sampler and response
assertions for inspection. For measured CLI traffic use the same arguments with
`-n -l results.jtl -e -o html-report`; use a fresh output directory per run.
To reopen raw evidence, decompress `raw.jtl.gz` first (do not edit the source).

## Remaining component comparisons

The cache runner is deliberately one experiment. Follow `docs/evaluation-plan.md`
for the remaining separate comparisons: limiter off/on with 429 counted as an
expected rejection, single-store versus logical-shard stock workloads, shard
addition/removal with persisted stock checks, Snowflake versus UUID generation,
and Trie versus linear lookup on the same product dataset. Keep transactional
stock protection enabled throughout. A logical shard on the same MongoDB process
does not demonstrate the capacity gain or failure isolation of a physical shard.

References: [CLI guidance](https://jmeter.apache.org/usermanual/best-practices.html),
[component reference](https://jmeter.apache.org/usermanual/component_reference.html),
[HTML reports](https://jmeter.apache.org/usermanual/generating-dashboard.html).
