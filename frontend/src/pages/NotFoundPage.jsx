import React from 'react';
import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="not-found-container">
      <h2>404 — Page Not Found</h2>
      <p>The requested campus resource does not exist.</p>
      <Link to="/" className="btn btn-primary">
        Return to Book4u Home
      </Link>
    </div>
  );
}
