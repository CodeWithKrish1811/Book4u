import React from 'react';
import { Link } from 'react-router-dom';
import { HealthBadge } from './HealthBadge';
import { APP_NAME, APP_TAGLINE } from '../utils/constants';

export function Navbar({ healthState }) {
  return (
    <header className="navbar">
      <div className="navbar-container">
        <Link to="/" className="navbar-brand">
          <div className="brand-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1-2.5-2.5Z"/>
              <path d="M6 6h10"/>
              <path d="M6 10h10"/>
            </svg>
          </div>
          <div className="brand-text">
            <span className="brand-name">{APP_NAME}</span>
            <span className="brand-campus-pill">Campus Edition</span>
          </div>
        </Link>

        <div className="navbar-actions">
          <HealthBadge
            status={healthState?.health?.status}
            loading={healthState?.loading}
            error={healthState?.error}
            onRetry={healthState?.refetch}
          />
        </div>
      </div>
    </header>
  );
}
