import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAppSelector } from '../hooks';

const ADMIN_ROLES = ['SUPER_ADMIN', 'ADMIN', 'SUB_ADMIN'];

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
