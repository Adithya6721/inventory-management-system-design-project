import React, { useState } from 'react';
import { ArrowLeftRight, Plus, CheckCircle, Truck, AlertCircle } from 'lucide-react';
import { api } from '../services/api';

export default function StockTransfers({
  warehouses = [],
  products = [],
  onShowToast,
  onRefreshAll
}) {
  const [transfers, setTransfers] = useState([]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Form state
  const [sourceWarehouseId, setSourceWarehouseId] = useState('');
  const [destWarehouseId, setDestWarehouseId] = useState('');
  const [selectedProductId, setSelectedProductId] = useState('');
  const [transferQuantity, setTransferQuantity] = useState('10');
  const [transferError, setTransferError] = useState('');

  const handleInitiateTransfer = async (e) => {
    e.preventDefault();
    setTransferError('');

    if (sourceWarehouseId === destWarehouseId) {
      setTransferError('Source and Destination warehouses must be distinct.');
      return;
    }
    if (!sourceWarehouseId || !destWarehouseId || !selectedProductId) {
      setTransferError('Please fill out all transfer fields.');
      return;
    }

    setSubmitting(true);
    try {
      const payload = {
        sourceWarehouseId,
        destinationWarehouseId: destWarehouseId,
        items: [
          {
            productId: selectedProductId,
            quantity: parseInt(transferQuantity, 10)
          }
        ]
      };

      const res = await api.createTransfer(payload);
      setTransfers(prev => [res, ...prev]);

      onShowToast({
        type: 'success',
        title: 'Transfer Dispatched (IN_TRANSIT)',
        message: `Transfer #${res.transferId} initiated. Stock deducted from origin and en route to destination.`
      });

      setShowCreateModal(false);
      if (onRefreshAll) onRefreshAll();
    } catch (err) {
      setTransferError(err.message || 'Failed to initiate transfer');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCompleteTransfer = async (transferId) => {
    try {
      const updated = await api.completeTransfer(transferId);
      setTransfers(prev => prev.map(t => t.transferId === transferId ? updated : t));

      onShowToast({
        type: 'success',
        title: 'Transfer Completed',
        message: `Stock safely delivered and credited to destination warehouse.`
      });

      if (onRefreshAll) onRefreshAll();
    } catch (err) {
      onShowToast({
        type: 'error',
        title: 'Completion Error',
        message: err.message
      });
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', marginBottom: '4px' }}>Inter-Warehouse Stock Transfers</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            Multi-stage movements with IN_TRANSIT state preventing duplicate allocation across nodes (FR8)
          </p>
        </div>
        <button id="btn-initiate-transfer" className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
          <Plus size={16} /> Initiate Stock Transfer
        </button>
      </div>

      {/* Transfers List */}
      <div className="card" style={{ padding: 0 }}>
        {transfers.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-muted)' }}>
            <Truck size={40} style={{ margin: '0 auto 12px auto', opacity: 0.4 }} />
            <div style={{ fontSize: '1rem', fontWeight: 600 }}>No stock transfers initiated</div>
            <p style={{ fontSize: '0.875rem', marginTop: '4px' }}>
              Transfer goods between warehouses to balance regional demand.
            </p>
          </div>
        ) : (
          <div className="table-container" style={{ border: 'none' }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Transfer ID</th>
                  <th>Origin Warehouse</th>
                  <th>Destination Warehouse</th>
                  <th>Items & Quantities</th>
                  <th>Transit Status</th>
                  <th>Timestamp</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {transfers.map(tr => {
                  const srcWh = warehouses.find(w => w.warehouseId === tr.sourceWarehouseId);
                  const dstWh = warehouses.find(w => w.warehouseId === tr.destinationWarehouseId);
                  const isInTransit = tr.status === 'IN_TRANSIT' || tr.status === 'INITIATED';
                  const isCompleted = tr.status === 'COMPLETED';

                  return (
                    <tr key={tr.transferId}>
                      <td>
                        <code style={{ color: 'var(--primary)', fontWeight: 600 }}>{tr.transferId}</code>
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{srcWh ? srcWh.name : tr.sourceWarehouseId}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{tr.sourceWarehouseId}</div>
                      </td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{dstWh ? dstWh.name : tr.destinationWarehouseId}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{tr.destinationWarehouseId}</div>
                      </td>
                      <td>
                        {tr.items.map((i, idx) => {
                          const p = products.find(prod => prod.productId === i.productId);
                          return (
                            <div key={idx} style={{ fontSize: '0.8125rem' }}>
                              <span style={{ fontWeight: 600 }}>{p ? p.name : i.productId}</span>: {i.quantity} units
                            </div>
                          );
                        })}
                      </td>
                      <td>
                        <span className={`badge ${isCompleted ? 'badge-emerald' : 'badge-amber'}`} style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                          <Truck size={12} />
                          {tr.status}
                        </span>
                      </td>
                      <td style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                        {new Date(tr.createdAt || Date.now()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        {isInTransit ? (
                          <button
                            className="btn btn-success"
                            style={{ padding: '6px 12px', fontSize: '0.75rem' }}
                            onClick={() => handleCompleteTransfer(tr.transferId)}
                          >
                            <CheckCircle size={14} /> Mark Delivered
                          </button>
                        ) : (
                          <span style={{ fontSize: '0.75rem', color: 'var(--accent-emerald)' }}>
                            Completed
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Initiate Transfer Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontSize: '1.25rem', marginBottom: '6px' }}>Initiate Stock Transfer (FR8)</h2>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
              Transfers units out of origin into <code>IN_TRANSIT</code> state until delivery receipt.
            </p>

            {transferError && (
              <div style={{
                background: 'rgba(244, 63, 94, 0.15)',
                border: '1px solid var(--accent-rose)',
                borderRadius: '8px',
                padding: '12px',
                marginBottom: '16px',
                color: '#fb7185',
                fontSize: '0.8125rem',
                display: 'flex',
                gap: '8px',
                alignItems: 'center'
              }}>
                <AlertCircle size={18} style={{ flexShrink: 0 }} />
                <span>{transferError}</span>
              </div>
            )}

            <form onSubmit={handleInitiateTransfer}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="form-group">
                  <label className="form-label">Source Warehouse *</label>
                  <select
                    className="form-select"
                    value={sourceWarehouseId}
                    onChange={e => setSourceWarehouseId(e.target.value)}
                    required
                  >
                    <option value="">-- Origin Facility --</option>
                    {warehouses.map(w => (
                      <option key={w.warehouseId} value={w.warehouseId}>
                        {w.name} ({w.warehouseId})
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Destination Warehouse *</label>
                  <select
                    className="form-select"
                    value={destWarehouseId}
                    onChange={e => setDestWarehouseId(e.target.value)}
                    required
                  >
                    <option value="">-- Target Facility --</option>
                    {warehouses.map(w => (
                      <option key={w.warehouseId} value={w.warehouseId} disabled={w.warehouseId === sourceWarehouseId}>
                        {w.name} ({w.warehouseId}) {w.warehouseId === sourceWarehouseId ? '(Origin)' : ''}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '14px' }}>
                <div className="form-group">
                  <label className="form-label">Product to Move *</label>
                  <select
                    className="form-select"
                    value={selectedProductId}
                    onChange={e => setSelectedProductId(e.target.value)}
                    required
                  >
                    <option value="">-- Select Product --</option>
                    {products.map(p => (
                      <option key={p.productId} value={p.productId}>
                        {p.name} ({p.sku})
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Quantity *</label>
                  <input
                    type="number"
                    min="1"
                    className="form-input"
                    value={transferQuantity}
                    onChange={e => setTransferQuantity(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Dispatching...' : 'Dispatch Transfer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
