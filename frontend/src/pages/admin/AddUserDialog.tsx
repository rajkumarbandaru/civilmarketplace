import React, { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, MenuItem, Stack, TextField,
} from '@mui/material';
import { userApi, InvitedUser } from '../../services/adminApi';
import { apiErrorMessage } from '../../services/apiError';
import { useWorkspace } from '../../providers/WorkspaceProvider';
import { useAppSelector } from '../../hooks';

const EMAIL = /^\S+@\S+\.\S+$/;

/** "SITE_ENGINEER" → "Site engineer". */
export const roleLabel = (role: string) =>
  role.charAt(0) + role.slice(1).toLowerCase().replace(/_/g, ' ');

/**
 * Adds someone to this workspace with a role. No password is set here: they are emailed a link to
 * set their own, the same way a new workspace's owner is. Only a Super Admin is offered the Super
 * Admin role (the server enforces it too).
 */
const AddUserDialog: React.FC<{ open: boolean; onClose: () => void; onAdded: (user: InvitedUser) => void }> = ({
  open, onClose, onAdded,
}) => {
  const workspace = useWorkspace();
  const actorRole = useAppSelector((state) => state.auth.user?.role);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState('');
  const [touched, setTouched] = useState(false);

  const roles = useQuery({
    queryKey: ['assignable-roles'],
    queryFn: async () => (await userApi.getRoles()).data.data,
    enabled: open,
    retry: false,
  });
  const options = (roles.data ?? []).filter((r) => r.name !== 'SUPER_ADMIN' || actorRole === 'SUPER_ADMIN');

  const invite = useMutation({
    mutationFn: async () => (await userApi.inviteUser({
      name: name.trim(),
      email: email.trim(),
      role,
      linkBase: window.location.origin,
      workspaceName: workspace?.branding?.brandName || workspace?.name || 'your workspace',
    })).data.data,
    onSuccess: (user) => {
      setName(''); setEmail(''); setRole(''); setTouched(false);
      onAdded(user);
    },
  });

  const emailError = touched && !EMAIL.test(email.trim());
  const roleError = touched && !role;

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>Add a user</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          They get an email with a link to set their own password. The link works once, for 72 hours.
        </DialogContentText>
        {(invite.isError || roles.isError) && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(invite.error || roles.error, 'The user could not be added.')}
          </Alert>
        )}
        <Stack spacing={2}>
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} inputProps={{ maxLength: 100 }} />
          <TextField label="Email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)}
            error={emailError} helperText={emailError ? 'Enter a valid email address' : undefined} />
          <TextField select label="Role" required value={role} data-testid="role-select" onChange={(e) => setRole(e.target.value)}
            error={roleError} helperText={roleError ? 'Choose a role' : 'What they can see and do.'}
            SelectProps={{ inputProps: { 'aria-label': 'Role' } }}>
            {options.map((r) => <MenuItem key={r.name} value={r.name}>{roleLabel(r.name)}</MenuItem>)}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button variant="contained" disabled={invite.isPending}
          onClick={() => {
            setTouched(true);
            if (EMAIL.test(email.trim()) && role) invite.mutate();
          }}>
          {invite.isPending ? 'Adding…' : 'Add and invite'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default AddUserDialog;
