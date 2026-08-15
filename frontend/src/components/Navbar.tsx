import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  AppBar,
  Toolbar,
  Typography,
  Button,
  IconButton,
  Badge,
  Avatar,
  Menu,
  MenuItem,
  Box,
  Container,
  Drawer,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
} from '@mui/material';
import {
  Menu as MenuIcon,
  Notifications as NotificationsIcon,
  AccountCircle,
  Dashboard,
  Build,
  People,
  Logout,
  Home,
  Engineering,
} from '@mui/icons-material';
import { styled, alpha, useTheme } from '@mui/material/styles';
import { useAppSelector, useAppDispatch } from '../hooks';
import { logout } from '../store/slices/authSlice';
import { toggleSidebar } from '../store/slices/uiSlice';
import { motion } from 'framer-motion';
import { useMenuSection, useUiConfig } from '../providers/UiConfigProvider';
import DynamicIcon from './DynamicIcon';
import ColorModeToggle from './ColorModeToggle';
import GlobalSearch from './GlobalSearch';

// A visitor who is not signed in holds no role, so there is no workspace to resolve a menu from.
// This is that public site's own navigation — not a fallback for a signed-in member, whose menu
// comes from their workspace and nowhere else.
const PUBLIC_NAV = [
  { key: 'home', label: 'Home', path: '/', icon: 'Home' },
  { key: 'services', label: 'Services', path: '/services', icon: 'Engineering' },
];




/**
 * @param navInDrawer the shell has put the workspace menu in a side drawer, so the bar must not
 *   repeat it — one navigation surface per layout, the same rule the admin shell follows.
 */
const Navbar: React.FC<{ navInDrawer?: boolean }> = ({ navInDrawer = false }) => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);
  const { sidebarOpen } = useAppSelector((state) => state.ui);
  const { unreadCount } = useAppSelector((state) => state.notification);
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const workMenuItems = useMenuSection('Work');

  // The wordmark is Super Admin's to set, and the gradient behind it follows the configured
  // palette — a re-themed platform that still says CivEngMarket in violet is not re-themed.
  const { theme: uiTheme } = useUiConfig();
  const muiTheme = useTheme();
  const brandName = uiTheme?.brandName || 'CivEngMarket';
  const brandGradient =
    `linear-gradient(135deg, ${muiTheme.palette.primary.main} 0%, ${muiTheme.palette.secondary.main} 100%)`;
  const accountMenuItems = useMenuSection('Account');

  const handleProfileMenu = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => setAnchorEl(null);

  const handleLogout = () => {
    dispatch(logout());
    handleClose();
    navigate('/');
  };

  // A signed-in member's navigation is exactly their workspace's Work section — nothing is added
  // to it here. Hiding an entry in the Workspaces screen therefore removes it from the shell, and
  // a workspace that hides everything renders an empty bar rather than a hardcoded remainder.
  // "Home" is not among them: it is the visitors' landing page, and `/` redirects a signed-in
  // user back into their workspace anyway.
  const navSource = isAuthenticated ? workMenuItems : PUBLIC_NAV;
  const navItems = navSource.map((item) => ({
    label: item.label,
    path: item.path,
    icon: <DynamicIcon name={item.icon} />,
  }));

  return (
    <>
      <AppBar
        position="fixed"
        // Resolved from the theme, not painted white: a hardcoded bar ignored every colour a
        // Super Admin set, and stayed white in dark mode. The alpha keeps the blur effect that
        // the flat colour would otherwise lose.
        sx={{
          background: alpha(muiTheme.palette.background.paper, 0.9),
          backdropFilter: 'blur(20px)',
          boxShadow: '0 1px 3px rgba(0,0,0,0.08)',
          borderBottom: `1px solid ${muiTheme.palette.divider}`,
        }}
      >
        <Container maxWidth="xl">
          <Toolbar disableGutters sx={{ height: 64 }}>
            {/* Mobile menu */}
            <IconButton
              edge="start"
              sx={{ display: { md: 'none' }, mr: 1, color: 'text.primary' }}
              onClick={() => dispatch(toggleSidebar())}
            >
              <MenuIcon />
            </IconButton>

            {/* Logo */}
            <motion.div
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ duration: 0.5 }}
            >
              <Typography
                variant="h6"
                component={Link}
                to="/"
                sx={{
                  fontWeight: 800,
                  background: brandGradient,
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent',
                  textDecoration: 'none',
                  whiteSpace: 'nowrap',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1,
                }}
              >
                {uiTheme?.logoUrl && (
                  <Box
                    component="img"
                    src={uiTheme.logoUrl}
                    alt=""
                    onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none'; }}
                    sx={{ height: 26, objectFit: 'contain' }}
                  />
                )}
                {brandName}
              </Typography>
            </motion.div>

            {/* Search bar. Its own component now — it dispatched into Redux and nothing read the
                value, so the box was inert. */}
            <GlobalSearch />

            {/* Desktop nav */}
            <Box sx={{ flexGrow: 1 }} />

            <Box sx={{ display: { xs: 'none', md: 'flex' }, gap: 1, alignItems: 'center' }}>
              {(navInDrawer ? [] : navItems).map((item) => (
                <Button
                  key={item.path}
                  component={Link}
                  to={item.path}
                  sx={{
                    color: 'text.primary',
                    fontWeight: 500,
                    '&:hover': { background: alpha(muiTheme.palette.primary.main, 0.08) },
                  }}
                >
                  {item.label}
                </Button>
              ))}

              {isAuthenticated ? (
                <>
                  <ColorModeToggle />

                  <IconButton sx={{ color: 'text.secondary' }}>
                    <Badge badgeContent={unreadCount} color="error">
                      <NotificationsIcon />
                    </Badge>
                  </IconButton>

                  <IconButton onClick={handleProfileMenu} sx={{ p: 0 }}>
                    <Avatar
                      src={user?.profilePicture}
                      sx={{
                        width: 36,
                        height: 36,
                        background: brandGradient,
                      }}
                    >
                      {user?.name?.charAt(0)}
                    </Avatar>
                  </IconButton>

                  <Menu
                    anchorEl={anchorEl}
                    open={Boolean(anchorEl)}
                    onClose={handleClose}
                    transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                    anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                    PaperProps={{
                      sx: {
                        mt: 1.5,
                        borderRadius: 3,
                        minWidth: 200,
                        boxShadow: '0 10px 40px rgba(0,0,0,0.1)',
                      },
                    }}
                  >
                    <MenuItem disabled>
                      <Typography variant="body2" color="text.secondary">
                        {user?.email}
                      </Typography>
                    </MenuItem>
                    <Divider />
                    <MenuItem onClick={() => { navigate('/dashboard'); handleClose(); }}>
                      <ListItemIcon><Dashboard fontSize="small" /></ListItemIcon>
                      Dashboard
                    </MenuItem>
                    <MenuItem onClick={() => { navigate('/profile'); handleClose(); }}>
                      <ListItemIcon><AccountCircle fontSize="small" /></ListItemIcon>
                      Profile
                    </MenuItem>
                    {accountMenuItems
                      .filter((item) => item.path !== '/profile')
                      .map((item) => (
                        <MenuItem key={item.key} onClick={() => { navigate(item.path); handleClose(); }}>
                          <ListItemIcon><DynamicIcon name={item.icon} fontSize="small" /></ListItemIcon>
                          {item.label}
                        </MenuItem>
                      ))}
                    <MenuItem onClick={handleLogout}>
                      <ListItemIcon><Logout fontSize="small" /></ListItemIcon>
                      Logout
                    </MenuItem>
                  </Menu>
                </>
              ) : (
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                  <ColorModeToggle />
                  <Button
                    component={Link}
                    to="/login"
                    variant="outlined"
                    sx={{ borderColor: 'primary.main', color: 'primary.main' }}
                  >
                    Login
                  </Button>
                  <Button
                    component={Link}
                    to="/register"
                    variant="contained"
                  >
                    Register
                  </Button>
                </Box>
              )}
            </Box>
          </Toolbar>
        </Container>
      </AppBar>

      {/* Mobile drawer */}
      <Drawer
        anchor="left"
        open={sidebarOpen}
        onClose={() => dispatch(toggleSidebar())}
      >
        <Box sx={{ width: 280, pt: 2 }}>
          <Typography
            variant="h6"
            sx={{
              px: 2,
              fontWeight: 800,
              background: brandGradient,
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
              mb: 2,
            }}
          >
            {brandName}
          </Typography>
          <Divider />
          <List>
            {[...navItems,
              ...(isAuthenticated
                ? accountMenuItems.map((item) => ({
                    label: item.label,
                    path: item.path,
                    icon: <DynamicIcon name={item.icon} />,
                  }))
                : [{ label: 'Login', path: '/login', icon: <AccountCircle /> },
                   { label: 'Register', path: '/register', icon: <People /> }]
              ),
            ].map((item) => (
              <ListItem
                key={item.path}
                component={Link}
                to={item.path}
                onClick={() => dispatch(toggleSidebar())}
                sx={{
                  borderRadius: 2,
                  mx: 1,
                  mb: 0.5,
                  '&:hover': { background: alpha(muiTheme.palette.primary.main, 0.08) },
                }}
              >
                <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
                <ListItemText primary={item.label} />
              </ListItem>
            ))}
          </List>

          {/* The desktop toggle lives in the toolbar, which is hidden below md — without this the
              switch would be unreachable on a phone. */}
          <Divider />
          <Box sx={{ px: 2, py: 1, display: 'flex', alignItems: 'center', gap: 1 }}>
            <ColorModeToggle />
            <Typography variant="body2" color="text.secondary">Colour mode</Typography>
          </Box>
        </Box>
      </Drawer>
    </>
  );
};

export default Navbar;
