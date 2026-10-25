# Five autocomplete paths in Chrome

Catalogue: 1000; initial browser index: 100; 3 independent repetitions; 27 queries per cold pass, 3 repeated warm passes; debounce: 250 ms.

Cache means a dedicated Redis search-result cache. All modes use the same token-prefix rules and top-eight limit. HTTP browser caching is disabled. The normal product search endpoint and operational UI are unchanged. This opt-in API queries a MongoDB token projection with a multikey token index.

| Mode | Phase | Queries | Work mean ms | Work p95 ms | Display mean ms | Correct queries % | Valid result fill % | API calls | Mongo reads | Redis hits |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Browser Trie only | cold | 81 | 0.488 | 1.300 | 0.488 | 66.7 | 62.5 | 0 | 0 | 0 |
| Browser Trie only | warm | 243 | 0.310 | 0.890 | 0.310 | 66.7 | 62.5 | 0 | 0 | 0 |
| Browser Trie + fallback | cold | 81 | 5.759 | 11.700 | 157.630 | 96.3 | 82.5 | 48 | 48 | 0 |
| Browser Trie + fallback | warm | 243 | 3.075 | 10.400 | 79.068 | 96.3 | 82.5 | 72 | 72 | 0 |
| Browser Trie + Redis + fallback | cold | 81 | 12.501 | 17.200 | 164.084 | 96.3 | 82.5 | 48 | 48 | 0 |
| Browser Trie + Redis + fallback | warm | 243 | 2.788 | 9.600 | 79.123 | 96.3 | 82.5 | 72 | 0 | 72 |
| API only | cold | 81 | 10.742 | 14.400 | 10.742 | 100.0 | 100.0 | 81 | 81 | 0 |
| API only | warm | 243 | 10.624 | 15.980 | 10.624 | 100.0 | 100.0 | 243 | 243 | 0 |
| Redis + API | cold | 81 | 13.886 | 18.300 | 13.886 | 100.0 | 100.0 | 81 | 81 | 0 |
| Redis + API | warm | 243 | 9.058 | 13.200 | 9.058 | 100.0 | 100.0 | 243 | 0 | 243 |

## How to read this

Work time includes Trie lookup, actual fetch/JSON parsing if used, incremental indexing and DOM/layout update; it excludes deliberate debounce waiting. Display time includes that waiting, starting from the completed-query input event. Neither metric claims to measure pixels reaching a physical screen.
Initial catalogue download and Trie construction are recorded separately in each point. Browser-only means no API search per query; the initial index still needs catalogue data.
Cold starts with a new browser index and a unique empty Redis query namespace. Warm repeats the same queries in the same client. Positive API results are added to the Trie, so repeated positive fallbacks can become local hits. Negative results remain empty locally; Redis can cache those empty lists.
Correct query means every returned product is a valid token-prefix match and the number returned equals min(8, full-catalogue matches). Fill compares valid returned counts with that expected top-eight count. No ranking/order equivalence is claimed. Empty Trie results for unloaded matching products count as incomplete, not successful fast answers.
Query classes: 8 initially indexed SKU prefixes, 8 deeper-catalogue SKU prefixes, 8 absent prefixes, 2 broad prefixes, and one partial-local prefix with one indexed match and ten outside the original 100. Zero-hit fallback does not fetch more when a local match already exists; its incomplete result is measured rather than hidden. See queries.csv for each class and phase. Expected sets are computed outside timed search work.
The server has a warm-up before measured requests; cold refers to data caches, not cold process/OS/disk. Mode order rotates across repetitions. One actual Chrome tab sends sequential completed-query events. This is not a JMeter concurrency test, a keystroke cancellation test, or the React component itself.
The catalogue is immutable during measurement. Snapshot revision and run tokens isolate Redis keys; all keys expire after five minutes. This does not implement normal product-edit search-cache invalidation.
resources.csv samples backend, MongoDB, Redis, driver and Chrome. Chrome RSS sums its process tree and can count shared pages repeatedly; browser heap includes the oracle and harness. Source hashes and settings are in manifest.json; browser version is saved separately.

![Browser result page](browser-results.png)

Reproduce: `powershell -ExecutionPolicy Bypass -File scripts/test-autocomplete-five.ps1`. Change `-Initial 1000` to compare a fully loaded browser index.

## Comparison graphs

![Five-mode comparison](comparison.png)

Bars pool the recorded queries from the independent repetitions. These are descriptive measurements, not confidence intervals. Cold means a fresh index/query namespace; warm means repeated queries in the same client.

## Warm queries by class

| Mode | Query class | Work mean ms | Display mean ms | Complete answers % | API calls |
| --- | --- | ---: | ---: | ---: | ---: |
| Trie | initial | 0.360 | 0.360 | 100.0 | 0 |
| Trie | deeper | 0.201 | 0.201 | 0.0 | 0 |
| Trie | missing | 0.196 | 0.196 | 100.0 | 0 |
| Trie | broad | 0.928 | 0.928 | 100.0 | 0 |
| Trie | partial_local | 0.456 | 0.456 | 0.0 | 0 |
| Trie + API | initial | 0.618 | 0.618 | 100.0 | 0 |
| Trie + API | deeper | 0.489 | 0.489 | 100.0 | 0 |
| Trie + API | missing | 8.932 | 265.408 | 100.0 | 72 |
| Trie + API | broad | 1.067 | 1.067 | 100.0 | 0 |
| Trie + API | partial_local | 0.578 | 0.578 | 0.0 | 0 |
| Trie + Redis/API | initial | 0.537 | 0.537 | 100.0 | 0 |
| Trie + Redis/API | deeper | 0.518 | 0.518 | 100.0 | 0 |
| Trie + Redis/API | missing | 8.064 | 265.696 | 100.0 | 72 |
| Trie + Redis/API | broad | 0.939 | 0.939 | 100.0 | 0 |
| Trie + Redis/API | partial_local | 0.444 | 0.444 | 0.0 | 0 |
| API | initial | 11.460 | 11.460 | 100.0 | 72 |
| API | deeper | 10.196 | 10.196 | 100.0 | 72 |
| API | missing | 9.735 | 9.735 | 100.0 | 72 |
| API | broad | 12.022 | 12.022 | 100.0 | 18 |
| API | partial_local | 11.678 | 11.678 | 100.0 | 9 |
| Redis/API | initial | 8.789 | 8.789 | 100.0 | 72 |
| Redis/API | deeper | 9.229 | 9.229 | 100.0 | 72 |
| Redis/API | missing | 8.704 | 8.704 | 100.0 | 72 |
| Redis/API | broad | 10.144 | 10.144 | 100.0 | 18 |
| Redis/API | partial_local | 10.511 | 10.511 | 100.0 | 9 |

## Bootstrap and cache costs

| Mode | Initial download mean ms | Trie construction mean ms | Warm Redis lookup mean ms |
| --- | ---: | ---: | ---: |
| Trie | 18.600 | 2.133 | N/A |
| Trie + API | 8.800 | 2.533 | N/A |
| Trie + Redis/API | 10.567 | 1.833 | 1.873 |
| API | 0.000 | 0.000 | N/A |
| Redis/API | 0.000 | 0.000 | 2.099 |

## Whole-workload process resources

| Process | Mean host CPU % | Peak RSS MiB |
| --- | ---: | ---: |
| backend | 1.44 | 424.10 |
| driver | 0.05 | 49.93 |
| chrome | 2.98 | 805.48 |
| redis | 0.02 | 38.45 |
| mongodb | 0.55 | 147.99 |

Resources cover the workload as a whole, including browser control/GC and shared MongoDB background work. Chrome sums its process tree; shared pages may be counted repeatedly. No per-mode CPU comparison is inferred from this table.
