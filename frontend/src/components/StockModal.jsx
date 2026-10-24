import React, { useState, useEffect } from 'react';
import { X, Package, Warehouse, History, ArrowUpRight, ArrowDownRight, RefreshCw, AlertCircle } from 'lucide-react';
import { api } from '../services/api';

export default function StockModal({ product, onClose, onOpenSales, onOpenTransfer, onRefreshNeeded }) {
  const [activeTab, setActiveTab] = useState('distribution'); // 'distribution' | 'history' | 'adjust'
  const [stockRecords, setStockRecords] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [movements, setMovements] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Adjustment state
  const [adjustWarehouseId, setAdjustWarehouseId] = useState('');
  const [physicalDelta, setPhysicalDelta] = useState(0);
  const [reservedDelta, setReservedDelta] = useState(0);
  const [adjustReason, setAdjustReason] = useState('Manual audit reconciliation');
  const [adjustSubmitting, setAdjustSubmitting] = useState(false);

  useEffect(() => {
    if (product) {
      loadStockData();
    }
  }, [product]);

  const loadStockData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [invRes, whRes, movRes] = await Promise.all([
        api.getInventoryByProduct(product.productId).catch(() => []),
        api.getWarehouses().catch(() => []),
        api.getInventoryMovements(product.productId).catch(() => [])
      ]);

      setStockRecords(invRes || []);
      setWarehouses(whRes || []);
      setMovements(movRes || []);
      if (whRes && whRes.length > 0 && !adjustWarehouseId) {
        setAdjustWarehouseId(whRes[0].warehouseId);
      }
    } catch (err) {
      setError(err.message || 'Failed to load stock data');
    } finally {
      setLoading(false);
    }
  };

  const handleAdjustSubmit = async (e) => {
    e.preventDefault();
    if (!adjustWarehouseId) return;
    setAdjustSubmitting(true);
    try {
      await api.adjustStock({
        productId: product.productId,
        warehouseId: adjustWarehouseId,
        physicalDelta: Number(physicalDelta),
        reservedDelta: Number(reservedDelta),
        reason: adjustReason
      });
      setPhysicalDelta(0);
      setReservedDelta(0);
      await loadStockData();
      if (onRefreshNeeded) onRefreshNeeded();
      setActiveTab('distribution');
    } catch (err) {
      alert(`Adjustment error: ${err.message}`);
    } finally {
      setAdjustSubmitting(false);
    }
  };

  if (!product) return null;

  // Calculate totals across warehouses
  const totalPhysical = stockRecords.reduce((sum, r) => sum + (r.quantity || 0), 0);
  const totalReserved = stockRecords.reduce((sum, r) => sum + (r.reservedQuantity || 0), 0);
  const totalAvailable = totalPhysical - totalReserved;
  const isLowStock = totalAvailable <= product.reorderLevel;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" style={{ maxWidth: '780px' }} onClick={e => e.stopPropagation()}>
        {/* Modal Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '20px' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <h2 style={{ fontSize: '1.25rem' }}>{product.name}</h2>
              {isLowStock && (
                <span className="badge badge-rose">Low Stock (Threshold: {product.reorderLevel})</span>
              )}
            </div>
            <div style={{ display: 'flex', gap: '12px', fontSize: '0.8125rem', color: 'var(--text-secondary)', marginTop: '4px' }}>
              <span>SKU: <code style={{ color: 'var(--accent-cyan)' }}>{product.sku}</code></span>
              <span>Category: <strong>{product.category}</strong></span>
              <span>Price: <strong style={{ color: 'var(--accent-emerald)' }}>${Number(product.price).toFixed(2)}</strong></span>
            </div>
          </div>
          <button onClick={onClose} className="btn-secondary" style={{ padding: '6px', borderRadius: '50%' }}>
            <X size={18} />
          </button>
        </div>

        {/* Global Summary KPI Bar */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(3, 1fr)',
          gap: '12px',
          background: 'var(--bg-glass-subtle)',
          border: '1px solid var(--border-subtle)',
          borderRadius: '10px',
          padding: '14px',
          marginBottom: '20px'
        }}>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Total Physical Stock</div>
            <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)' }}>{totalPhysical}</div>
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Reserved for Orders</div>
            <div style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent-amber)' }}>{totalReserved}</div>
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Real Available Stock</div>
            <div style={{ fontSize: '1.25rem', fontWeight: 700, color: totalAvailable > 0 ? 'var(--accent-emerald)' : 'var(--accent-rose)' }}>
              {totalAvailable}
            </div>
          </div>
        </div>

        {/* Tab Controls */}
        <div style={{ display: 'flex', gap: '8px', borderBottom: '1px solid var(--border-subtle)', marginBottom: '18px' }}>
          <button
            onClick={() => setActiveTab('distribution')}
            style={{
              padding: '8px 16px',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'distribution' ? '2px solid var(--primary)' : '2px solid transparent',
              color: activeTab === 'distribution' ? 'var(--text-primary)' : 'var(--text-secondary)',
              fontWeight: 600,
              fontSize: '0.875rem',
              cursor: 'pointer'
            }}
          >
            Warehouse Distribution
          </button>
          <button
            onClick={() => setActiveTab('history')}
            style={{
              padding: '8px 16px',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'history' ? '2px solid var(--primary)' : '2px solid transparent',
              color: activeTab === 'history' ? 'var(--text-primary)' : 'var(--text-secondary)',
              fontWeight: 600,
              fontSize: '0.875rem',
              cursor: 'pointer'
            }}
          >
            Audit Trail ({movements.length})
          </button>
          <button
            onClick={() => setActiveTab('adjust')}
            style={{
              padding: '8px 16px',
              background: 'transparent',
              border: 'none',
              borderBottom: activeTab === 'adjust' ? '2px solid var(--primary)' : '2px solid transparent',
              color: activeTab === 'adjust' ? 'var(--text-primary)' : 'var(--text-secondary)',
              fontWeight: 600,
              fontSize: '0.875rem',
              cursor: 'pointer'
            }}
          >
            Stock Adjustment
          </button>
        </div>

        {/* Tab 1: Warehouse Distribution */}
        {activeTab === 'distribution' && (
          <div>
            {loading ? (
              <div style={{ textAlign: 'center', padding: '30px', color: 'var(--text-muted)' }}>
                Loading warehouse stocks...
              </div>
            ) : stockRecords.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '30px', color: 'var(--text-muted)' }}>
                No stock records created for this product yet.
              </div>
            ) : (
              <div className="table-container">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Warehouse</th>
                      <th>Physical</th>
                      <th>Reserved</th>
                      <th>Available</th>
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stockRecords.map(record => {
                      const wh = warehouses.find(w => w.warehouseId === record.warehouseId) || { name: record.warehouseId };
                      const avail = (record.quantity || 0) - (record.reservedQuantity || 0);
                      return (
                        <tr key={record.id || `${record.productId}_${record.warehouseId}`}>
                          <td>
                            <div style={{ fontWeight: 600 }}>{wh.name}</div>
                            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                              ID: {record.warehouseId} {wh.location ? `• ${wh.location}` : ''}
                            </div>
                          </td>
                          <td style={{ fontWeight: 600 }}>{record.quantity}</td>
                          <td style={{ color: 'var(--accent-amber)' }}>{record.reservedQuantity}</td>
                          <td>
                            <span className={`badge ${avail > 0 ? 'badge-emerald' : 'badge-rose'}`}>
                              {avail} units
                            </span>
                          </td>
                          <td>
                            <div style={{ display: 'flex', gap: '6px' }}>
                              <button
                                className="btn btn-secondary"
                                style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                                onClick={() => {
                                  onClose();
                                  if (onOpenSales) onOpenSales(product, record.warehouseId);
                                }}
                              >
                                Sell
                              </button>
                              <button
                                className="btn btn-secondary"
                                style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                                onClick={() => {
                                  onClose();
                                  if (onOpenTransfer) onOpenTransfer(product, record.warehouseId);
                                }}
                              >
                                Transfer
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
        )}

        {/* Tab 2: Movement Audit Trail */}
        {activeTab === 'history' && (
          <div>
            {movements.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '30px', color: 'var(--text-muted)' }}>
                No audit movements recorded yet.
              </div>
            ) : (
              <div className="table-container" style={{ maxHeight: '320px', overflowY: 'auto' }}>
                <table className="table">
                  <thead>
                    <tr>
                      <th>Timestamp</th>
                      <th>Type</th>
                      <th>Warehouse</th>
                      <th>Delta</th>
                      <th>Ref ID</th>
                    </tr>
                  </thead>
                  <tbody>
                    {movements.map((m, idx) => {
                      const isPositive = (m.quantityDelta || 0) > 0;
                      return (
                        <tr key={m.movementId || idx}>
                          <td style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                            {new Date(m.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                          </td>
                          <td>
                            <span className="badge badge-indigo" style={{ fontSize: '0.6875rem' }}>
                              {m.movementType}
                            </span>
                          </td>
                          <td style={{ fontSize: '0.8125rem' }}>{m.warehouseId}</td>
                          <td style={{ fontWeight: 700, color: isPositive ? 'var(--accent-emerald)' : 'var(--accent-rose)' }}>
                            {isPositive ? `+${m.quantityDelta}` : m.quantityDelta}
                          </td>
                          <td style={{ fontSize: '0.75rem', fontFamily: 'monospace', color: 'var(--text-muted)' }}>
                            {m.referenceId || '-'}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Stock Adjustment */}
        {activeTab === 'adjust' && (
          <form onSubmit={handleAdjustSubmit}>
            <div className="form-group">
              <label className="form-label">Warehouse</label>
              <select
                className="form-select"
                value={adjustWarehouseId}
                onChange={e => setAdjustWarehouseId(e.target.value)}
                required
              >
                {warehouses.map(w => (
                  <option key={w.warehouseId} value={w.warehouseId}>
                    {w.name} ({w.warehouseId})
                  </option>
                ))}
              </select>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
              <div className="form-group">
                <label className="form-label">Physical Delta (e.g. +50 or -10)</label>
                <input
                  type="number"
                  className="form-input"
                  value={physicalDelta}
                  onChange={e => setPhysicalDelta(e.target.value)}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label">Reserved Delta (e.g. 0)</label>
                <input
                  type="number"
                  className="form-input"
                  value={reservedDelta}
                  onChange={e => setReservedDelta(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Audit Reason</label>
              <input
                type="text"
                className="form-input"
                value={adjustReason}
                onChange={e => setAdjustReason(e.target.value)}
                required
              />
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
              <button type="button" className="btn btn-secondary" onClick={() => setActiveTab('distribution')}>
                Cancel
              </button>
              <button type="submit" className="btn btn-primary" disabled={adjustSubmitting}>
                {adjustSubmitting ? 'Recording Audit...' : 'Commit Adjustment'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
