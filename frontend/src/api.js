const API_BASE_URL = 'http://localhost:8080/api';

export const fetchDashboardSummary = async () => {
  const res = await fetch(`${API_BASE_URL}/dashboard/summary`);
  if (!res.ok) throw new Error('Failed to fetch summary');
  return res.json();
};

export const fetchStockAlerts = async () => {
  const res = await fetch(`${API_BASE_URL}/dashboard/alerts`);
  if (!res.ok) throw new Error('Failed to fetch alerts');
  return res.json();
};

export const fetchPendingReorders = async () => {
  const res = await fetch(`${API_BASE_URL}/reorders/pending`);
  if (!res.ok) throw new Error('Failed to fetch pending reorders');
  return res.json();
};

export const triggerReorderSweep = async () => {
  const res = await fetch(`${API_BASE_URL}/reorders/sweep`, { method: 'POST' });
  if (!res.ok) throw new Error('Failed to trigger sweep');
  return res.json();
};

export const approveReorder = async (reorderId) => {
  const res = await fetch(`${API_BASE_URL}/reorders/${reorderId}/status`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status: 'APPROVED' }),
  });
  if (!res.ok) throw new Error('Failed to approve reorder');
  return res.json();
};
