import React, { Suspense, lazy } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AnimatePresence } from 'framer-motion';
import { CircularProgress, Box } from '@mui/material';
import { useAppSelector } from './hooks';

// Layout components
const MainLayout = lazy(() => import('./layouts/MainLayout'));
const AuthLayout = lazy(() => import('./layouts/AuthLayout'));
const AdminLayout = lazy(() => import('./layouts/AdminLayout'));

// Pages
const HomePage = lazy(() => import('./pages/HomePage'));
const LoginPage = lazy(() => import('./pages/auth/LoginPage'));
const RegisterPage = lazy(() => import('./pages/auth/RegisterPage'));
const DashboardPage = lazy(() => import('./pages/dashboard/DashboardPage'));
const ServicesPage = lazy(() => import('./pages/services/ServicesPage'));
const BookingPage = lazy(() => import('./pages/booking/BookingPage'));
const ProfilePage = lazy(() => import('./pages/profile/ProfilePage'));
const AppearancePage = lazy(() => import('./pages/settings/AppearancePage'));

// Admin Pages
const AdminDashboard = lazy(() => import('./pages/admin/AdminDashboard'));
const UserManagement = lazy(() => import('./pages/admin/UserManagement'));
const CategoryManagement = lazy(() => import('./pages/admin/CategoryManagement'));
const BookingManagement = lazy(() => import('./pages/admin/BookingManagement'));
const AnalyticsPage = lazy(() => import('./pages/admin/AnalyticsPage'));
const RevenuePage = lazy(() => import('./pages/admin/RevenuePage'));
const ThemeSettings = lazy(() => import('./pages/admin/ThemeSettings'));
const WorkspaceManagement = lazy(() => import('./pages/admin/WorkspaceManagement'));

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
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}
  >
    <CircularProgress sx={{ color: '#fff' }} size={48} />
  </Box>
);

const App: React.FC = () => {
  return (
    <Suspense fallback={<LoadingFallback />}>
      <AnimatePresence mode="wait">
        <Routes>
          {/* Public routes */}
          <Route path="/" element={<MainLayout />}>
            <Route index element={<HomePage />} />
            <Route path="services" element={<ServicesPage />} />
            <Route path="services/:category" element={<ServicesPage />} />
            <Route path="book/:serviceId" element={
              <ProtectedRoute><BookingPage /></ProtectedRoute>
            } />
            <Route path="dashboard" element={
              <ProtectedRoute><DashboardPage /></ProtectedRoute>
            } />
            <Route path="profile" element={
              <ProtectedRoute><ProfilePage /></ProtectedRoute>
            } />
            <Route path="appearance" element={
              <ProtectedRoute><AppearancePage /></ProtectedRoute>
            } />
          </Route>

          {/* Auth routes */}
          <Route path="/" element={<AuthLayout />}>
            <Route path="login" element={<LoginPage />} />
            <Route path="register" element={<RegisterPage />} />
            <Route path="login/otp" element={<LoginPage />} />
          </Route>

          {/* Admin routes */}
          <Route path="/admin" element={
            <AdminRouteComponent><AdminLayout /></AdminRouteComponent>
          }>
            <Route index element={<AdminDashboard />} />
            <Route path="users" element={<UserManagement />} />
            <Route path="categories" element={<CategoryManagement />} />
            <Route path="bookings" element={<BookingManagement />} />
            <Route path="analytics" element={<AnalyticsPage />} />
            <Route path="revenue" element={<RevenuePage />} />
            <Route path="workspaces" element={<WorkspaceManagement />} />
            <Route path="theme" element={<ThemeSettings />} />
            <Route path="reports" element={<RevenuePage />} />
            <Route path="invoices" element={<RevenuePage />} />
            <Route path="settings" element={<AdminDashboard />} />
          </Route>

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AnimatePresence>
    </Suspense>
  );
};

export default App;
