import React, { useMemo, useState } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import {
  Box,
  Drawer,
  AppBar,
  Toolbar,
  Typography,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  ListSubheader,
  Avatar,
  Menu,
  MenuItem,
  Divider,
  Badge,
  Container,
  Stack,
  useTheme,
} from '@mui/material';
import {
  Menu as MenuIcon,
  Notifications,
  Settings,
  Logout,
  Palette,
  ChevronLeft,
  ChevronRight,
  Engineering,
} from '@mui/icons-material';
import { motion } from 'framer-motion';
import { useAppSelector, useAppDispatch } from '../hooks';
import { logout } from '../store/slices/authSlice';
import { useUiConfig } from '../providers/UiConfigProvider';
import { sidebarPalette } from '../theme';
import DynamicIcon from '../components/DynamicIcon';
import ColorModeToggle from '../components/ColorModeToggle';
import GlobalSearch from '../components/GlobalSearch';
import NotificationBell from '../components/NotificationBell';
import AskAiButton from '../components/AskAiButton';

const DRAWER_WIDTH = 280;

/**
 * What the sidebar shows before the UI config arrives, and if it never does — admin-service
 * being unreachable must not leave an admin with no way to navigate. Keys and paths match the
 * catalogue seeded in admin-service's V2 migration.
 */
const FALLBACK_NAV = [
  { key: 'admin-overview', label: 'Dashboard', path: '/admin', icon: 'Dashboard', menuGroup: 'Overview', exactMatch: true },
  { key: 'admin-users', label: 'Users', path: '/admin/users', icon: 'People', menuGroup: 'People', exactMatch: false },
  { key: 'admin-categories', label: 'Categories', path: '/admin/categories', icon: 'Category', menuGroup: 'Operations', exactMatch: false },
  { key: 'admin-services', label: 'Services', path: '/admin/services', icon: 'Handyman', menuGroup: 'Operations', exactMatch: false },
  { key: 'admin-bookings', label: 'Bookings', path: '/admin/bookings', icon: 'BookOnline', menuGroup: 'Operations', exactMatch: false },
  { key: 'admin-analytics', label: 'Analytics', path: '/admin/analytics', icon: 'Analytics', menuGroup: 'Operations', exactMatch: false },
  { key: 'admin-revenue', label: 'Revenue', path: '/admin/revenue', icon: 'AccountBalanceWallet', menuGroup: 'Finance', exactMatch: false },
  { key: 'admin-reports', label: 'Reports', path: '/admin/reports', icon: 'Assessment', menuGroup: 'Finance', exactMatch: false },
  { key: 'admin-invoices', label: 'Invoices', path: '/admin/invoices', icon: 'Receipt', menuGroup: 'Finance', exactMatch: false },
  { key: 'admin-workspaces', label: 'Workspaces', path: '/admin/workspaces', icon: 'ViewQuilt', menuGroup: 'System', exactMatch: false },
  { key: 'admin-theme', label: 'Theme & UI style', path: '/admin/theme', icon: 'Palette', menuGroup: 'System', exactMatch: false },
  { key: 'admin-settings', label: 'Settings', path: '/admin/settings', icon: 'Settings', menuGroup: 'System', exactMatch: false },
];

/** A menu group and the items under it, in the order the server sorted them. */
interface NavGroup {
  /** null for items the catalogue has not placed in a group; they render without a heading. */
  name: string | null;
  items: Array<{ key: string; label: string; path: string; icon: string; exactMatch: boolean }>;
}

/**
 * Splits the flat, already-sorted item list into its groups.
 *
 * Consecutive runs rather than a lookup by name: a group's position is the position of its first
 * item, so the server's sort order alone decides both the order of the groups and the order within
 * them. Nothing here needs a second list of group names to keep in step.
 */
const toGroups = (items: Array<{ menuGroup?: string | null } & NavGroup['items'][number]>): NavGroup[] =>
  items.reduce<NavGroup[]>((groups, item) => {
    const name = item.menuGroup ?? null;
    const current = groups[groups.length - 1];
    if (current && current.name === name) current.items.push(item);
    else groups.push({ name, items: [item] });
    return groups;
  }, []);

const AdminLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const [drawerOpen, setDrawerOpen] = useState(true);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const { menu, theme: uiTheme } = useUiConfig();

  // The console's own nav comes from the "Platform" section of the signed-in admin's menu, so
  // hiding an item for a role in the Workspaces screen actually hides it here.
  const platformItems = menu.filter((item) => item.section === 'Platform');
  const navItems = platformItems.length > 0 ? platformItems : FALLBACK_NAV;
  // Eleven flat rows is past the point where a sidebar is scanned rather than read, so the items
  // are drawn under their catalogue group. The grouping is presentation only — visibility and
  // order still come from the workspace's menu.
  const navGroups = useMemo(() => toGroups(navItems), [navItems]);

  // Super Admin can move the navigation to the other edge; nothing else about the shell's
  // geometry is configurable, so this is a single flex direction rather than a layout engine.
  const navOnRight = uiTheme?.layoutStyle === 'sidebar-right';
  // 'topbar' drops the drawer entirely and runs the same items along the top instead, so the
  // shell has one navigation surface in every layout rather than two that can disagree.
  const navOnTop = uiTheme?.layoutStyle === 'topbar';

  // The sidebar is not part of the MUI palette — it is one surface owned by this layout, so its
  // colours are resolved from the config in one place and used throughout the drawer below.
  const nav = sidebarPalette(uiTheme);

  // Read back from the built MUI theme rather than the raw config, so the brand gradient uses the
  // shipped defaults when a colour is left unset instead of rendering "undefined" into the CSS.
  const muiTheme = useTheme();
  const primaryColor = muiTheme.palette.primary.main;
  const accentColor = muiTheme.palette.secondary.main;

  const isItemActive = (item: { path: string; exactMatch: boolean }) =>
    item.exactMatch ? location.pathname === item.path : location.pathname.startsWith(item.path);

  const handleLogout = () => {
    dispatch(logout());
    navigate('/login');
  };

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: navOnRight ? 'row-reverse' : 'row',
        minHeight: '100vh',
        bgcolor: 'background.default',
      }}
    >
      {/* Sidebar */}
      {!navOnTop && (
      <Drawer
        variant="permanent"
        anchor={navOnRight ? 'right' : 'left'}
        open={drawerOpen}
        sx={{
          width: drawerOpen ? DRAWER_WIDTH : 72,
          transition: 'width 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
          '& .MuiDrawer-paper': {
            width: drawerOpen ? DRAWER_WIDTH : 72,
            transition: 'width 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
            overflowX: 'hidden',
            bgcolor: nav.bg,
            color: nav.text,
            borderRight: 'none',
          },
        }}
      >
        {/* Logo */}
        <Box sx={{ p: 2.5, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          {drawerOpen && (
            <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
              {uiTheme?.logoUrl && (
                <Box
                  component="img"
                  src={uiTheme.logoUrl}
                  alt=""
                  // A broken logo URL must not leave a torn-image icon in the shell's chrome;
                  // the wordmark beside it already names the product.
                  onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none'; }}
                  sx={{ width: 28, height: 28, objectFit: 'contain', flexShrink: 0 }}
                />
              )}
              <Typography
                variant="h6"
                noWrap
                sx={{
                  fontWeight: 800,
                  // The wordmark follows the configured palette; without a gradient to clip,
                  // plain text would be the only way to read a solid-colour brand.
                  background: `linear-gradient(135deg, ${primaryColor}, ${accentColor})`,
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                }}
              >
                {uiTheme?.brandName || 'Admin Panel'}
              </Typography>
            </Stack>
          )}
          <IconButton onClick={() => setDrawerOpen(!drawerOpen)} sx={{ color: nav.muted }}>
            {!drawerOpen ? <MenuIcon /> : navOnRight ? <ChevronRight /> : <ChevronLeft />}
          </IconButton>
        </Box>

        <Divider sx={{ borderColor: nav.divider }} />

        {/* Navigation */}
        <List sx={{ px: 1.5, py: 2 }}>
          {navGroups.map((group, groupIdx) => (
            <React.Fragment key={group.name ?? `ungrouped-${groupIdx}`}>
              {/* Collapsed to icons there is no room for a heading, so the groups are separated by
                  a rule instead — the grouping still reads, just without its names. */}
              {group.name && groupIdx > 0 && !drawerOpen && (
                <Divider sx={{ borderColor: nav.divider, my: 1, mx: 1 }} />
              )}
              {group.name && drawerOpen && (
                <ListSubheader
                  disableSticky
                  sx={{
                    bgcolor: 'transparent',
                    color: nav.muted,
                    fontSize: '0.68rem',
                    fontWeight: 700,
                    letterSpacing: '0.08em',
                    lineHeight: 2.2,
                    px: 2,
                    mt: groupIdx > 0 ? 1.5 : 0,
                    opacity: 0.75,
                  }}
                >
                  {group.name.toUpperCase()}
                </ListSubheader>
              )}
              {group.items.map((item) => {
            const isActive = isItemActive(item);

            return (
              <ListItem key={item.key} disablePadding sx={{ mb: 0.5 }}>
                <ListItemButton
                  onClick={() => navigate(item.path)}
                  sx={{
                    borderRadius: 2,
                    minHeight: 44,
                    // The active highlight follows the configured primary colour rather than the
                    // shipped purple, so a re-themed console does not keep one lilac row.
                    bgcolor: isActive ? nav.activeBg : 'transparent',
                    color: isActive ? 'primary.main' : nav.muted,
                    '&:hover': {
                      bgcolor: isActive ? nav.activeHoverBg : nav.hoverBg,
                    },
                    justifyContent: drawerOpen ? 'initial' : 'center',
                    px: 2,
                  }}
                >
                  <ListItemIcon
                    sx={{
                      minWidth: 0,
                      mr: drawerOpen ? 2 : 'auto',
                      color: isActive ? 'primary.main' : nav.icon,
                      justifyContent: 'center',
                    }}
                  >
                    <DynamicIcon name={item.icon} />
                  </ListItemIcon>
                  {drawerOpen && (
                    <ListItemText
                      primary={item.label}
                      primaryTypographyProps={{
                        fontSize: '0.875rem',
                        fontWeight: isActive ? 600 : 400,
                      }}
                    />
                  )}
                </ListItemButton>
              </ListItem>
            );
              })}
            </React.Fragment>
          ))}
        </List>

        <Box sx={{ flexGrow: 1 }} />

        {/* User info at bottom */}
        {drawerOpen && (
          <Box sx={{ p: 2, borderTop: `1px solid ${nav.divider}` }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Avatar
                sx={{
                  width: 36,
                  height: 36,
                  background: `linear-gradient(135deg, ${primaryColor}, ${accentColor})`,
                  fontSize: '0.875rem',
                }}
              >
                {user?.name?.charAt(0) || 'A'}
              </Avatar>
              <Box>
                <Typography variant="body2" sx={{ color: nav.text, fontWeight: 600, fontSize: '0.8rem' }}>
                  {user?.name || 'Admin'}
                </Typography>
                <Typography variant="caption" sx={{ color: nav.muted, fontSize: '0.7rem' }}>
                  {user?.role
                    ? user.role.toLowerCase().split('_').map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(' ')
                    : 'Admin'}
                </Typography>
              </Box>
            </Box>
          </Box>
        )}
      </Drawer>
      )}

      {/* Main Content */}
      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
        {/* Top Bar */}
        <AppBar
          position="sticky"
          sx={{
            // Theme tokens rather than literal white: dark mode is an admin- and member-settable
            // option now, and a hardcoded white bar renders black-on-black text under it.
            bgcolor: 'background.paper',
            backgroundImage: 'none',
            backdropFilter: 'blur(20px)',
            boxShadow: '0 1px 3px rgba(0,0,0,0.06)',
            color: 'text.primary',
          }}
        >
          <Toolbar>
            {navOnTop && (
              <Stack direction="row" spacing={1} alignItems="center" sx={{ mr: 2, minWidth: 0 }}>
                {uiTheme?.logoUrl && (
                  <Box
                    component="img"
                    src={uiTheme.logoUrl}
                    alt=""
                    onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none'; }}
                    sx={{ width: 26, height: 26, objectFit: 'contain', flexShrink: 0 }}
                  />
                )}
                <Typography
                  variant="h6"
                  noWrap
                  sx={{
                    fontWeight: 800,
                    background: `linear-gradient(135deg, ${primaryColor}, ${accentColor})`,
                    WebkitBackgroundClip: 'text',
                    WebkitTextFillColor: 'transparent',
                  }}
                >
                  {uiTheme?.brandName || 'Admin Panel'}
                </Typography>
              </Stack>
            )}
            <Typography variant="h6" sx={{ fontWeight: 700, flexGrow: 1 }}>
              {/* The heading is the menu label, so a renamed entry renames the page too. */}
              {navOnTop ? '' : navItems.find(isItemActive)?.label || 'Admin Panel'}
            </Typography>

            {/* Same catalogue search as the member header — the assistant is on every page and
                this should be too, rather than dropping out the moment an admin crosses into
                /admin. Shared component, so the two headers cannot drift apart. */}
            <GlobalSearch />

            <AskAiButton color="#64748b" />

            <ColorModeToggle color="#64748b" />

            {/* Was a hardcoded badgeContent={3} over a button that did nothing. */}
            <Box sx={{ mr: 1 }}>
              <NotificationBell color="#64748b" />
            </Box>

            <IconButton onClick={(e) => setAnchorEl(e.currentTarget)} sx={{ p: 0 }}>
              <Avatar sx={{ width: 36, height: 36, background: (t) => `linear-gradient(135deg, ${t.palette.primary.main}, ${t.palette.secondary.main})` }}>
                {user?.name?.charAt(0) || 'A'}
              </Avatar>
            </IconButton>

            <Menu
              anchorEl={anchorEl}
              open={Boolean(anchorEl)}
              onClose={() => setAnchorEl(null)}
              PaperProps={{ sx: { borderRadius: 3, mt: 1, minWidth: 200 } }}
            >
              <MenuItem onClick={() => { navigate('/admin/settings'); setAnchorEl(null); }}>
                <ListItemIcon><Settings fontSize="small" /></ListItemIcon>
                Settings
              </MenuItem>
              {/* The admin shell has its own account menu rather than the site Navbar's, so this
                  entry has to be repeated here — without it an admin has no route to their own
                  timezone, date format, colour mode or density. */}
              <MenuItem onClick={() => { navigate('/appearance'); setAnchorEl(null); }}>
                <ListItemIcon><Palette fontSize="small" /></ListItemIcon>
                Appearance &amp; date format
              </MenuItem>
              <MenuItem onClick={() => { navigate('/'); setAnchorEl(null); }}>
                <ListItemIcon><Engineering fontSize="small" /></ListItemIcon>
                View Site
              </MenuItem>
              <Divider />
              <MenuItem onClick={handleLogout}>
                <ListItemIcon><Logout fontSize="small" /></ListItemIcon>
                Logout
              </MenuItem>
            </Menu>
          </Toolbar>

          {navOnTop && (
            <Box
              sx={{
                bgcolor: nav.bg,
                px: 2,
                py: 0.75,
                display: 'flex',
                gap: 0.5,
                // The row scrolls rather than wrapping: a wrapped nav changes the page's top
                // offset as items are hidden or renamed per workspace.
                overflowX: 'auto',
                '&::-webkit-scrollbar': { height: 4 },
              }}
            >
              {navGroups.map((group, groupIdx) => (
                <React.Fragment key={group.name ?? `top-ungrouped-${groupIdx}`}>
                  {/* A horizontal bar has no room for headings; the groups read as separated runs
                      instead, which is the same information without a second row of text. */}
                  {groupIdx > 0 && (
                    <Divider orientation="vertical" flexItem sx={{ borderColor: nav.divider, mx: 0.5 }} />
                  )}
                  {group.items.map((item) => {
                const isActive = isItemActive(item);
                return (
                  <Box
                    key={item.key}
                    onClick={() => navigate(item.path)}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 1,
                      px: 1.5,
                      py: 0.75,
                      borderRadius: 2,
                      cursor: 'pointer',
                      whiteSpace: 'nowrap',
                      fontSize: '0.875rem',
                      fontWeight: isActive ? 600 : 400,
                      bgcolor: isActive ? nav.activeBg : 'transparent',
                      color: isActive ? 'primary.main' : nav.muted,
                      '&:hover': { bgcolor: isActive ? nav.activeHoverBg : nav.hoverBg },
                    }}
                  >
                    <Box sx={{ display: 'flex', color: isActive ? 'primary.main' : nav.icon }}>
                      <DynamicIcon name={item.icon} />
                    </Box>
                    {item.label}
                  </Box>
                );
                  })}
                </React.Fragment>
              ))}
            </Box>
          )}
        </AppBar>

        {/* Page Content */}
        <Box sx={{ flexGrow: 1, p: 3 }}>
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
          >
            <Outlet />
          </motion.div>
        </Box>
      </Box>
    </Box>
  );
};

export default AdminLayout;
