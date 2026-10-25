import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Play } from 'lucide-react';
import { ProductTrie } from '../services/trie';
import { buildLinearReference, measurePrefix } from '../services/autocompleteBenchmark';
import { api } from '../services/api';

const queries = ['a', 'ap', 'app', 'appl', 'apple', 'mac', 'pro', 'sku', 'lap', 'nonexistentxyz'];
const time = value => value === null ? 'Failed' : value === 0 ? 'Below timer resolution' : `${value.toPrecision(3)} ms`;

export default function AutocompleteLab({ products = [], totalProducts = products.length }) {
  // This screen owns its test index; visiting it must not reset the global search cache.
  const trie = useMemo(() => { const index = new ProductTrie(); index.buildIndex(products); return index; }, [products]);
  const scan = useMemo(() => buildLinearReference(products), [products]);
  const [prefix, setPrefix] = useState('mac');
  const [result, setResult] = useState(null);
  const [rows, setRows] = useState([]);
  const [running, setRunning] = useState(false);
  const run = useRef(0);
  useEffect(() => {
    run.current++;
    setRows([]); setRunning(false);
    return () => { run.current++; };
  }, [trie]);
  useEffect(() => { setResult(trie.searchPrefix(prefix, 10)); }, [trie, prefix]);
  const benchmark = async () => {
    const current = ++run.current;
    setRunning(true); setRows([]);
    try {
      for (const query of queries) {
        const row = await measurePrefix(query, trie, scan, value => api.getProducts(value, '', 0, 10), products.length);
        if (current !== run.current) return;
        setRows(previous => [...previous, row]);
      }
    } finally { if (current === run.current) setRunning(false); }
  };
  const stats = trie.getStats();
  const failures = rows.filter(row => row.apiError).length;
  return <div>
    <div className="page-heading">
      <div><h1>Autocomplete</h1><p>Inspect prefix matches and compare measured lookup times.</p></div>
      <button id="btn-run-benchmark" className="btn btn-primary" onClick={benchmark} disabled={running || !products.length}>
        <Play size={16} />{running ? 'Running checks…' : 'Run benchmark'}
      </button>
    </div>
    <div className="card" style={{ marginBottom: 20 }}>
      <h2 style={{ fontSize: 18, marginBottom: 8 }}>Browser index</h2>
      <p id="trie-index-scope">{products.length} of {totalProducts} catalog products indexed · {stats.nodeCount} nodes · {stats.totalWords} token entries</p>
      <p style={{ color: 'var(--text-secondary)', marginTop: 8 }}>The test uses the products loaded into this browser. Normal global search can fall back to the API when its Trie finds no match.</p>
    </div>
    <div className="card" style={{ marginBottom: 20 }}>
      <h2 style={{ fontSize: 18, marginBottom: 8 }}>Try a prefix</h2>
      <p style={{ color: 'var(--text-secondary)', marginBottom: 16 }}>Matches the start of name, SKU and category tokens. An empty result can be correct.</p>
      <label htmlFor="trie-test-prefix" className="form-label">Prefix</label>
      <input id="trie-test-prefix" className="form-input" style={{ maxWidth: 400, marginBottom: 16 }} value={prefix} onChange={event => setPrefix(event.target.value)} placeholder="Try a product name, SKU or category prefix" />
      {result && <div id="trie-query-results" aria-live="polite">
        <p style={{ marginBottom: 12 }}>{result.results.length} displayed matches{result.results.length === 10 ? ' (limit 10)' : ''}</p>
        {!result.results.length ? <p style={{ color: 'var(--text-secondary)' }}>No indexed tokens start with “{result.prefix}”.</p> :
          <ul style={{ paddingLeft: 20 }}>{result.results.map(product => <li key={product.productId} style={{ padding: '5px 0' }}>{product.name} <span style={{ color: 'var(--text-secondary)' }}>· {product.sku}</span></li>)}</ul>}
      </div>}
    </div>
    <div className="card" style={{ padding: 0 }}>
      <div style={{ padding: 20 }}>
        <h2 style={{ fontSize: 18, marginBottom: 8 }}>Lookup comparison</h2>
        <p style={{ color: 'var(--text-secondary)' }}>Match checks compare the complete Trie result set with an independent linear token-prefix search over the same browser catalog. A zero-match query passes when both return zero.</p>
        <p style={{ color: 'var(--text-secondary)', marginTop: 8 }}>Local timings average 100 lookups returning up to 10 products. The API column measures one real request per prefix; its substring matching and full catalog can return different counts. API failures are shown as errors.</p>
        {!!rows.length && <p id="trie-benchmark-status" role="status" style={{ marginTop: 12 }}>{running ? `${rows.length}/${queries.length} checked` : failures ? `Finished with ${failures} API error(s)` : 'Checks completed'}</p>}
      </div>
      {!rows.length ? <p style={{ padding: '0 20px 20px', color: 'var(--text-secondary)' }}>Run the benchmark to record this session’s results.</p> :
        <div className="table-container"><table className="table" id="trie-benchmark-table"><thead><tr>
          <th>Prefix</th><th>Trie lookup</th><th>Linear lookup</th><th>API request</th><th>API matches</th><th>Trie match check</th>
        </tr></thead><tbody>{rows.map(row => <tr key={row.prefix}>
          <td><code>{row.prefix}</code></td><td>{time(row.trieMs)}</td><td>{time(row.scanMs)}</td>
          <td title={row.apiError || undefined}>{row.apiError ? `Error: ${row.apiError}` : time(row.apiMs)}</td><td>{row.apiCount === null ? 'Request failed' : row.apiCount}</td>
          <td data-match-check={row.check.correct ? 'pass' : 'fail'}>
            {row.check.correct ? row.check.expected ? `Pass · ${row.check.returned}/${row.check.expected} matches` : 'Pass · no matches' : `Fail · ${row.check.missing} missing, ${row.check.extra} unexpected`}
          </td>
        </tr>)}</tbody></table></div>}
    </div>
  </div>;
}
