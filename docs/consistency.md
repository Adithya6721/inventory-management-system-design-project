# Consistency hardening

Branch: `consistency/hardening`. Performance experiments follow correctness checks.

## Problems addressed

1. A stock mutation could succeed while the movement log, low-stock update or order
   save failed. Partial orders/transfers could survive a failed request.
2. Concurrent creation requests could both reserve/deduct stock before one lost
   the unique idempotency-key race.
3. Purchase receipt keys were ignored, so retrying a partial receipt credited it
   twice. Duplicate product lines could bypass the remaining-quantity check.
4. Sales completion/cancellation ignored false stock-operation results and could
   save a success status without the corresponding stock change.
5. Transfer completion accepted an INITIATED transfer without proving dispatch.
6. Stock cache invalidation could fail or race with a reader filling an old value.
7. Migration locked each stock operation, but did not hold membership fixed for
   the surrounding order/receipt/transfer transaction.
8. First-time manual adjustment used a replacing save that could overwrite a
   concurrent creator. Concatenated IDs could collide for different pairs with
   underscores; unchecked Java addition could overflow during adjustments.

## Transaction boundary

Spring MongoDB transactions wrap sales, purchases, transfers and inventory service
mutations. Stock, movement logs, alerts, receipt identities and order/transfer
state commit together or roll back together. Transactions use snapshot reads and
majority write concern. Database conflicts replace per-service JVM-wide locks.

Each logical shard template shares the core MongoDatabaseFactory and session;
creating an unrelated factory per database would silently put stock outside the
core transaction. A transaction keeps the router's inventory read lock until
after commit/rollback, so migration cannot copy half-finished business work.

The application now requires a replica set or MongoDB sharded-cluster deployment
and rejects standalone mode at startup. A one-member replica set enables atomic
transactions for the prototype; it does not provide machine-failure redundancy.
All logical databases must be in the same MongoDB deployment. Transactions do not
span independently configured MongoDB clusters.

Concurrent writes can abort with a transient transaction conflict. The API returns
409 for recognised retryable conflicts and duplicate keys. Retry the entire
request with the SAME idempotency key, never an isolated stock write. An uncertain
transaction commit returns 503 and requires reconciliation/retry with the same
key. The server does not blindly rerun a transaction with an unknown commit outcome.

Idempotency keys are trimmed; blank keys mean absent. Existing keys are checked
against the original payload. A different payload with the same key returns 409.
Without a key, repeated creation requests are distinct operations; callers must
retain a key for retry-safe sales, transfer and purchase creation.

The three creation forms now retain a key for an unchanged pending request in
sessionStorage (with an in-memory fallback). Failed requests and page reloads
reuse it; successful acknowledgements clear it, so a later intentional creation
gets a new key. Four Node tests verify reuse, scope/payload separation, stale
acknowledgement handling and restored session state. Run them with:

```powershell
node --test frontend/src/services/idempotency.test.mjs
```

Partial/itemised receipts require an idempotencyKey. Receipt keys and canonical
payload fingerprints are stored on the purchase order in the stock transaction.
Repeated lines are summed before validating remaining quantities. The same key
with another quantity is rejected. Empty/no receipt items mean receive all
remaining stock; this operation has a stable implicit identity. The frontend's
receive-all action uses this form. The receipt controller also validates payloads.

Stock availability reads bypass Redis and read authoritative inventory. Redis
remains available for product metadata. Legacy stock cache entries are invalidated
after commit, never published as authoritative availability. A unique partial
index permits at most one ACTIVE low-stock alert per product/warehouse pair.

## Running locally

Do not convert the normal local MongoDB merely to run these checks. Start an
isolated instance with data/logs under the project's ignored `.verification/`:

```powershell
.\scripts\start-consistency-mongo.ps1
.\scripts\test-consistency.ps1
```

The script prints its PID and refuses to start on an occupied port. Reuse that
known instance for subsequent tests. Stop the printed PID when finished; data is
retained. An existing standalone service on port 27017 is not changed.

`ConsistencyMongoIntegrationTest` clears only its explicitly named
`inventory_consistency_test*` databases' test collections. Sharding tests use
`inventory_member2_test*`. The runner gives the existing end-to-end test isolated
`inventory_consistency_e2e*` core AND shard databases. It restores environment
variables when finished and exports results to a timestamped directory under
`evaluation/results/`. Do not run multiple copies against these fixed test
names concurrently. The runner script was syntax checked; its underlying Maven
command and result collector were executed separately for this recorded run.

Compose now starts MongoDB with `--replSet inventory-rs`, initializes the set in a
one-shot service, waits for a primary, and only then starts the backend. Docker
configuration still needs validation on a machine with Docker installed. Host-side
clients should use directConnection=true when the advertised Compose hostname is
not resolvable outside its network.

## Test inventory

The test scope is explicit; passing tests is not proof of all possible failures.

| Area | Required cases | Current coverage |
| --- | --- | --- |
| Stock invariants | Non-negative physical/reserved stock, reserved <= physical, failed atomic updates | Existing unit/sharding tests; real concurrent sales test |
| Real contention | 100 orders for 10 units, persisted orders and reservations reconcile | New replica-set integration test |
| Order atomicity | Audit failure, final order-save failure, second item unavailable on another logical shard | New failure-injection integration tests |
| Sales lifecycle | Deduction/release failure must not change order status | New unit regression tests |
| Creation idempotency | Concurrent same key, one effect; different payload conflict; blank keys | Integration sales concurrency and unit payload checks; additional entity cases below |
| Receipts | Replay, changed payload, duplicate-line over-receipt, final save failure, concurrent same-key delivery | New unit and integration tests |
| Transfers | Reject undisbursed completion; save failure and retry; concurrent delivery/cancellation conserves stock | New unit and integration tests |
| Cache | Authoritative availability ignores cached stock | New integration test; product-cache behaviour has existing tests |
| Alerts | Atomic rollback and unique active alert under real contention | New integration checks and existing low-stock tests |
| Migration | Membership fixed until commit, preservation/add/remove/restart, interrupted-copy recovery | New transaction/migration integration test; existing router and Mongo tests |
| Deployment | Replica-set transactions required | Startup guard and isolated real replica-set execution |

Additional executed checks cover concurrent sales completion/cancellation,
concurrent transfer creation with one key, multi-item deduction rollback,
receive-all replay, blank keys, overflowing adjustment, ambiguous legacy ID pairs,
and a snapshot started before a membership change. The new database suite has
20 passing tests. The full backend run has 82 passing tests with no skips; see
`../evaluation/results/20261024-consistency/README.md` for per-class timings and
raw observations. The six new initial regression cases all failed on the old code.

Before broad performance conclusions, extend the matrix with:

- Purchase creation and transfer creation payload mismatch/reordered item cases.
- Multi-item cancellation failure after the first item, with rollback (completion
  rollback is already covered).
- Disconnect/kill after writes but before response, then retry from a new process.
- Real primary step-down/unknown-commit scenarios on a multi-member replica set.
- Backend restart during migration, beyond the current simulated failure tests.
- Concurrent first-time adjustment creation and overflow on stock credits
  (adjustment overflow and ID ambiguity are already covered).
- Simultaneous product/warehouse deactivation policy and supplier import boundaries.
- Full HTTP tests of validation and 409/503 classification, not only service calls.

Record these cases as pending until executed; do not infer them from one successful
contention run. The initial tests mock Redis in the new consistency suite to
isolate database correctness. They do not establish Redis outage performance.

## Remaining architectural limits

- One backend coordinates shard membership. Its router lock is not distributed;
  multiple backend processes must not change membership concurrently.
- Logical shards still share one database deployment and its resources.
- Order/movement transaction-data partitioning is a separate remaining requirement.
- API clients beyond the provided forms must retain stable creation keys;
  historical failed work from the old implementation is not automatically repaired.
- Receipt fingerprints grow with deliveries; a separate bounded receipt ledger is
  appropriate if an order can accumulate an exceptionally large number of receipts.
- Existing duplicate ACTIVE alerts must be reconciled before the new unique index
  can be created. The application must not silently delete historical alerts.
- Product metadata cache freshness is separate from authoritative stock consistency.

The experiment protocol and baseline matrix remain in `evaluation-plan.md`.

Transaction requirements and session participation were checked against the
[MongoDB transaction documentation](https://www.mongodb.com/docs/manual/core/transactions/)
and the installed Spring Data MongoDB 4.3.4 API. A single shared factory/session is
essential for the cross-database rollback assertions in these tests.
