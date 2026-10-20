import React from 'react';
import { Bell, Activity, Layers, ExternalLink } from 'lucide-react';
import AutocompleteSearch from './AutocompleteSearch';

export default function Navbar({
  activeTab,
  setActiveTab,
  lowStockCount = 0,
  backendConnected = true,
  onSelectProduct
}) {
  return (
    <header className="top-navbar">
      {/* Left: Autocomplete Search Bar */}
      <div style={{ flex: 1, maxWidth: '540px' }}>
        <AutocompleteSearch onSelectProduct={onSelectProduct} />
      </div>

      {/* Right: Telemetry & Actions */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
        {/* Backend System Status */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
            fontSize: '0.8125rem',
            padding: '4px 10px',
            borderRadius: '9999px',
            background: backendConnected ? 'rgba(16, 185, 129, 0.1)' : 'rgba(244, 63, 94, 0.1)',
            border: `1px solid ${backendConnected ? 'rgba(16, 185, 129, 0.25)' : 'rgba(244, 63, 94, 0.25)'}`,
            color: backendConnected ? '#34d399' : '#fb7185'
          }}
          title={backendConnected ? "Spring Boot Backend (8080) Online" : "Cannot reach Backend. Verify Docker/mvn run"}
        >
          <span className={backendConnected ? "pulse-dot" : ""} style={{
            width: '8px',
            height: '8px',
            borderRadius: '50%',
            backgroundColor: backendConnected ? '#10b981' : '#f43f5e'
          }} />
          <span>{backendConnected ? "Backend Active" : "Backend Offline"}</span>
        </div>

        {/* Member 4 Badge */}
        <div
          style={{
            fontSize: '0.75rem',
            fontWeight: 600,
            padding: '4px 8px',
            borderRadius: '6px',
            background: 'rgba(99, 102, 241, 0.15)',
            border: '1px solid rgba(99, 102, 241, 0.3)',
            color: '#a5b4fc',
            display: 'flex',
            alignItems: 'center',
            gap: '4px'
          }}
        >
          <Layers size={13} />
          <span>Member 4: UI & Search</span>
        </div>

        {/* Low Stock Alerts Bell */}
        <button
          id="nav-alerts-button"
          onClick={() => setActiveTab('alerts')}
          style={{
            position: 'relative',
            background: activeTab === 'alerts' ? 'rgba(99, 102, 241, 0.2)' : 'rgba(255, 255, 255, 0.05)',
            border: '1px solid var(--border-subtle)',
            borderRadius: '8px',
            width: '38px',
            height: '38px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            cursor: 'pointer',
            color: activeTab === 'alerts' ? 'var(--primary)' : 'var(--text-secondary)',
            transition: 'all 150ms ease'
          }}
          title={`${lowStockCount} Active Low-Stock Alerts`}
        >
          <Bell size={18} />
          {lowStockCount > 0 && (
            <span
              style={{
                position: 'absolute',
                top: '-4px',
                right: '-4px',
                background: 'var(--accent-rose)',
                color: '#fff',
                fontSize: '0.6875rem',
                fontWeight: 700,
                width: '18px',
                height: '18px',
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 0 8px rgba(244, 63, 94, 0.7)'
              }}
            >
              {lowStockCount > 9 ? '9+' : lowStockCount}
            </span>
          )}
        </button>
      </div>
    </header>
  );
}
