import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { store } from '../store';
import { logout, setCredentials } from '../store/slices/authSlice';

// Gateway runs on host port 8080 (HOST_PORT_GATEWAY in docker/.env). Keep this default in step
// with that variable — pointing at a port nothing serves sends every API call into the void.
import { API_ORIGIN } from './apiBase';

const API_BASE_URL = API_ORIGIN;

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

/**
 * One refresh at a time per tab.
 *
 * A page that fires several requests at once gets several 401s at once when the access token
 * expires. Each used to start its own refresh with the same refresh token; now that the server
 * rotates tokens and treats a spent one as stolen, the second of those would sign the user out
 * everywhere. They all wait on the first refresh instead.
 */
let refreshInFlight: Promise<string> | null = null;

const refreshAccessToken = (): Promise<string> => {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = store.getState().auth.refreshToken;
      if (!refreshToken) throw new Error('No refresh token');
      const response = await axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, { refreshToken });
      const { accessToken, refreshToken: newRefreshToken } = response.data;
      store.dispatch(setCredentials({ accessToken, refreshToken: newRefreshToken }));
      return accessToken as string;
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
};

// Response interceptor - handle token refresh
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & {
      _retry?: boolean;
    };

    // A 401 from the auth endpoints means "those credentials are wrong", not "your
    // session expired" — refreshing (and logging out on failure) there would turn a
    // simple bad-password response into a surprise sign-out.
    const isAuthEndpoint = /\/auth\/(login|register|refresh|otp|logout)\b/.test(
      originalRequest?.url || ''
    );

    if (error.response?.status === 401 && !originalRequest._retry && !isAuthEndpoint) {
      originalRequest._retry = true;

      try {
        // Sent before an earlier refresh finished: the store already has a newer token, so
        // retry with that rather than spending another refresh.
        const current = store.getState().auth.accessToken;
        const sentWith = String(originalRequest.headers.Authorization ?? '').replace(/^Bearer /, '');
        const accessToken = current && current !== sentWith ? current : await refreshAccessToken();
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
