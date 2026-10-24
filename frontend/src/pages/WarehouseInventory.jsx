import React, { useState, useEffect } from 'react';
import { Warehouse, Plus, MapPin, Box, ArrowRight, ShieldCheck, RefreshCw } from 'lucide-react';
import { api } from '../services/api';

export default function WarehouseInventory({
  warehouses = [],
  products = [],
  loading = false,
  onRefreshWarehouses,
  onShowToast,
  onSelectProduct
}) {
  const [selectedWarehouseId, setSelectedWarehouseId] = useState('');
  const [warehouseStock, setWarehouseStock] = useState([]);
  const [stockLoading, setStockLoading] = useState(false);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // New warehouse form state
  const [whName, setWhName] = useState('');
  const [whLocation, setWhLocation] = useState('');
  const [whStatus, setWhStatus] = useState('ACTIVE');

  // Select first warehouse automatically when available
  useEffect(() => {
    if (warehouses.length > 0 && !selectedWarehouseId) {
      setSelectedWarehouseId(warehouses[0].warehouseId);
    }
  }, [warehouses]);

  // Load inventory for selected warehouse
  useEffect(() => {
    if (selectedWarehouseId) {
      loadInventory(selectedWarehouseId);
    }
  }, [selectedWarehouseId]);

  const loadInventory = async (whId) => {
    setStockLoading(true);
    try {
      const data = await api.getWarehouseInventory(whId);
      setWarehouseStock(data || []);
    } catch (err) {
      console.warn('Failed to load warehouse inventory:', err);
      setWarehouseStock([]);
    } finally {
      setStockLoading(false);
    }
  };

  const handleCreateWarehouse = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const created = await api.createWarehouse({
        name: whName.trim(),
        location: whLocation.trim(),
        status: whStatus
      });

      onShowToast({
        type: 'success',
        title: 'Warehouse Registered',
        message: `${created.name} (${created.warehouseId}) added to directory.`
      });

      setShowCreateModal(false);
      setWhName('');
      setWhLocation('');
      if (onRefreshWarehouses) onRefreshWarehouses();
      setSelectedWarehouseId(created.warehouseId);
    } catch (err) {
      onShowToast({
        type: 'error',
        title: 'Registration Error',
        message: err.message
      });
    } finally {
      setSubmitting(false);
    }
  };

  const selectedWarehouse = warehouses.find(w => w.warehouseId === selectedWarehouseId);

  return (
    <div>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '1.875rem', marginBottom: '4px' }}>Warehouses & stock</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            Track available and reserved stock across your warehouse network.
          </p>
        </div>
        <button id="btn-add-warehouse" className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
          <Plus size={16} /> Add warehouse
        </button>
      </div>

      {/* Warehouse Selector Cards */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 260px), 1fr))',
        gap: '16px',
        marginBottom: '28px'
      }}>
        {warehouses.map(wh => {
          const isSelected = wh.warehouseId === selectedWarehouseId;
          return (
            <div
              key={wh.warehouseId}
              onClick={() => setSelectedWarehouseId(wh.warehouseId)}
              style={{
                background: isSelected ? 'var(--primary-light)' : 'var(--bg-card)',
                border: `1px solid ${isSelected ? 'var(--primary)' : 'var(--border-subtle)'}`,
                borderRadius: '12px',
                padding: '18px',
                cursor: 'pointer',
                transition: 'all 150ms ease',
                position: 'relative',
                boxShadow: isSelected ? 'var(--shadow-md)' : 'var(--shadow-sm)'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '10px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                  <Warehouse size={20} style={{ color: isSelected ? 'var(--primary)' : 'var(--text-muted)' }} />
                  <div style={{ fontWeight: 600, fontSize: '0.9375rem' }}>{wh.name}</div>
                </div>
                <span className={`badge ${wh.status === 'ACTIVE' ? 'badge-emerald' : 'badge-rose'}`} style={{ fontSize: '0.65rem' }}>
                  {wh.status}
                </span>
              </div>

              <div style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <MapPin size={14} />
                <span>{wh.location || 'Location Unspecified'}</span>
              </div>
              <div style={{ fontSize: '0.75rem', fontFamily: 'monospace', color: 'var(--text-muted)', marginTop: '6px' }}>
                ID: {wh.warehouseId}
              </div>
            </div>
          );
        })}
      </div>

      {/* Warehouse Specific Stock List */}
      <div className="card">
        <div className="card-header">
          <div>
            <h2 style={{ fontSize: '1.125rem' }}>
              Stock at {selectedWarehouse ? selectedWarehouse.name : 'Selected Warehouse'}
            </h2>
            <div style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', marginTop: '2px' }}>
              Formula: <code>Available Stock = Quantity - Reserved Quantity</code>
            </div>
          </div>
          <button
            className="btn btn-secondary"
            style={{ padding: '6px 12px', fontSize: '0.75rem' }}
            onClick={() => selectedWarehouseId && loadInventory(selectedWarehouseId)}
          >
            <RefreshCw size={14} /> Refresh Stock
          </button>
        </div>

        {stockLoading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
            Loading inventory items...
          </div>
        ) : warehouseStock.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-muted)' }}>
            <Box size={40} style={{ margin: '0 auto 12px auto', opacity: 0.4 }} />
            <div style={{ fontSize: '1rem', fontWeight: 600 }}>No stock recorded at this facility</div>
            <p style={{ fontSize: '0.875rem', marginTop: '4px' }}>
              Receive a Purchase Order or transfer stock to this warehouse.
            </p>
          </div>
        ) : (
          <div className="table-container" style={{ border: 'none' }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Product</th>
                  <th>Physical Quantity</th>
                  <th>Reserved (Pending Orders)</th>
                  <th>Available to Sell</th>
                  <th>Stock Health</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {warehouseStock.map(stock => {
                  const prod = products.find(p => p.productId === stock.productId) || {
                    name: stock.productId,
                    sku: stock.productId,
                    reorderLevel: 10
                  };
                  const available = (stock.quantity || 0) - (stock.reservedQuantity || 0);
                  const isLow = available <= (prod.reorderLevel || 10);

                  return (
                    <tr key={stock.id || `${stock.productId}_${stock.warehouseId}`}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{prod.name}</div>
                        <div style={{ fontSize: '0.75rem', color: 'var(--accent-cyan)' }}>
                          SKU: {prod.sku}
                        </div>
                      </td>
                      <td style={{ fontWeight: 700 }}>{stock.quantity}</td>
                      <td style={{ color: 'var(--accent-amber)', fontWeight: 600 }}>{stock.reservedQuantity}</td>
                      <td>
                        <span className={`badge ${available > 0 ? 'badge-emerald' : 'badge-rose'}`} style={{ fontSize: '0.8125rem' }}>
                          {available} units
                        </span>
                      </td>
                      <td>
                        {isLow ? (
                          <span className="badge badge-rose">Below Threshold ({prod.reorderLevel})</span>
                        ) : (
                          <span className="badge badge-emerald">Optimal</span>
                        )}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <button
                          className="btn btn-secondary"
                          style={{ padding: '4px 10px', fontSize: '0.75rem' }}
                          onClick={() => onSelectProduct(prod)}
                        >
                          View Details
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Add Warehouse Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-content" role="dialog" aria-modal="true" aria-label="Add warehouse" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontSize: '1.25rem', marginBottom: '6px' }}>Register Warehouse Facility</h2>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
              Add a new storage hub or distribution center to the system.
            </p>

            <form onSubmit={handleCreateWarehouse}>
              <div className="form-group">
                <label className="form-label" htmlFor="warehouseinventory-field-1">Warehouse Name *</label>
                <input id="warehouseinventory-field-1"
                  type="text"
                  className="form-input"
                  placeholder="e.g. Pacific Northwest Distribution Hub"
                  value={whName}
                  onChange={e => setWhName(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="warehouseinventory-field-2">Location / City *</label>
                <input id="warehouseinventory-field-2"
                  type="text"
                  className="form-input"
                  placeholder="e.g. Seattle, WA"
                  value={whLocation}
                  onChange={e => setWhLocation(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="warehouseinventory-field-3">Operational Status *</label>
                <select id="warehouseinventory-field-3"
                  className="form-select"
                  value={whStatus}
                  onChange={e => setWhStatus(e.target.value)}
                >
                  <option value="ACTIVE">ACTIVE (Ready for orders & transfers)</option>
                  <option value="INACTIVE">INACTIVE (Temporarily decommissioned)</option>
                </select>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Registering...' : 'Save Warehouse'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
