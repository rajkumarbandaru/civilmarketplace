import React from 'react';
import { IconButton, Tooltip } from '@mui/material';
import { AutoAwesome as AiIcon } from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from '../hooks';
import { toggleAskAi } from '../store/slices/uiSlice';

/**
 * The Civil AI Assistant entry point, sitting beside the colour-mode toggle in every header.
 *
 * It takes the `color` prop for the same reason `ColorModeToggle` does: the admin app bar passes an
 * explicit slate grey to match its own icon row, while the member header lets it fall through to
 * the theme's `text.secondary`.
 *
 * Signed out it renders nothing at all. The endpoint behind it is authenticated — every question
 * spends a shared free-tier quota — so showing the button to a visitor would only offer a door that
 * opens onto a 401.
 */
const AskAiButton: React.FC<{ color?: string }> = ({ color }) => {
  const dispatch = useAppDispatch();
  const open = useAppSelector((state) => state.ui.askAiOpen);
  const isAuthenticated = useAppSelector((state) => state.auth.isAuthenticated);

  if (!isAuthenticated) return null;

  return (
    <Tooltip title={open ? 'Close Civil AI Assistant' : 'Civil AI Assistant'}>
      <IconButton
        onClick={() => dispatch(toggleAskAi())}
        aria-label={open ? 'Close Civil AI Assistant' : 'Open Civil AI Assistant'}
        aria-expanded={open}
        sx={{
          color: color || 'text.secondary',
          // Tinted while open so the header shows which panel is up, matching how the nav marks
          // the active route rather than leaving the drawer as the only clue.
          ...(open && { color: 'primary.main' }),
        }}
      >
        <AiIcon />
      </IconButton>
    </Tooltip>
  );
};

export default AskAiButton;
