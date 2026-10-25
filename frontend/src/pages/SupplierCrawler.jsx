import React, { useEffect, useMemo, useState } from 'react';
import { ExternalLink, Play } from 'lucide-react';
import { api } from '../services/api';

const supplierBase = (import.meta.env.VITE_SUPPLIER_BASE_URL || 'http://127.0.0.1:8085').replace(/\/$/, '');
const portals = [
  { key: 'apex', name: 'Apex Tech Distribution', path: '/apex/products', format: 'Product cards and pagination' },
  { key: 'global', name: 'Global Wholesale Logistics', path: '/global/catalog', format: 'Product table' },
  { key: 'nordic', name: 'Nordic Component Supply', path: '/nordic/store', format: 'Embedded product metadata' },
];
const money = value => Number(value).toLocaleString('en-US', { style: 'currency', currency: 'USD' });

export default function SupplierCrawler({ products = [], warehouses = [], onShowToast = () => {}, onRefreshAll = () => {} }) {
  const [suppliers, setSuppliers] = useState(portals.map(portal => portal.key));
  const [warehouse, setWarehouse] = useState('');
  const [createOrders, setCreateOrders] = useState(true);
  const [items, setItems] = useState([]);
  const [selected, setSelected] = useState(new Set());
  const [query, setQuery] = useState('');
  const [supplierFilter, setSupplierFilter] = useState('');
  const [busy, setBusy] = useState('');
  const [telemetry, setTelemetry] = useState(null);
  const [error, setError] = useState('');
  const [responses, setResponses] = useState([]);
  useEffect(() => { if (!warehouse && warehouses.length) setWarehouse(warehouses[0].warehouseId); }, [warehouses, warehouse]);
  const prices = useMemo(() => new Map(products.map(product => [product.sku, product.price])), [products]);
  const filtered = items.filter(item => (!supplierFilter || item.supplierId === supplierFilter) &&
    `${item.sku} ${item.name} ${item.category}`.toLowerCase().includes(query.toLowerCase()));
  const toggle = sku => setSelected(previous => {
    const next = new Set(previous);
    if (next.has(sku)) next.delete(sku); else next.add(sku);
    return next;
  });
  const crawl = async () => {
    setBusy('crawl'); setError(''); setResponses([]); setItems([]); setSelected(new Set()); setTelemetry(null);
    try {
      const response = await fetch(`${supplierBase}/api/crawl`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ suppliers }),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.error || 'Some supplier pages could not be fetched. Retry the crawl.');
      setItems(data.items); setSelected(new Set(data.items.map(item => item.sku))); setTelemetry(data.telemetry);
    } catch (failure) { setError(`Crawl failed: ${failure.message}. Check that the supplier service is running.`); }
    finally { setBusy(''); }
  };
  const importItems = async () => {
    setBusy('import'); setError(''); setResponses([]);
    const completed = [];
    try {
      const groups = new Map();
      for (const item of items.filter(item => selected.has(item.sku))) {
        if (!groups.has(item.supplierId)) groups.set(item.supplierId, {
          supplierId: item.supplierId, supplierName: item.supplierName,
          targetWarehouseId: warehouse, autoCreatePurchaseOrder: createOrders, items: [],
        });
        groups.get(item.supplierId).items.push(item);
      }
      for (const group of groups.values()) {
        const result = await api.importSuppliers(group);
        completed.push({ ...result, supplierName: group.supplierName });
        // Deselect completed groups so a failed group can be retried separately.
        setSelected(previous => new Set([...previous].filter(sku => !group.items.some(item => item.sku === sku))));
        setResponses([...completed]);
      }
      onShowToast({ type: 'success', title: 'Supplier import complete', message: 'Catalog updated. Receive purchase orders when the goods arrive.' });
    } catch (failure) { setError(`Import stopped: ${failure.message}. Successfully imported groups are shown below.`); }
    finally {
      try { if (completed.length) await onRefreshAll(); }
      finally { setBusy(''); }
    }
  };
  return <div>
    <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 16, marginBottom: 24 }}>
      <div><h1 style={{ fontSize: '1.875rem', marginBottom: 4 }}>Suppliers</h1><p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Discover supplier products and prepare incoming purchase orders.</p></div>
      <button className="btn btn-primary" id="btn-run-crawl" onClick={crawl} disabled={!!busy || !suppliers.length}>
        <Play size={15} />{busy === 'crawl' ? 'Crawling…' : 'Run crawl'}
      </button>
    </div>
    <div className="card" style={{ marginBottom: 20 }}>
      <h2 style={{ fontSize: 18, marginBottom: 16 }}>Supplier portals</h2>
      <p style={{ color: 'var(--text-secondary)', marginBottom: 16 }}>These local websites simulate external catalogs. Supplier availability is separate from your warehouse stock.</p>
      {portals.map(portal => <div key={portal.key} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 0', borderBottom: '1px solid var(--border-subtle)' }}>
        <input type="checkbox" aria-label={`Crawl ${portal.name}`} checked={suppliers.includes(portal.key)} disabled={!!busy}
          onChange={() => setSuppliers(previous => previous.includes(portal.key) ? previous.filter(key => key !== portal.key) : [...previous, portal.key])} />
        <div style={{ flex: 1 }}><strong>{portal.name}</strong><div style={{ color: 'var(--text-secondary)', fontSize: 13 }}>{portal.format}</div></div>
        <a href={`${supplierBase}${portal.path}`} target="_blank" rel="noreferrer">Open portal <ExternalLink size={13} /></a>
      </div>)}
    </div>
    {error && <div role="alert" className="card" style={{ color: 'var(--accent-rose)', marginBottom: 20 }}>{error}</div>}
    {telemetry && <p id="crawl-metrics" style={{ marginBottom: 20, color: 'var(--text-secondary)' }}>
      {telemetry.itemsValid} products · {telemetry.pagesFetched} pages · {telemetry.totalDurationMs} ms · {telemetry.itemsPerSecond} products/s · {telemetry.pagesFailed} failed pages
    </p>}
    {items.length > 0 && <div className="card" style={{ padding: 0 }}>
      <div style={{ padding: 20, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 12 }}>
        <input className="form-input" style={{ width: 230 }} aria-label="Filter supplier products" placeholder="Search supplier products" value={query} onChange={event => setQuery(event.target.value)} />
        <select className="form-select" style={{ width: 200 }} aria-label="Filter by supplier" value={supplierFilter} onChange={event => setSupplierFilter(event.target.value)}>
          <option value="">All suppliers</option>{[...new Map(items.map(item => [item.supplierId, item.supplierName]))].map(([id, name]) => <option key={id} value={id}>{name}</option>)}
        </select>
        <button className="btn btn-secondary" disabled={!!busy} onClick={() => setSelected(new Set(filtered.map(item => item.sku)))}>Select visible</button>
      </div>
      <div className="table-container"><table className="table"><thead><tr>
        <th>Select</th><th>Product</th><th>Supplier price</th><th>Catalog price</th><th>Supplier stock</th><th>Source</th>
      </tr></thead><tbody>{filtered.map(item => <tr key={item.sku}>
        <td><input type="checkbox" aria-label={`Import ${item.sku}`} checked={selected.has(item.sku)} disabled={!!busy} onChange={() => toggle(item.sku)} /></td>
        <td><strong>{item.name}</strong><div style={{ color: 'var(--text-secondary)', fontSize: 12 }}>{item.sku} · {item.category} · {item.supplierName}</div></td>
        <td>{money(item.supplierPrice)}</td><td>{prices.has(item.sku) ? money(prices.get(item.sku)) : '—'}</td><td>{item.supplierReportedStock}</td>
        <td><a href={item.externalUrl} target="_blank" rel="noreferrer" aria-label={`View source for ${item.sku}`}><ExternalLink size={15} /></a></td>
      </tr>)}</tbody></table></div>
      <div style={{ padding: 20, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 16 }}>
        <label>Destination <select className="form-select" value={warehouse} disabled={!!busy} onChange={event => setWarehouse(event.target.value)}>
          <option value="">Choose warehouse</option>{warehouses.map(wh => <option key={wh.warehouseId} value={wh.warehouseId}>{wh.name}</option>)}
        </select></label>
        <label><input type="checkbox" checked={createOrders} disabled={!!busy} onChange={event => setCreateOrders(event.target.checked)} /> Create purchase orders</label>
        <button className="btn btn-primary" id="btn-import-suppliers" onClick={importItems} disabled={!!busy || !selected.size || (createOrders && !warehouse)}>
          {busy === 'import' ? 'Importing…' : `Import ${selected.size} selected`}
        </button>
      </div>
      <p style={{ padding: '0 20px 20px', color: 'var(--text-secondary)' }}>Importing updates the product catalog. Warehouse stock changes only when a purchase order is received.</p>
    </div>}
    {responses.length > 0 && <div className="card" style={{ marginTop: 20 }}><h2 style={{ fontSize: 18 }}>Import results</h2>
      {responses.map(result => <p key={result.importBatchId}>{result.supplierName}: {result.productsCatalogUpdated} catalog updates · {result.proposedPurchaseOrderId ? `Purchase order ${result.proposedPurchaseOrderId}` : 'Catalog only'}</p>)}
    </div>}
  </div>;
}
