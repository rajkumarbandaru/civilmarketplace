import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAppSelector } from '../hooks';

export const ADMIN_ROLES = ['SUPER_ADMIN', 'ADMIN', 'SUB_ADMIN'];

/**
 * Where a role belongs after signing in. An admin sent to /dashboard lands in the member shell —
 * the marketplace's own navigation, with no console in sight — and has to know to type /admin to
 * find their tools, so the landing route follows the role rather than being one constant.
 */
export const landingPathFor = (role?: string | null): string =>
  role && ADMIN_ROLES.includes(role) ? '/admin' : '/dashboard';

const AdminRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (!user?.role || !ADMIN_ROLES.includes(user.role)) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
};

export default AdminRoute;
