import React, { useState } from 'react';
import { ArrowUpRight, ArrowRight, Plus, Package, Warehouse, CheckCircle2, ArrowDownToLine,
  ShoppingCart, ArrowLeftRight, MapPin, Circle, ChevronRight } from 'lucide-react';

export default function Dashboard({ products = [], productCount = products.length, warehouses = [], alerts = [],
  setActiveTab, onOpenCreateProduct, onOpenCreatePO, onOpenCreateSale, onOpenTransfer, onSelectProduct }) {
  const activeWarehouses = warehouses.filter(w => w.status === 'ACTIVE').length;
  const catalog = [...products].sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0)).slice(0, 5);
  const [date] = useState(() => new Intl.DateTimeFormat('en', { weekday: 'short', month: 'short', day: 'numeric' }).format(new Date()));
  return (
    <div className="overview">
      <div className="page-heading">
        <div><div className="eyebrow">WORKSPACE OVERVIEW <span> / </span> {date}</div><h1>Overview</h1><p>Monitor inventory, stock movements, and warehouse operations.</p></div>
        <div className="page-actions">
          <button className="btn btn-secondary" onClick={onOpenCreatePO}><ArrowDownToLine size={15} /> Receive stock</button>
          <button className="btn btn-secondary" onClick={onOpenCreateSale}><ShoppingCart size={15} /> Create sales order</button>
          <button className="btn btn-primary" onClick={onOpenCreateProduct}><Plus size={16} /> Add product</button>
        </div>
      </div>
      <section className="overview-metrics" aria-label="Workspace summary">
        <button className="metric" onClick={() => setActiveTab('products')}><span className="metric-label">Catalog products <ArrowUpRight size={15} /></span><strong>{productCount.toLocaleString()}</strong><span className="metric-note">Products registered in your catalog</span></button>
        <button className="metric" onClick={() => setActiveTab('warehouses')}><span className="metric-label">Active warehouses <ArrowUpRight size={15} /></span><strong>{activeWarehouses.toLocaleString()}<small> / {warehouses.length}</small></strong><span className="metric-note">Facilities available for stock operations</span></button>
        <button className={`metric${alerts.length ? ' has-attention' : ''}`} onClick={() => setActiveTab('alerts')}><span className="metric-label">Needs replenishment <ArrowUpRight size={15} /></span><strong>{alerts.length.toLocaleString()}</strong><span className="metric-note">Product and warehouse stock alerts</span></button>
      </section>
      <div className="overview-grid">
        <section className="panel catalog-panel">
          <div className="panel-heading"><div><h2>Product catalog</h2><p>A snapshot of the products you manage.</p></div><button className="text-button" onClick={() => setActiveTab('products')}>View catalog <ArrowRight size={14} /></button></div>
          <div className="table-container overview-table"><table className="table"><thead><tr><th>Product</th><th>SKU</th><th>Reorder point</th><th>Status</th><th><span className="sr-only">Details</span></th></tr></thead>
            <tbody>{catalog.map(product => <tr key={product.productId} onClick={() => onSelectProduct(product)} className="clickable-row">
              <td><button className="product-cell" onClick={event => { event.stopPropagation(); onSelectProduct(product); }}><span className="product-avatar"><Package size={17} /></span><span><strong>{product.name}</strong><small>{product.category || 'Uncategorized'}</small></span></button></td>
              <td><code>{product.sku}</code></td><td>{product.reorderLevel} units</td><td><span className={`status-label ${product.active ? 'is-success' : 'is-muted'}`}><i />{product.active ? 'Active' : 'Inactive'}</span></td><td><ChevronRight size={15} /></td>
            </tr>)}</tbody></table></div>
          {!catalog.length && <div className="catalog-empty"><div className="empty-symbol"><Package size={27} strokeWidth={1.4} /></div><h3>Your catalog starts here</h3><p>Add your first product to start tracking stock<br className="desktop-break" /> across your warehouses.</p><button className="btn btn-secondary" onClick={onOpenCreateProduct}><Plus size={15} /> Add your first product</button></div>}
          <div className="panel-footer"><span>{catalog.length ? `Showing ${catalog.length} of ${productCount} products` : 'No products registered yet'}</span><span>Product details & stock availability</span></div>
        </section>
        <div className="overview-aside">
          <section className="panel attention-panel"><div className="panel-heading"><h2>Needs attention</h2><span className="quiet-count">{alerts.length}</span></div>
            {alerts.length ? <div className="attention-list">{alerts.slice(0, 3).map(alert => {
              const product = products.find(p => p.productId === alert.productId);
              const warehouse = warehouses.find(w => w.warehouseId === alert.warehouseId);
              return <button key={alert.alertId || `${alert.productId}:${alert.warehouseId}`} className="attention-row" onClick={() => setActiveTab('alerts')}><span className="attention-indicator" /><span><strong>{product?.name || alert.productId}</strong><small>{warehouse?.name || 'Warehouse'} · {alert.availableQuantity} available</small></span><span className="status-label is-warning">Low stock</span></button>;
            })}<button className="text-button" onClick={() => setActiveTab('alerts')}>Review stock alerts <ArrowRight size={14} /></button></div>
              : <div className="attention-empty"><CheckCircle2 size={23} strokeWidth={1.5} /><div><h3>No active stock alerts</h3><p>Replenishment needs will appear here<br /> as you receive and move inventory.</p></div></div>}
          </section>
          <section className="panel warehouse-panel"><div className="panel-heading"><h2>Warehouse network</h2><button className="text-button" onClick={() => setActiveTab('warehouses')} aria-label="View warehouses"><ArrowUpRight size={16} /></button></div>
            {warehouses.length ? <div className="warehouse-list">{warehouses.slice(0, 3).map(warehouse => <button key={warehouse.warehouseId} onClick={() => setActiveTab('warehouses')}><Warehouse size={18} /><span><strong>{warehouse.name}</strong><small><MapPin size={11} />{warehouse.location || 'No location set'}</small></span><Circle size={7} className={warehouse.status === 'ACTIVE' ? 'warehouse-active' : ''} /></button>)}</div>
              : <div className="warehouse-empty"><Warehouse size={24} strokeWidth={1.4} /><h3>Connect your first warehouse</h3><p>Track inventory by facility and location.</p><button className="text-button" onClick={() => setActiveTab('warehouses')}>Add a warehouse <ArrowRight size={14} /></button></div>}
          </section>
        </div>
      </div>
      <section className="operations-section"><div className="section-heading"><h2>Operations</h2><p>Create orders and move inventory.</p></div>
        <div className="operation-links">
          {[{ title: 'Receive inventory', description: 'Bring supplier stock into a warehouse.', icon: ArrowDownToLine, action: onOpenCreatePO },
            { title: 'Create a sales order', description: 'Reserve available stock for fulfilment.', icon: ShoppingCart, action: onOpenCreateSale },
            { title: 'Transfer stock', description: 'Move inventory between your facilities.', icon: ArrowLeftRight, action: onOpenTransfer }].map(({ title, description, icon: Icon, action }) => <button key={title} className="operation-link" onClick={action}><Icon size={20} strokeWidth={1.5} /><span><strong>{title}</strong><small>{description}</small></span><ArrowUpRight size={16} /></button>)}
        </div>
      </section>
    </div>
  );
}
