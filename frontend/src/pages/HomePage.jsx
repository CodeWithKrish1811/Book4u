import React from 'react';
import { APP_NAME, APP_TAGLINE, SHARING_MODES } from '../utils/constants';

export function HomePage({ healthState }) {
  const { health, dbHealth, loading, error, refetch } = healthState;

  return (
    <div className="home-container">
      {/* Hero Section */}
      <section className="hero-section">
        <div className="hero-badge">
          <span className="badge-pulse" />
          <span>Phase 1: Project Initialization & Foundation</span>
        </div>

        <h1 className="hero-title">
          Welcome to <span className="brand-highlight">{APP_NAME}</span>
        </h1>

        <p className="hero-tagline">{APP_TAGLINE}</p>

        <p className="hero-description">
          A dedicated peer-to-peer university academic book exchange platform. 
          Connect with fellow university students to exchange, give away, or temporarily lend textbooks 
          for your courses—fostering campus sustainability and zero-cost academic access.
        </p>

        <div className="hero-actions">
          <a href="#sharing-modes" className="btn btn-primary">
            Explore Sharing Modes
          </a>
          <a href="#system-diagnostics" className="btn btn-secondary">
            System Diagnostics
          </a>
        </div>
      </section>

      {/* 3 Book-Sharing Modes Section */}
      <section id="sharing-modes" className="modes-section">
        <div className="section-header">
          <span className="section-eyebrow">Three Zero-Cost Workflows</span>
          <h2 className="section-title">How Book4u Works on Campus</h2>
          <p className="section-subtitle">
            Strictly peer-to-peer. There is no buying, selling, or commercial transactions.
          </p>
        </div>

        <div className="modes-grid">
          {SHARING_MODES.map((mode) => (
            <div key={mode.id} className="mode-card">
              <div className="mode-header">
                <span className={`mode-badge ${mode.badgeColor}`}>{mode.id}</span>
                <span className="mode-tagline">{mode.tagline}</span>
              </div>
              <h3 className="mode-title">{mode.title}</h3>
              <p className="mode-desc">{mode.description}</p>
              <div className="mode-footer">
                <span className="mode-cost">Cost: $0 (Peer Exchange)</span>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* Backend & Database Health Check Section */}
      <section id="system-diagnostics" className="diagnostics-section">
        <div className="diagnostics-card">
          <div className="diagnostics-header">
            <div>
              <h3 className="diagnostics-title">Backend Architecture Health Check</h3>
              <p className="diagnostics-subtitle">
                Real-time validation connecting to Spring Boot 3.x REST API (<code>/api/health</code>) and MySQL via Spring JDBC.
              </p>
            </div>
            <button 
              type="button" 
              className="btn btn-sm btn-outline" 
              onClick={refetch}
              disabled={loading}
            >
              {loading ? 'Checking...' : '↻ Refresh Status'}
            </button>
          </div>

          <div className="diagnostics-grid">
            {/* API Health Box */}
            <div className="diagnostic-box">
              <div className="diagnostic-box-header">
                <span className="box-label">Spring Boot REST API</span>
                <span className={`status-pill ${health?.status === 'UP' ? 'status-up' : 'status-down'}`}>
                  {loading ? 'Pinging...' : (health?.status || 'OFFLINE')}
                </span>
              </div>
              <div className="diagnostic-details">
                <div className="detail-row">
                  <span className="detail-key">Endpoint:</span>
                  <span className="detail-val"><code>GET /api/health</code></span>
                </div>
                <div className="detail-row">
                  <span className="detail-key">Application:</span>
                  <span className="detail-val">{health?.application || 'Book4u'}</span>
                </div>
                <div className="detail-row">
                  <span className="detail-key">Status:</span>
                  <span className="detail-val">{health?.status === 'UP' ? 'Online & Responsive' : 'Unreachable'}</span>
                </div>
              </div>
            </div>

            {/* Database Health Box */}
            <div className="diagnostic-box">
              <div className="diagnostic-box-header">
                <span className="box-label">MySQL 8.x (Spring JDBC)</span>
                <span className={`status-pill ${dbHealth?.status === 'CONNECTED' ? 'status-up' : 'status-warn'}`}>
                  {loading ? 'Checking...' : (dbHealth?.status || 'AWAITING CREDENTIALS')}
                </span>
              </div>
              <div className="diagnostic-details">
                <div className="detail-row">
                  <span className="detail-key">Data Access:</span>
                  <span className="detail-val">Spring JdbcTemplate (No ORM/JPA)</span>
                </div>
                <div className="detail-row">
                  <span className="detail-key">Engine:</span>
                  <span className="detail-val">{dbHealth?.databaseEngine || 'MySQL Relational'}</span>
                </div>
                <div className="detail-row">
                  <span className="detail-key">Connection:</span>
                  <span className="detail-val">
                    {dbHealth?.status === 'CONNECTED' 
                      ? `Connected (${dbHealth?.responseTimeMs}ms latency)` 
                      : (dbHealth?.message || 'Configured via .env variables')}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {error && (
            <div className="diagnostics-alert alert-error">
              <strong>Notice:</strong> {error}. Ensure the Spring Boot backend is running on <code>http://localhost:8080</code>.
            </div>
          )}
        </div>
      </section>

      {/* Architectural Guarantee Note */}
      <section className="architecture-note">
        <div className="note-card">
          <div className="note-icon">🏛️</div>
          <div className="note-content">
            <h4>Senior Architectural Guarantee</h4>
            <p>
              Book4u is built on strict software engineering patterns: controllers never hold SQL, 
              business logic is isolated in services, data operations are managed exclusively by 
              Spring JDBC repositories with parameterized statements, and secrets are strictly environment-managed.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
