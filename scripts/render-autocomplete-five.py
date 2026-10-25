"""Append graphs and diagnostic tables to saved browser observations."""
from collections import defaultdict
import json
from pathlib import Path
import statistics
import sys


def render(folder):
    modes=['trie_only','trie_fallback','trie_cache_fallback','api_only','cache_api']
    labels=['Trie','Trie + API','Trie + Redis/API','API','Redis/API']
    summary=json.loads((folder/'summary.json').read_text())
    points=[json.loads(p.read_text()) for mode in modes for p in sorted(folder.glob(mode+'-r*.json'))]
    grouped=defaultdict(list)
    for point in points:
        for row in point['rows']: grouped[(row['mode'],row['phase'],row['kind'])].append(row)
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    fig,axes=plt.subplots(2,2,figsize=(12,8),constrained_layout=True)
    settings=[('work_mean_ms','Work, excluding debounce','Milliseconds'),
              ('display_mean_ms','Time until suggestions reach the DOM','Milliseconds'),
              ('correct_pct','Complete valid top-eight answers','Percent of queries'),
              ('database_reads','MongoDB search reads','Reads per 100 queries')]
    for ax,(field,title,ylabel) in zip(axes.flat,settings):
        for phase,offset,color in [('cold',-.18,'#9aa3b2'),('warm',.18,'#3659c9')]:
            rows=[next(r for r in summary if r['mode']==mode and r['phase']==phase) for mode in modes]
            values=[r[field]*100/r['queries'] if field=='database_reads' else r[field] for r in rows]
            ax.bar([i+offset for i in range(5)],values,width=.34,color=color,label=phase)
        ax.set_title(title,fontsize=11); ax.set_ylabel(ylabel,fontsize=9)
        ax.set_xticks(range(5),labels,rotation=15,ha='right',fontsize=8)
        ax.spines[['top','right']].set_visible(False); ax.grid(axis='y',alpha=.15); ax.set_axisbelow(True)
        if field in ['correct_pct','database_reads']: ax.set_ylim(0,105)
    axes[0,0].legend(frameon=False)
    config=json.loads((folder/'manifest.json').read_text())['settings']
    fig.suptitle(f"Five autocomplete paths · {config['initial']}/{config['products']} products initially indexed · {config['debounce']} ms fallback delay",fontsize=12)
    fig.savefig(folder/'comparison.png',dpi=180); plt.close(fig)
    extra=['','## Comparison graphs','','![Five-mode comparison](comparison.png)','',
           'Bars pool the recorded queries from the independent repetitions. These are descriptive measurements, not confidence intervals. Cold means a fresh index/query namespace; warm means repeated queries in the same client.','',
           '## Warm queries by class','','| Mode | Query class | Work mean ms | Display mean ms | Complete answers % | API calls |',
           '| --- | --- | ---: | ---: | ---: | ---: |']
    for mode in modes:
        for kind in ['initial','deeper','missing','broad','partial_local']:
            rows=grouped[(mode,'warm',kind)]
            extra.append(f"| {labels[modes.index(mode)]} | {kind} | {statistics.mean(r['work_ms'] for r in rows):.3f} | {statistics.mean(r['display_ms'] for r in rows):.3f} | {100*sum(r['correct'] for r in rows)/len(rows):.1f} | {sum(r['source'] in ['mongo','redis'] for r in rows)} |")
    extra+=['','## Bootstrap and cache costs','','| Mode | Initial download mean ms | Trie construction mean ms | Warm Redis lookup mean ms |','| --- | ---: | ---: | ---: |']
    for mode in modes:
        selected=[p for p in points if p['mode']==mode]
        phases=[s for p in selected for s in p['phases'] if s['phase']=='warm']
        operations=sum(p['counters']['redisHits']+p['counters']['redisMisses'] for p in phases)
        lookup=sum(p['counters']['totalRedisLookupNs'] for p in phases)/1e6/operations if operations else None
        value=f'{lookup:.3f}' if lookup is not None else 'N/A'
        extra.append(f"| {labels[modes.index(mode)]} | {statistics.mean(p['startup']['downloadMs'] for p in selected):.3f} | {statistics.mean(p['startup']['buildMs'] for p in selected):.3f} | {value} |")
    resources=json.loads((folder/'resource-summary.json').read_text())
    extra+=['','## Whole-workload process resources','','| Process | Mean host CPU % | Peak RSS MiB |','| --- | ---: | ---: |']
    for role,values in resources.items(): extra.append(f"| {role} | {values['mean_cpu_host_pct']:.2f} | {values['peak_rss_mib']:.2f} |")
    extra+=['','Resources cover the workload as a whole, including browser control/GC and shared MongoDB background work. Chrome sums its process tree; shared pages may be counted repeatedly. No per-mode CPU comparison is inferred from this table.','']
    report=folder/'README.md'
    original=report.read_text(encoding='utf-8').split('\n## Comparison graphs')[0]
    report.write_text(original+'\n'.join(extra),encoding='utf-8')


if __name__=='__main__': render(Path(sys.argv[1]).resolve())
