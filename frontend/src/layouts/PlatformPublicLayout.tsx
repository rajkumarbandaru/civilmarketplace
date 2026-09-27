import React from 'react';
import { Link as RouterLink, Navigate, Outlet, useLocation } from 'react-router-dom';
import { AppBar, Box, Button, Container, Stack, Toolbar, Typography } from '@mui/material';
import { Hub } from '@mui/icons-material';
import { useAppSelector } from '../hooks';
import { useWorkspace } from '../providers/WorkspaceProvider';
import { landingPathFor } from '../components/AdminRoute';
import { usePlatformName } from '../hooks/usePlatformHost';

/**
 * The public shell on the platform company's own address, in place of the marketplace's navbar
 * and footer. The platform runs no marketplace of its own, so its only public page is the landing
 * page: any marketplace path typed here (/services, /book/…) goes back to it rather than rendering a
 * store with nothing in it.
 */
const PlatformPublicLayout: React.FC = () => {
  const location = useLocation();
  const workspace = useWorkspace();
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);
  const brand = usePlatformName();

  if (location.pathname !== '/') {
    return <Navigate to={isAuthenticated ? landingPathFor(user?.role) : '/'} replace />;
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Container maxWidth="lg">
          <Toolbar disableGutters>
            <Stack direction="row" spacing={1} alignItems="center" component={RouterLink} to="/"
              sx={{ textDecoration: 'none', color: 'primary.main', flexGrow: 1 }}>
              {workspace?.branding?.logoUrl
                ? <Box component="img" src={workspace.branding.logoUrl} alt="" sx={{ height: 28 }} />
                : <Hub />}
              <Typography variant="h6" sx={{ fontWeight: 800 }} data-testid="platform-brand">{brand}</Typography>
            </Stack>
            <Button component={RouterLink} to={isAuthenticated ? landingPathFor(user?.role) : '/login'} variant="contained">
              {isAuthenticated ? 'Open console' : 'Staff sign in'}
            </Button>
          </Toolbar>
        </Container>
      </AppBar>

      <Box component="main" sx={{ flexGrow: 1 }}>
        <Outlet />
      </Box>

      <Box component="footer" sx={{ py: 4, borderTop: 1, borderColor: 'divider' }}>
        <Container maxWidth="lg">
          <Typography variant="body2" color="text.secondary">
            © {new Date().getFullYear()} {brand}
          </Typography>
        </Container>
      </Box>
    </Box>
  );
};

export default PlatformPublicLayout;
