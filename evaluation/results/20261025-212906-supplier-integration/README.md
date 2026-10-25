# Supplier merge verification

Measured locally against a new isolated core database and three active logical
inventory shards on the existing single MongoDB replica-set member. Redis was
enabled with its own cache namespace. Rate limiting and experiment APIs were off.
The seeded demo database was not used by this test.

Reproduce with services running:

```powershell
mvn.cmd -f backend/pom.xml -Pbenchmark package -DskipTests -q
python scripts/test-supplier.py
```

| Check | Result |
| --- | --- |
| Crawler parser, normalization, retries, browser endpoint checks | 9 passed |
| HTML pages fetched / failed | 4 / 0 |
| Distinct supplier products | 24 |
| Supplier purchase orders created | 3 |
| Warehouse stock immediately after import | 0 |
| Stock after receiving the Apex order | 555 |
| Movement entries after receipt | 8 |
| Repeated receipt duplicates stock or movements | No |
| Supplier category update evicts cached product details | Passed |
| One measured crawl | 283 ms, 84.81 products/s |

This is a correctness smoke test and a single crawl timing, not a statistically
controlled throughput benchmark. The legacy average latency field includes
politeness waits. See `summary.json` and the two test logs for assertions/results.

The separate consistency run in `../20261025-211741-736-consistency/` passed all
90 backend checks. The frontend production build and four browser idempotency
tests passed. Four Python result-parser tests also passed. A real headless Chrome
check of the seeded app displayed 24 products from four HTML pages, verified no
member/academic labels, and checked sidebar collapse/expand. Its screenshot is
`../../demo/supplier-crawler.png`.
