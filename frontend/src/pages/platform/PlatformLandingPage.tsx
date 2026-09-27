import React from 'react';
import { Link as RouterLink, Navigate } from 'react-router-dom';
import {
  Box, Button, Card, CardContent, Chip, Container, Grid, Stack, Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import {
  AccountTree, AddBusiness, ArrowForward, Business, Dns, Hub, Insights, Lock, Payments, WorkspacePremium,
} from '@mui/icons-material';
import { useAppSelector } from '../../hooks';
import { landingPathFor } from '../../components/AdminRoute';
import { usePlatformName } from '../../hooks/usePlatformHost';

const SERVICES = [
  { icon: <Hub />, title: 'One platform', text: 'Sign-in, payments, messaging, notifications, search and analytics built once, shared by every business we run.' },
  { icon: <AddBusiness />, title: 'Launch in minutes', text: 'A new business is drafted, provisioned and published from the platform factory — its own brand, staff and customers from day one.' },
  { icon: <Lock />, title: 'Run securely', text: 'Every business is isolated from every other, with its own data, its own accounts and its own keys.' },
];

const CAPABILITIES = [
  { icon: <AddBusiness />, title: 'Platform factory', text: 'Draft, provision and publish a new business in minutes — every service builds its storage and reports back.' },
  { icon: <Lock />, title: 'Isolation', text: 'A schema per business in every service, signed identity between services, sessions bound to their business.' },
  { icon: <WorkspacePremium />, title: 'Plans & entitlements', text: 'Each business runs the features its plan entitles it to, with time-limited grants for exceptions.' },
  { icon: <Payments />, title: 'Own provider accounts', text: 'Payment gateway, SMS, WhatsApp, email and AI model — each business can bring its own.' },
  { icon: <Dns />, title: 'Custom domains', text: 'Businesses on their own domains, with certificates issued and renewed automatically.' },
  { icon: <Insights />, title: 'Analytics', text: 'Figures across every business from a warehouse kept current by change-data capture.' },
];

/** One box of the platform → factory → tenants diagram. */
const Node: React.FC<{ title: string; subtitle: string; icon: React.ReactNode; strong?: boolean; testId?: string }> = ({
  title, subtitle, icon, strong, testId,
}) => {
  const theme = useTheme();
  return (
    <Box data-testid={testId} sx={{
      px: 2.5, py: 1.5, borderRadius: 2, textAlign: 'center', minWidth: 180,
      bgcolor: strong ? 'primary.main' : 'background.paper',
      color: strong ? 'primary.contrastText' : 'text.primary',
      border: `1px solid ${strong ? theme.palette.primary.main : alpha(theme.palette.primary.main, 0.3)}`,
      boxShadow: strong ? `0 8px 24px ${alpha(theme.palette.primary.main, 0.3)}` : 'none',
    }}>
      <Stack direction="row" spacing={1} alignItems="center" justifyContent="center">
        {icon}
        <Typography sx={{ fontWeight: 700 }}>{title}</Typography>
      </Stack>
      <Typography variant="caption" sx={{ opacity: 0.8 }}>{subtitle}</Typography>
    </Box>
  );
};

const Connector: React.FC = () => (
  <Box sx={{ width: 2, height: 28, bgcolor: 'divider', mx: 'auto' }} />
);

/**
 * The platform company's public page, on its own address. It is about the company alone — no
 * tenant is named here — and its name is the platform tenant's published brand, so renaming the
 * company never touches this file.
 */
const PlatformLandingPage: React.FC = () => {
  const theme = useTheme();
  const name = usePlatformName();
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);
  if (isAuthenticated) {
    return <Navigate to={landingPathFor(user?.role)} replace />;
  }

  return (
    <Box data-testid="platform-landing">
      {/* Hero */}
      <Box sx={{
        py: { xs: 8, md: 12 },
        color: '#fff',
        background: `linear-gradient(135deg, ${theme.palette.primary.dark} 0%, ${theme.palette.primary.main} 55%, ${theme.palette.secondary.main} 100%)`,
      }}>
        <Container maxWidth="lg">
          <Chip label="Technology / Platform company" sx={{ bgcolor: 'rgba(255,255,255,0.15)', color: '#fff', mb: 3 }} />
          <Typography variant="h2" data-testid="platform-name"
            sx={{ fontWeight: 800, fontSize: { xs: '2.2rem', md: '3.4rem' }, maxWidth: 820 }}>
            {name}
          </Typography>
          <Typography variant="h5" sx={{ mt: 2, opacity: 0.9, maxWidth: 760, fontWeight: 400 }}>
            We build and run digital businesses on one secure, multi-tenant platform.
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mt: 5 }}>
            <Button component={RouterLink} to="/login" size="large" variant="contained" endIcon={<ArrowForward />}
              // Explicit background image: the theme's button style paints a gradient over bgcolor.
              sx={{ background: '#fff', backgroundImage: 'none', color: 'primary.main', fontWeight: 700,
                '&:hover': { background: 'rgba(255,255,255,0.9)', backgroundImage: 'none' } }}>
              Staff sign in
            </Button>
            <Button href="#what-we-do" size="large" variant="outlined"
              sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.6)', '&:hover': { borderColor: '#fff' } }}>
              What we do
            </Button>
          </Stack>
        </Container>
      </Box>

      {/* What the company does */}
      <Container maxWidth="lg" id="what-we-do" sx={{ py: { xs: 6, md: 10 } }}>
        <Typography variant="h4" sx={{ fontWeight: 800, textAlign: 'center' }}>What we do</Typography>
        <Typography color="text.secondary" sx={{ textAlign: 'center', mt: 1, mb: 5 }}>
          {name} designs, launches and operates businesses on its own platform.
        </Typography>
        <Grid container spacing={3} sx={{ mb: 8 }}>
          {SERVICES.map((c) => (
            <Grid item xs={12} md={4} key={c.title}>
              <Card sx={{ height: '100%', borderRadius: 3, borderTop: `4px solid ${theme.palette.primary.main}` }}>
                <CardContent>
                  <Box sx={{ color: 'primary.main', mb: 1 }}>{c.icon}</Box>
                  <Typography variant="h6" sx={{ fontWeight: 700 }}>{c.title}</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>{c.text}</Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>

        <Box data-testid="platform-diagram">
          <Typography variant="h5" sx={{ fontWeight: 800, textAlign: 'center', mb: 4 }}>How the platform works</Typography>
          <Stack alignItems="center">
            <Node strong title={name} subtitle="Technology / platform company" icon={<Hub />} />
            <Connector />
            <Node title="Multi-tenant platform" subtitle="Auth, payments, messaging, analytics…" icon={<AccountTree />} />
            <Connector />
            <Node title="Platform factory" subtitle="Creates and runs each business" icon={<AddBusiness />} />
            <Connector />
            <Node title="Businesses" subtitle="Each with its own brand, staff and customers" icon={<Business />} />
          </Stack>
        </Box>
      </Container>

      {/* Platform capabilities */}
      <Box sx={{ bgcolor: alpha(theme.palette.primary.main, 0.04), py: { xs: 6, md: 10 } }}>
        <Container maxWidth="lg">
          <Typography variant="h4" sx={{ fontWeight: 800, textAlign: 'center', mb: 5 }}>Platform capabilities</Typography>
          <Grid container spacing={3}>
            {CAPABILITIES.map((c) => (
              <Grid item xs={12} sm={6} md={4} key={c.title}>
                <Stack direction="row" spacing={2}>
                  <Box sx={{ color: 'primary.main', mt: 0.3 }}>{c.icon}</Box>
                  <Box>
                    <Typography sx={{ fontWeight: 700 }}>{c.title}</Typography>
                    <Typography variant="body2" color="text.secondary">{c.text}</Typography>
                  </Box>
                </Stack>
              </Grid>
            ))}
          </Grid>
        </Container>
      </Box>
    </Box>
  );
};

export default PlatformLandingPage;
