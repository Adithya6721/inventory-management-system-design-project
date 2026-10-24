import { ProductTrie } from '../../frontend/src/services/trie.js';
import { performance } from 'node:perf_hooks';
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const output = process.argv[2];
const repeats = Number(process.argv[3] || 1);
const normalize = text => text.toLowerCase().replace(/[^a-z0-9\s-]/g, ' ').split(/[\s-]+/).filter(Boolean);
const tokens = p => [...new Set([...normalize(p.sku), p.sku.toLowerCase().replace(/[^a-z0-9]/g, ''), ...normalize(p.name), ...normalize(p.category)])];
const quantile = (sorted, q) => {
  const pos = (sorted.length - 1) * q, low = Math.floor(pos), high = Math.ceil(pos);
  return sorted[low] + (sorted[high] - sorted[low]) * (pos - low);
};
const results = [], raw = [], builds = [];
for (const size of [100, 1000, 10000]) {
  const products = Array.from({ length: size }, (_, i) => ({ productId: `p${i}`, sku: `AS-${String(i).padStart(6, '0')}`, name: `Sensor ${i} Tool`, category: `Category ${i % 10}` }));
  global.gc?.(); const before = process.memoryUsage().heapUsed;
  let start = performance.now();
  const linear = products.map(p => ({ product: p, tokens: tokens(p) }));
  const linearBuild = performance.now() - start, afterLinear = process.memoryUsage().heapUsed;
  const trie = new ProductTrie(); start = performance.now(); trie.buildIndex(products);
  const trieBuild = performance.now() - start, afterTrie = process.memoryUsage().heapUsed;
  builds.push({ products: size, linear_build_ms: linearBuild, trie_build_ms: trieBuild,
    linear_heap_growth_bytes: afterLinear - before, trie_heap_growth_bytes: afterTrie - afterLinear,
    trie_nodes: trie.nodeCount, note: 'Approximate heap growth, including allocations/GC; not retained object size.' });
  const scan = (prefix, limit = 8) => {
    const clean = prefix.trim().toLowerCase(), found = [];
    if (!clean) return found;
    for (const item of linear) if (item.tokens.some(t => t.startsWith(clean))) {
      found.push(item.product); if (found.length === limit) break;
    }
    return found;
  };
  const queries = {
    broad: Array.from({ length: 100 }, (_, i) => ['s','sensor','tool','category','as'][i % 5]),
    selective: Array.from({ length: 100 }, (_, i) => `as${String((i * 97) % size).padStart(6, '0')}`),
    missing: Array.from({ length: 100 }, (_, i) => `missing${i}`),
  };
  for (const [kind, prefixes] of Object.entries(queries)) {
    let checked = 0, correctCandidates = 0, returned = 0, correctReturned = 0;
    // Compare all candidate IDs separately, since top-8 order has no ranking contract.
    for (const prefix of new Set(prefixes)) {
      const expected = scan(prefix, size).map(p => p.productId).sort();
      assert.equal(expected.length, kind === 'broad' ? size : kind === 'selective' ? 1 : 0);
      assert.deepEqual(trie.searchPrefix(prefix, size).results.map(p => p.productId).sort(), expected);
      checked++; correctCandidates++;
      const top = trie.searchPrefix(prefix, 8).results;
      assert.equal(top.length, Math.min(8, expected.length));
      assert.ok(top.every(p => expected.includes(p.productId)));
      returned += top.length; correctReturned += top.filter(p => expected.includes(p.productId)).length;
    }
    for (let repeat = 1; repeat <= repeats; repeat++) {
      for (const mode of repeat % 2 ? ['linear','trie'] : ['trie','linear']) {
        const lookup = mode === 'trie' ? p => trie.searchPrefix(p, 8).results : p => scan(p, 8);
        for (let i = 0; i < 100; i++) lookup(prefixes[i]);
        const cpu = process.cpuUsage(), values = [], runStart = performance.now();
        for (let i = 0; i < 500; i++) {
          const prefix = prefixes[i % prefixes.length], time = performance.now();
          const found = lookup(prefix), ms = performance.now() - time;
          values.push(ms); raw.push([size, kind, mode, repeat, prefix, ms, found.length]);
        }
        const duration = performance.now() - runStart, used = process.cpuUsage(cpu);
        values.sort((a,b) => a-b);
        results.push({ products: size, kind, mode, repeat, queries: values.length,
          mean_ms: values.reduce((a,b) => a+b,0)/values.length, p95_ms: quantile(values,.95),
          queries_per_second: values.length * 1000 / duration, cpu_ms: (used.user+used.system)/1000,
          candidate_validation_prefixes: checked, candidate_set_accuracy_pct: correctCandidates * 100 / checked,
          returned_match_precision_pct: returned ? correctReturned * 100 / returned : null, top8_count_correct: true });
      }
    }
  }
}
fs.writeFileSync(path.join(output,'autocomplete.json'), JSON.stringify({ results, builds,
  node_version: process.version,
  source_sha256: createHash('sha256').update(fs.readFileSync(fileURLToPath(import.meta.url))).digest('hex'),
  trie_source_sha256: createHash('sha256').update(fs.readFileSync(fileURLToPath(new URL('../../frontend/src/services/trie.js',import.meta.url)))).digest('hex'),
  peak_process_rss_kib: process.resourceUsage().maxRSS,
  scope: 'Actual frontend ProductTrie vs pre-tokenized linear token-prefix scan over identical full catalogues. External high-resolution timing includes actual top-8 search work; full candidate-set correctness checked separately. No ordering/ranking equivalence claimed. Node timings are not browser rendering or network timings.' },null,2));
fs.writeFileSync(path.join(output,'autocomplete-samples.csv'), 'products,kind,mode,repeat,prefix,milliseconds,result_count\n' + raw.map(r=>r.join(',')).join('\n') + '\n');
console.log('Autocomplete: all candidate-set and top-8 checks passed at 100, 1000 and 10000 products');
