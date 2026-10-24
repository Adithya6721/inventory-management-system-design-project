import React, { useState } from 'react';
import { ShoppingCart, Plus, Check, X, ShieldAlert, ArrowUpRight, AlertTriangle } from 'lucide-react';
import { api } from '../services/api';
import { pendingCreate, completeCreate } from '../services/idempotency';

export default function SalesOrders({
  warehouses = [],
  products = [],
  onShowToast,
  onRefreshAll
}) {
  const [salesOrders, setSalesOrders] = useState([]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Form state
  const [selectedWarehouseId, setSelectedWarehouseId] = useState('');
  const [selectedProductId, setSelectedProductId] = useState('');
  const [orderQuantity, setOrderQuantity] = useState('1');
  const [conflictError, setConflictError] = useState(null);

  const handlePlaceOrder = async (e) => {
    e.preventDefault();
    setConflictError(null);
    if (!selectedWarehouseId || !selectedProductId) {
      alert('Please select product and warehouse.');
      return;
    }
    setSubmitting(true);

    try {
      const payload = {
        items: [
          {
            productId: selectedProductId,
            warehouseId: selectedWarehouseId,
            quantity: parseInt(orderQuantity, 10)
          }
        ]
      };

      const intent = pendingCreate('sales', payload);
      const res = await api.createSalesOrder(intent);
      completeCreate('sales', intent.idempotencyKey);
      setSalesOrders(prev => [res, ...prev]);

      onShowToast({
        type: 'success',
        title: 'Sales Order Reserved',
        message: `Order #${res.orderId} reserved ${orderQuantity} units. Status: ${res.status}`
      });

      setShowCreateModal(false);
      if (onRefreshAll) onRefreshAll();
    } catch (err) {
      if (err.isConflict || err.status === 409) {
        setConflictError(err.message || 'Order conflicts with the current inventory state.');
        onShowToast({
          type: 'warning',
          title: 'Order Conflict (HTTP 409)',
          message: err.message || 'Retry the unchanged order after refreshing inventory.'
        });
      } else {
        setConflictError(err.message || 'Order failed');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleCompleteOrder = async (orderId) => {
    try {
      const updated = await api.completeSalesOrder(orderId);
      setSalesOrders(prev => prev.map(o => o.orderId === orderId ? updated : o));
      onShowToast({
        type: 'success',
        title: 'Order Completed & Fulfilled',
        message: `Order #${orderId} fulfilled. Physical stock deducted from warehouse.`
      });
      if (onRefreshAll) onRefreshAll();
    } catch (err) {
      onShowToast({ type: 'error', title: 'Complete Order Failed', message: err.message });
    }
  };

  const handleCancelOrder = async (orderId) => {
    try {
      const updated = await api.cancelSalesOrder(orderId);
      setSalesOrders(prev => prev.map(o => o.orderId === orderId ? updated : o));
      onShowToast({
        type: 'warning',
        title: 'Order Cancelled',
        message: `Order #${orderId} cancelled. Reserved stock released back to available pool.`
      });
      if (onRefreshAll) onRefreshAll();
    } catch (err) {
      onShowToast({ type: 'error', title: 'Cancellation Failed', message: err.message });
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', marginBottom: '4px' }}>Sales Orders & Atomic Reservations</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            Two-phase order fulfillment lifecycle with atomic reservation preventing negative stock (FR7)
          </p>
        </div>
        <button id="btn-place-sales-order" className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
          <Plus size={16} /> Place Sales Order
        </button>
      </div>

      {/* Orders Table */}
      <div className="card" style={{ padding: 0 }}>
        {salesOrders.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-muted)' }}>
            <ShoppingCart size={40} style={{ margin: '0 auto 12px auto', opacity: 0.4 }} />
            <div style={{ fontSize: '1rem', fontWeight: 600 }}>No sales orders placed yet</div>
            <p style={{ fontSize: '0.875rem', marginTop: '4px' }}>
              Click "Place Sales Order" to test atomic reservation and overselling prevention.
            </p>
          </div>
        ) : (
          <div className="table-container" style={{ border: 'none' }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Order ID</th>
                  <th>Items & Quantities</th>
                  <th>Warehouse</th>
                  <th>Lifecycle Status</th>
                  <th>Created At</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {salesOrders.map(order => {
                  const isReserved = order.status === 'RESERVED';
                  const isCompleted = order.status === 'COMPLETED';
                  const isCancelled = order.status === 'CANCELLED';

                  let statusBadge = 'badge-indigo';
                  if (isCompleted) statusBadge = 'badge-emerald';
                  if (isCancelled) statusBadge = 'badge-rose';

                  return (
                    <tr key={order.orderId}>
                      <td>
                        <code style={{ color: 'var(--primary)', fontWeight: 600 }}>{order.orderId}</code>
                      </td>
                      <td>
                        {order.items.map((i, idx) => {
                          const p = products.find(prod => prod.productId === i.productId);
                          return (
                            <div key={idx} style={{ fontSize: '0.8125rem' }}>
                              <span style={{ fontWeight: 600 }}>{p ? p.name : i.productId}</span>: {i.quantity} units
                            </div>
                          );
                        })}
                      </td>
                      <td>
                        <span style={{ fontSize: '0.8125rem' }}>
                          {order.items[0]?.warehouseId || 'Central'}
                        </span>
                      </td>
                      <td>
                        <span className={`badge ${statusBadge}`}>
                          {order.status}
                        </span>
                      </td>
                      <td style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                        {new Date(order.createdAt || Date.now()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        {isReserved && (
                          <div style={{ display: 'inline-flex', gap: '8px' }}>
                            <button
                              className="btn btn-success"
                              style={{ padding: '4px 10px', fontSize: '0.75rem' }}
                              onClick={() => handleCompleteOrder(order.orderId)}
                              title="Deduct physical stock and fulfill order"
                            >
                              <Check size={14} /> Fulfill
                            </button>
                            <button
                              className="btn btn-danger"
                              style={{ padding: '4px 10px', fontSize: '0.75rem' }}
                              onClick={() => handleCancelOrder(order.orderId)}
                              title="Release reservation back to available stock"
                            >
                              <X size={14} /> Cancel
                            </button>
                          </div>
                        )}
                        {isCompleted && (
                          <span style={{ fontSize: '0.75rem', color: 'var(--accent-emerald)' }}>
                            Stock Deducted
                          </span>
                        )}
                        {isCancelled && (
                          <span style={{ fontSize: '0.75rem', color: 'var(--accent-rose)' }}>
                            Released
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

      {/* Place Order Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontSize: '1.25rem', marginBottom: '6px' }}>Place Sales Order (FR7)</h2>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
              Executes conditional atomic reservation at the database layer.
            </p>

            {conflictError && (
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
                alignItems: 'flex-start'
              }}>
                <ShieldAlert size={20} style={{ flexShrink: 0, marginTop: '2px' }} />
                <span>{conflictError}</span>
              </div>
            )}

            <form onSubmit={handlePlaceOrder}>
              <div className="form-group">
                <label className="form-label">Fulfillment Warehouse *</label>
                <select
                  className="form-select"
                  value={selectedWarehouseId}
                  onChange={e => setSelectedWarehouseId(e.target.value)}
                  required
                >
                  <option value="">-- Select Fulfillment Warehouse --</option>
                  {warehouses.map(w => (
                    <option key={w.warehouseId} value={w.warehouseId}>
                      {w.name} ({w.location}) - {w.warehouseId}
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '14px' }}>
                <div className="form-group">
                  <label className="form-label">Product *</label>
                  <select
                    className="form-select"
                    value={selectedProductId}
                    onChange={e => setSelectedProductId(e.target.value)}
                    required
                  >
                    <option value="">-- Select Product --</option>
                    {products.map(p => (
                      <option key={p.productId} value={p.productId}>
                        {p.name} (${Number(p.price).toFixed(2)})
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">Quantity to Buy *</label>
                  <input
                    type="number"
                    min="1"
                    className="form-input"
                    value={orderQuantity}
                    onChange={e => setOrderQuantity(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div style={{
                background: 'rgba(99, 102, 241, 0.08)',
                border: '1px solid rgba(99, 102, 241, 0.2)',
                borderRadius: '8px',
                padding: '12px',
                fontSize: '0.8125rem',
                color: 'var(--text-secondary)',
                marginBottom: '16px'
              }}>
                💡 <strong>Demonstration Tip:</strong> Try ordering a quantity greater than currently available in this warehouse to test atomic rejection (HTTP 409).
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Reserving...' : 'Submit Sales Order'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
