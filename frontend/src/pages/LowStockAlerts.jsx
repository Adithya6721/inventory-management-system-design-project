import React, { useState, useEffect } from 'react';
import { AlertTriangle, ShoppingBag, CheckCircle, RefreshCw, Eye } from 'lucide-react';
import { api } from '../services/api';

export default function LowStockAlerts({
  alerts = [],
  products = [],
  warehouses = [],
  loading = false,
  onRefreshAlerts,
  onSelectProduct,
  onQuickRestock
}) {
  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '1.875rem', marginBottom: '4px' }}>Low-stock alerts</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            Review products that need replenishment across your warehouses.
          </p>
        </div>
        <button className="btn btn-secondary" onClick={onRefreshAlerts}>
          <RefreshCw size={14} /> Refresh Alerts
        </button>
      </div>

      {/* Explanatory Banner */}
      <div style={{
        background: 'var(--accent-amber-light)',
        border: '1px solid rgba(245, 158, 11, 0.25)',
        borderRadius: '12px',
        padding: '16px 20px',
        marginBottom: '24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: '12px'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <AlertTriangle size={24} style={{ color: 'var(--accent-amber)' }} />
          <div>
            <div style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--text-primary)' }}>
              Replenishment thresholds
            </div>
            <div style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
              An alert stays active until available stock rises above the product’s reorder level.
            </div>
          </div>
        </div>
        <span className="badge badge-amber" style={{ padding: '6px 12px' }}>
          {alerts.length} Active Incidents
        </span>
      </div>

      {/* Alerts Table */}
      <div className="card" style={{ padding: 0 }}>
        {alerts.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-muted)' }}>
            <CheckCircle size={40} style={{ margin: '0 auto 12px auto', color: 'var(--accent-emerald)' }} />
            <div style={{ fontSize: '1rem', fontWeight: 600, color: 'var(--text-primary)' }}>All Inventory Levels Healthy</div>
            <p style={{ fontSize: '0.875rem', marginTop: '4px' }}>
              No warehouse currently has available stock at or below its reorder threshold.
            </p>
          </div>
        ) : (
          <div className="table-container" style={{ border: 'none' }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Warehouse Facility</th>
                  <th>Available Quantity</th>
                  <th>Reorder Threshold</th>
                  <th>Deficit</th>
                  <th>Status</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {alerts.map((alert, idx) => {
                  const prod = products.find(p => p.productId === alert.productId) || { name: alert.productId, sku: alert.productId };
                  const wh = warehouses.find(w => w.warehouseId === alert.warehouseId) || { name: alert.warehouseId };
                  const available = alert.availableQuantity !== undefined ? alert.availableQuantity : 0;
                  const threshold = alert.threshold || 10;
                  const deficit = Math.max(0, threshold - available);

                  return (
                    <tr key={alert.alertId || idx}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{prod.name}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--accent-cyan)' }}>
                          SKU: {prod.sku}
                        </div>
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{wh.name}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>ID: {alert.warehouseId}</div>
                      </td>
                      <td>
                        <span style={{ fontSize: '0.9375rem', fontWeight: 700, color: 'var(--accent-rose)' }}>
                          {available} units
                        </span>
                      </td>
                      <td>
                        <span style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
                          {threshold} units
                        </span>
                      </td>
                      <td>
                        <span className="badge badge-rose">
                          -{deficit} units
                        </span>
                      </td>
                      <td>
                        <span className="badge badge-amber">
                          {alert.status || 'ACTIVE'}
                        </span>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', gap: '8px' }}>
                          <button
                            className="btn btn-secondary"
                            style={{ padding: '6px 10px', fontSize: '0.75rem' }}
                            onClick={() => onSelectProduct(prod)}
                          >
                            <Eye size={13} /> View Stock
                          </button>
                          <button
                            className="btn btn-primary"
                            style={{ padding: '6px 12px', fontSize: '0.75rem' }}
                            onClick={() => onQuickRestock(prod, alert.warehouseId)}
                          >
                            <ShoppingBag size={13} /> Restock PO
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
