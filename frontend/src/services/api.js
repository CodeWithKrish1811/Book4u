import axios from 'axios';
import { API_BASE_URL } from '../utils/constants';

/**
 * Configured Axios client instance for Book4u REST API communication.
 */
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// Response interceptor for unified error logging
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const errorDetails = {
      message: error.response?.data?.message || error.message,
      status: error.response?.status,
      url: error.config?.url,
    };
    console.error('API Error Encountered:', errorDetails);
    return Promise.reject(error);
  }
);

export default apiClient;
