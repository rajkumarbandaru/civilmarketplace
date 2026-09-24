import React, { createContext, useContext, useEffect, useMemo, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { landingPathFor } from '../components/AdminRoute';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { Box, Button } from '@mui/material';
import { useAppSelector } from '../hooks';
import { buildTheme } from '../theme';
import {
  fetchPreviewTheme,
  fetchPublicTheme,
  fetchUiConfig,
  ResolvedMenuItem,
  ResolvedTheme,
  UiConfigSnapshot,
} from '../services/uiConfigApi';
import {
  DateInput,
  DateTimePreferences,
  formatDate as formatDateWith,
  formatDateTime as formatDateTimeWith,
  formatTime as formatTimeWith,
} from '../utils/datetime';

interface UiConfigContextValue {
  menu: ResolvedMenuItem[];
  theme: ResolvedTheme | null;
  /** True while the first fetch is in flight — layouts fall back to their static menu until then. */
  loading: boolean;
  /** True when the config could not be fetched, so the shipped defaults are what is painted. */
  failed: boolean;
  /** Re-reads the snapshot; call after saving a theme so the change shows without a reload. */
  refresh: () => void;
  /**
   * The member's timezone and date layout. Held here because the provider already wraps every
   * workspace, so one saved preference reaches every screen at once.
   */
  dateTime: DateTimePreferences;
  /**
   * The operator-chosen landing screen for this tenant, or null for the shipped route. Exposed as
   * well as acted on, so a screen that offers its own "go home" can agree with where sign-in lands.
   */
  landingPath: string | null;
}

const UiConfigContext = createContext<UiConfigContextValue>({
  menu: [],
  theme: null,
  loading: false,
  failed: false,
  refresh: () => undefined,
  // Signed out, and before the first fetch resolves: the browser's own zone and the site default,
  // which is what a visitor saw before this preference existed.
  dateTime: { timezone: null, dateFormat: null },
  landingPath: null,
});

export const useUiConfig = () => useContext(UiConfigContext);

/** The menu entries for one section ("Work", "Account", "Platform"), already sorted server-side. */
export const useMenuSection = (section: string): ResolvedMenuItem[] => {
  const { menu } = useUiConfig();
  return useMemo(() => menu.filter((item) => item.section === section), [menu, section]);
};

/**
 * Prefix only — the live key appends the signed-in user's id (see {@link UiConfigProvider}).
 * `invalidateQueries` matches by prefix, so callers can keep using this to refresh.
 */
export const UI_CONFIG_QUERY_KEY = ['ui-config', 'me'];

/**
 * Fetches the signed-in user's UI config and applies its theme to the whole app.
 *
 * Everything degrades to the shipped design system: signed-out users, a failed request, or
 * admin-service being down all render `buildTheme(null)` rather than a blank page. Losing the
 * ability to *customise* the UI must never cost the ability to *use* it.
 *
 * The cache key carries the user id. It used to be a bare constant, which meant one signed-in
 * user's menu and theme were cached under the same key as the next one's: after signing out and
 * back in as a different role, React Query served the previous user's snapshot — still inside
 * its 5-minute `staleTime`, so it did not even refetch — and the nav and theme only corrected
 * themselves on a full page reload, which drops the in-memory cache.
 */
export const UiConfigProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);
  const guestMode = useAppSelector((state) => state.ui.theme);
  const queryClient = useQueryClient();

  // Identity, not just presence: signing in as a different user must miss the cache.
  const userKey = user?.id ?? user?.email ?? 'anonymous';
  const queryKey = useMemo(() => [...UI_CONFIG_QUERY_KEY, userKey], [userKey]);

  // Sign-out drops the snapshot outright. Without this it lingers for the full 30-minute
  // gcTime, and the next user to sign in on this tab would briefly be painted the previous
  // user's menu and theme before their own fetch resolved.
  useEffect(() => {
    if (!isAuthenticated) {
      queryClient.removeQueries({ queryKey: UI_CONFIG_QUERY_KEY });
    }
  }, [isAuthenticated, queryClient]);

  const { data, isLoading, isError } = useQuery<UiConfigSnapshot>({
    queryKey,
    queryFn: () => fetchUiConfig(),
    enabled: isAuthenticated,
    // One retry, not the app-wide two: the shell is blocked on this, and a slow failure is worse
    // than a fast fallback to the shipped theme.
    retry: 1,
    staleTime: 5 * 60 * 1000,
  });

  // Signed out there is no config to fetch, but the visitor may still have picked light or dark in
  // the header — so the shipped theme is built with that mode and nothing else overridden.
  // A preview link (?preview=…) shows an unpublished theme for this tab until it is exited; it wins
  // over everything else, which is the point of it.
  const previewToken = usePreviewToken();
  const preview = useQuery({
    queryKey: ['ui-config', 'preview', previewToken],
    queryFn: () => fetchPreviewTheme(previewToken!),
    enabled: !!previewToken,
    retry: false,
    staleTime: Infinity,
  });
  // Anonymous visitors see the workspace's published look too (05 §7.2 public bundle), not the
  // shipped default: the home page and sign-in are where a tenant's brand matters most.
  const publicTheme = useQuery({
    queryKey: ['ui-config', 'public-theme'],
    queryFn: fetchPublicTheme,
    enabled: !isAuthenticated,
    retry: false,
    staleTime: 60 * 1000,
  });

  const muiTheme = useMemo(() => {
    if (preview.data) return buildTheme(preview.data);
    if (data?.theme) return buildTheme(data.theme);
    const base = publicTheme.data ?? null;
    // A guest's own light/dark toggle still applies over the workspace's look.
    return buildTheme(guestMode === 'dark' ? ({ ...(base ?? {}), mode: 'dark' } as ResolvedTheme) : base);
  }, [preview.data, data?.theme, publicTheme.data, guestMode]);

  const value = useMemo<UiConfigContextValue>(
    () => ({
      menu: data?.menu ?? [],
      theme: data?.theme ?? null,
      loading: isAuthenticated && isLoading,
      failed: isError,
      refresh: () => queryClient.invalidateQueries({ queryKey }),
      dateTime: {
        timezone: data?.timezone ?? null,
        dateFormat: data?.dateFormat ?? null,
      },
      landingPath: data?.landingPath ?? null,
    }),
    [data, isAuthenticated, isLoading, isError, queryClient, queryKey]
  );

  return (
    <UiConfigContext.Provider value={value}>
      <ThemeProvider theme={muiTheme}>
        <CssBaseline />
        <TenantLandingRedirect landingPath={value.landingPath} role={user?.role} />
        {previewToken && <PreviewBanner failed={preview.isError} />}
        {children}
      </ThemeProvider>
    </UiConfigContext.Provider>
  );
};

/**
 * Sends the member to the tenant's own landing screen, once, just after sign-in.
 *
 * The redirect cannot happen at sign-in itself: the landing path arrives on the UI-config snapshot,
 * which is fetched only once there is a session to fetch it for. So the login flow keeps sending
 * people to their role's default route and this moves them when the answer arrives.
 *
 * Two guards keep it from being a hijack. It only fires while the member is standing on that role
 * default, so a deep link, a bookmark, or a refresh on any other page is left alone — being bounced
 * to the tenant's landing screen mid-task would be indistinguishable from a bug. And it fires once
 * per session, so navigating back to the dashboard on purpose stays possible.
 */
const TenantLandingRedirect: React.FC<{
  landingPath: string | null;
  role?: string | null;
}> = ({ landingPath, role }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const done = useRef(false);

  useEffect(() => {
    if (done.current || !landingPath) return;

    const roleDefault = landingPathFor(role);
    if (location.pathname !== roleDefault || landingPath === roleDefault) return;

    done.current = true;
    // replace, so Back goes where the member came from rather than to a route that immediately
    // redirects them forward again.
    navigate(landingPath, { replace: true });
  }, [landingPath, role, location.pathname, navigate]);

  return null;
};

/**
 * The formatters, already bound to the signed-in member's timezone and date layout.
 *
 * Components call these instead of `toLocaleDateString`, which silently uses the browser's zone
 * and locale and so ignores the setting entirely.
 */
export const useDateTime = () => {
  const { dateTime } = useUiConfig();
  return useMemo(
    () => ({
      formatDate: (value: DateInput, fallback?: string) =>
        formatDateWith(value, dateTime, fallback),
      formatTime: (value: DateInput, fallback?: string) =>
        formatTimeWith(value, dateTime, fallback),
      formatDateTime: (value: DateInput, fallback?: string) =>
        formatDateTimeWith(value, dateTime, fallback),
      preferences: dateTime,
    }),
    [dateTime],
  );
};

export default UiConfigProvider;

const PREVIEW_KEY = 'themePreview';

/** The preview token from ?preview=, remembered for this tab so navigating keeps the preview. */
const usePreviewToken = (): string | null => {
  const location = useLocation();
  return useMemo(() => {
    const fromUrl = new URLSearchParams(location.search).get('preview');
    try {
      if (fromUrl) sessionStorage.setItem(PREVIEW_KEY, fromUrl);
      return fromUrl ?? sessionStorage.getItem(PREVIEW_KEY);
    } catch {
      return fromUrl;
    }
  }, [location.search]);
};

/** Says, on every page, that what is shown is not live — and how to get out of it. */
const PreviewBanner: React.FC<{ failed: boolean }> = ({ failed }) => (
  <Box role="status" data-testid="preview-banner" sx={{
    position: 'fixed', bottom: 16, left: '50%', transform: 'translateX(-50%)', zIndex: 2000,
    bgcolor: failed ? 'error.main' : '#111827', color: '#fff', px: 2.5, py: 1, borderRadius: 999,
    boxShadow: 6, display: 'flex', gap: 1.5, alignItems: 'center', fontSize: 14,
  }}>
    {failed ? 'This preview link has expired.' : 'Preview — this theme is not published.'}
    <Button size="small" variant="outlined" sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.5)', py: 0 }}
      onClick={() => {
        try { sessionStorage.removeItem(PREVIEW_KEY); } catch { /* storage unavailable */ }
        window.location.assign(window.location.pathname);
      }}>
      Exit preview
    </Button>
  </Box>
);
