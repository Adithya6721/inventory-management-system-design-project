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
import SupplierCrawler from './pages/SupplierCrawler';
import AutocompleteLab from './pages/AutocompleteLab';
import SystemDesignLab from './pages/SystemDesignLab';

import { api } from './services/api';
import { globalProductTrie } from './services/trie';

function preference(key, fallback) {
  try { return localStorage.getItem(key) || localStorage.getItem(key.replace('efficientstock.', 'aetherstock.')) || fallback; } catch { return fallback; }
}

export default function App() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [products, setProducts] = useState([]);
  const [productCount, setProductCount] = useState(0);
  const [warehouses, setWarehouses] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [backendConnected, setBackendConnected] = useState(true);
  const [toasts, setToasts] = useState([]);
  const [inspectedProduct, setInspectedProduct] = useState(null);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => preference('efficientstock.sidebar', 'expanded') === 'collapsed');
  const [theme, setTheme] = useState(() => preference('efficientstock.theme', 'light'));
  const [createRequest, setCreateRequest] = useState(null);
  const finishCreateRequest = useCallback(() => setCreateRequest(null), []);
  const openCreate = tab => { setCreateRequest(tab); setActiveTab(tab); };

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    try { localStorage.setItem('efficientstock.theme', theme); } catch { /* Storage is optional. */ }
  }, [theme]);
  useEffect(() => {
    try { localStorage.setItem('efficientstock.sidebar', sidebarCollapsed ? 'collapsed' : 'expanded'); } catch { /* Storage is optional. */ }
  }, [sidebarCollapsed]);

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

  // Listen for HTTP 429 Rate Limit events
  useEffect(() => {
    const unsubscribe = api.onRateLimit((detail) => {
      showToast({
        type: 'ratelimit',
        title: 'HTTP 429 Rate Limit Exceeded',
        message: detail.message || 'Too many requests. Please wait before retrying.'
      });
    });
    return unsubscribe;
  }, [showToast]);

  // Load all foundational data
  const loadInitialData = useCallback(async () => {
    setLoading(true);
    try {
      const [prodRes, whRes, alertRes] = await Promise.all([
        api.getProducts('', '', 0, 100),
        api.getWarehouses(),
        api.getLowStockAlerts()
      ]);

      const loadedProducts = prodRes.content || [];
      setProducts(loadedProducts);
      setProductCount(prodRes.totalElements ?? loadedProducts.length);
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
    <div className={`app-container${sidebarCollapsed ? ' sidebar-is-collapsed' : ''}`}>
      {/* Fixed Sidebar */}
      <Sidebar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        lowStockCount={alerts.length}
        collapsed={sidebarCollapsed}
        onToggle={() => setSidebarCollapsed(current => !current)}
      />

      {/* Main Layout Area */}
      <div className="main-content">
        <Navbar
          activeTab={activeTab}
          setActiveTab={setActiveTab}
          lowStockCount={alerts.length}
          backendConnected={backendConnected}
          onSelectProduct={(p) => setInspectedProduct(p)}
          theme={theme}
          onToggleTheme={() => setTheme(current => current === 'light' ? 'dark' : 'light')}
          onRefresh={loadInitialData}
          refreshing={loading}
        />

        {/* Dynamic Page Body */}
        <main className="page-body">
          <div className="page-view" data-page={activeTab}>
          {activeTab === 'dashboard' && (
            <Dashboard
              products={products}
              productCount={productCount}
              warehouses={warehouses}
              alerts={alerts}
              setActiveTab={setActiveTab}
              onOpenCreateProduct={() => openCreate('products')}
              onOpenCreatePO={() => openCreate('purchase')}
              onOpenCreateSale={() => openCreate('sales')}
              onOpenTransfer={() => openCreate('transfers')}
              onSelectProduct={setInspectedProduct}
            />
          )}

          {activeTab === 'products' && (
            <ProductCatalog
              createRequested={createRequest === 'products'}
              onCreateRequestHandled={finishCreateRequest}
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
              createRequested={createRequest === 'purchase'}
              onCreateRequestHandled={finishCreateRequest}
              warehouses={warehouses}
              products={products}
              onShowToast={showToast}
              onRefreshAll={loadInitialData}
            />
          )}

          {activeTab === 'sales' && (
            <SalesOrders
              createRequested={createRequest === 'sales'}
              onCreateRequestHandled={finishCreateRequest}
              warehouses={warehouses}
              products={products}
              onShowToast={showToast}
              onRefreshAll={loadInitialData}
            />
          )}

          {activeTab === 'transfers' && (
            <StockTransfers
              createRequested={createRequest === 'transfers'}
              onCreateRequestHandled={finishCreateRequest}
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

          {activeTab === 'supplier-crawler' && (
            <SupplierCrawler
              products={products}
              warehouses={warehouses}
              onShowToast={showToast}
              onRefreshAll={loadInitialData}
            />
          )}

          {activeTab === 'autocomplete' && (
            <AutocompleteLab
              products={products}
              totalProducts={productCount}
            />
          )}

          {activeTab === 'system-design' && (
            <SystemDesignLab
              onNavigate={setActiveTab}
              products={products}
              warehouses={warehouses}
              onShowToast={showToast}
              onRefreshAll={loadInitialData}
            />
          )}
          </div>
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
