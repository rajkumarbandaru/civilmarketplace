import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControlLabel, FormGroup, IconButton, MenuItem, Stack, TextField, Typography,
} from '@mui/material';
import { Delete as DeleteIcon } from '@mui/icons-material';
import { apiErrorMessage } from '../../services/apiError';
import {
  CAPABILITY_LABELS, Capability, MemberRole, Organization, OrganizationInput, RelationshipType,
  addMember, createOrganization, fetchDirectory, fetchOrganization, formatMoney, removeMember,
  removeRelationship, setRelationship, updateOrganization,
} from '../../services/procurementApi';

const CAPABILITIES = Object.keys(CAPABILITY_LABELS) as Capability[];

/** Name, GSTIN, capabilities and approval threshold: used to create and to edit. */
export const OrganizationForm: React.FC<{
  initial?: Organization;
  submitLabel: string;
  pending: boolean;
  error?: unknown;
  onSubmit: (input: OrganizationInput) => void;
  onCancel?: () => void;
}> = ({ initial, submitLabel, pending, error, onSubmit, onCancel }) => {
  const [name, setName] = useState(initial?.name ?? '');
  const [gstin, setGstin] = useState(initial?.gstin ?? '');
  const [caps, setCaps] = useState<Capability[]>(initial?.capabilities ?? ['BUYER']);
  const [threshold, setThreshold] = useState(initial?.approvalThreshold?.toString() ?? '');
  const gstinOk = gstin === '' || /^[0-9A-Z]{15}$/.test(gstin);
  return (
    <Stack spacing={2}>
      <TextField label="Organization name" value={name} onChange={(e) => setName(e.target.value)} required
        inputProps={{ maxLength: 160 }} />
      <TextField label="GSTIN" value={gstin} onChange={(e) => setGstin(e.target.value.toUpperCase())}
        error={!gstinOk} helperText={gstinOk ? 'Optional' : '15 letters and digits'} inputProps={{ maxLength: 15 }} />
      <Box>
        <Typography variant="subtitle2">What it does</Typography>
        <FormGroup row>
          {CAPABILITIES.map((c) => (
            <FormControlLabel key={c} label={CAPABILITY_LABELS[c]} control={
              <Checkbox checked={caps.includes(c)}
                onChange={(e) => setCaps(e.target.checked ? [...caps, c] : caps.filter((x) => x !== c))} />
            } />
          ))}
        </FormGroup>
        <Typography variant="caption" color="text.secondary">
          A contractor that buys materials is both Contractor and Buyer.
        </Typography>
      </Box>
      <TextField label="Orders above this need a second approver (₹)" type="number" value={threshold}
        onChange={(e) => setThreshold(e.target.value)} helperText="Leave empty for the workspace default"
        inputProps={{ min: 0 }} />
      {error != null && <Alert severity="error">{apiErrorMessage(error, 'That did not save.')}</Alert>}
      <Stack direction="row" spacing={1} justifyContent="flex-end">
        {onCancel && <Button color="inherit" onClick={onCancel}>Cancel</Button>}
        <Button variant="contained" disabled={!name.trim() || caps.length === 0 || !gstinOk || pending}
          onClick={() => onSubmit({
            name: name.trim(), gstin: gstin || undefined, capabilities: caps,
            approvalThreshold: threshold === '' ? null : Number(threshold),
          })}>
          {submitLabel}
        </Button>
      </Stack>
    </Stack>
  );
};

const ROLE_HELP: Record<MemberRole, string> = {
  OWNER: 'Manages the organization and its people',
  APPROVER: 'Approves orders and invoices',
  MEMBER: 'Raises RFQs, quotes and receives goods',
};

/** People, and whom the organization prefers or refuses to trade with. */
const ManageDialog: React.FC<{ org: Organization; onClose: () => void }> = ({ org, onClose }) => {
  const queryClient = useQueryClient();
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<MemberRole>('MEMBER');
  const [target, setTarget] = useState('');
  const [relType, setRelType] = useState<RelationshipType>('PREFERRED_SUPPLIER');
  const [editing, setEditing] = useState(false);
  const owner = org.myRole === 'OWNER';
  const canRelate = org.myRole !== 'MEMBER';

  const detail = useQuery({ queryKey: ['procurement-org', org.id], queryFn: () => fetchOrganization(org.id) });
  const others = useQuery({
    queryKey: ['procurement-directory', 'all', org.id],
    queryFn: () => fetchDirectory(undefined, org.id),
    enabled: canRelate,
  });
  const refresh = (d: unknown) => {
    queryClient.setQueryData(['procurement-org', org.id], d);
    queryClient.invalidateQueries({ queryKey: ['procurement-orgs'] });
    queryClient.invalidateQueries({ queryKey: ['procurement-directory'] });
  };
  const add = useMutation({ mutationFn: () => addMember(org.id, email.trim(), role), onSuccess: (d) => { setEmail(''); refresh(d); } });
  const remove = useMutation({ mutationFn: (id: number) => removeMember(org.id, id), onSuccess: refresh });
  const relate = useMutation({ mutationFn: () => setRelationship(org.id, Number(target), relType), onSuccess: (d) => { setTarget(''); refresh(d); } });
  const unrelate = useMutation({ mutationFn: (id: number) => removeRelationship(org.id, id), onSuccess: refresh });
  const update = useMutation({
    mutationFn: (input: OrganizationInput) => updateOrganization(org.id, input),
    onSuccess: (d) => { setEditing(false); refresh(d); },
  });
  const error = add.error || remove.error || relate.error || unrelate.error;

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{org.name}</DialogTitle>
      <DialogContent>
        {editing ? (
          <Box sx={{ mt: 1 }}>
            <OrganizationForm initial={detail.data?.organization ?? org} submitLabel="Save" pending={update.isPending}
              error={update.error} onSubmit={(i) => update.mutate(i)} onCancel={() => setEditing(false)} />
          </Box>
        ) : owner && (
          <Button size="small" onClick={() => setEditing(true)} sx={{ mb: 1 }}>Edit details</Button>
        )}

        <Typography variant="subtitle1" sx={{ mt: 1 }}>People</Typography>
        <Stack spacing={0.5} sx={{ mb: 2 }} data-testid="org-members">
          {detail.data?.members.map((m) => (
            <Stack key={m.id} direction="row" alignItems="center" spacing={1}>
              <Typography variant="body2" sx={{ flex: 1 }}>{m.email}</Typography>
              {!m.joined && <Chip size="small" variant="outlined" label="Not signed in yet" />}
              <Chip size="small" label={m.role} title={ROLE_HELP[m.role]} />
              {owner && (
                <IconButton size="small" aria-label={`Remove ${m.email}`} onClick={() => remove.mutate(m.id)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              )}
            </Stack>
          ))}
        </Stack>
        {owner && (
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 3 }}>
            <TextField size="small" label="Email" value={email} onChange={(e) => setEmail(e.target.value)} sx={{ flex: 1 }} />
            <TextField size="small" select label="Role" value={role} onChange={(e) => setRole(e.target.value as MemberRole)}
              sx={{ minWidth: 130 }}>
              {(['MEMBER', 'APPROVER', 'OWNER'] as MemberRole[]).map((r) => <MenuItem key={r} value={r}>{r}</MenuItem>)}
            </TextField>
            <Button variant="outlined" disabled={!/.+@.+\..+/.test(email) || add.isPending} onClick={() => add.mutate()}>
              Add
            </Button>
          </Stack>
        )}

        <Typography variant="subtitle1">Trading relationships</Typography>
        <Stack spacing={0.5} sx={{ mb: 1 }} data-testid="org-relationships">
          {detail.data?.relationships.length === 0 && (
            <Typography variant="body2" color="text.secondary">None yet.</Typography>
          )}
          {detail.data?.relationships.map((r) => (
            <Stack key={r.id} direction="row" alignItems="center" spacing={1}>
              <Typography variant="body2" sx={{ flex: 1 }}>{r.targetOrgName}</Typography>
              <Chip size="small" color={r.type === 'BLOCKED' ? 'error' : 'success'}
                label={r.type === 'BLOCKED' ? 'Blocked' : 'Preferred supplier'} />
              {canRelate && (
                <IconButton size="small" aria-label={`Remove relationship with ${r.targetOrgName}`}
                  onClick={() => unrelate.mutate(r.id)}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              )}
            </Stack>
          ))}
        </Stack>
        {canRelate && (
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <TextField size="small" select label="Organization" value={target} onChange={(e) => setTarget(e.target.value)}
              sx={{ flex: 1 }}>
              {others.data?.map((o) => <MenuItem key={o.id} value={String(o.id)}>{o.name}</MenuItem>)}
            </TextField>
            <TextField size="small" select label="Mark as" value={relType}
              onChange={(e) => setRelType(e.target.value as RelationshipType)} sx={{ minWidth: 170 }}>
              <MenuItem value="PREFERRED_SUPPLIER">Preferred supplier</MenuItem>
              <MenuItem value="BLOCKED">Blocked</MenuItem>
            </TextField>
            <Button variant="outlined" disabled={!target || relate.isPending} onClick={() => relate.mutate()}>Save</Button>
          </Stack>
        )}
        {error && <Alert severity="error" sx={{ mt: 2 }}>{apiErrorMessage(error, 'That did not work.')}</Alert>}
      </DialogContent>
      <DialogActions><Button onClick={onClose}>Done</Button></DialogActions>
    </Dialog>
  );
};

/** The organizations the signed-in person acts for, and registering a new one. */
const OrganizationsPanel: React.FC<{ organizations: Organization[] }> = ({ organizations }) => {
  const queryClient = useQueryClient();
  const [creating, setCreating] = useState(false);
  const [managing, setManaging] = useState<Organization | null>(null);
  const create = useMutation({
    mutationFn: createOrganization,
    onSuccess: () => { setCreating(false); queryClient.invalidateQueries({ queryKey: ['procurement-orgs'] }); },
  });

  return (
    <Box>
      <Stack direction="row" justifyContent="flex-end" sx={{ mb: 2 }}>
        <Button variant="outlined" onClick={() => setCreating(true)}>New organization</Button>
      </Stack>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: '1fr 1fr' } }}>
        {organizations.map((o) => (
          <Card key={o.id} data-testid={`org-${o.id}`}>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                <Box>
                  <Typography variant="h6">{o.name}</Typography>
                  {o.gstin && <Typography variant="caption" color="text.secondary">GSTIN {o.gstin}</Typography>}
                </Box>
                <Chip size="small" label={o.myRole} />
              </Stack>
              <Stack direction="row" spacing={0.5} sx={{ my: 1 }} useFlexGap flexWrap="wrap">
                {o.capabilities.map((c) => <Chip key={c} size="small" variant="outlined" label={CAPABILITY_LABELS[c]} />)}
              </Stack>
              <Typography variant="body2" color="text.secondary">
                Orders above {formatMoney(o.effectiveApprovalThreshold)} need a second approver.
              </Typography>
              <Button size="small" sx={{ mt: 1 }} onClick={() => setManaging(o)}>Manage</Button>
            </CardContent>
          </Card>
        ))}
      </Box>
      <Dialog open={creating} onClose={() => setCreating(false)} fullWidth maxWidth="sm">
        <DialogTitle>New organization</DialogTitle>
        <DialogContent>
          <Box sx={{ mt: 1 }}>
            <OrganizationForm submitLabel="Create" pending={create.isPending} error={create.error}
              onSubmit={(i) => create.mutate(i)} onCancel={() => setCreating(false)} />
          </Box>
        </DialogContent>
      </Dialog>
      {managing && <ManageDialog org={managing} onClose={() => setManaging(null)} />}
    </Box>
  );
};

export default OrganizationsPanel;
