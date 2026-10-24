import React, { useState } from 'react';
import { ShoppingBag, Plus, CheckCircle, Package, ArrowDownLeft, Clock } from 'lucide-react';
import { api } from '../services/api';
import { pendingCreate, completeCreate } from '../services/idempotency';

export default function PurchaseOrders({
  warehouses = [],
  products = [],
  onShowToast,
  onRefreshAll
}) {
  const [purchaseOrders, setPurchaseOrders] = useState([
    // Seed sample state for immediate UI readiness if backend hasn't populated yet
    {
      orderId: 'po_demo_101',
      supplierId: 'sup_apple_distributors',
      destinationWarehouseId: warehouses[0]?.warehouseId || 'wh_central',
      items: [
        { productId: products[0]?.productId || 'prod_mac', quantity: 100, receivedQuantity: 100 }
      ],
      status: 'RECEIVED',
      createdAt: new Date(Date.now() - 3600000).toISOString()
    }
  ]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Form state
  const [supplierId, setSupplierId] = useState('sup_global_logistics');
  const [destWarehouseId, setDestWarehouseId] = useState(warehouses[0]?.warehouseId || '');
  const [selectedProductId, setSelectedProductId] = useState(products[0]?.productId || '');
  const [orderQuantity, setOrderQuantity] = useState('50');

  const handleCreatePO = async (e) => {
    e.preventDefault();
    if (!destWarehouseId || !selectedProductId) {
      alert('Please select a destination warehouse and product.');
      return;
    }
    setSubmitting(true);

    try {
      const payload = {
        supplierId: supplierId.trim(),
        destinationWarehouseId: destWarehouseId,
        items: [
          {
            productId: selectedProductId,
            quantity: parseInt(orderQuantity, 10)
          }
        ]
      };

      const intent = pendingCreate('purchase', payload);
      const res = await api.createPurchaseOrder(intent);
      completeCreate('purchase', intent.idempotencyKey);
      setPurchaseOrders(prev => [res, ...prev]);

      onShowToast({
        type: 'success',
        title: 'Purchase Order Created',
        message: `PO #${res.orderId} created for ${orderQuantity} units.`
      });

      setShowCreateModal(false);
    } catch (err) {
      onShowToast({
        type: 'error',
        title: 'PO Creation Failed',
        message: err.message
      });
    } finally {
      setSubmitting(false);
    }
  };

  const handleReceiveStock = async (po) => {
    const unreceived = po.items.reduce((sum, i) => sum + (i.quantity - (i.receivedQuantity || 0)), 0);
    if (unreceived <= 0) {
      alert('This Purchase Order is already fully received.');
      return;
    }

    try {
      // Receive all remaining units. The server records a stable receipt identity,
      // so retrying after a lost response cannot credit stock twice.
      const receivePayload = {};

      const updated = await api.receivePurchaseOrder(po.orderId, receivePayload);
      setPurchaseOrders(prev => prev.map(p => p.orderId === po.orderId ? updated : p));

      onShowToast({
        type: 'success',
        title: 'Stock Received & Credited',
        message: `Successfully received into warehouse ${po.destinationWarehouseId}. Inventory physical count updated!`
      });

      if (onRefreshAll) onRefreshAll();
    } catch (err) {
      onShowToast({
        type: 'error',
        title: 'Receiving Failed',
        message: err.message
      });
    }
  };

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', marginBottom: '4px' }}>Purchase Orders & Receipts</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            Inbound stock accounting from suppliers into destination warehouses (FR6)
          </p>
        </div>
        <button id="btn-create-po" className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
          <Plus size={16} /> Create Purchase Order
        </button>
      </div>

      {/* PO List */}
      <div className="card" style={{ padding: 0 }}>
        <div className="table-container" style={{ border: 'none' }}>
          <table className="table">
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Supplier</th>
                <th>Destination Warehouse</th>
                <th>Products & Quantities</th>
                <th>Status</th>
                <th>Created At</th>
                <th style={{ textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {purchaseOrders.map(po => {
                const wh = warehouses.find(w => w.warehouseId === po.destinationWarehouseId);
                const isFullyReceived = po.status === 'RECEIVED';

                return (
                  <tr key={po.orderId}>
                    <td>
                      <code style={{ color: 'var(--primary)', fontWeight: 600 }}>{po.orderId}</code>
                    </td>
                    <td>
                      <div style={{ fontWeight: 600 }}>{po.supplierId}</div>
                    </td>
                    <td>
                      <div style={{ fontWeight: 600 }}>{wh ? wh.name : po.destinationWarehouseId}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>ID: {po.destinationWarehouseId}</div>
                    </td>
                    <td>
                      {po.items.map((item, idx) => {
                        const prod = products.find(p => p.productId === item.productId);
                        return (
                          <div key={idx} style={{ fontSize: '0.8125rem' }}>
                            <span style={{ fontWeight: 600 }}>{prod ? prod.name : item.productId}</span>: {item.receivedQuantity || 0}/{item.quantity} units
                          </div>
                        );
                      })}
                    </td>
                    <td>
                      <span className={`badge ${isFullyReceived ? 'badge-emerald' : 'badge-amber'}`}>
                        {po.status}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                      {new Date(po.createdAt || Date.now()).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      {!isFullyReceived ? (
                        <button
                          className="btn btn-success"
                          style={{ padding: '6px 12px', fontSize: '0.75rem' }}
                          onClick={() => handleReceiveStock(po)}
                        >
                          <ArrowDownLeft size={14} /> Receive Stock
                        </button>
                      ) : (
                        <span style={{ fontSize: '0.75rem', color: 'var(--accent-emerald)', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                          <CheckCircle size={14} /> Received
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Create PO Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontSize: '1.25rem', marginBottom: '6px' }}>Create Purchase Order (FR6)</h2>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
              Records incoming stock shipment from an external supplier.
            </p>

            <form onSubmit={handleCreatePO}>
              <div className="form-group">
                <label className="form-label">Supplier Identifier *</label>
                <input
                  type="text"
                  className="form-input"
                  value={supplierId}
                  onChange={e => setSupplierId(e.target.value)}
                  placeholder="e.g. sup_apple_us"
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">Destination Warehouse *</label>
                <select
                  className="form-select"
                  value={destWarehouseId}
                  onChange={e => setDestWarehouseId(e.target.value)}
                  required
                >
                  <option value="">-- Select Destination Facility --</option>
                  {warehouses.map(w => (
                    <option key={w.warehouseId} value={w.warehouseId}>
                      {w.name} ({w.location}) - {w.warehouseId}
                    </option>
                  ))}
                </select>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '14px' }}>
                <div className="form-group">
                  <label className="form-label">Product to Order *</label>
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
                    value={orderQuantity}
                    onChange={e => setOrderQuantity(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Creating PO...' : 'Create Purchase Order'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
