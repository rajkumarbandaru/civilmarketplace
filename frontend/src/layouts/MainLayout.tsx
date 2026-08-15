import React from 'react';
import { Outlet, Link as RouterLink, useLocation } from 'react-router-dom';
import { Box, Drawer, List, ListItemButton, ListItemIcon, ListItemText, Toolbar } from '@mui/material';
import Navbar from '../components/Navbar';
import Footer from '../components/Footer';
import DynamicIcon from '../components/DynamicIcon';
import { useMenuSection, useUiConfig } from '../providers/UiConfigProvider';
import { motion } from 'framer-motion';

const DRAWER_WIDTH = 240;

const MainLayout: React.FC = () => {
  const { theme: uiTheme } = useUiConfig();
  const workItems = useMenuSection('Work');
  const location = useLocation();

  // Navigation position is Super Admin's to set, and it applied only to the admin console until
  // now — the member shell ignored it entirely, so moving the navigation appeared to do nothing
  // for everyone who is not an admin. 'topbar' and an unset value both keep the bar, which is
  // this shell's original shape.
  const navOnLeft = uiTheme?.layoutStyle === 'sidebar-left';
  const navOnRight = uiTheme?.layoutStyle === 'sidebar-right';
  const inDrawer = navOnLeft || navOnRight;

  const isActive = (path: string) =>
    path === '/' ? location.pathname === '/' : location.pathname.startsWith(path);

  const content = (
    <Box component="main" sx={{ flexGrow: 1, pt: '64px' }}>
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -20 }}
        transition={{ duration: 0.3 }}
      >
        <Outlet />
      </motion.div>
    </Box>
  );

  if (!inDrawer) {
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
        <Navbar />
        {content}
        <Footer />
      </Box>
    );
  }

  const drawer = (
    <Drawer
      variant="permanent"
      anchor={navOnRight ? 'right' : 'left'}
      sx={{
        width: DRAWER_WIDTH,
        flexShrink: 0,
        display: { xs: 'none', md: 'block' },
        '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
      }}
    >
      {/* Clears the fixed bar above, which stays in place to carry search and the account menu. */}
      <Toolbar />
      <List>
        {workItems.map((item) => (
          <ListItemButton
            key={item.key}
            component={RouterLink}
            to={item.path}
            selected={isActive(item.path)}
          >
            <ListItemIcon><DynamicIcon name={item.icon} /></ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
      </List>
    </Drawer>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      {/* Ordered by the anchor rather than by source position, so the main column sits beside the
          drawer on both sides instead of underneath it on one. */}
      {navOnLeft && drawer}
      <Box sx={{ display: 'flex', flexDirection: 'column', flexGrow: 1, minWidth: 0 }}>
        <Navbar navInDrawer />
        {content}
        <Footer />
      </Box>
      {navOnRight && drawer}
    </Box>
  );
};

export default MainLayout;
