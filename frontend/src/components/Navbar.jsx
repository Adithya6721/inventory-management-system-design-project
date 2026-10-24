import React from 'react';
import { Bell, ChevronRight, Moon, Sun, RefreshCw } from 'lucide-react';
import AutocompleteSearch from './AutocompleteSearch';

const titles = { dashboard: 'Overview', products: 'Product catalog', warehouses: 'Warehouses & stock',
  purchase: 'Purchase orders', sales: 'Sales orders', transfers: 'Stock transfers', alerts: 'Low-stock alerts',
  autocomplete: 'Autocomplete', 'system-design': 'Architecture' };

export default function Navbar({ activeTab, setActiveTab, lowStockCount = 0, backendConnected,
  onSelectProduct, theme, onToggleTheme, onRefresh, refreshing }) {
  return (
    <header className="top-navbar">
      <div className="breadcrumb"><span>{['autocomplete', 'system-design'].includes(activeTab) ? 'Developer' : 'Workspace'}</span><ChevronRight size={13} /><strong>{titles[activeTab]}</strong></div>
      <div className="global-search"><AutocompleteSearch onSelectProduct={onSelectProduct} placeholder="Search products, SKUs, categories…" /></div>
      <div className="navbar-actions">
        <span className={`connection-status ${backendConnected ? 'connected' : 'offline'}`} role="status" title={backendConnected ? 'API connection is healthy' : 'Cannot reach the inventory API'}>
          <i /><span>{backendConnected ? 'Connected' : 'Offline'}</span>
        </span>
        <span className="toolbar-divider" />
        <button className="icon-button" title="Refresh workspace" aria-label="Refresh workspace" onClick={onRefresh} disabled={refreshing}><RefreshCw size={17} className={refreshing ? 'is-spinning' : ''} /></button>
        <button className="icon-button" title={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`} aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`} onClick={onToggleTheme}>{theme === 'light' ? <Moon size={17} /> : <Sun size={17} />}</button>
        <button id="nav-alerts-button" className={`icon-button notification-button${activeTab === 'alerts' ? ' selected' : ''}`} onClick={() => setActiveTab('alerts')}
          title={`${lowStockCount} active low-stock alerts`} aria-label={`Notifications, ${lowStockCount} active low-stock alerts`}>
          <Bell size={18} />{lowStockCount > 0 && <span className="notification-dot" />}
        </button>
      </div>
    </header>
  );
}
