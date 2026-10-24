import React from 'react';
import { ArrowUpRight } from 'lucide-react';

const components = [
  ['Inventory consistency', 'Stock reservations, receipts, transfers, and movement history', 'Atomic updates · MongoDB transactions'],
  ['Sharding & consistent hashing', 'Warehouse-level stock placement and shard membership changes', 'SHA-256 ring · Virtual nodes · Record migration'],
  ['Key-value store', 'Faster product metadata reads with cache invalidation', 'Redis · Cache-aside · Time-to-live'],
  ['Rate limiter', 'Per-client protection against request bursts', 'Token bucket · HTTP 429 · Retry-After'],
  ['Unique ID generator', 'Identifiers for products, orders, movements, and alerts', 'Snowflake · Timestamp, node, and sequence'],
  ['Autocomplete Engine', 'Product, SKU, and category suggestions', 'Prefix Trie · Debounced API fallback'],
];

export default function ArchitectureOverview({ onNavigate }) {
  return <section className="architecture-intro">
    <div className="page-heading"><div><div className="eyebrow">DEVELOPER WORKSPACE</div><h1>Architecture</h1><p>Inspect the services and safeguards behind inventory operations.</p></div>
      <button className="btn btn-secondary" onClick={() => onNavigate?.('autocomplete')}>Inspect autocomplete <ArrowUpRight size={15} /></button>
    </div>
    <div className="panel architecture-table"><div className="panel-heading"><h2>System components</h2></div><div className="table-container" style={{ border: 0 }}><table className="table"><thead><tr><th>Component</th><th>Inventory functionality</th><th>Implementation</th></tr></thead><tbody>{components.map(([name, role, implementation]) => <tr key={name}><td>{name}</td><td>{role}</td><td>{implementation}</td></tr>)}</tbody></table></div></div>
    <details className="architecture-notes"><summary>Inventory lifecycle & operational walkthrough</summary><ol>
      <li>Register products with a unique SKU and set a reorder threshold.</li>
      <li>Create warehouse facilities and receive supplier purchase orders.</li>
      <li>Search the catalog and inspect stock distribution across warehouses.</li>
      <li>Place a sales order to reserve stock; fulfil it to deduct physical inventory.</li>
      <li>Transfer stock between warehouses, tracking dispatch and completion.</li>
      <li>Review low-stock alerts and replenish inventory when needed.</li>
    </ol></details>
  </section>;
}
