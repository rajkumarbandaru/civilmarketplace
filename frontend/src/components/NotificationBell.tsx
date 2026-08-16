import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Badge, Box, Button, CircularProgress, Divider, IconButton, List, ListItemButton, ListItemText,
  Menu, Tooltip, Typography,
} from '@mui/material';
import { DoneAll, Notifications as NotificationsIcon } from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from '../hooks';
import {
  fetchNotifications,
  markAllAsRead,
  markAsRead,
  refreshUnreadCount,
} from '../store/slices/notificationSlice';
import { AppNotification } from '../services/notificationApi';

/**
 * The notification bell, and the menu behind it.
 *
 * Both shells previously painted a bell that did nothing: the customer navbar showed a count that
 * could only ever be zero, and the admin shell showed the literal number 3. One component now
 * serves both, so the two cannot drift apart again.
 */

/** How often the badge is refreshed while the app is open. */
const POLL_MS = 60_000;

/**
 * Where a notification leads.
 *
 * Driven by what the notification is *about* rather than its type string, so a new event type
 * lands somewhere sensible without a change here. Anything unrecognised opens the dashboard, which
 * is where every reference eventually appears.
 */
const destinationFor = (n: AppNotification): string => {
  if (n.referenceType === 'BOOKING' && n.referenceId) {
    // The arrival alert is the one worth opening the map for; the rest read fine on the booking list.
    return n.type === 'WORKER_ARRIVING' ? `/track/${n.referenceId}` : '/dashboard';
  }
  return '/dashboard';
};

/** "3 minutes ago" without pulling in a date library for one line of text. */
const relativeTime = (iso: string): string => {
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) return '';
  const mins = Math.round((Date.now() - then) / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
};

const NotificationBell: React.FC<{ color?: string }> = ({ color = 'text.secondary' }) => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { isAuthenticated } = useAppSelector((state) => state.auth);
  const { notifications, unreadCount, loading, loaded } = useAppSelector((state) => state.notification);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);

  // Only the badge is polled. Refetching the whole list every minute would cost a page of rows to
  // render a number, and the list is re-read whenever the menu is opened anyway.
  useEffect(() => {
    if (!isAuthenticated) return undefined;
    dispatch(refreshUnreadCount());
    const timer = window.setInterval(() => dispatch(refreshUnreadCount()), POLL_MS);
    return () => window.clearInterval(timer);
  }, [dispatch, isAuthenticated]);

  const open = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
    // Fetched on open rather than held fresh in the background: this is the only moment the list
    // is actually looked at.
    dispatch(fetchNotifications());
  };

  const close = () => setAnchorEl(null);

  const openNotification = (n: AppNotification) => {
    if (!n.isRead) dispatch(markAsRead(n.id));
    close();
    navigate(destinationFor(n));
  };

  if (!isAuthenticated) return null;

  return (
    <>
      <Tooltip title="Notifications">
        <IconButton onClick={open} sx={{ color }} aria-label={`Notifications (${unreadCount} unread)`}>
          <Badge badgeContent={unreadCount} color="error" max={99}>
            <NotificationsIcon />
          </Badge>
        </IconButton>
      </Tooltip>

      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ paper: { sx: { width: 380, maxWidth: '100vw', borderRadius: 2 } } }}
      >
        <Box sx={{ px: 2, py: 1.5, display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, flexGrow: 1 }}>
            Notifications
          </Typography>
          {unreadCount > 0 && (
            <Button
              size="small"
              startIcon={<DoneAll />}
              onClick={() => dispatch(markAllAsRead())}
              sx={{ textTransform: 'none' }}
            >
              Mark all read
            </Button>
          )}
        </Box>
        <Divider />

        {loading && !loaded && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
            <CircularProgress size={24} />
          </Box>
        )}

        {loaded && notifications.length === 0 && (
          <Box sx={{ px: 2, py: 4, textAlign: 'center' }}>
            <Typography variant="body2" color="text.secondary">
              Nothing yet. Booking updates and alerts will appear here.
            </Typography>
          </Box>
        )}

        <List dense sx={{ maxHeight: 420, overflowY: 'auto', py: 0 }}>
          {notifications.map((n) => (
            <ListItemButton
              key={n.id}
              onClick={() => openNotification(n)}
              // Unread rows are tinted rather than badged individually — the whole point of
              // opening this menu is to see which ones are new.
              sx={{
                alignItems: 'flex-start',
                bgcolor: n.isRead ? 'transparent' : 'action.hover',
                borderLeft: (t) => `3px solid ${n.isRead ? 'transparent' : t.palette.primary.main}`,
              }}
            >
              <ListItemText
                primary={
                  <Typography variant="body2" sx={{ fontWeight: n.isRead ? 500 : 700 }}>
                    {n.title}
                  </Typography>
                }
                secondary={
                  <>
                    <Typography variant="caption" component="span" sx={{ display: 'block', color: 'text.secondary' }}>
                      {n.message}
                    </Typography>
                    <Typography variant="caption" component="span" sx={{ color: 'text.disabled' }}>
                      {relativeTime(n.createdAt)}
                    </Typography>
                  </>
                }
              />
            </ListItemButton>
          ))}
        </List>
      </Menu>
    </>
  );
};

export default NotificationBell;
