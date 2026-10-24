"""Turn saved component observations into readable tables and one chart."""
from collections import defaultdict
import json
import math
from pathlib import Path
import statistics
import sys


def render(folder):
    def read(name, default=None):
        path = folder / name
        return json.loads(path.read_text(encoding='utf-8')) if path.exists() else default
    manifest = read('manifest.json')
    points = read('http-results.json', [])
    ids, hashing, autocomplete = read('ids.json'), read('hashing.json'), read('autocomplete.json')
    migration = read('migration-check.json')
    lines = ['# Component evaluation results', '',
        'Run everything from the project root:', '',
        '```powershell', 'powershell -ExecutionPolicy Bypass -File scripts/test-components.ps1', '```', '',
        'Use `-Only limiter`, `-Only shards`, `-Only stock`, `-Only ids`, `-Only hash` or `-Only autocomplete` to reproduce one experiment.', '',
        f"Settings: {manifest['settings']['seconds']} measured seconds, {manifest['settings']['repeats']} repeat(s), 5/25/100 HTTP users by default. This is a quick local comparison. See [the instructions](../../QUICK-TESTS.md) for setup and longer runs.", '']
    charts = []
    def series(items, x, value):
        grouped = defaultdict(list)
        for item in items: grouped[item[x]].append(value(item))
        keys = sorted(grouped)
        return keys, [statistics.mean(grouped[k]) for k in keys]
    def mode_label(point):
        if point['experiment'] == 'limiter': label = 'on' if point['mode'] == 'limited' else 'off'
        else: label = 'single DB' if point['mode'] == 'single' else '3 logical shards'
        return label + (f" (repeat {point['repeat']})" if manifest['settings']['repeats'] > 1 else '')
    def table(headers, rows):
        lines.append('| ' + ' | '.join(headers) + ' |')
        lines.append('| ' + ' | '.join(['---'] * len(headers)) + ' |')
        lines.extend('| ' + ' | '.join(map(str, row)) + ' |' for row in rows)
        lines.append('')
    def f(value, places=2): return 'N/A' if value is None else f'{value:.{places}f}'
    limiter = [p for p in points if p['experiment'] == 'limiter']
    if limiter:
        lines += ['## 1. Rate limiter off versus on', '',
            'All threads in each run share one client bucket: capacity 10, refill 5/sec. The measurement excludes five warm-up seconds. Expected 429 responses are rejections, not successful product reads.', '']
        table(['Mode','Users','Offered/s','Actual/s','Admitted/s','Admitted p95 ms','Rejected %','Pacing p95 lag ms','Unexpected errors'],
            [[mode_label(p),p['users'],p['offered_rps'],f(p['attempted_rps']),f(p['accepted_rps']),f(p['accepted_p95_ms']),f(p['rejection_pct']),f(p['p95_scheduling_lag_ms']),p['unexpected_errors']] for p in limiter])
        def limiter_chart(ax):
            for mode in ['single','limited']:
                rows = [p for p in limiter if p['mode'] == mode]
                ax.plot(*series(rows,'offered_rps',lambda p:p['accepted_rps']), 'o-', label='Limiter ' + ('on' if mode == 'limited' else 'off'))
            ax.set(xlabel='Offered requests / second', ylabel='Admitted reads / second', title='Rate limiting'); ax.legend(fontsize=8)
        charts.append(limiter_chart)
    reads = [p for p in points if p['experiment'] == 'inventory-reads']
    if reads:
        lines += ['## 2. Single database versus three logical shards', '',
            'Both have 100 products × 5 warehouses and 1,000 units per record. Requests alternate point lookups and product-wide queries. Cache and limiter are off. Shards share one MongoDB process; this measures routing/query overhead, not physical scale-out.', '']
        table(['Mode','Users','Point mean ms','Point p95 ms','Scatter mean ms','Scatter p95 ms','Accepted reads/s','Unexpected errors'],
            [[mode_label(p),p['users'],f(p['by_request']['point']['mean_ms']),f(p['by_request']['point']['p95_ms']),f(p['by_request']['scatter']['mean_ms']),f(p['by_request']['scatter']['p95_ms']),f(p['accepted_rps']),p['unexpected_errors']] for p in reads])
        def reads_chart(ax):
            for mode in ['single','sharded']:
                for kind in ['point','scatter']:
                    rows = [p for p in reads if p['mode'] == mode]
                    ax.plot(*series(rows,'users',lambda p:p['by_request'][kind]['mean_ms']), 'o-' if kind == 'point' else 's--', label=f'{mode}: {kind}')
            ax.set(xlabel='Concurrent users', ylabel='Mean milliseconds', title='Inventory reads'); ax.legend(fontsize=7)
        charts.append(reads_chart)
    stock = [p for p in points if p['experiment'] in ['distributed-sales','hot-stock']]
    if stock:
        lines += ['## 3. Stock consistency under concurrent orders', '',
            'Each case sends 100 one-unit orders. Distributed sales use 100 distinct stock records, each initially 1,000 units. Hot-stock orders share one record containing only 10 units. No automatic retries are hidden in these counts.', '']
        table(['Case','Mode','Users','Reserved orders','Insufficient stock','Retryable writes','Reserved orders/s','Final reserved units','Order/audit/stock match'],
            [[p['experiment'],mode_label(p),p['users'],p['outcomes'].get('reserved',0),p['outcomes'].get('insufficient_stock',0),p['outcomes'].get('write_conflict',0),f(p['accepted_rps']),read(p['folder']+'/stock-check.json')['persisted_reserved_units'],read(p['folder']+'/stock-check.json')['passed']] for p in stock])
        lines += ['Retryable write conflicts mean the database safely rejected competing transactions; they do not imply stock was exhausted. Without retries a burst can reserve fewer than 10 units. Every saved `stock-check.json` reconciles persisted stock with returned orders and reservation audits. Global MongoDB snapshots additionally check for orphan orders/audits, invalid stock and duplicate/wrong physical owners.', '']
    if migration:
        lines += ['## 4. Add and remove a shard during read traffic', '',
            f"Full documents preserved: **{migration['records_preserved']}**. Duplicate stock copies: **{migration['duplicates']}**. Maximum observed HTTP latency: **{migration['max_http_ms']:.0f} ms**.", '']
        table(['Change','Scanned records','Moved records','Remapped %','Server migration ms','Overlapping requests'],
            [[e['action'],e['result']['recordsScanned'],e['result']['recordsMoved'],f(e['result']['remappedPercent']),e['result']['durationMs'],migration['requests_overlapping_changes'][e['action']]] for e in migration['events']])
        point = next(p for p in points if p['experiment'] == 'shard-changes')
        lines += [f"HTTP outcomes: `{point['outcomes']}`. A `migration_conflict` is an explicit retryable 409 from a transaction whose snapshot predates the new routing; it is not counted as an admitted read. Other HTTP/assertion errors: {point['unexpected_errors']}.", '',
            'The final stock documents, versions, timestamps, reservations, orders and audits match the before snapshot exactly. The source remained reachable during graceful removal; this is not a physical shard-failure or failover test.', '']
        def migration_chart(ax):
            stages = [('before',read('migration-before.json')['recordCounts'])]
            for event in migration['events']:
                stages.append((event['action'],{s['shardId']:s['records'] for s in event['status']['shards']}))
            for stage, counts in stages:
                if stage == 'before': counts = {('shard-'+k.rsplit('_',1)[-1]):v for k,v in counts.items() if '_shard_' in k}
                ax.plot(range(1,5), [counts.get('shard-'+str(i),0) for i in range(1,5)], 'o-',label=stage)
            ax.set(xticks=range(1,5), xlabel='Logical shard', ylabel='Records',title='Shard membership / distribution'); ax.legend(fontsize=8)
        charts.append(migration_chart)
    if ids:
        lines += ['## 5. Snowflake versus full UUID generation', '',
            '250,000 IDs per point; actual application Snowflake shared across threads. Generation is timed after warm-up; collision checking is outside that timed region. These are direct Java microbenchmarks.', '']
        table(['Generator','Threads','Repeat','IDs/s','Collisions','Process CPU ms','Mean ID length'],
            [[p['mode'],p['threads'],p['repeat'],f(p['ids_per_second'],0),p['collisions'],f(p['cpu_milliseconds']),f(p['mean_id_characters'],0)] for p in ids['results']])
        lines += [f"Controlled fixed-clock node check: `{ids['node_configuration_check']}`. Duplicate-node collisions are intentional misconfiguration, not collisions in the normal benchmark.", '']
        def ids_chart(ax):
            for mode in ['uuid','snowflake']:
                rows=[p for p in ids['results'] if p['mode']==mode]
                ax.plot(*series(rows,'threads',lambda p:p['ids_per_second']/1e6),'o-',label=mode)
            ax.set(xlabel='Threads',ylabel='Million IDs / second',title='ID generation'); ax.legend(fontsize=8)
        charts.append(ids_chart)
    if hashing:
        lines += ['## 6. Hashing baseline and distribution', '',
            'The actual ring is compared with SHA256(key) modulo shard count. Virtual-node and key-count levels use identical deterministic keys. Hot traffic is an explicit simulation: 80% targets one key.', '']
        table(['Keys','Virtual nodes','Ring add remap %','Modulo add remap %','Record imbalance (CV)','Record counts'],
            [[p['keys'],p['virtual_nodes'],f(p['ring_add_remapped_pct']),f(p['modulo_add_remapped_pct']),f(p['coefficient_of_variation'],3),str(p['record_distribution'])] for p in hashing['results']])
        lines += ['The detailed JSON includes the hot-key request distribution. More balanced record placement cannot split a single hot inventory key across owners. Removing the newly added fourth shard moves its keys back to the original owners.', '']
        def hash_chart(ax):
            rows=[p for p in hashing['results'] if p['virtual_nodes']==32]
            ax.plot([p['keys'] for p in rows],[p['ring_add_remapped_pct'] for p in rows],'o-',label='Ring, 32 virtual nodes')
            ax.plot([p['keys'] for p in rows],[p['modulo_add_remapped_pct'] for p in rows],'s--',label='Modulo')
            ax.set(xscale='log',xlabel='Keys',ylabel='Remapped %',title='Adding the fourth shard'); ax.legend(fontsize=8)
        charts.append(hash_chart)
    if autocomplete:
        lines += ['## 7. Trie versus linear autocomplete', '',
            'Both use the same token-prefix semantics and complete catalogue. Timing uses an external high-resolution clock around the actual top-8 lookup. Full candidate sets are compared separately; returned order has no ranking contract. These Node timings exclude HTTP and browser rendering.', '']
        groups=defaultdict(dict)
        for p in autocomplete['results']: groups[(p['products'],p['kind'],p['repeat'])][p['mode']]=p
        table(['Products','Prefix','Repeat','Linear mean ms','Trie mean ms','Linear p95 ms','Trie p95 ms','Candidate-set accuracy'],
            [[size,kind,repeat,f(g['linear']['mean_ms'],4),f(g['trie']['mean_ms'],4),f(g['linear']['p95_ms'],4),f(g['trie']['p95_ms'],4),f(g['trie']['candidate_set_accuracy_pct'],1)+'%'] for (size,kind,repeat),g in groups.items()])
        table(['Products','Linear build ms','Trie build ms','Approx linear heap growth MiB','Approx Trie heap growth MiB'],
            [[p['products'],f(p['linear_build_ms']),f(p['trie_build_ms']),f(p['linear_heap_growth_bytes']/1024**2),f(p['trie_heap_growth_bytes']/1024**2)] for p in autocomplete['builds']])
        lines += ['Heap growth includes temporary allocations and GC effects; it is not retained object size. Precision of returned matches and top-8 result counts were also checked, with all checks passing.', '']
        def trie_chart(ax):
            for mode in ['linear','trie']:
                for kind in ['broad','selective']:
                    rows=[p for p in autocomplete['results'] if p['mode']==mode and p['kind']==kind]
                    ax.plot(*series(rows,'products',lambda p:p['mean_ms']),'o-' if kind=='selective' else 's--',label=f'{mode}: {kind}')
            ax.set(xscale='log',yscale='log',xlabel='Products',ylabel='Mean milliseconds (log scale)',title='Autocomplete'); ax.legend(fontsize=7)
        charts.append(trie_chart)
    if charts:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        columns=min(3,len(charts)); rows=math.ceil(len(charts)/columns)
        fig,axes=plt.subplots(rows,columns,figsize=(5*columns,3.8*rows),squeeze=False,constrained_layout=True)
        for ax,draw in zip(axes.flat,charts):
            draw(ax); ax.spines[['top','right']].set_visible(False); ax.grid(alpha=.15)
        for ax in list(axes.flat)[len(charts):]: ax.set_visible(False)
        fig.suptitle('AetherStock — quick component comparisons',fontsize=14)
        fig.savefig(folder/'component-comparisons.png',dpi=160); plt.close(fig)
        lines += ['## Graphs', '', 'Chart points are means across repetitions; tables retain each run.', '', '![Component comparisons](component-comparisons.png)', '']
    if points:
        lines += ['## Resource observations', '', 'Representative 100-user runs. CPU is a percentage of total host logical-CPU capacity; RSS is the peak observed in that run. MongoDB usage includes any other clients of the shared process.', '']
        table(['Experiment','Mode','Backend CPU %','Backend RSS MiB','MongoDB CPU %','MongoDB RSS MiB','JMeter CPU %','JMeter RSS MiB'],
            [[p['experiment'],mode_label(p),*[f(p['resources'].get(role,{}).get(key)) for role in ['backend','mongodb','jmeter'] for key in ['mean_cpu_host_pct','peak_rss_mib']]] for p in points if p['users']==100])
    if ids:
        resource=read('ids-resources.json',{})
        lines += [f"The whole ID benchmark JVM peaked at {f(resource.get('peak_process_rss_mib'))} MiB RSS; per-point generation CPU time is reported above.", '']
    if autocomplete:
        lines += [f"The autocomplete process peaked at {f(autocomplete['peak_process_rss_kib']/1024)} MiB RSS. Its query CPU times and approximate build allocation growth are retained in autocomplete.json. Test fixtures index each complete generated catalogue; this does not change the application's initial catalogue fetch limit.", '']
    lines += ['## Evidence and limitations', '',
        'Exact settings/hashes: `manifest.json`. HTTP summaries: `http-summary.csv` and `http-results.json`. Each run keeps its command, dataset, per-process CPU/RSS observations and compressed raw JTL. Algorithm files include CPU/build/memory observations. Measured HTTP counts exclude warm-up and tail samples; server shard-operation deltas include the whole run and count database operations, including scatter fan-out.', '',
        'Short runs and fresh load-generator JVMs limit performance conclusions. Existing apps remain running on the shared host. Repeat with `-Seconds 60 -Repeats 3` for a longer study. One MongoDB deployment backs all logical shards. Transaction/order data is still in the core database; partitioning it remains an explicit requirement gap. Reservation expiry is deferred.', '']
    (folder/'README.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')


if __name__=='__main__': render(Path(sys.argv[1]).resolve())
