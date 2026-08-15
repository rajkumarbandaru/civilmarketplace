import React from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CircularProgress, IconButton, Tooltip } from '@mui/material';
import {
  DarkMode as DarkModeIcon,
  LightMode as LightModeIcon,
  SettingsBrightness as FollowIcon,
} from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from '../hooks';
import { toggleGuestTheme } from '../store/slices/uiSlice';
import { useUiConfig } from '../providers/UiConfigProvider';
import { resolveMode } from '../theme';
import {
  AppearanceSettings,
  fetchMyAppearance,
  updateMyAppearance,
} from '../services/uiConfigApi';

const APPEARANCE_QUERY_KEY = ['ui-config', 'my-appearance'];

/** null is "follow the workspace" — the same absent-override the API uses, not a third value. */
type Choice = 'light' | 'dark' | null;

/** light → dark → follow → light. `null` is a real stop here, so it cannot be a `??` fallback. */
const nextChoice = (current: Choice): Choice =>
  current === 'light' ? 'dark' : current === 'dark' ? null : 'light';

const LABEL = (choice: Choice) =>
  choice === null ? 'follow the workspace' : `${choice} mode`;

/**
 * The signed-out switch: light and dark only, held in Redux and localStorage.
 *
 * "Follow the workspace" is not offered because a visitor has no workspace yet — and the choice
 * made here is not carried into the account on sign-in, where the member's own `colorMode` and the
 * workspace theme take over.
 */
const GuestColorModeToggle: React.FC<{ color?: string }> = ({ color }) => {
  const dispatch = useAppDispatch();
  const mode = useAppSelector((state) => state.ui.theme);
  const next = mode === 'dark' ? 'light' : 'dark';

  return (
    <Tooltip title={`Switch to ${next} mode`}>
      <IconButton
        onClick={() => dispatch(toggleGuestTheme())}
        aria-label={`Switch to ${next} mode`}
        sx={{ color: color || 'text.secondary' }}
      >
        {mode === 'dark' ? <LightModeIcon /> : <DarkModeIcon />}
      </IconButton>
    </Tooltip>
  );
};

/**
 * Cycles the signed-in member through light → dark → follow the workspace from the shell, so the
 * common case does not cost a trip to Settings → Appearance.
 *
 * It writes the same `colorMode` member setting that screen writes — there is no second, shell-only
 * notion of "theme" to drift out of sync. The third stop matters: without it the shell could only
 * ever pin a member to an explicit mode, and "I want whatever the workspace picked" would be
 * unreachable from here.
 */
const ColorModeToggle: React.FC<{ color?: string }> = ({ color }) => {
  const queryClient = useQueryClient();
  const { theme, refresh } = useUiConfig();

  // The member's own override is what the cycle steps through, so it has to be known before the
  // first press — the icon shows where they *are*, which "following" is a distinct state of. The
  // Appearance page shares this key, so opening either screen warms the other.
  const { data, isLoading } = useQuery<AppearanceSettings>({
    queryKey: APPEARANCE_QUERY_KEY,
    queryFn: fetchMyAppearance,
    enabled: Boolean(theme),
    staleTime: 5 * 60 * 1000,
  });

  const current: Choice = data?.myColorMode === 'light' || data?.myColorMode === 'dark'
    ? data.myColorMode
    : null;
  const next = nextChoice(current);

  const save = useMutation({
    mutationFn: () => updateMyAppearance({ colorMode: next, density: data?.myDensity ?? null }),
    onSuccess: (saved) => {
      queryClient.setQueryData(APPEARANCE_QUERY_KEY, saved);
      refresh();
    },
  });

  // Signed out (and when the config could not be fetched) there is no appearance record to save
  // against and no workspace to follow, so the header gets the local two-state switch instead.
  if (!theme) return <GuestColorModeToggle color={color} />;

  const busy = isLoading || save.isPending;

  // While following, the icon is the "auto" glyph rather than a mode — but the tooltip still names
  // what is actually on screen, including an admin 'system' resolved against the OS.
  const icon = current === 'light' ? <LightModeIcon />
    : current === 'dark' ? <DarkModeIcon />
    : <FollowIcon />;

  const title = save.isError
    ? 'Could not switch — try again'
    : current === null
      ? `Following the workspace (${resolveMode(theme.mode)}) — switch to ${LABEL(next)}`
      : `${LABEL(current)} — switch to ${LABEL(next)}`;

  return (
    <Tooltip title={title}>
      <span>
        <IconButton
          onClick={() => save.mutate()}
          disabled={busy}
          aria-label={`Colour mode: ${LABEL(current)}. Switch to ${LABEL(next)}.`}
          sx={{ color: color || 'text.secondary' }}
        >
          {save.isPending ? <CircularProgress size={20} color="inherit" /> : icon}
        </IconButton>
      </span>
    </Tooltip>
  );
};

export default ColorModeToggle;
