import { useState, useEffect, useCallback } from 'react';
import { healthService } from '../services/healthService';

/**
 * Custom React hook to check backend API and database health status.
 */
export function useHealthCheck(autoPollInterval = 0) {
  const [health, setHealth] = useState(null);
  const [dbHealth, setDbHealth] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchHealth = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const appData = await healthService.checkHealth();
      setHealth(appData);

      // Attempt DB health check gracefully
      try {
        const dbData = await healthService.checkDatabaseHealth();
        setDbHealth(dbData);
      } catch (dbErr) {
        setDbHealth({
          status: 'UNAVAILABLE',
          message: 'Database check endpoint unreachable',
        });
      }
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Backend connection failed');
      setHealth({ status: 'DOWN', application: 'Book4u' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchHealth();

    if (autoPollInterval > 0) {
      const timer = setInterval(fetchHealth, autoPollInterval);
      return () => clearInterval(timer);
    }
  }, [fetchHealth, autoPollInterval]);

  return { health, dbHealth, loading, error, refetch: fetchHealth };
}
