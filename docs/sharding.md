# Member 2 Sharding and Consistent Hashing

Member 2 is implemented in `backend/src/main/java/com/example/inventory/sharding`.
It routes the existing inventory APIs to three logical MongoDB database shards,
with a fourth configured as a standby for the scaling demonstration.
This is application-level logical sharding on one MongoDB instance. It does not
use MongoDB's native sharded-cluster infrastructure.

## Run

Use Java 17, Maven and a running MongoDB. From the repository root:

```powershell
cd backend
mvn spring-boot:run
```

The API listens on `http://localhost:8080`; MongoDB defaults to `localhost:27017`.
The existing `MONGO_HOST`, `MONGO_PORT`, `MONGO_DB` and `PORT` environment variables
still apply. Alternatively, `docker compose up --build` starts the existing stack
with sharding enabled by default.

From a second terminal at the repository root:

```powershell
.\scripts\demo-sharding.ps1
```

The demo creates 30 products in three warehouses (90 inventory records), displays
per-shard record/request counts, adds shard 4, checks every record, removes shard 4,
and checks that the original routing and stock values are restored. It leaves the
sample records available for inspection. `-ProductCount 100` creates 300 records;
`-BaseUrl http://localhost:8081` selects another API instance. Run against one
backend with shards 1, 2 and 3 initially active.

## Requirement mapping

| Requirement | Implementation |
| --- | --- |
| Multiple logical database shards | `MongoShard`, one database per configured shard |
| Consistent hashing with virtual nodes | Immutable `ConsistentHashRing`, SHA-256, 32 virtual nodes per shard |
| Composite routing key | Exact string `productId:warehouseId` |
| Server addition/removal and reassignment | Journaled migration in `ShardedInventoryPersistenceRouter` |
| Member 1 integration | Primary `InventoryPersistencePort` bean used by all existing inventory services |
| Distribution and remapping evidence | `/api/shards`, migration response metrics, automated tests and demo |

Each virtual node hashes `shardId#virtualNodeIndex`. The record key hashes to an
unsigned 256-bit position. The first virtual node at or clockwise after that
position owns the record; lookups wrap to the first token past the end of the ring.
Sorted server IDs and UTF-8 SHA-256 make placement stable across restarts and
configuration order. IDs cannot contain `:` so composite keys are unambiguous.

The default of 32 virtual nodes per shard gives 96 ring positions with three active
shards, or 128 with four. Virtual nodes are routing entries, not additional databases
or copies of records. More virtual nodes generally improve distribution at the cost
of more routing metadata; the count is a tuning parameter, not a project requirement.

Point reads and every stock mutation use the same ring and composite key. Queries
by product only or warehouse only query every active shard and combine the results.
Each shard reuses Member 1's `MongoInventoryPersistenceAdapter`, including the
conditional atomic stock updates. Every shard has the unique product/warehouse
index and the individual product and warehouse indexes. Core products, warehouses,
orders, alerts and audit records remain in the existing core database.

## Management API

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/shards` | State, active/pending membership, virtual-node count and per-shard records/requests |
| GET | `/api/shards/route?productId=prod_1&warehouseId=wh_1` | Composite key, SHA-256 hash, owner and database |
| POST | `/api/shards/shard-4` | Activate the configured standby and migrate records |
| DELETE | `/api/shards/shard-2` | Drain the shard, migrate its records and remove it from the ring |
| POST | `/api/shards/rebalance` | Resume an interrupted migration; no change if there is none pending |

For example:

```powershell
Invoke-RestMethod http://localhost:8080/api/shards
Invoke-RestMethod -Method Post http://localhost:8080/api/shards/shard-4
Invoke-RestMethod -Method Delete http://localhost:8080/api/shards/shard-4
```

A successful migration returns `previousShards`, `activeShards`, `recordsScanned`,
`recordsMoved`, `remappedPercent` and `durationMs`. Adding a shard moves only records
whose new owner is the new shard. Removing a shard moves only its records.
Existing stock, reservations, version, timestamps, document ID and extra fields
are copied exactly. No empty inventory rows are created by a routing lookup.

Unknown shard IDs return 400, duplicate additions return 409, removing an inactive
configured shard returns 404, and removing the last active shard returns 409.
The endpoints are for the local project demonstration and inherit the repository's
current lack of authentication; keep them behind an authenticated admin boundary
if the application is later exposed to other users.

Request counters count persistence calls sent to each shard since backend startup,
including failed stock conditions. A query by product or warehouse contributes one
call to each active shard. Management lookups, migrations and statistics do not
increment them. Record counts are physical database document counts.

## Migration and recovery

The coordinator takes a fair write lock before changing membership. In-flight
inventory operations complete first; subsequent inventory operations wait until
the migration finishes. Normal stock operations take a read lock and can run
concurrently with one another.

1. Save the pending target membership in `shard_topology` in the core database.
2. Build the target ring and ensure destination indexes exist.
3. Scan current owners and copy records whose owner changes. Keep the originals.
4. Persist the target membership as the new authority, retaining the pending marker.
5. Delete copies outside the new owner's database, including drained shards.
6. Clear the pending marker and allow inventory requests to proceed.

Copying is idempotent. Before authority changes, retries read from the old owner;
after it changes, retries read from the new owner and finish cleanup. If a migration
fails, inventory operations return 503 until `POST /api/shards/rebalance` succeeds
or the application restarts and completes recovery. The retry reloads persisted
metadata, including when a write succeeded but its acknowledgement was lost.
Recovery reports count the records processed in that recovery pass.

The active membership survives restarts. `initial-shards` is used only when no
topology document exists. The shard catalog and virtual-node count are saved with
the topology; changing those settings on an existing deployment fails startup
instead of silently rehashing stored data.

`SHARD_VIRTUAL_NODES` overrides the default count of 32. For a dataset previously
created with 128 virtual nodes per shard, keep `SHARD_VIRTUAL_NODES=128` until a
planned data migration is performed. Changing only the count changes ownership for
many keys; it does not safely migrate an existing dataset. Fresh deployments use 32.

## Configuration and boundaries

`application.yml` enables sharding with three active shards and one standby:

| Shard | Default database | Environment override | Initial state |
| --- | --- | --- | --- |
| shard-1 | inventory_shard_1 | SHARD_DB_1 | Active |
| shard-2 | inventory_shard_2 | SHARD_DB_2 | Active |
| shard-3 | inventory_shard_3 | SHARD_DB_3 | Active |
| shard-4 | inventory_shard_4 | SHARD_DB_4 | Standby |

Set `SHARDING_ENABLED=false` to use Member 1's original single-database adapter.
This selects a separate storage mode; it does not migrate inventory between the
core database and shard databases. Start this implementation with a fresh dataset
or explicitly migrate any inventory already stored in the original core database
before enabling it. Do not toggle modes on a populated deployment expecting the
same inventory view.

Use exactly one backend process for membership changes. The lock is local to the
JVM, so other processes sharing these shard databases would need a distributed
membership coordinator and a fencing/locking protocol. Give independent application
deployments distinct shard database names, even if they use different core databases.
Migrations load one shard's documents into memory and pause inventory requests, which
suits this college prototype. Large datasets need streaming, batching and an online
migration protocol. Removal is graceful draining and requires the source data to be
reachable; it does not provide replication or automatic failover after data loss.
Cross-shard order/transfer consistency continues to use Member 1's existing workflows.

## Validation

From `backend`, run the unit tests without MongoDB:

```powershell
mvn test '-Dtest=!InventoryIntegrationTest,!ShardingMongoIntegrationTest'
```

With a disposable MongoDB running, run all tests, including the new MongoDB tests:

```powershell
mvn test -Dsharding.mongo.tests=true
```

Use `-DMONGO_PORT=27028` for a test MongoDB on another port. The new integration
tests use the `inventory_member2_test` core database and
`inventory_member2_test_shard_1` through `_4`; they clear inventory in those test
shards before each case. Member 1's existing integration test uses the configured
core database and default shard databases, so use an isolated test MongoDB for the
full suite.

The 38-test suite passed on Java 17.0.17, MongoDB 8.2 and Windows on 2026-10-19.
Checks include clockwise lookup and wraparound, deterministic placement,
limited remapping, every persistence operation, queries across shards, preservation
of raw documents, addition/removal/restart, interrupted copy/commit/cleanup recovery,
and blocking concurrent mutations during a membership change. The original
purchase/sales/transfer API integration flow passes with sharding enabled.

Measured samples from that run with 32 virtual nodes per shard:

| Check | Observed result |
| --- | --- |
| Four-shard distribution of 20,000 deterministic keys | 4,430 / 4,186 / 6,038 / 5,346 |
| Add shard 4 to three-shard ring, 20,000 keys | 5,346 remapped (26.73%), all to shard 4 |
| Add shard 4 with 200 stored inventory records | 51 moved (25.5%); exact document preservation |
| Then remove shard 2 | 40 moved (20%); source emptied, reads correct |
| 100 concurrent MongoDB reservations for 10 units | 10 succeeded, 90 rejected; no overselling |

These are correctness/distribution samples, not a throughput benchmark. Use the
demo and actual workloads to collect further measurements for the project report.

For comparison, the earlier run with 128 virtual nodes per shard assigned the same
20,000 keys as 4,733 / 4,722 / 5,317 / 5,228. That configuration was more balanced
for this sample. The 32-node default uses fewer ring entries and still satisfies
the correctness and limited-remapping checks.
