import React from 'react';
import { Chip } from '@mui/material';
import { AdminPanelSettings, Storefront } from '@mui/icons-material';
import { useAppSelector } from '../hooks';
import { useWorkspace } from '../providers/WorkspaceProvider';
import { isPlatformOperator, roleLabel } from '../utils/roles';
import { usePlatformName } from '../hooks/usePlatformHost';

/**
 * Which tier of the platform this console is: the platform company's own console, or one tenant's own
 * admin. The two used to look identical — one "Super Admin" ran both — so the header says which
 * one the signed-in person is standing in, and as which role.
 */
const ConsoleTierBadge: React.FC = () => {
  const role = useAppSelector((state) => state.auth.user?.role);
  const workspace = useWorkspace();
  const platformName = usePlatformName();
  if (!role) return null;

  const platform = isPlatformOperator(role, workspace?.tenantKey);
  const where = platform
    ? `${platformName} · Platform console`
    : `${workspace?.branding?.brandName || workspace?.name || 'Tenant'} · Tenant admin`;

  return (
    <Chip
      data-testid="console-tier"
      data-tier={platform ? 'platform' : 'tenant'}
      size="small"
      color={platform ? 'secondary' : 'primary'}
      variant="outlined"
      icon={platform ? <AdminPanelSettings /> : <Storefront />}
      label={`${where} — ${roleLabel(role)}`}
      sx={{ mr: 1.5, maxWidth: { xs: 180, md: 'none' }, display: { xs: 'none', sm: 'inline-flex' } }}
    />
  );
};

export default ConsoleTierBadge;
