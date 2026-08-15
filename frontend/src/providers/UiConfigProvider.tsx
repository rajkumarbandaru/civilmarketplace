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
  const guestMode = useAppSelector((state) => state.ui.theme);
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

  // Signed out there is no config to fetch, but the visitor may still have picked light or dark in
  // the header — so the shipped theme is built with that mode and nothing else overridden.
  const muiTheme = useMemo(() => {
    if (data?.theme) return buildTheme(data.theme);
    return buildTheme(guestMode === 'dark' ? ({ mode: 'dark' } as ResolvedTheme) : null);
  }, [data?.theme, guestMode]);

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
