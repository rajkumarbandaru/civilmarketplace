import React, { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Alert, Box, Button, Card, CardContent, CircularProgress, TextField, Typography } from '@mui/material';
import api from '../../services/api';
import { apiErrorMessage } from '../../services/apiError';
import { useWorkspace } from '../../providers/WorkspaceProvider';

const MIN_LENGTH = 12;

/**
 * Where a new workspace's owner lands from their invitation email: they choose their own password
 * (nobody else ever knew one), then sign in — and set up an authenticator app on that first
 * sign-in, which owner accounts require.
 */
const InviteAcceptPage: React.FC = () => {
  const { token = '' } = useParams();
  const workspace = useWorkspace();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');

  const invitation = useQuery({
    queryKey: ['invitation', token],
    queryFn: async () => (await api.get<{ email: string; name: string; expiresAt: string }>(`/auth/invitations/${token}`)).data,
    retry: false,
  });
  const accept = useMutation({
    mutationFn: async () => (await api.post(`/auth/invitations/${token}/accept`, { password })).data,
  });

  const tooShort = password.length > 0 && password.length < MIN_LENGTH;
  const mismatch = confirm.length > 0 && confirm !== password;
  const workspaceName = workspace?.branding?.brandName || workspace?.name;

  return (
    <Card sx={{ borderRadius: 4, maxWidth: 480, mx: 'auto' }} data-testid="invite-accept">
      <CardContent sx={{ p: 4 }}>
        <Typography variant="h5" sx={{ fontWeight: 700, mb: 1 }}>
          {workspaceName ? `Welcome to ${workspaceName}` : 'Set your password'}
        </Typography>

        {invitation.isLoading && <CircularProgress size={24} />}
        {invitation.isError && (
          <Alert severity="error">{apiErrorMessage(invitation.error, 'This invitation link is not valid.')}</Alert>
        )}

        {accept.isSuccess ? (
          <Box>
            <Alert severity="success" sx={{ mb: 2 }}>
              Your password is set. Sign in now — you will be asked to set up an authenticator app.
            </Alert>
            <Button component={RouterLink} to="/login" variant="contained" fullWidth>Sign in</Button>
          </Box>
        ) : invitation.data && (
          <Box component="form" onSubmit={(e) => { e.preventDefault(); accept.mutate(); }}>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
              Choose a password for <strong>{invitation.data.email}</strong>. This link works once.
            </Typography>
            {accept.isError && (
              <Alert severity="error" sx={{ mb: 2 }}>{apiErrorMessage(accept.error, 'The password could not be set.')}</Alert>
            )}
            <TextField fullWidth type="password" label="Password" value={password} autoComplete="new-password"
              onChange={(e) => setPassword(e.target.value)} sx={{ mb: 2 }} error={tooShort}
              helperText={`At least ${MIN_LENGTH} characters. A few unrelated words make a strong one.`} />
            <TextField fullWidth type="password" label="Confirm password" value={confirm} autoComplete="new-password"
              onChange={(e) => setConfirm(e.target.value)} sx={{ mb: 3 }} error={mismatch}
              helperText={mismatch ? 'The passwords do not match.' : ' '} />
            <Button type="submit" variant="contained" fullWidth size="large"
              disabled={password.length < MIN_LENGTH || confirm !== password || accept.isPending}>
              {accept.isPending ? 'Saving…' : 'Set password'}
            </Button>
          </Box>
        )}
      </CardContent>
    </Card>
  );
};

export default InviteAcceptPage;
