import React from 'react';
import ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { store } from './store';
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
