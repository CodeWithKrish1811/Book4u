import React from 'react';
import { APP_NAME, APP_TAGLINE } from '../utils/constants';

export function Footer() {
  return (
    <footer className="footer">
      <div className="footer-container">
        <div className="footer-top">
          <div className="footer-branding">
            <span className="footer-logo">{APP_NAME}</span>
            <p className="footer-tagline">{APP_TAGLINE}</p>
          </div>
          <div className="footer-badges">
            <span className="tech-badge">React + Vite</span>
            <span className="tech-badge">Java 21 + Spring Boot 3</span>
            <span className="tech-badge">Spring JDBC</span>
            <span className="tech-badge">MySQL 8</span>
          </div>
        </div>

        <div className="footer-bottom">
          <p>© {new Date().getFullYear()} {APP_NAME}. University Peer-to-Peer Academic Book Sharing Platform.</p>
          <p className="footer-subtext">No Buying • No Selling • Pure Peer-to-Peer Exchange</p>
        </div>
      </div>
    </footer>
  );
}
