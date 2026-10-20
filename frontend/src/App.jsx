import React, { useState, useEffect, useCallback } from 'react';
import Sidebar from './components/Sidebar';
import Navbar from './components/Navbar';
import ToastContainer from './components/ToastContainer';
import StockModal from './components/StockModal';

import Dashboard from './pages/Dashboard';
import ProductCatalog from './pages/ProductCatalog';
import WarehouseInventory from './pages/WarehouseInventory';
import PurchaseOrders from './pages/PurchaseOrders';
import SalesOrders from './pages/SalesOrders';
import StockTransfers from './pages/StockTransfers';
import LowStockAlerts from './pages/LowStockAlerts';
import AutocompleteLab from './pages/AutocompleteLab';
import SystemDesignLab from './pages/SystemDesignLab';

import { api } from './services/api';
import { globalProductTrie } from './services/trie';

export default function App() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [products, setProducts] = useState([]);
  const [warehouses, setWarehouses] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [backendConnected, setBackendConnected] = useState(true);
  const [toasts, setToasts] = useState([]);
  const [inspectedProduct, setInspectedProduct] = useState(null);

  // Toast Helper
  const showToast = useCallback((toast) => {
    const id = Date.now() + Math.random();
    setToasts(prev => [...prev, { ...toast, id }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 4500);
  }, []);

  const closeToast = useCallback((id) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  }, []);

  // Listen for Member 3's HTTP 429 Rate Limit events
  useEffect(() => {
    const unsubscribe = api.onRateLimit((detail) => {
      showToast({
        type: 'ratelimit',
        title: 'HTTP 429 Rate Limit Exceeded',
        message: detail.message || 'Token bucket exhausted. Member 3 traffic limiter activated.'
      });
    });
    return unsubscribe;
  }, [showToast]);

  // Load all foundational data
  const loadInitialData = useCallback(async () => {
    setLoading(true);
    try {
      const [prodRes, whRes, alertRes] = await Promise.all([
        api.getProducts('', '', 0, 100).catch(() => ({ content: [] })),
        api.getWarehouses().catch(() => []),
        api.getLowStockAlerts().catch(() => [])
      ]);

      const loadedProducts = prodRes.content || [];
      setProducts(loadedProducts);
      setWarehouses(whRes || []);
      setAlerts(alertRes || []);
      setBackendConnected(true);

      // Build in-memory Trie index for Autocomplete component
      globalProductTrie.buildIndex(loadedProducts);
    } catch (err) {
      console.warn('Initial backend connection issue:', err);
      setBackendConnected(false);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadInitialData();

    // Check backend health periodically (every 15s)
    const interval = setInterval(() => {
      api.checkHealth().then(connected => setBackendConnected(connected));
    }, 15000);

    return () => clearInterval(interval);
  }, [loadInitialData]);

  // Quick action helpers
  const handleQuickRestock = (product, warehouseId) => {
    setActiveTab('purchase');
    showToast({
      type: 'warning',
      title: 'Restock Action Initiated',
      message: `Navigated to Purchase Orders to restock ${product.name} at ${warehouseId}.`
    });
  };

  return (
    <div className="app-container">
      {/* Fixed Sidebar */}
      <Sidebar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        lowStockCount={alerts.length}
      />

      {/* Main Layout Area */}
      <div className="main-content">
        <Navbar
          activeTab={activeTab}
          setActiveTab={setActiveTab}
          lowStockCount={alerts.length}
          backendConnected={backendConnected}
          onSelectProduct={(p) => setInspectedProduct(p)}
        />

        {/* Dynamic Page Body */}
        <main className="page-body">
          {activeTab === 'dashboard' && (
            <Dashboard
              products={products}
              warehouses={warehouses}
              alerts={alerts}
              setActiveTab={setActiveTab}
              onOpenCreateProduct={() => setActiveTab('products')}
              onOpenCreatePO={() => setActiveTab('purchase')}
              onOpenCreateSale={() => setActiveTab('sales')}
              onOpenTransfer={() => setActiveTab('transfers')}
            />
          )}

          {activeTab === 'products' && (
            <ProductCatalog
              products={products}
              loading={loading}
              onRefresh={loadInitialData}
              onSelectProduct={(p) => setInspectedProduct(p)}
              onShowToast={showToast}
            />
          )}

          {activeTab === 'warehouses' && (
            <WarehouseInventory
              warehouses={warehouses}
              products={products}
              loading={loading}
              onRefreshWarehouses={loadInitialData}
              onShowToast={showToast}
              onSelectProduct={(p) => setInspectedProduct(p)}
            />
          )}

          {activeTab === 'purchase' && (
            <PurchaseOrders
              warehouses={warehouses}
              products={products}
              onShowToast={showToast}
              onRefreshAll={loadInitialData}
            />
          )}

          {activeTab === 'sales' && (
            <SalesOrders
              warehouses={warehouses}
              products={products}
              onShowToast={showToast}
              onRefreshAll={loadInitialData}
            />
          )}

          {activeTab === 'transfers' && (
            <StockTransfers
              warehouses={warehouses}
              products={products}
              onShowToast={showToast}
              onRefreshAll={loadInitialData}
            />
          )}

          {activeTab === 'alerts' && (
            <LowStockAlerts
              alerts={alerts}
              products={products}
              warehouses={warehouses}
              loading={loading}
              onRefreshAlerts={loadInitialData}
              onSelectProduct={(p) => setInspectedProduct(p)}
              onQuickRestock={handleQuickRestock}
            />
          )}

          {activeTab === 'autocomplete' && (
            <AutocompleteLab
              products={products}
            />
          )}

          {activeTab === 'system-design' && (
            <SystemDesignLab
              products={products}
              warehouses={warehouses}
              onShowToast={showToast}
              onRefreshAll={loadInitialData}
            />
          )}
        </main>
      </div>

      {/* Product Stock & Audit Modal */}
      {inspectedProduct && (
        <StockModal
          product={inspectedProduct}
          onClose={() => setInspectedProduct(null)}
          onRefreshNeeded={loadInitialData}
          onOpenSales={(prod, whId) => {
            setInspectedProduct(null);
            setActiveTab('sales');
          }}
          onOpenTransfer={(prod, whId) => {
            setInspectedProduct(null);
            setActiveTab('transfers');
          }}
        />
      )}

      {/* Toast Notification Layer */}
      <ToastContainer
        toasts={toasts}
        onCloseToast={closeToast}
      />
    </div>
  );
}
