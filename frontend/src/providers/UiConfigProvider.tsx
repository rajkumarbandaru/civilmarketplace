import React, { createContext, useContext, useMemo } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { useAppSelector } from '../hooks';
import { buildTheme } from '../theme';
import {
  fetchUiConfig,
  ResolvedMenuItem,
  ResolvedTheme,
  UiConfigSnapshot,
} from '../services/uiConfigApi';

interface UiConfigContextValue {
  menu: ResolvedMenuItem[];
  theme: ResolvedTheme | null;
  /** True while the first fetch is in flight — layouts fall back to their static menu until then. */
  loading: boolean;
  /** True when the config could not be fetched, so the shipped defaults are what is painted. */
  failed: boolean;
  /** Re-reads the snapshot; call after saving a theme so the change shows without a reload. */
  refresh: () => void;
}

const UiConfigContext = createContext<UiConfigContextValue>({
  menu: [],
  theme: null,
  loading: false,
  failed: false,
  refresh: () => undefined,
});

export const useUiConfig = () => useContext(UiConfigContext);

/** The menu entries for one section ("Work", "Account", "Platform"), already sorted server-side. */
export const useMenuSection = (section: string): ResolvedMenuItem[] => {
  const { menu } = useUiConfig();
  return useMemo(() => menu.filter((item) => item.section === section), [menu, section]);
};

export const UI_CONFIG_QUERY_KEY = ['ui-config', 'me'];

/**
 * Fetches the signed-in user's UI config once and applies its theme to the whole app.
 *
 * Everything degrades to the shipped design system: signed-out users, a failed request, or
 * admin-service being down all render `buildTheme(null)` rather than a blank page. Losing the
 * ability to *customise* the UI must never cost the ability to *use* it.
 */
export const UiConfigProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAppSelector((state) => state.auth);
  const queryClient = useQueryClient();

  const { data, isLoading, isError } = useQuery<UiConfigSnapshot>({
    queryKey: UI_CONFIG_QUERY_KEY,
    queryFn: () => fetchUiConfig(),
    enabled: isAuthenticated,
    // One retry, not the app-wide two: the shell is blocked on this, and a slow failure is worse
    // than a fast fallback to the shipped theme.
    retry: 1,
    staleTime: 5 * 60 * 1000,
  });

  const muiTheme = useMemo(() => buildTheme(data?.theme ?? null), [data?.theme]);

  const value = useMemo<UiConfigContextValue>(
    () => ({
      menu: data?.menu ?? [],
      theme: data?.theme ?? null,
      loading: isAuthenticated && isLoading,
      failed: isError,
      refresh: () => queryClient.invalidateQueries({ queryKey: UI_CONFIG_QUERY_KEY }),
    }),
    [data, isAuthenticated, isLoading, isError, queryClient]
  );

  return (
    <UiConfigContext.Provider value={value}>
      <ThemeProvider theme={muiTheme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </UiConfigContext.Provider>
  );
};

export default UiConfigProvider;
