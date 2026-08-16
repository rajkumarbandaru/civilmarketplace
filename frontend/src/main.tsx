import React from 'react';
import ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { store } from './store';
import { restoreRememberedSession, shouldRestoreRemembered } from './store/slices/authSlice';
import UiConfigProvider from './providers/UiConfigProvider';
import App from './App';
import './styles/index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 2,
      refetchOnWindowFocus: false,
      staleTime: 5 * 60 * 1000,
      gcTime: 30 * 60 * 1000,
    },
  },
});

// "Remember me": a brand-new tab with no session of its own trades the remembered refresh token
// for a fresh one before the first render. Dispatched here rather than from a component so it
// happens once per page load, not once per mount of whatever component owned it.
if (shouldRestoreRemembered()) {
  store.dispatch(restoreRememberedSession());
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          {/* Supplies the MUI theme, so it must sit inside the query client and the store —
              it reads the auth state and fetches the signed-in user's config. */}
          <UiConfigProvider>
            <App />
          </UiConfigProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </Provider>
  </React.StrictMode>
);
