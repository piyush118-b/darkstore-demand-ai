import React, { useState, useEffect } from 'react';
import { Store, AlertTriangle, ShoppingCart, RefreshCcw, Activity, CheckCircle, Zap } from 'lucide-react';
import { fetchDashboardSummary, fetchStockAlerts, fetchPendingReorders, triggerReorderSweep, approveReorder } from './api';

function App() {
  const [summary, setSummary] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [reorders, setReorders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sweeping, setSweeping] = useState(false);

  const loadData = async () => {
    try {
      setLoading(true);
      const [sumData, alertsData, reordersData] = await Promise.all([
        fetchDashboardSummary(),
        fetchStockAlerts(),
        fetchPendingReorders()
      ]);
      setSummary(sumData);
      setAlerts(alertsData);
      setReorders(reordersData);
    } catch (err) {
      console.error("Failed to load data", err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    // Refresh every 30 seconds
    const interval = setInterval(loadData, 30000);
    return () => clearInterval(interval);
  }, []);

  const handleSweep = async () => {
    setSweeping(true);
    try {
      await triggerReorderSweep();
      await loadData(); // Reload data after sweep
    } catch (err) {
      console.error("Sweep failed", err);
    } finally {
      setSweeping(false);
    }
  };

  const handleApprove = async (id) => {
    try {
      await approveReorder(id);
      // Remove from list optimistically or reload
      await loadData();
    } catch (err) {
      console.error("Failed to approve", err);
    }
  };

  if (loading && !summary) {
    return (
      <div className="loading-overlay">
        <div className="spinner"></div>
      </div>
    );
  }

  return (
    <div className="dashboard-container">
      {sweeping && (
        <div className="loading-overlay">
          <div className="spinner"></div>
        </div>
      )}

      {/* Header */}
      <header className="header">
        <div className="header-title">
          <Activity size={32} color="#06b6d4" />
          <h1>StockPulse AI</h1>
        </div>
        <button className="sweep-btn" onClick={handleSweep} disabled={sweeping}>
          <Zap size={18} />
          {sweeping ? 'Scanning Network...' : 'Trigger AI Sweep'}
        </button>
      </header>

      {/* KPI Cards */}
      {summary && (
        <div className="kpi-grid">
          <div className="kpi-card">
            <div className="kpi-icon stores"><Store size={24} /></div>
            <div className="kpi-content">
              <h3>Active Dark Stores</h3>
              <p>{summary.totalStores}</p>
            </div>
          </div>
          <div className="kpi-card">
            <div className="kpi-icon alerts"><AlertTriangle size={24} /></div>
            <div className="kpi-content">
              <h3>Critical Alerts</h3>
              <p>{summary.criticalAlerts}</p>
            </div>
          </div>
          <div className="kpi-card">
            <div className="kpi-icon reorders"><ShoppingCart size={24} /></div>
            <div className="kpi-content">
              <h3>Pending Reorders</h3>
              <p>{summary.pendingReorders}</p>
            </div>
          </div>
        </div>
      )}

      {/* Critical Alerts Section */}
      <div className="section-title">
        <AlertTriangle size={20} color="#f59e0b" />
        <h2>Stock Risk Predictions</h2>
      </div>
      
      {alerts.length === 0 ? (
        <div className="empty-state">
          <CheckCircle size={48} color="#10b981" opacity={0.5} />
          <p>No critical stock risks detected. AI forecasts look healthy.</p>
        </div>
      ) : (
        <div className="alerts-grid">
          {alerts.map((alert, idx) => {
            // Safely parse SHAP explanation if it's a string, or use as object
            let shapData = {};
            try {
              shapData = typeof alert.shapExplanation === 'string' 
                ? JSON.parse(alert.shapExplanation) 
                : alert.shapExplanation || {};
            } catch (e) {
              console.error("Failed to parse SHAP", e);
            }

            return (
              <div key={idx} className={`alert-card ${alert.riskLevel.toLowerCase()}`}>
                <div className="alert-header">
                  <div className="alert-title">
                    <span className="store-badge">{alert.storeName} ({alert.storeId})</span>
                    <h4>{alert.productName}</h4>
                  </div>
                  <span className={`badge ${alert.riskLevel.toLowerCase()}`}>
                    {alert.riskLevel}
                  </span>
                </div>
                
                <div className="alert-body">
                  <div className="stock-stats">
                    <div className="stat-item">
                      <span className="stat-label">Current Stock</span>
                      <span className="stat-value">{alert.currentStock} units</span>
                    </div>
                    <div className="stat-item">
                      <span className="stat-label">AI Forecast (Next Hr)</span>
                      <span className={`stat-value ${alert.predictedDemand > alert.currentStock ? 'low' : ''}`}>
                        {alert.predictedDemand} units
                      </span>
                    </div>
                  </div>

                  {/* AI Explainability */}
                  <div className="ai-insights">
                    <div className="ai-header">
                      <Activity size={14} />
                      <span>Why did AI predict this?</span>
                    </div>
                    <div className="shap-tag-container">
                      {Object.entries(shapData).slice(0, 4).map(([key, value]) => (
                        <div key={key} className={`shap-tag ${value > 0 ? 'positive' : 'negative'}`}>
                          {value > 0 ? '+' : ''}{parseFloat(value).toFixed(1)} {key.replace('_', ' ')}
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Pending Reorders Section */}
      <div className="section-title">
        <ShoppingCart size={20} color="#3b82f6" />
        <h2>Action Required: Pending Reorders</h2>
      </div>

      {reorders.length === 0 ? (
        <div className="empty-state">
          <CheckCircle size={48} color="#10b981" opacity={0.5} />
          <p>No pending reorders requiring approval.</p>
        </div>
      ) : (
        <div className="reorders-container">
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Store</th>
                  <th>Product</th>
                  <th>Trigger Reason</th>
                  <th>Current Stock</th>
                  <th>Reorder Qty</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {reorders.map(reorder => (
                  <tr key={reorder.id}>
                    <td>
                      <span className="store-badge">{reorder.storeId}</span>
                      <br/>{reorder.storeName}
                    </td>
                    <td>{reorder.productName}</td>
                    <td><span className="badge pending">{reorder.triggerReason.replace('_', ' ')}</span></td>
                    <td><strong>{reorder.currentStock}</strong> units</td>
                    <td><strong style={{color: '#6ee7b7'}}>+{reorder.reorderQuantity}</strong> units</td>
                    <td>
                      <button className="approve-btn" onClick={() => handleApprove(reorder.id)}>
                        <CheckCircle size={16} /> Approve
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

export default App;
