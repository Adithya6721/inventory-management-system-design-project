# EfficientStock: short links and autocomplete checks

Start the app using [the startup guide](start-demo.md). Open **Architecture** for
the URL shortener, and **Autocomplete** for the Trie checks.

## Short links

Enter an HTTP or HTTPS destination and click **Shorten URL**. The result is a
clickable link on the running backend (or frontend proxy), not an example domain.
Copy it or open it in another tab. Local addresses are supported, for example
`http://127.0.0.1:8081/api/warehouses` in the seeded demo.

- `POST /api/links`, body `{"url":"https://supplier.example/catalog?sku=ABC"}`:
  creates a mapping and returns code, destination, relative redirect path,
  creation time and redirect count. Status 201.
- `GET /r/{code}`: looks up the mapping, atomically increments its redirect
  counter and returns HTTP 302 with the original destination in `Location`.
- `GET /api/links/{code}`: shows the saved destination and redirect count.
- Unknown codes return 404. Invalid URLs return 400. Both creation and redirects
  go through the existing rate limiter when it is enabled.

The code is the existing Snowflake numeric ID encoded in Base62. MongoDB stores
the mapping in the core database's `short_links` collection; `_id` is the unique
code. Inserts cannot overwrite an existing link. Collisions retry with another
ID, up to three attempts. The mapping persists across restarts. There is no
expiry or custom public domain in this version. Redirects use MongoDB directly;
the extra counter write trades some speed for simple, accurate counting.

Vite and Nginx proxy `/r/` as well as `/api/`. With `VITE_API_BASE_URL` set to
another origin, the returned clickable link uses that backend origin.

## Trie result checks

The old UI displayed `100%` when a query returned anything and `N/A` for most
empty results. It did not compare expected product IDs, and API failures were
replaced with an invented 24.5 ms time.

The updated table compares the complete Trie candidate set against an independent
linear token-prefix search over the exact same browser products:

- `Pass · 3/3 matches`: both searches found the same three product IDs.
- `Pass · no matches`: neither search found a match; this is a correct result.
- `Fail`: products are missing or unexpected, or the Trie returned duplicates.

Local timings average 100 top-ten lookups without a fabricated minimum. A zero
clock reading is shown as below timer resolution. The API column measures one
real request and reports failures directly. It uses the normal substring-search
API, so its counts can differ from token-prefix matches and its catalog can be
larger. Those API counts do not determine Trie correctness.

The screen displays indexed versus total product counts. It uses its own Trie
and leaves the global search index intact.

## Reproduce checks

```powershell
node --test frontend/src/services/autocompleteBenchmark.test.mjs frontend/src/services/idempotency.test.mjs
npm.cmd --prefix frontend run build
powershell -ExecutionPolicy Bypass -File scripts/test-consistency.ps1
```

The backend suite includes a real-Mongo short-link test checking a saved mapping,
twenty concurrent redirects, exact destination preservation, atomic counters,
invalid input, and missing codes. It uses a separate test database. Unit tests
also cover collision handling and rejection of unsupported URL schemes.
