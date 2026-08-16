import React, { Suspense, lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { CircularProgress, Box } from '@mui/material';
import { useAppSelector } from './hooks';
// A value import, not the lazy component below: the redirect needs it during render.
import { landingPathFor } from './components/AdminRoute';
// Eagerly imported, unlike the routes: it renders on every page including the first paint, so
// lazy-loading it would only add a second chunk request for something never absent.
import SupportChatWidget from './components/SupportChatWidget';
// Same reasoning as the widget above: opened from a header icon in every portal, so it belongs to
// the shell rather than to any one layout.
import AskAiDrawer from './components/AskAiDrawer';

// Layout components
const MainLayout = lazy(() => import('./layouts/MainLayout'));
const AuthLayout = lazy(() => import('./layouts/AuthLayout'));
const AdminLayout = lazy(() => import('./layouts/AdminLayout'));

// Pages
const HomePage = lazy(() => import('./pages/HomePage'));
const LoginPage = lazy(() => import('./pages/auth/LoginPage'));
const RegisterPage = lazy(() => import('./pages/auth/RegisterPage'));
const OAuth2RedirectPage = lazy(() => import('./pages/auth/OAuth2RedirectPage'));
const DashboardPage = lazy(() => import('./pages/dashboard/DashboardPage'));
const ServicesPage = lazy(() => import('./pages/services/ServicesPage'));
const BookingPage = lazy(() => import('./pages/booking/BookingPage'));
const BookingReviewPage = lazy(() => import('./pages/booking/BookingReviewPage'));
const BookingInvoicePage = lazy(() => import('./pages/booking/BookingInvoicePage'));
const ProfilePage = lazy(() => import('./pages/profile/ProfilePage'));
const MaterialPricesPage = lazy(() => import('./pages/profile/MaterialPricesPage'));
const AppearancePage = lazy(() => import('./pages/settings/AppearancePage'));
const SupportTicketsPage = lazy(() => import('./pages/support/SupportTicketsPage'));
const LiveTrackingPage = lazy(() => import('./pages/tracking/LiveTrackingPage'));

// Admin Pages
const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard'));
const UserManagement = lazy(() => import('./pages/admin/UserManagement'));
const CategoryManagement = lazy(() => import('./pages/admin/CategoryManagement'));
const ServiceCatalogueManagement = lazy(() => import('./pages/admin/ServiceCatalogueManagement'));
const BookingManagement = lazy(() => import('./pages/admin/BookingManagement'));
const AnalyticsPage = lazy(() => import('./pages/admin/AnalyticsPage'));
const RevenuePage = lazy(() => import('./pages/admin/RevenuePage'));
const ThemeSettings = lazy(() => import('./pages/admin/ThemeSettings'));
const SiteContentManagement = lazy(() => import('./pages/admin/SiteContentManagement'));
const AlertsPage = lazy(() => import('./pages/admin/AlertsPage'));
const EmailTemplateManagement = lazy(() => import('./pages/admin/EmailTemplateManagement'));
const EmailLogPage = lazy(() => import('./pages/admin/EmailLogPage'));
const WorkspaceManagement = lazy(() => import('./pages/admin/WorkspaceManagement'));
const ReportsPage = lazy(() => import('./pages/admin/ReportsPage'));
const InvoicesPage = lazy(() => import('./pages/admin/InvoicesPage'));
const PlatformSettingsPage = lazy(() => import('./pages/admin/PlatformSettingsPage'));
const SupportQueuePage = lazy(() => import('./pages/admin/SupportQueuePage'));
const UserActivityPage = lazy(() => import('./pages/admin/UserActivityPage'));
const AdminTrackingPage = lazy(() => import('./pages/admin/AdminTrackingPage'));

/**
 * The landing page is for visitors. Someone already signed in has a workspace, so `/` sends them
 * to it rather than to the marketing page — which is neither themed by their workspace nor
 * reachable from its menu, and so reads as having been signed out.
 */
const HomeOrWorkspace: React.FC = () => {
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);
  if (isAuthenticated) {
    return <Navigate to={landingPathFor(user?.role)} replace />;
  }
  return <HomePage />;
};

// Protected route component
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAppSelector((state) => state.auth);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

const AdminRouteComponent = React.lazy(() => import('./components/AdminRoute'));

const LoadingFallback = () => (
  <Box
    display="flex"
    justifyContent="center"
    alignItems="center"
    minHeight="100vh"
    sx={{
      background: (t) => `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
    }}
  >
    <CircularProgress sx={{ color: '#fff' }} size={48} />
  </Box>
);

const App: React.FC = () => {
  return (
    <>
    <Suspense fallback={<LoadingFallback />}>
      <AnimatePresence mode="wait">
        <Routes>
          {/* Public routes */}
          <Route path="/" element={<MainLayout />}>
            <Route index element={<HomeOrWorkspace />} />
            <Route path="services" element={<ServicesPage />} />
            <Route path="services/:category" element={<ServicesPage />} />
            <Route path="book/:serviceId" element={
              <ProtectedRoute><BookingPage /></ProtectedRoute>
            } />
            {/* Where the completion email's two calls to action land. Protected, so a forwarded
                link cannot let a stranger rate someone else's job or read their invoice. */}
            <Route path="bookings/:bookingId/review" element={
              <ProtectedRoute><BookingReviewPage /></ProtectedRoute>
            } />
            <Route path="bookings/:bookingId/pay" element={
              <ProtectedRoute><BookingInvoicePage /></ProtectedRoute>
            } />
            <Route path="dashboard" element={
              <ProtectedRoute><DashboardPage /></ProtectedRoute>
            } />
            <Route path="profile" element={
              <ProtectedRoute><ProfilePage /></ProtectedRoute>
            } />
            {/* A supplier's own price list. Protected rather than public: the page shows and edits
                the signed-in supplier's rates, and the aggregate ranges are what customers see. */}
            <Route path="material-prices" element={
              <ProtectedRoute><MaterialPricesPage /></ProtectedRoute>
            } />
            <Route path="appearance" element={
              <ProtectedRoute><AppearancePage /></ProtectedRoute>
            } />
            {/* Protected: a ticket belongs to the account that raised it, so there is nothing
                to show a signed-out visitor here. */}
            <Route path="support" element={
              <ProtectedRoute><SupportTicketsPage /></ProtectedRoute>
            } />
            {/* Both forms: the list of what can be tracked, and one booking's live view. */}
            <Route path="track" element={
              <ProtectedRoute><LiveTrackingPage /></ProtectedRoute>
            } />
            <Route path="track/:bookingId" element={
              <ProtectedRoute><LiveTrackingPage /></ProtectedRoute>
            } />
          </Route>

          {/* Auth routes */}
          <Route path="/" element={<AuthLayout />}>
            <Route path="login" element={<LoginPage />} />
            <Route path="register" element={<RegisterPage />} />
            <Route path="login/otp" element={<LoginPage />} />
            {/* Landing point for the OAuth2 success redirect from auth-service */}
            <Route path="oauth2/redirect" element={<OAuth2RedirectPage />} />
          </Route>

          {/* Admin routes */}
          <Route path="/admin" element={
            <AdminRouteComponent><AdminLayout /></AdminRouteComponent>
          }>
            <Route index element={<AdminDashboard />} />
            <Route path="users" element={<UserManagement />} />
            <Route path="activity" element={<UserActivityPage />} />
            <Route path="categories" element={<CategoryManagement />} />
            <Route path="services" element={<ServiceCatalogueManagement />} />
            <Route path="bookings" element={<BookingManagement />} />
            <Route path="analytics" element={<AnalyticsPage />} />
            <Route path="revenue" element={<RevenuePage />} />
            <Route path="workspaces" element={<WorkspaceManagement />} />
            <Route path="theme" element={<ThemeSettings />} />
            <Route path="content" element={<SiteContentManagement />} />
            <Route path="alerts" element={<AlertsPage />} />
            <Route path="email-templates" element={<EmailTemplateManagement />} />
            <Route path="emails" element={<EmailLogPage />} />
            <Route path="reports" element={<ReportsPage />} />
            <Route path="invoices" element={<InvoicesPage />} />
            <Route path="support" element={<SupportQueuePage />} />
            <Route path="tracking" element={<AdminTrackingPage />} />
            <Route path="settings" element={<PlatformSettingsPage />} />
          </Route>

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AnimatePresence>
    </Suspense>
    {/*
      Outside the router, not inside a layout: it is meant to be reachable from every page,
      and the three layouts (main, auth, admin) would each need their own copy otherwise —
      three chances for them to drift apart. Outside <Suspense> too, so a route still loading
      never takes the assistant off screen with it.
    */}
    <SupportChatWidget />
    <AskAiDrawer />
    </>
  );
};

export default App;
