/**
 * REST API Client for Scalable Inventory Management System
 * Integrates with backend services and handles
 * Rate Limiter (429) & Atomic Conflict (409) responses.
 */

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api';

class ApiClient {
  constructor() {
    this.rateLimitListeners = [];
  }

  onRateLimit(listener) {
    this.rateLimitListeners.push(listener);
    return () => {
      this.rateLimitListeners = this.rateLimitListeners.filter(l => l !== listener);
    };
  }

  notifyRateLimit(detail) {
    this.rateLimitListeners.forEach(listener => listener(detail));
  }

  async request(endpoint, options = {}) {
    const url = `${API_BASE}${endpoint}`;
    const headers = {
      'Content-Type': 'application/json',
      ...(options.headers || {})
    };

    try {
      const response = await fetch(url, {
        ...options,
        headers
      });

      // Special System Design Handling: 429 Too Many Requests (Rate Limiter)
      if (response.status === 429) {
        const errorData = await response.json().catch(() => ({}));
        this.notifyRateLimit({
          endpoint,
          status: 429,
          message: errorData.message || 'Rate limit exceeded! Token bucket exhausted. Please wait a moment.'
        });
        const err = new Error(errorData.message || 'Rate limit exceeded (HTTP 429).');
        err.status = 429;
        err.isRateLimited = true;
        throw err;
      }

      // Special Handling: 409 Conflict (Overselling Protection & Duplicate SKU)
      if (response.status === 409) {
        const errorData = await response.json().catch(() => ({}));
        const err = new Error(errorData.message || 'Stock conflict or duplicate SKU detected (HTTP 409).');
        err.status = 409;
        err.isConflict = true;
        err.data = errorData;
        throw err;
      }

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        const err = new Error(errorData.message || `Request failed with status ${response.status}`);
        err.status = response.status;
        err.data = errorData;
        throw err;
      }

      if (response.status === 204) {
        return null;
      }

      return await response.json();
    } catch (err) {
      console.warn(`[API] ${options.method || 'GET'} ${endpoint} error:`, err.message);
      throw err;
    }
  }

  // --- 1. Products ---
  getProducts(query = '', category = '', page = 0, size = 50) {
    const params = new URLSearchParams();
    if (query) params.append('query', query);
    if (category) params.append('category', category);
    params.append('page', page);
    params.append('size', size);
    return this.request(`/products?${params.toString()}`);
  }

  getProductById(productId) {
    return this.request(`/products/${productId}`);
  }

  createProduct(product) {
    return this.request('/products', {
      method: 'POST',
      body: JSON.stringify(product)
    });
  }

  updateProduct(productId, product) {
    return this.request(`/products/${productId}`, {
      method: 'PUT',
      body: JSON.stringify(product)
    });
  }

  deleteProduct(productId) {
    return this.request(`/products/${productId}`, {
      method: 'DELETE'
    });
  }

  // --- 2. Warehouses ---
  getWarehouses() {
    return this.request('/warehouses');
  }

  createWarehouse(warehouse) {
    return this.request('/warehouses', {
      method: 'POST',
      body: JSON.stringify(warehouse)
    });
  }

  getWarehouseInventory(warehouseId) {
    return this.request(`/warehouses/${warehouseId}/inventory`);
  }

  // --- 3. Inventory & Auditing ---
  getInventoryByProduct(productId) {
    return this.request(`/inventory/${productId}`);
  }

  getInventoryByProductAndWarehouse(productId, warehouseId) {
    return this.request(`/inventory/${productId}/${warehouseId}`);
  }

  adjustStock(adjustment) {
    return this.request('/inventory/adjustments', {
      method: 'POST',
      body: JSON.stringify(adjustment)
    });
  }

  getInventoryMovements(productId) {
    return this.request(`/inventory/${productId}/movements`);
  }

  // --- 4. Purchase Orders ---
  getPurchaseOrders() {
    return this.request('/purchase-orders?size=100');
  }

  createPurchaseOrder(po) {
    return this.request('/purchase-orders', {
      method: 'POST',
      body: JSON.stringify(po)
    });
  }

  receivePurchaseOrder(orderId, receivePayload) {
    return this.request(`/purchase-orders/${orderId}/receive`, {
      method: 'POST',
      body: JSON.stringify(receivePayload)
    });
  }

  // --- 5. Sales Orders ---
  getSalesOrders() {
    return this.request('/sales-orders?size=100');
  }

  createSalesOrder(order) {
    return this.request('/sales-orders', {
      method: 'POST',
      body: JSON.stringify(order)
    });
  }

  completeSalesOrder(orderId) {
    return this.request(`/sales-orders/${orderId}/complete`, {
      method: 'POST'
    });
  }

  cancelSalesOrder(orderId) {
    return this.request(`/sales-orders/${orderId}/cancel`, {
      method: 'POST'
    });
  }

  // --- 6. Stock Transfers ---
  getTransfers() {
    return this.request('/transfers?size=100');
  }

  createTransfer(transfer) {
    return this.request('/transfers', {
      method: 'POST',
      body: JSON.stringify(transfer)
    });
  }

  completeTransfer(transferId) {
    return this.request(`/transfers/${transferId}/complete`, {
      method: 'POST'
    });
  }

  // --- 7. Low Stock Alerts ---
  getLowStockAlerts() {
    return this.request('/alerts/low-stock');
  }

  // --- 8. Supplier Import ---
  importSuppliers(payload) {
    return this.request('/suppliers/import', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  }

  // --- Health Check ---
  async checkHealth() {
    try {
      await this.getWarehouses();
      return true;
    } catch {
      return false;
    }
  }
}

export const api = new ApiClient();
