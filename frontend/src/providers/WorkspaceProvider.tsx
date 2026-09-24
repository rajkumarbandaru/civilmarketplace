import React, { createContext, useContext, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Box, Typography } from '@mui/material';
import { DomainDisabled } from '@mui/icons-material';
import { Workspace, lookupWorkspace } from '../services/workspaceApi';

const WorkspaceContext = createContext<Workspace | null>(null);

/** The workspace this app was opened on, or null while unknown (then the platform defaults apply). */
export const useWorkspace = () => useContext(WorkspaceContext);

/**
 * Loads which workspace this address is before anything else, so the sign-in screen can carry the
 * tenant's name and logo — there is no user, and so no /ui-config, yet. An address that serves no
 * workspace, or one that is suspended, gets a page that says so instead of an app whose every call
 * fails.
 */
const WorkspaceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { data } = useQuery({ queryKey: ['workspace'], queryFn: lookupWorkspace, staleTime: Infinity, retry: false });
  const workspace = data?.kind === 'ok' ? data.workspace : null;

  useEffect(() => {
    if (workspace?.name) document.title = workspace.branding?.brandName || workspace.name;
  }, [workspace]);

  if (data?.kind === 'unknown' || data?.kind === 'unavailable') {
    return (
      <Box data-testid="workspace-unavailable" sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', gap: 2, p: 3, textAlign: 'center' }}>
        <DomainDisabled sx={{ fontSize: 56, color: 'text.secondary' }} />
        <Typography variant="h5" sx={{ fontWeight: 700 }}>
          {data.kind === 'unknown' ? 'No workspace at this address' : 'This workspace is unavailable'}
        </Typography>
        <Typography color="text.secondary" sx={{ maxWidth: 420 }}>
          {data.kind === 'unknown'
            ? 'Check the address you were given. If it is right, the workspace may have moved.'
            : 'It has been suspended or closed. Contact the organisation that runs it.'}
        </Typography>
      </Box>
    );
  }

  return <WorkspaceContext.Provider value={workspace}>{children}</WorkspaceContext.Provider>;
};

export default WorkspaceProvider;
