import useCreateRequest from '../hooks/useCreateRequest';
import React, { useState } from 'react';
import { Package, Plus, Search, Filter, Eye, Trash2, Edit3, AlertCircle } from 'lucide-react';
import { api } from '../services/api';
import { globalProductTrie } from '../services/trie';

export default function ProductCatalog({
  createRequested = false,
  onCreateRequestHandled,
  products = [],
  loading = false,
  onRefresh,
  onSelectProduct,
  onShowToast
}) {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  const [showCreateModal, setShowCreateModal] = useState(false);
  useCreateRequest(createRequested, onCreateRequestHandled, setShowCreateModal);
  const [submitting, setSubmitting] = useState(false);

  // New product form state
  const [sku, setSku] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('Electronics');
  const [price, setPrice] = useState('999.00');
  const [reorderLevel, setReorderLevel] = useState('10');
  const [formError, setFormError] = useState('');

  // Extract unique categories
  const categories = ['ALL', ...new Set(products.map(p => p.category).filter(Boolean))];

  // Client-side filtering
  const filteredProducts = products.filter(p => {
    const matchesCat = selectedCategory === 'ALL' || p.category === selectedCategory;
    const matchesSearch = !searchTerm ||
      p.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      p.sku?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      p.category?.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesCat && matchesSearch;
  });

  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    setSubmitting(true);

    try {
      const created = await api.createProduct({
        sku: sku.trim().toUpperCase(),
        name: name.trim(),
        description: description.trim(),
        category: category.trim(),
        price: parseFloat(price),
        reorderLevel: parseInt(reorderLevel, 10),
        active: true
      });

      // Index in Trie immediately
      globalProductTrie.indexProduct(created);

      onShowToast({
        type: 'success',
        title: 'Product Created',
        message: `Product ${created.name} (${created.sku}) successfully registered.`
      });

      setShowCreateModal(false);
      setSku('');
      setName('');
      setDescription('');
      if (onRefresh) onRefresh();
    } catch (err) {
      if (err.status === 409) {
        setFormError(`Conflict: Product with SKU '${sku}' already exists (enforced by MongoDB unique index).`);
      } else {
        setFormError(err.message || 'Failed to create product.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (productId, productName) => {
    if (!window.confirm(`Are you sure you want to delete ${productName}?`)) return;

    try {
      await api.deleteProduct(productId);
      onShowToast({
        type: 'success',
        title: 'Product Deleted',
        message: `${productName} was removed.`
      });
      if (onRefresh) onRefresh();
    } catch (err) {
      onShowToast({
        type: 'error',
        title: 'Delete Failed',
        message: err.message
      });
    }
  };

  return (
    <div>
      {/* Page Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '1.875rem', marginBottom: '4px' }}>Product catalog</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
            Manage your products, pricing, and replenishment thresholds.
          </p>
        </div>
        <button id="btn-add-product" className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
          <Plus size={16} /> Register New Product
        </button>
      </div>

      {/* Filter and Search Bar */}
      <div className="card" style={{ padding: '16px 20px', marginBottom: '20px' }}>
        <div style={{ display: 'flex', gap: '16px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ position: 'relative', flex: 1, minWidth: '260px' }}>
            <Search size={16} style={{ position: 'absolute', left: '12px', top: '13px', color: 'var(--text-muted)' }} />
            <input
              type="text"
              className="form-input"
              style={{ paddingLeft: '36px' }}
              placeholder="Filter products by name or SKU..."
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '2px' }}>
            {categories.map(cat => (
              <button
                key={cat}
                onClick={() => setSelectedCategory(cat)}
                style={{
                  padding: '6px 14px',
                  borderRadius: '20px',
                  fontSize: '0.8125rem',
                  fontWeight: 500,
                  border: '1px solid',
                  cursor: 'pointer',
                  borderColor: selectedCategory === cat ? 'var(--primary)' : 'var(--border-subtle)',
                  background: selectedCategory === cat ? 'var(--primary-light)' : 'transparent',
                  color: selectedCategory === cat ? 'var(--primary)' : 'var(--text-secondary)'
                }}
              >
                {cat}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Products Table */}
      <div className="card" style={{ padding: 0 }}>
        {loading ? (
          <div style={{ padding: '40px', textAlign: 'center', color: 'var(--text-muted)' }}>
            Loading products catalog...
          </div>
        ) : filteredProducts.length === 0 ? (
          <div style={{ padding: '48px', textAlign: 'center', color: 'var(--text-muted)' }}>
            <Package size={40} style={{ margin: '0 auto 12px auto', opacity: 0.4 }} />
            <div style={{ fontSize: '1rem', fontWeight: 600 }}>No products found</div>
            <p style={{ fontSize: '0.875rem', marginTop: '4px' }}>
              {searchTerm ? 'Try a different search query or filter.' : 'Click "Register New Product" to add your first SKU.'}
            </p>
          </div>
        ) : (
          <div className="table-container" style={{ border: 'none' }}>
            <table className="table">
              <thead>
                <tr>
                  <th>Product Name / Description</th>
                  <th>SKU</th>
                  <th>Category</th>
                  <th>Price</th>
                  <th>Reorder Level</th>
                  <th>Status</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredProducts.map(p => (
                  <tr key={p.productId}>
                    <td>
                      <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>{p.name}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', maxWidth: '320px', textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                        {p.description || 'No description provided'}
                      </div>
                    </td>
                    <td>
                      <code style={{ color: 'var(--accent-cyan)', background: 'rgba(6, 182, 212, 0.1)', padding: '2px 6px', borderRadius: '4px' }}>
                        {p.sku}
                      </code>
                    </td>
                    <td>
                      <span className="badge badge-indigo">{p.category}</span>
                    </td>
                    <td style={{ fontWeight: 600, color: 'var(--accent-emerald)' }}>
                      ${Number(p.price || 0).toFixed(2)}
                    </td>
                    <td>
                      <span style={{ fontSize: '0.875rem', color: 'var(--accent-amber)' }}>
                        {p.reorderLevel} units
                      </span>
                    </td>
                    <td>
                      <span className={`badge ${p.active !== false ? 'badge-emerald' : 'badge-rose'}`}>
                        {p.active !== false ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: '6px' }}>
                        <button
                          className="btn btn-secondary"
                          style={{ padding: '6px 10px', fontSize: '0.75rem' }}
                          onClick={() => onSelectProduct(p)}
                          title="Inspect Warehouse Inventory & Audit Trail"
                        >
                          <Eye size={14} /> Stock
                        </button>
                        <button
                          className="btn btn-danger"
                          style={{ padding: '6px 8px' }}
                          onClick={() => handleDelete(p.productId, p.name)}
                          title="Soft Delete Product"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Register Product Modal */}
      {showCreateModal && (
        <div className="modal-overlay" onClick={() => setShowCreateModal(false)}>
          <div className="modal-content" role="dialog" aria-modal="true" aria-label="Register product" onClick={e => e.stopPropagation()}>
            <h2 style={{ fontSize: '1.25rem', marginBottom: '6px' }}>Register Product</h2>
            <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', marginBottom: '20px' }}>
              Use a unique SKU to identify this product across your warehouses.
            </p>

            {formError && (
              <div style={{
                background: 'var(--accent-rose-light)',
                border: '1px solid var(--accent-rose)',
                borderRadius: '8px',
                padding: '12px',
                marginBottom: '16px',
                color: 'var(--accent-rose)',
                fontSize: '0.8125rem',
                display: 'flex',
                gap: '8px',
                alignItems: 'center'
              }}>
                <AlertCircle size={18} style={{ flexShrink: 0 }} />
                <span>{formError}</span>
              </div>
            )}

            <form onSubmit={handleCreateSubmit}>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="form-group">
                  <label className="form-label" htmlFor="productcatalog-field-1">SKU (Unique Identifier) *</label>
                  <input id="productcatalog-field-1"
                    type="text"
                    className="form-input"
                    placeholder="e.g. SKU-DELL-XPS15"
                    value={sku}
                    onChange={e => setSku(e.target.value)}
                    required
                  />
                </div>
                <div className="form-group">
                  <label className="form-label" htmlFor="productcatalog-field-2">Category *</label>
                  <input id="productcatalog-field-2"
                    type="text"
                    className="form-input"
                    placeholder="e.g. Laptops"
                    value={category}
                    onChange={e => setCategory(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="productcatalog-field-3">Product Name *</label>
                <input id="productcatalog-field-3"
                  type="text"
                  className="form-input"
                  placeholder="e.g. Dell XPS 15 OLED"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="productcatalog-field-4">Description</label>
                <textarea id="productcatalog-field-4"
                  className="form-textarea"
                  rows={2}
                  placeholder="Specifications, details, color..."
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                />
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px' }}>
                <div className="form-group">
                  <label className="form-label" htmlFor="productcatalog-field-5">Selling Price ($) *</label>
                  <input id="productcatalog-field-5"
                    type="number"
                    step="0.01"
                    min="0"
                    className="form-input"
                    value={price}
                    onChange={e => setPrice(e.target.value)}
                    required
                  />
                </div>
                <div className="form-group">
                  <label className="form-label" htmlFor="productcatalog-field-6">Reorder Level Threshold *</label>
                  <input id="productcatalog-field-6"
                    type="number"
                    min="1"
                    className="form-input"
                    value={reorderLevel}
                    onChange={e => setReorderLevel(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '20px' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowCreateModal(false)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary" disabled={submitting}>
                  {submitting ? 'Registering...' : 'Save Product'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
