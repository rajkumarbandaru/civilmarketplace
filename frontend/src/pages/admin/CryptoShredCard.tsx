import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Button, Card, CardContent, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, TextField,
  Typography,
} from '@mui/material';
import { useDateTime } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import { Tenant, cryptoShred } from '../../services/tenantApi';

/**
 * For an archived tenant: destroy its encryption keys in the secrets broker, making every provider
 * credential sealed under them unreadable for good — in the database and in every backup.
 */
const CryptoShredCard: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const queryClient = useQueryClient();
  const { formatDateTime } = useDateTime();
  const [open, setOpen] = useState(false);
  const [confirm, setConfirm] = useState('');
  const shred = useMutation({
    mutationFn: () => cryptoShred(tenant.tenantKey, confirm),
    onSuccess: () => {
      setOpen(false);
      queryClient.invalidateQueries({ queryKey: ['tenants'] });
      queryClient.invalidateQueries({ queryKey: ['tenants', tenant.tenantKey] });
    },
  });
  if (tenant.status !== 'ARCHIVED' && !tenant.keysDestroyedAt) return null;
  const destroyedAt = tenant.keysDestroyedAt ?? shred.data?.destroyedAt;

  return (
    <Card sx={{ mb: 3 }} data-testid="crypto-shred-card">
      <CardContent>
        <Typography variant="h6" gutterBottom>Encryption keys</Typography>
        {destroyedAt ? (
          <Alert severity="info" data-testid="keys-destroyed">
            Destroyed {formatDateTime(destroyedAt)}
            {shred.data && ` (${shred.data.keysDestroyed} key${shred.data.keysDestroyed === 1 ? '' : 's'})`}. This tenant's stored
            provider credentials can no longer be read by anyone, including from backups.
          </Alert>
        ) : (
          <>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              This tenant is archived. Destroying its keys in the secrets broker makes every provider credential it stored
              unreadable, permanently — the database rows and every backup of them included.
            </Typography>
            <Button color="error" variant="outlined" onClick={() => setOpen(true)}>Destroy encryption keys</Button>
          </>
        )}
      </CardContent>
      <Dialog open={open} onClose={() => setOpen(false)}>
        <DialogTitle>Destroy {tenant.name}'s encryption keys?</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            This cannot be undone. Type <strong>{tenant.tenantKey}</strong> to confirm.
          </DialogContentText>
          <TextField fullWidth size="small" label="Tenant key" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          {shred.isError && <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(shred.error, 'The keys were not destroyed.')}</Alert>}
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setOpen(false)}>Cancel</Button>
          <Button color="error" variant="contained" disabled={confirm !== tenant.tenantKey || shred.isPending}
            onClick={() => shred.mutate()}>Destroy keys</Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
};

export default CryptoShredCard;
