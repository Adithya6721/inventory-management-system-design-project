import React from 'react';
import {
  Package,
  Warehouse,
  AlertTriangle,
  Zap,
  TrendingUp,
  Cpu,
  ArrowRight,
  ShieldCheck,
  Database,
  Layers
} from 'lucide-react';

export default function Dashboard({
  products = [],
  warehouses = [],
  alerts = [],
  setActiveTab,
  onOpenCreateProduct,
  onOpenCreatePO,
  onOpenCreateSale,
  onOpenTransfer
}) {
  const lowStockCount = alerts.length;
  const activeWarehouses = warehouses.filter(w => w.status === 'ACTIVE').length;

  return (
    <div>
      {/* Top Banner / Welcome */}
      <div style={{
        background: 'linear-gradient(135deg, rgba(99, 102, 241, 0.15), rgba(6, 182, 212, 0.1))',
        border: '1px solid var(--border-glass)',
        borderRadius: '16px',
        padding: '24px 28px',
        marginBottom: '28px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '20px'
      }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
            <span className="badge badge-indigo">System Design Laboratory</span>
            <span className="badge badge-cyan">Topic 4: Scalable IMS</span>
          </div>
          <h1 style={{ fontSize: '1.75rem', marginBottom: '8px' }}>
            Inventory Management Dashboard
          </h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', maxWidth: '640px' }}>
            High-concurrency distributed inventory platform featuring atomic stock reservations, 
            consistent shard routing, sub-millisecond Trie autocomplete, and live audit reconciliation.
          </p>
        </div>

        {/* Quick Launch Buttons */}
        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          <button className="btn btn-primary" onClick={onOpenCreateProduct}>
            <Package size={16} /> Add Product
          </button>
          <button className="btn btn-secondary" onClick={onOpenCreatePO}>
            Receive Stock
          </button>
          <button className="btn btn-secondary" onClick={onOpenCreateSale}>
            Place Sale Order
          </button>
        </div>
      </div>

      {/* KPI Stats Grid */}
      <div className="stats-grid">
        <div className="stat-card" style={{ '--stat-accent': 'var(--primary)', cursor: 'pointer' }} onClick={() => setActiveTab('products')}>
          <div className="stat-icon" style={{ '--stat-icon-bg': 'var(--primary-light)', '--stat-icon-color': 'var(--primary)' }}>
            <Package size={24} />
          </div>
          <div>
            <div className="stat-val">{products.length}</div>
            <div className="stat-lbl">Registered Products</div>
          </div>
        </div>

        <div className="stat-card" style={{ '--stat-accent': 'var(--accent-cyan)', cursor: 'pointer' }} onClick={() => setActiveTab('warehouses')}>
          <div className="stat-icon" style={{ '--stat-icon-bg': 'var(--accent-cyan-light)', '--stat-icon-color': 'var(--accent-cyan)' }}>
            <Warehouse size={24} />
          </div>
          <div>
            <div className="stat-val">{warehouses.length}</div>
            <div className="stat-lbl">Warehouses ({activeWarehouses} Active)</div>
          </div>
        </div>

        <div className="stat-card" style={{ '--stat-accent': 'var(--accent-rose)', cursor: 'pointer' }} onClick={() => setActiveTab('alerts')}>
          <div className="stat-icon" style={{ '--stat-icon-bg': 'var(--accent-rose-light)', '--stat-icon-color': 'var(--accent-rose)' }}>
            <AlertTriangle size={24} />
          </div>
          <div>
            <div className="stat-val" style={{ color: lowStockCount > 0 ? 'var(--accent-rose)' : 'inherit' }}>
              {lowStockCount}
            </div>
            <div className="stat-lbl">Low Stock Alerts</div>
          </div>
        </div>

        <div className="stat-card" style={{ '--stat-accent': 'var(--accent-emerald)', cursor: 'pointer' }} onClick={() => setActiveTab('autocomplete')}>
          <div className="stat-icon" style={{ '--stat-icon-bg': 'var(--accent-emerald-light)', '--stat-icon-color': 'var(--accent-emerald)' }}>
            <Zap size={24} />
          </div>
          <div>
            <div className="stat-val">&lt; 2ms</div>
            <div className="stat-lbl">Trie Search Latency</div>
          </div>
        </div>
      </div>

      {/* Main Grid: Architecture & Team Responsibilities */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(420px, 1fr))', gap: '24px' }}>
        {/* System Design Component Mapping Card */}
        <div className="card">
          <div className="card-header">
            <div>
              <h3 style={{ fontSize: '1.125rem' }}>System Design Component Mapping</h3>
              <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
                Blueprint architecture mapping 8 system-design components
              </p>
            </div>
            <span className="badge badge-indigo">All 5 Members</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '12px',
              background: 'rgba(255, 255, 255, 0.02)',
              borderRadius: '8px',
              border: '1px solid var(--border-subtle)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Zap size={18} style={{ color: 'var(--accent-cyan)' }} />
                <div>
                  <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>Autocomplete Engine (Member 4)</div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Prefix Trie, debouncing, & client telemetry</div>
                </div>
              </div>
              <button className="btn btn-secondary" style={{ padding: '4px 10px', fontSize: '0.75rem' }} onClick={() => setActiveTab('autocomplete')}>
                Inspect <ArrowRight size={12} />
              </button>
            </div>

            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '12px',
              background: 'rgba(255, 255, 255, 0.02)',
              borderRadius: '8px',
              border: '1px solid var(--border-subtle)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Database size={18} style={{ color: '#818cf8' }} />
                <div>
                  <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>Core Inventory & Atomicity (Member 1)</div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>MongoDB conditional updates & 0 overselling</div>
                </div>
              </div>
              <span className="badge badge-emerald">Verified</span>
            </div>

            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '12px',
              background: 'rgba(255, 255, 255, 0.02)',
              borderRadius: '8px',
              border: '1px solid var(--border-subtle)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Layers size={18} style={{ color: '#fbbf24' }} />
                <div>
                  <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>Consistent Hashing & Sharding (Member 2)</div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>InventoryPersistencePort router</div>
                </div>
              </div>
              <span className="badge badge-amber">Interface Ready</span>
            </div>

            <div style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              padding: '12px',
              background: 'rgba(255, 255, 255, 0.02)',
              borderRadius: '8px',
              border: '1px solid var(--border-subtle)'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <ShieldCheck size={18} style={{ color: '#fb7185' }} />
                <div>
                  <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>Redis & Rate Limiting (Member 3)</div>
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Token Bucket (429) & Cache-Aside</div>
                </div>
              </div>
              <button className="btn btn-secondary" style={{ padding: '4px 10px', fontSize: '0.75rem' }} onClick={() => setActiveTab('system-design')}>
                Test <ArrowRight size={12} />
              </button>
            </div>
          </div>
        </div>

        {/* Live Lab Demonstration Script Card */}
        <div className="card">
          <div className="card-header">
            <div>
              <h3 style={{ fontSize: '1.125rem' }}>10-Minute Final Demo Flow</h3>
              <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
                Guided evaluation steps as defined in Section 6.2 of the project design
              </p>
            </div>
            <span className="badge badge-cyan">Demo Checklist</span>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '0.8125rem' }}>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
              <span style={{ width: '22px', height: '22px', borderRadius: '50%', background: 'var(--primary)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 700 }}>1</span>
              <span><strong>Register Product & Warehouses:</strong> Create item with SKU and minimum 2 warehouses.</span>
            </div>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
              <span style={{ width: '22px', height: '22px', borderRadius: '50%', background: 'var(--primary)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 700 }}>2</span>
              <span><strong>Receive Purchase Order:</strong> Stock arrival into destination facility.</span>
            </div>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
              <span style={{ width: '22px', height: '22px', borderRadius: '50%', background: 'var(--primary)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 700 }}>3</span>
              <span><strong>Trie Autocomplete Search:</strong> Type prefix, observe sub-2ms lookup & match highlighting.</span>
            </div>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
              <span style={{ width: '22px', height: '22px', borderRadius: '50%', background: 'var(--primary)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 700 }}>4</span>
              <span><strong>Sales Order & Concurrency:</strong> Demonstrate atomic reservation & graceful 409 rejection on insufficient stock.</span>
            </div>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
              <span style={{ width: '22px', height: '22px', borderRadius: '50%', background: 'var(--primary)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 700 }}>5</span>
              <span><strong>Inter-Warehouse Stock Transfer:</strong> Track <code>INITIATED</code> → <code>IN_TRANSIT</code> → <code>COMPLETED</code>.</span>
            </div>
            <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
              <span style={{ width: '22px', height: '22px', borderRadius: '50%', background: 'var(--primary)', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.75rem', fontWeight: 700 }}>6</span>
              <span><strong>Low-Stock Alert Trigger:</strong> Stock dropping below reorderLevel immediately alerts in navbar.</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
