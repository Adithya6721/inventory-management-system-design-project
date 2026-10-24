# Initial MongoDB correctness run

Date: 2026-10-23, approximately 21:57 Asia/Kolkata.
Base revision: `7a3eb40`, plus the working-tree fix to the active-shard duplicate
assertion in `ShardingMongoIntegrationTest`.

Command, from `backend/`, with `RATELIMIT_ENABLED=false` in that command process:

```powershell
mvn test '-Dsharding.mongo.tests=true' '-Dtest=ShardingMongoIntegrationTest' -q
```

Java 17.0.17, Spring Boot 3.3.4, local MongoDB on port 27017, one backend JVM.
The database driver identified a standalone MongoDB server. Dedicated core/shard
test database names are configured in the test class. Redis remained selected;
its availability and resource use were not measured in this correctness run.

The first attempt exposed a nondeterministic test assertion: removing the random
product's owner could remove shard-4, making a subsequent add valid rather than a
duplicate. The assertion now selects a currently active shard. The corrected run
passed all three tests; see `surefire-summary.txt`.

Selected console observations from the successful run:

```text
Mongo migration: added=RebalanceResult[previousShards=[shard-1, shard-2, shard-3], activeShards=[shard-1, shard-2, shard-3, shard-4], recordsScanned=200, recordsMoved=51, remappedPercent=25.5, durationMs=155], removed=RebalanceResult[previousShards=[shard-1, shard-2, shard-3, shard-4], activeShards=[shard-1, shard-3, shard-4], recordsScanned=200, recordsMoved=40, remappedPercent=20.0, durationMs=149]
Mongo reservations: 100 attempts, 10 succeeded, 90 rejected
```

The reservation test directly invokes the persistence adapter with a 24-thread
executor. Rejected reservations return false; these are not 90 observed HTTP 409
responses. The API integration test separately checks API behaviour across
membership changes. Migration assertions compare every stored document field and
check placement, reservations and reads after migration/restart.

These are single-run correctness observations, not a repeated performance study.
Migration durations are implementation timings, not HTTP latency or measured
request-pause duration. No throughput, resource usage, three-level workload,
live-traffic migration or node-failure conclusion is supported by this run.
