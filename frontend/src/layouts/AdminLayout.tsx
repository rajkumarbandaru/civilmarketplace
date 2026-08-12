import React, { useState } from 'react';
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

const DRAWER_WIDTH = 280;

/**
 * What the sidebar shows before the UI config arrives, and if it never does — admin-service
 * being unreachable must not leave an admin with no way to navigate. Keys and paths match the
 * catalogue seeded in admin-service's V2 migration.
 */
const FALLBACK_NAV = [
  { key: 'admin-overview', label: 'Dashboard', path: '/admin', icon: 'Dashboard', exactMatch: true },
  { key: 'admin-users', label: 'Users', path: '/admin/users', icon: 'People', exactMatch: false },
  { key: 'admin-categories', label: 'Categories', path: '/admin/categories', icon: 'Category', exactMatch: false },
  { key: 'admin-bookings', label: 'Bookings', path: '/admin/bookings', icon: 'BookOnline', exactMatch: false },
  { key: 'admin-analytics', label: 'Analytics', path: '/admin/analytics', icon: 'Analytics', exactMatch: false },
  { key: 'admin-revenue', label: 'Revenue', path: '/admin/revenue', icon: 'AccountBalanceWallet', exactMatch: false },
  { key: 'admin-reports', label: 'Reports', path: '/admin/reports', icon: 'Assessment', exactMatch: false },
  { key: 'admin-invoices', label: 'Invoices', path: '/admin/invoices', icon: 'Receipt', exactMatch: false },
  { key: 'admin-workspaces', label: 'Workspaces', path: '/admin/workspaces', icon: 'ViewQuilt', exactMatch: false },
  { key: 'admin-theme', label: 'Theme & UI style', path: '/admin/theme', icon: 'Palette', exactMatch: false },
  { key: 'admin-settings', label: 'Settings', path: '/admin/settings', icon: 'Settings', exactMatch: false },
];

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

  // Super Admin can move the navigation to the other edge; nothing else about the shell's
  // geometry is configurable, so this is a single flex direction rather than a layout engine.
  const navOnRight = uiTheme?.layoutStyle === 'sidebar-right';

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
          {navItems.map((item) => {
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
            <Typography variant="h6" sx={{ fontWeight: 700, flexGrow: 1 }}>
              {/* The heading is the menu label, so a renamed entry renames the page too. */}
              {navItems.find(isItemActive)?.label || 'Admin Panel'}
            </Typography>

            <IconButton sx={{ color: '#64748b', mr: 1 }}>
              <Badge badgeContent={3} color="error">
                <Notifications />
              </Badge>
            </IconButton>

            <IconButton onClick={(e) => setAnchorEl(e.currentTarget)} sx={{ p: 0 }}>
              <Avatar sx={{ width: 36, height: 36, background: 'linear-gradient(135deg, #667eea, #764ba2)' }}>
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
