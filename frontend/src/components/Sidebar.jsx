import React from 'react';
import {
  LayoutDashboard,
  Package,
  Warehouse,
  ShoppingBag,
  ShoppingCart,
  ArrowLeftRight,
  AlertTriangle,
  Zap,
  Cpu,
  Boxes,
  Globe
} from 'lucide-react';

export default function Sidebar({ activeTab, setActiveTab, lowStockCount = 0 }) {
  const menuItems = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { id: 'products', label: 'Product Catalog', icon: Package },
    { id: 'warehouses', label: 'Warehouses & Stock', icon: Warehouse },
    { id: 'purchase', label: 'Purchase Orders', icon: ShoppingBag },
    { id: 'sales', label: 'Sales Orders', icon: ShoppingCart },
    { id: 'transfers', label: 'Stock Transfers', icon: ArrowLeftRight },
    { id: 'alerts', label: 'Low-Stock Alerts', icon: AlertTriangle, badge: lowStockCount },
    { id: 'supplier-crawler', label: 'Supplier Crawler', icon: Globe, member5: true },
    { id: 'autocomplete', label: 'Autocomplete Lab', icon: Zap, highlight: true },
    { id: 'system-design', label: 'System Architecture', icon: Cpu },
  ];

  return (
    <aside className="sidebar">
      {/* Brand Header */}
      <div style={{
        padding: '24px 20px',
        borderBottom: '1px solid var(--border-subtle)',
        display: 'flex',
        alignItems: 'center',
        gap: '12px'
      }}>
        <div style={{
          width: '38px',
          height: '38px',
          borderRadius: '10px',
          background: 'linear-gradient(135deg, var(--primary), var(--accent-cyan))',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          boxShadow: '0 4px 12px rgba(99, 102, 241, 0.4)'
        }}>
          <Boxes size={22} />
        </div>
        <div>
          <div style={{ fontSize: '1rem', fontWeight: 700, letterSpacing: '-0.02em' }}>
            Aether<span style={{ color: 'var(--primary)' }}>Stock</span>
          </div>
          <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>
            Scalable IMS • Lab Topic 4
          </div>
        </div>
      </div>

      {/* Navigation List */}
      <nav style={{ padding: '16px 12px', flex: 1, overflowY: 'auto' }}>
        <div style={{
          fontSize: '0.6875rem',
          fontWeight: 600,
          color: 'var(--text-muted)',
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          padding: '0 12px 8px 12px'
        }}>
          Operations & Inventory
        </div>

        {menuItems.slice(0, 7).map(item => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              id={`sidebar-link-${item.id}`}
              onClick={() => setActiveTab(item.id)}
              style={{
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 14px',
                marginBottom: '4px',
                borderRadius: '8px',
                border: 'none',
                background: isActive ? 'linear-gradient(90deg, rgba(99, 102, 241, 0.18), rgba(99, 102, 241, 0.05))' : 'transparent',
                color: isActive ? '#fff' : 'var(--text-secondary)',
                fontWeight: isActive ? 600 : 500,
                fontSize: '0.875rem',
                cursor: 'pointer',
                textAlign: 'left',
                borderLeft: isActive ? '3px solid var(--primary)' : '3px solid transparent',
                transition: 'all 120ms ease'
              }}
              onMouseEnter={(e) => {
                if (!isActive) e.currentTarget.style.background = 'rgba(255, 255, 255, 0.03)';
              }}
              onMouseLeave={(e) => {
                if (!isActive) e.currentTarget.style.background = 'transparent';
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Icon size={18} style={{ color: isActive ? 'var(--primary)' : 'var(--text-muted)' }} />
                <span>{item.label}</span>
              </div>
              {item.badge > 0 && (
                <span className="badge badge-rose" style={{ fontSize: '0.7rem' }}>
                  {item.badge}
                </span>
              )}
            </button>
          );
        })}

        <div style={{
          fontSize: '0.6875rem',
          fontWeight: 600,
          color: 'var(--text-muted)',
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          padding: '18px 12px 8px 12px'
        }}>
          System Design Evaluation
        </div>

        {menuItems.slice(7).map(item => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              id={`sidebar-link-${item.id}`}
              onClick={() => setActiveTab(item.id)}
              style={{
                width: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '10px 14px',
                marginBottom: '4px',
                borderRadius: '8px',
                border: 'none',
                background: isActive ? 'linear-gradient(90deg, rgba(6, 182, 212, 0.18), rgba(6, 182, 212, 0.05))' : 'transparent',
                color: isActive ? '#fff' : 'var(--text-secondary)',
                fontWeight: isActive ? 600 : 500,
                fontSize: '0.875rem',
                cursor: 'pointer',
                textAlign: 'left',
                borderLeft: isActive ? '3px solid var(--accent-cyan)' : '3px solid transparent',
                transition: 'all 120ms ease'
              }}
              onMouseEnter={(e) => {
                if (!isActive) e.currentTarget.style.background = 'rgba(255, 255, 255, 0.03)';
              }}
              onMouseLeave={(e) => {
                if (!isActive) e.currentTarget.style.background = 'transparent';
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Icon size={18} style={{ color: isActive ? 'var(--accent-cyan)' : 'var(--text-muted)' }} />
                <span>{item.label}</span>
              </div>
              {item.highlight && (
                <span className="badge badge-cyan" style={{ fontSize: '0.65rem' }}>
                  Member 4
                </span>
              )}
              {item.member5 && (
                <span className="badge badge-emerald" style={{ fontSize: '0.65rem' }}>
                  Member 5
                </span>
              )}
            </button>
          );
        })}
      </nav>

      {/* Footer / System Info */}
      <div style={{
        padding: '16px 20px',
        borderTop: '1px solid var(--border-subtle)',
        fontSize: '0.75rem',
        color: 'var(--text-muted)',
        background: 'rgba(0, 0, 0, 0.2)'
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '4px' }}>
          <span>Architecture:</span>
          <span style={{ color: 'var(--text-secondary)', fontWeight: 600 }}>Distributed Mongo</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
          <span>Cache:</span>
          <span style={{ color: 'var(--accent-emerald)', fontWeight: 600 }}>Redis + Trie</span>
        </div>
      </div>
    </aside>
  );
}
