import { useEffect, useState } from 'react';
import { api } from '../services/api';

const loaders = {
  purchase: () => api.getPurchaseOrders(),
  sales: () => api.getSalesOrders(),
  transfer: () => api.getTransfers(),
};

// Load recent persisted records whenever a page is opened, including after reload.
export default function useStoredOrders(kind, onShowToast) {
  const [records, setRecords] = useState([]);
  useEffect(() => {
    let active = true;
    loaders[kind]().then(page => {
      if (!active) return;
      // Preserve a create/update that completed while the initial list was loading.
      setRecords(previous => {
        const id = kind === 'transfer' ? 'transferId' : 'orderId';
        const merged = new Map((page.content || []).map(item => [item[id], item]));
        previous.forEach(item => merged.set(item[id], item));
        return [...merged.values()].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      });
    }).catch(error => {
      if (active) onShowToast?.({ type: 'error', title: 'Could not load saved records', message: error.message });
    });
    return () => { active = false; };
  }, [kind, onShowToast]);
  return [records, setRecords];
}
