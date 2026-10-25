# Five autocomplete paths

Branch: `experiment/autocomplete-five-modes`.

From the project root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test-autocomplete-five.ps1
```

This builds a separate benchmark JAR and runs all five strategies in **real
headless Chrome**, using the application's actual `ProductTrie` class and native
browser `fetch`. There is no Playwright/Puppeteer installation: Node 24's built-in
WebSocket drives Chrome's local DevTools protocol. Python handles data, processes,
counters and reports.

| Mode | Lookup path |
| --- | --- |
| Browser Trie only | Local Trie; a missing local match returns empty |
| Browser Trie + fallback | Trie; on zero matches, wait then query MongoDB through the API |
| Browser Trie + cache + fallback | Trie; on zero matches, wait then query API → Redis → MongoDB on a cache miss |
| API only | Every query calls the API and reads MongoDB |
| Cache + API | Every query calls the API; Redis hit or MongoDB miss/fill |

**Cache means Redis search-result caching**, including empty results. Browser
HTTP caching is disabled. This is separate from the application's existing
product-detail Redis cache.

The normal API searches substrings; the Trie searches token prefixes. To compare
equal results, this branch adds an opt-in experiment API that searches an indexed
MongoDB token projection with the same SKU/name/category prefix rules. Normal
search and the operational React component are unchanged. A separate DOM harness
mirrors the component's zero-hit fallback and 250 ms delay; this is not a full
React rendering/keystroke-cancellation test.

## What runs

- 1,000 generated products; the first 100 by product ID enter the initial Trie.
  This models the app's 100-product loading limit, not its exact newest-first
  catalogue ordering.
- 27 query prefixes: 8 indexed SKUs, 8 deeper SKUs, 8 absent values, 2 broad
  prefixes, and a prefix with only one of its 11 matches in the initial index.
- Three independent repetitions with rotating mode order. Each has one cold
  pass and three warm passes in the same client.
- Fresh Trie and Redis run namespace for every mode/repetition. API results
  populate the Trie in hybrid modes. Negative results stay empty locally;
  the Redis variants can remember those empty results.
- Identical limit of eight, with each suggestion checked against the full
  catalogue. Any invalid/duplicate product fails the run. Incomplete local
  results are reported as coverage loss, never counted as correct fast answers.

**Work time** includes actual Trie lookup or HTTP/JSON work, incremental indexing
and DOM/layout update. **Display time** adds the intentional fallback delay.
Bootstrap download/build time is separate. No artificial network delay is added.
These are completed-query events in one tab; timing does not claim physical
screen paint or concurrent-user throughput.

## Simple controls

```powershell
# A full browser index, to separate coverage loss from lookup speed:
powershell -ExecutionPolicy Bypass -File scripts/test-autocomplete-five.ps1 -Initial 1000 -Repeats 1

# Remove the deliberate fallback delay:
powershell -ExecutionPolicy Bypass -File scripts/test-autocomplete-five.ps1 -Debounce 0 -Repeats 1
```

Prerequisites: JDK 17/Maven, Python with requests/psutil/matplotlib, Node 24+, Chrome
and Memurai/Redis. Default paths match this machine. The Python runner accepts
`--chrome` and `--redis-exe` for other installations. It reuses the test MongoDB
replica set on 27019, or starts it through the existing guarded setup script if
the port is free. A replica set is required.

Only experiment-owned processes are stopped. Backends use 8082, dedicated Redis
6381, a localhost static page server 9003 and isolated Chrome debugging 9225.
Occupied ports cause an abort. Fresh database names and a separate Chrome profile
protect existing application data and browser sessions. The database is retained
for inspection. Redis keys expire after five minutes.

Results: `evaluation/results/<timestamp>-autocomplete-five/README.md`.
Look at summary.csv, queries.csv, per-mode JSON, resource samples, browser version
and verification.json. The browser page screenshot is also saved. Source hashes
and exact settings are in manifest.json.

The experiment endpoint exists only with `EVALUATION_ENABLED=true` and is bound
to localhost by the runner. Its catalogue is immutable during measurement; a
snapshot revision isolates rebuilt catalogue caches. It does not add production
search-cache invalidation or alter stock availability reads.

Browser automation references: [Chrome DevTools Runtime](https://chromedevtools.github.io/devtools-protocol/v8/Runtime/)
and [Node's built-in WebSocket](https://nodejs.org/docs/latest/api/globals.html#class-websocket).
