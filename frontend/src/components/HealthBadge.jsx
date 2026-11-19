import React from 'react';

export function HealthBadge({ status, loading, error, onRetry }) {
  if (loading) {
    return (
      <div className="health-badge health-badge-loading" title="Pinging backend /api/health...">
        <span className="dot dot-pulse" />
        <span className="text">Connecting...</span>
      </div>
    );
  }

  if (error || status !== 'UP') {
    return (
      <div className="health-badge health-badge-down" onClick={onRetry} title="Backend unreachable. Click to retry.">
        <span className="dot dot-red" />
        <span className="text">Backend Offline</span>
        <button className="badge-retry-btn" type="button">↻</button>
      </div>
    );
  }

  return (
    <div className="health-badge health-badge-up" onClick={onRetry} title="Spring Boot Backend is UP (/api/health)">
      <span className="dot dot-green" />
      <span className="text">API Online</span>
    </div>
  );
}
