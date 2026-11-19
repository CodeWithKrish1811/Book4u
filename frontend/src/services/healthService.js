import apiClient from './api';

/**
 * Service handling health diagnostic queries to the Spring Boot backend.
 */
export const healthService = {
  /**
   * Queries GET /api/health
   * Expected: { status: "UP", application: "Book4u" }
   */
  async checkHealth() {
    const response = await apiClient.get('/health');
    return response.data;
  },

  /**
   * Queries GET /api/health/db for MySQL JDBC connection diagnostics
   */
  async checkDatabaseHealth() {
    const response = await apiClient.get('/health/db');
    return response.data;
  },
};
