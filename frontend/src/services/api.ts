import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { store } from '../store';
import { logout, setCredentials } from '../store/slices/authSlice';

// Gateway runs on host port 8087 (HOST_PORT_GATEWAY) — 8080 is taken by another app on this
// host, so defaulting to 8080 silently sends API calls to a foreign server.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8087';

const api = axios.create({
  baseURL: `${API_BASE_URL}/api/v1`,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 30000,
});

// Request interceptor - add auth token
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = store.getState().auth.accessToken;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor - handle token refresh
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const refreshToken = store.getState().auth.refreshToken;
        if (!refreshToken) {
          store.dispatch(logout());
          return Promise.reject(error);
        }

        const response = await axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, {
          refreshToken,
        });

        const { accessToken, refreshToken: newRefreshToken } = response.data;
        store.dispatch(setCredentials({ accessToken, refreshToken: newRefreshToken }));

        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return api(originalRequest);
      } catch {
        store.dispatch(logout());
        return Promise.reject(error);
      }
    }

    return Promise.reject(error);
  }
);

export default api;
