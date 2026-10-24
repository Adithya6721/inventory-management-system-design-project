import React from 'react';
import { LayoutDashboard, Package, Warehouse, ShoppingBag, ShoppingCart, ArrowLeftRight,
  Bell, Search, Network, PanelLeftClose, PanelLeftOpen, Boxes, ChevronDown } from 'lucide-react';

const groups = [
  { label: 'Workspace', items: [
    { id: 'dashboard', label: 'Overview', icon: LayoutDashboard },
    { id: 'products', label: 'Product catalog', icon: Package },
    { id: 'warehouses', label: 'Warehouses & stock', icon: Warehouse },
  ] },
  { label: 'Operations', items: [
    { id: 'purchase', label: 'Purchase orders', icon: ShoppingBag },
    { id: 'sales', label: 'Sales orders', icon: ShoppingCart },
    { id: 'transfers', label: 'Stock transfers', icon: ArrowLeftRight },
    { id: 'alerts', label: 'Low-stock alerts', icon: Bell },
  ] },
  { label: 'Developer', items: [
    { id: 'system-design', label: 'Architecture', icon: Network },
    { id: 'autocomplete', label: 'Autocomplete', icon: Search },
  ] },
];

export default function Sidebar({ activeTab, setActiveTab, lowStockCount = 0, collapsed, onToggle }) {
  return (
    <aside className="sidebar" aria-label="Main navigation">
      <button className="brand" onClick={() => setActiveTab('dashboard')} aria-label="AetherStock overview">
        <span className="brand-mark"><Boxes size={21} strokeWidth={1.7} /></span>
        <span className="brand-name">AetherStock<span>Inventory workspace</span></span>
      </button>
      <div className="workspace-switch"><span className="workspace-avatar">A</span><span className="nav-label">Operations workspace</span><ChevronDown size={13} className="workspace-chevron" /></div>
      <nav className="sidebar-nav">
        {groups.map(group => <div className="nav-group" key={group.label}>
          <div className="nav-group-label">{group.label}</div>
          {group.items.map(({ id, label, icon: Icon }) => (
            <button key={id} id={`sidebar-link-${id}`} className={`nav-item${activeTab === id ? ' active' : ''}`}
              aria-current={activeTab === id ? 'page' : undefined} aria-label={label} title={collapsed ? label : undefined}
              onClick={() => setActiveTab(id)}>
              <Icon size={17} strokeWidth={1.65} /><span className="nav-label">{label}</span>
              {id === 'alerts' && lowStockCount > 0 && <span className="nav-count">{lowStockCount}</span>}
            </button>
          ))}
        </div>)}
      </nav>
      <div className="sidebar-footer">
        <button className="nav-item collapse-control" onClick={onToggle} aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} aria-expanded={!collapsed}>
          {collapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}<span className="nav-label">Collapse sidebar</span>
        </button>
        <div className="workspace-footer"><span className="workspace-avatar">AS</span><span className="nav-label"><strong>AetherStock</strong><span>Inventory & operations</span></span></div>
      </div>
    </aside>
  );
}
