import { ProductTrie } from '../../frontend/src/services/trie.js';

export const MODES = {
  trie_only: { label: 'Browser Trie only', trie: true, fallback: false, cache: false },
  trie_fallback: { label: 'Browser Trie + API fallback', trie: true, fallback: true, cache: false },
  trie_cache_fallback: { label: 'Browser Trie + Redis/API fallback', trie: true, fallback: true, cache: true },
  api_only: { label: 'API only', trie: false, fallback: true, cache: false },
  cache_api: { label: 'Redis + API', trie: false, fallback: true, cache: true },
};
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const field = document.querySelector('#search'), suggestions = document.querySelector('#suggestions');
const status = document.querySelector('#status'), sourceLabel = document.querySelector('#source');

function render(items, source) {
  const fragment = document.createDocumentFragment();
  for (const item of items) {
    const li = document.createElement('li'); li.textContent = `${item.sku} — ${item.name}`; fragment.append(li);
  }
  suggestions.replaceChildren(fragment); sourceLabel.textContent = source;
  void suggestions.offsetHeight; // Include the DOM/layout update; do not label this a screen-paint measurement.
}

class BrowserPath {
  constructor(config) {
    this.config = config; this.mode = MODES[config.mode]; this.trie = this.mode.trie ? new ProductTrie() : null;
    this.apiCalls = 0;
  }
  async initialize() {
    const start = performance.now(); let downloadMs = 0, buildMs = 0;
    if (this.trie) {
      const response = await fetch(`${this.config.api}/catalogue?limit=${this.config.initial}`, { cache: 'no-store' });
      if (!response.ok) throw new Error('Initial catalogue failed');
      const items = await response.json(); downloadMs = performance.now() - start;
      if (items.length !== this.config.initial) throw new Error('Initial catalogue size differs');
      const buildStart = performance.now(); this.trie.buildIndex(items); buildMs = performance.now() - buildStart;
    }
    return { downloadMs, buildMs, totalMs: performance.now() - start };
  }
  async search(prefix) {
    const start = performance.now(); let results = [], source = 'trie_empty', debounceMs = 0, networkMs = 0;
    if (this.trie) {
      results = this.trie.searchPrefix(prefix, 8).results;
      if (results.length) source = 'trie';
    }
    if ((!this.trie || results.length === 0) && this.mode.fallback) {
      if (this.trie && this.config.debounce > 0) {
        const waitStart = performance.now(); await pause(this.config.debounce); debounceMs = performance.now() - waitStart;
      }
      const networkStart = performance.now();
      const params = new URLSearchParams({ prefix, limit: '8', cache: String(this.mode.cache), run: this.config.token });
      const response = await fetch(`${this.config.api}/search?${params}`, { cache: 'no-store' });
      if (!response.ok) throw new Error(`Search returned HTTP ${response.status}`);
      const value = await response.json(); networkMs = performance.now() - networkStart;
      results = value.results; source = value.source; this.apiCalls++;
      if (this.trie) results.forEach(p => this.trie.indexProduct(p));
    }
    render(results, source);
    const displayMs = performance.now() - start;
    return { results, source, workMs: displayMs - debounceMs, displayMs, debounceMs, networkMs };
  }
}

export async function runPoint(config) {
  if (!MODES[config.mode]) throw new Error('Unknown mode');
  const client = new BrowserPath(config), startup = await client.initialize(), rows = [];
  let pending;
  const listener = () => client.search(field.value).then(pending.resolve, pending.reject);
  field.addEventListener('input', listener);
  try {
    for (const phase of ['cold', 'warm']) {
      const loops = phase === 'cold' ? 1 : config.warmLoops;
      const before = await (await fetch(`${config.api}/metrics`, { cache: 'no-store' })).json();
      const phaseStart = performance.now();
      for (let loop = 0; loop < loops; loop++) {
        for (const query of config.queries) {
          status.textContent = `${MODES[config.mode].label} · repeat ${config.repeat} · ${phase} · ${query.prefix}`;
          const result = new Promise((resolve, reject) => { pending = { resolve, reject }; });
          field.value = query.prefix; field.dispatchEvent(new Event('input', { bubbles: true }));
          const observation = await result;
          const expected = new Set(query.expectedIds), ids = observation.results.map(p => p.productId);
          const valid = ids.filter(id => expected.has(id)).length;
          if (new Set(ids).size !== ids.length || valid !== ids.length) throw new Error('Invalid/duplicate suggestion');
          const expectedCount = Math.min(8, expected.size), correct = ids.length === expectedCount;
          if (config.mode !== 'trie_only' && !correct && (query.kind !== 'partial_local' || !client.trie)) {
            throw new Error('Fallback/API path missed expected suggestions');
          }
          rows.push({ mode: config.mode, repeat: config.repeat, phase, loop, prefix: query.prefix, kind: query.kind,
            source: observation.source, work_ms: observation.workMs, display_ms: observation.displayMs,
            debounce_ms: observation.debounceMs, network_ms: observation.networkMs, returned: ids.length,
            expected: expectedCount, valid, correct, resultIds: ids });
        }
      }
      const phaseElapsed = performance.now() - phaseStart;
      const after = await (await fetch(`${config.api}/metrics`, { cache: 'no-store' })).json();
      const phaseRows = rows.filter(r => r.phase === phase);
      const counters = Object.fromEntries(Object.keys(after).map(key => [key, after[key] - before[key]]));
      const calls = phaseRows.filter(r => r.source === 'mongo' || r.source === 'redis').length;
      if (counters.requests !== calls || counters.databaseReads + counters.redisHits !== calls || counters.redisFailures !== 0) {
        throw new Error('Browser/server counters differ, or Redis failed');
      }
      if (!MODES[config.mode].cache && (counters.redisHits || counters.redisMisses)) throw new Error('Uncached mode used Redis');
      const summary = { mode: config.mode, repeat: config.repeat, phase, counters,
        elapsed_ms: phaseElapsed, query_count: phaseRows.length };
      window.phaseSummaries.push(summary);
      const tr = document.createElement('tr');
      const values = [MODES[config.mode].label, phase, phaseRows.length,
        (phaseRows.reduce((s,r)=>s+r.work_ms,0)/phaseRows.length).toFixed(3),
        (phaseRows.reduce((s,r)=>s+r.display_ms,0)/phaseRows.length).toFixed(3),
        (100*phaseRows.filter(r=>r.correct).length/phaseRows.length).toFixed(1), calls];
      for (const value of values) { const td = document.createElement('td'); td.textContent = value; tr.append(td); }
      document.querySelector('#results').append(tr);
    }
  } finally { field.removeEventListener('input', listener); }
  return { mode: config.mode, repeat: config.repeat, startup, rows,
    phases: window.phaseSummaries.filter(p=>p.mode===config.mode && p.repeat===config.repeat),
    heap_used_bytes: performance.memory?.usedJSHeapSize ?? null };
}

window.runAutocompletePoint = runPoint;
window.phaseSummaries = [];
window.benchmarkReady = true;
