import React, { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, Box, Button, Card, CardContent, Chip, Dialog, DialogActions, DialogContent, DialogContentText,
  DialogTitle, LinearProgress, Stack, Step, StepLabel, Stepper, Typography,
} from '@mui/material';
import { useDateTime } from '../../providers/UiConfigProvider';
import { apiErrorMessage } from '../../services/apiError';
import {
  ProvisioningProgress, Tenant, TenantDraft, discardDraft, discardTenant, fetchDrafts, fetchProvisioning,
  publishTenant, resendOwnerInvitation,
} from '../../services/tenantApi';

/** Wizard drafts nobody has finished: continue one, or throw it away. */
export const DraftList: React.FC<{ onContinue: (draft: TenantDraft) => void }> = ({ onContinue }) => {
  const queryClient = useQueryClient();
  const { formatDateTime } = useDateTime();
  const { data } = useQuery({ queryKey: ['tenant-drafts'], queryFn: fetchDrafts, retry: false });
  const discard = useMutation({
    mutationFn: discardDraft,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tenant-drafts'] }),
  });
  if (!data?.length) return null;
  return (
    <Card sx={{ mb: 3 }} data-testid="tenant-drafts">
      <CardContent>
        <Typography variant="h6" gutterBottom>Unfinished</Typography>
        <Stack spacing={1}>
          {data.map((d) => (
            <Stack key={d.id} direction="row" spacing={2} alignItems="center" data-testid={`draft-${d.id}`}
              sx={{ flexWrap: 'wrap', rowGap: 1 }}>
              <Box sx={{ flex: 1, minWidth: 200 }}>
                <Typography sx={{ fontWeight: 600 }}>{d.title || 'Untitled draft'}</Typography>
                <Typography variant="caption" color="text.secondary">
                  Saved {formatDateTime(d.updatedAt)}
                  {d.issues.length > 0 && ` · ${d.issues.length} thing${d.issues.length === 1 ? '' : 's'} to finish`}
                </Typography>
              </Box>
              <Button size="small" variant="outlined" onClick={() => onContinue(d)}>Continue</Button>
              <Button size="small" color="error" onClick={() => discard.mutate(d.id)}>Discard</Button>
            </Stack>
          ))}
        </Stack>
      </CardContent>
    </Card>
  );
};

const STEPS = ['Services ready', 'Owner account', 'Live', 'Owner invited'] as const;

/** Where the saga is, as an index into STEPS. */
const activeStep = (p: ProvisioningProgress): number => {
  switch (p.step) {
    case 'AWAIT_SCHEMAS': return 0;
    case 'CREATE_OWNER': return 1;
    case 'ACTIVATE': return 2;
    case 'INVITE_OWNER': return 3;
    case 'DONE': return 4;
    default: return p.status === 'ACTIVE' ? 3 : p.services.every((s) => s.state === 'READY') ? 1 : 0;
  }
};

/**
 * Publishing a tenant and following it live: a DRAFT is published here; while it provisions, each
 * service's readiness is shown as it arrives; a failure says which step and why, with Retry; a
 * live tenant can have its owner re-invited.
 */
export const PublishCard: React.FC<{ tenant: Tenant }> = ({ tenant }) => {
  const queryClient = useQueryClient();
  const [discarding, setDiscarding] = useState(false);
  const inFactory = ['DRAFT', 'PROVISIONING', 'PROVISIONING_FAILED'].includes(tenant.status);

  const progress = useQuery({
    queryKey: ['provisioning', tenant.tenantKey],
    queryFn: () => fetchProvisioning(tenant.tenantKey),
    retry: false,
    refetchInterval: (q) => (q.state.data?.status === 'PROVISIONING' ? 2000 : false),
  });
  const p = progress.data;
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['tenants'] });
    queryClient.invalidateQueries({ queryKey: ['tenants', tenant.tenantKey] });
    queryClient.invalidateQueries({ queryKey: ['provisioning', tenant.tenantKey] });
  };
  // When the saga moves the tenant on, the rest of the page (status, list) follows.
  const sagaStatus = p?.status;
  useEffect(() => {
    if (sagaStatus && sagaStatus !== tenant.status) refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sagaStatus, tenant.status]);

  const publish = useMutation({ mutationFn: () => publishTenant(tenant.tenantKey), onSuccess: refresh });
  const resend = useMutation({ mutationFn: () => resendOwnerInvitation(tenant.tenantKey), onSuccess: refresh });
  const discard = useMutation({
    mutationFn: () => discardTenant(tenant.tenantKey),
    onSuccess: () => { setDiscarding(false); queryClient.invalidateQueries({ queryKey: ['tenants'] }); },
  });

  if (!inFactory && !p?.step) return null;

  const blockers = [
    !tenant.ownerEmail && "Add the owner's email (Identity): they are invited to set their own password.",
  ].filter(Boolean) as string[];
  const ready = p?.services.filter((s) => s.state === 'READY').length ?? 0;
  const total = p?.services.length ?? 0;

  return (
    <Card sx={{ mb: 3 }} data-testid="publish-card">
      <CardContent>
        <Typography variant="h6" gutterBottom>
          {tenant.status === 'DRAFT' ? 'Publish' : tenant.status === 'ACTIVE' ? 'Onboarding' : 'Provisioning'}
        </Typography>

        {tenant.status === 'DRAFT' && (
          <>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Publishing builds this workspace in every service, creates the owner's account and, once
              everything is ready, puts it live and emails {tenant.ownerEmail || 'the owner'} a link to
              set their password. Usually under a minute.
            </Typography>
            {blockers.map((b) => <Alert key={b} severity="warning" sx={{ mb: 1 }}>{b}</Alert>)}
          </>
        )}

        {(publish.isError || resend.isError || discard.isError) && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(publish.error || resend.error || discard.error, 'That did not work.')}
          </Alert>
        )}

        {p?.step && (
          <Box sx={{ mb: 2 }} data-testid="provisioning-progress">
            <Stepper activeStep={activeStep(p)} alternativeLabel sx={{ mb: 2 }}>
              {STEPS.map((label, i) => (
                <Step key={label} completed={activeStep(p) > i}>
                  <StepLabel error={p.step === 'FAILED' && activeStep(p) === i}>{label}</StepLabel>
                </Step>
              ))}
            </Stepper>
            {p.status === 'PROVISIONING' && (
              <>
                <Typography variant="body2" sx={{ mb: 0.5 }}>{ready} of {total} services ready</Typography>
                <LinearProgress variant="determinate" value={total ? (ready / total) * 100 : 0} sx={{ mb: 1.5 }} />
              </>
            )}
            <Stack direction="row" spacing={0.5} useFlexGap flexWrap="wrap">
              {p.services.map((s) => (
                <Chip key={s.service} size="small" label={s.service.replace('-service', '')}
                  color={s.state === 'READY' ? 'success' : s.state === 'FAILED' ? 'error' : 'default'}
                  variant={s.state === 'WAITING' ? 'outlined' : 'filled'}
                  title={s.error ?? s.state} />
              ))}
            </Stack>
            {p.lastError && (
              <Alert severity={p.step === 'FAILED' ? 'error' : 'warning'} sx={{ mt: 2 }}>{p.lastError}</Alert>
            )}
            {p.step === 'DONE' && (
              <Alert severity="success" sx={{ mt: 2 }}>
                Live. {tenant.ownerEmail} has been sent a link to set their password.
              </Alert>
            )}
          </Box>
        )}

        <Stack direction="row" spacing={1}>
          {(tenant.status === 'DRAFT' || tenant.status === 'PROVISIONING_FAILED') && (
            <Button variant="contained" disabled={blockers.length > 0 || publish.isPending} onClick={() => publish.mutate()}>
              {publish.isPending ? 'Publishing…' : tenant.status === 'DRAFT' ? 'Publish' : 'Retry'}
            </Button>
          )}
          {(tenant.status === 'DRAFT' || tenant.status === 'PROVISIONING_FAILED') && (
            <Button color="error" onClick={() => setDiscarding(true)}>Discard</Button>
          )}
          {tenant.status === 'ACTIVE' && p?.step && (
            <Button variant="outlined" disabled={resend.isPending} onClick={() => resend.mutate()}>
              {resend.isPending ? 'Sending…' : resend.isSuccess ? 'Invitation sent again' : 'Resend owner invitation'}
            </Button>
          )}
        </Stack>
      </CardContent>

      <Dialog open={discarding} onClose={() => setDiscarding(false)}>
        <DialogTitle>Discard {tenant.name}?</DialogTitle>
        <DialogContent>
          <DialogContentText>
            It never went live, so nothing but its settings is lost. The key <strong>{tenant.tenantKey}</strong> and
            its subdomain become free again.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setDiscarding(false)}>Cancel</Button>
          <Button variant="contained" color="error" disabled={discard.isPending} onClick={() => discard.mutate()}>
            Discard
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
};
