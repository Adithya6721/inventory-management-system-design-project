# What the five-path browser experiment shows

[Tables, graphs and raw-observation guide](README.md) · [Run instructions](../../autocomplete-five/README.md)

Branch: `experiment/autocomplete-five-modes`. The main run used real Chrome,
1,000 products, a 100-product initial Trie, three independent repetitions, cold
and warm passes, and the current 250 ms zero-hit fallback delay. It recorded
1,620 completed-query events. These are sequential browser observations, not
concurrent-user load or React rendering measurements.

Warm-pass averages:

| Mode | Work excluding debounce ms | Time to DOM update ms | Complete valid answers % | MongoDB reads / 243 queries |
| --- | ---: | ---: | ---: | ---: |
| Browser Trie only | 0.310 | 0.310 | 66.7 | 0 |
| Browser Trie + API fallback | 3.075 | 79.068 | 96.3 | 72 |
| Browser Trie + Redis/API fallback | 2.788 | 79.123 | 96.3 | 0 |
| API only | 10.624 | 10.624 | 100.0 | 243 |
| Redis + API | 9.058 | 9.058 | 100.0 | 0 |

All returned suggestions were valid and duplicate-free. A complete answer also
had to contain min(8, full-catalogue matches) products. Missing known products
therefore reduce coverage even when an empty response is fast. Redis errors were
zero; browser API counts reconciled with server cache/database counters.

The browser-only Trie missed eight deeper-catalogue queries because those
products were not in its initial 100. Both hybrids found those through the API
and learned them, so their warm positive requests became local hits.

The deliberate partial-local query exposed a separate limitation: one match was
already indexed, while ten were outside the index. The current fallback policy
only runs at zero matches. Both hybrids consequently stopped at one suggestion
instead of obtaining eight. Redis cannot repair a query that never reaches the
server. This explains their 96.3% complete-answer rate and 82.5% result-fill rate.

Redis removed all warm search database reads in both cache-enabled modes. In the
hybrid it mainly served repeated negative results: positive fallbacks had already
entered the Trie. Its small work-time gain did not improve the mean displayed
latency, which remained dominated by deliberate debounce waits. API-only modes
have no deliberate delay in this comparison; the work-time column separates
that policy difference from lookup work. A `-Debounce 0` control is reproducible.

Cold cache misses cost extra operations. For example, Redis/API cold work averaged
13.886 ms versus 10.742 ms for uncached API. A cache is not automatically a faster
path for a small hot local database query.

## Full-index control

The [one-repeat control](../20261025-174446-autocomplete-five/README.md) indexed
all 1,000 products. All five modes then achieved 100% complete valid answers.
Browser-only warm work averaged **0.195 ms**. That isolates the coverage loss to
the initial subset and fallback policy, rather than incorrect Trie matching.
It does not establish freshness after edits or the cost of indexing much larger
catalogues. The control is descriptive, with only one repetition.

## What to change next, if integrating a strategy

For a partial client index, consider API fallback when fewer than eight matches
are known, then merge/deduplicate results, rather than falling back only on zero.
A client that knows its catalogue is complete can use a different rule. Measure
that policy and rapid-typing cancellation separately before selecting a mode.

Alternatively, a complete browser index is very fast for this 1,000-product
fixture, but needs an update/freshness mechanism and catalogue-size limits.
Redis search caching can reduce database work across requests; production use
would need cache invalidation/versioning tied to product changes.

The experiment API uses an immutable indexed token projection so matching rules
agree across all modes. It is opt-in and separate from normal substring search.
Production UI/search and legacy product-detail caching were not changed. The
full backend correctness suite passed **90 tests**, including five new cache/
token tests; the experiment-owned services were stopped after measurement.
