import React, { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  IconButton,
  MenuItem,
  Radio,
  RadioGroup,
  Stack,
  Switch,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { ContentCopy } from '@mui/icons-material';
import { apiErrorMessage } from '../../services/apiError';
import {
  CAPABILITY_LABELS,
  CapabilitySpec,
  DEFAULT_MODELS,
  IntegrationClient,
  IntegrationDraft,
  TenantIntegration,
  capabilityLabel,
  draftFrom,
  fieldLabel,
  integrationStatus,
  missingFields,
  operatorIntegrations,
  providerLabel,
  toSaveRequest,
  webhookUrl,
} from '../../services/tenantIntegrationApi';

type Tenantish = { tenantKey: string; name: string };

/**
 * One capability's form. Secrets are never shown: a stored one appears as its masked hint in the
 * placeholder, and leaving the box empty keeps it.
 */
const EditIntegrationDialog: React.FC<{
  name: string;
  client: IntegrationClient;
  spec: CapabilitySpec;
  current?: TenantIntegration;
  onClose: () => void;
}> = ({ name, client, spec, current, onClose }) => {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<IntegrationDraft>(() => draftFrom(spec, current));
  const [touched, setTouched] = useState(false);

  const save = useMutation({
    mutationFn: () => client.save(spec.capability, toSaveRequest(spec, draft)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: client.key });
      onClose();
    },
  });

  const provider = spec.providers[draft.provider];
  const missing = missingFields(spec, draft, current);
  const keepsStored =
    current?.configured && current.mode === 'BYO' && current.provider === draft.provider;
  const set = (group: 'settings' | 'secrets', key: string, value: string) =>
    setDraft((d) => ({ ...d, [group]: { ...d[group], [key]: value } }));

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        {capabilityLabel(spec.capability)} for {name}
      </DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{CAPABILITY_LABELS[spec.capability]?.help}</DialogContentText>

        {save.isError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(save.error, 'The integration could not be saved.')}
          </Alert>
        )}

        <RadioGroup
          value={draft.mode}
          onChange={(e) => setDraft((d) => ({ ...d, mode: e.target.value as IntegrationDraft['mode'] }))}
          sx={{ mb: 2 }}
        >
          <FormControlLabel
            value="PLATFORM_SHARED"
            control={<Radio />}
            label={`Use the platform's account (default). ${CAPABILITY_LABELS[spec.capability]?.platformNote ?? ''}`}
          />
          <FormControlLabel value="BYO" control={<Radio />} label="Use our own account" />
        </RadioGroup>

        {draft.mode === 'BYO' && (
          <Stack spacing={2}>
            <TextField
              select
              label="Provider"
              value={draft.provider}
              onChange={(e) => setDraft((d) => ({ ...d, provider: e.target.value }))}
              SelectProps={{ inputProps: { 'aria-label': 'Provider' } }}
            >
              {Object.keys(spec.providers).map((key) => (
                <MenuItem key={key} value={key}>{providerLabel(key)}</MenuItem>
              ))}
            </TextField>

            {provider?.settings.map((key) => (
              <TextField
                key={key}
                label={fieldLabel(key)}
                value={draft.settings[key] ?? ''}
                onChange={(e) => set('settings', key, e.target.value)}
                error={touched && missing.includes(key)}
                helperText={touched && missing.includes(key) ? 'Required' : undefined}
                required
              />
            ))}

            {provider?.optionalSettings?.map((key) => (
              <TextField
                key={key}
                label={`${fieldLabel(key)} (optional)`}
                value={draft.settings[key] ?? ''}
                onChange={(e) => set('settings', key, e.target.value)}
                placeholder={key === 'model' ? DEFAULT_MODELS[draft.provider] : undefined}
                InputLabelProps={{ shrink: true }}
                helperText={key === 'model' ? 'Leave blank for the provider\'s default model.' : undefined}
              />
            ))}

            {provider?.secrets.map((key) => {
              const hint = keepsStored ? current?.secretHints[key] : undefined;
              return (
                <TextField
                  key={key}
                  type="password"
                  autoComplete="new-password"
                  label={fieldLabel(key)}
                  value={draft.secrets[key] ?? ''}
                  onChange={(e) => set('secrets', key, e.target.value)}
                  placeholder={hint ? `${hint} — leave blank to keep` : undefined}
                  InputLabelProps={hint ? { shrink: true } : undefined}
                  error={touched && missing.includes(key)}
                  helperText={
                    touched && missing.includes(key)
                      ? 'Required'
                      : hint
                        ? 'Stored encrypted. Type a new value only to replace it.'
                        : 'Stored encrypted and never shown again.'
                  }
                  required={!hint}
                />
              );
            })}
          </Stack>
        )}

        <FormControlLabel
          sx={{ mt: 2 }}
          control={
            <Switch
              checked={draft.enabled}
              onChange={(e) => setDraft((d) => ({ ...d, enabled: e.target.checked }))}
            />
          }
          label={draft.enabled ? 'On' : 'Off — this feature is switched off for this workspace'}
        />
      </DialogContent>
      <DialogActions>
        <Button color="inherit" onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={save.isPending}
          onClick={() => {
            setTouched(true);
            if (missing.length === 0) save.mutate();
          }}
        >
          {save.isPending ? 'Saving…' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

/**
 * A tenant's provider accounts: payments, mail, SMS, WhatsApp and AI. Each runs on the platform's
 * account until the tenant brings its own, and can be switched off. Used by the operator console
 * (any tenant) and by a workspace's own admins (`client`).
 */
export const IntegrationsPanel: React.FC<{ name: string; client: IntegrationClient; intro: string }> = ({
  name, client, intro,
}) => {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<string | null>(null);
  const [removing, setRemoving] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const catalog = useQuery({ queryKey: [...client.key, 'catalog'], queryFn: client.fetchCatalog, retry: false });
  const integrations = useQuery({ queryKey: client.key, queryFn: client.fetchIntegrations, retry: false });

  const remove = useMutation({
    mutationFn: (capability: string) => client.reset(capability),
    onSuccess: () => {
      setRemoving(null);
      queryClient.invalidateQueries({ queryKey: client.key });
    },
  });

  const specs = catalog.data ?? [];
  const byCapability = new Map((integrations.data ?? []).map((i) => [i.capability, i]));
  const editingSpec = specs.find((s) => s.capability === editing);
  const error = catalog.error || integrations.error;

  return (
    <Card sx={{ mb: 3 }} data-testid="tenant-integrations">
      <CardContent>
        <Typography variant="h6" gutterBottom>Integrations</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{intro}</Typography>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {apiErrorMessage(error, 'Could not load integrations.')}
          </Alert>
        )}
        {(catalog.isLoading || integrations.isLoading) && <CircularProgress size={24} />}

        <Stack divider={<Box sx={{ borderTop: 1, borderColor: 'divider' }} />}>
          {specs.map((spec) => {
            const item = byCapability.get(spec.capability);
            const status = item ? integrationStatus(item) : { label: 'Platform default', color: 'info' as const };
            const hints = item ? Object.entries(item.secretHints) : [];
            return (
              <Box
                key={spec.capability}
                data-testid={`integration-${spec.capability}`}
                sx={{ py: 1.5, display: 'flex', gap: 2, alignItems: 'flex-start', flexWrap: 'wrap' }}
              >
                <Box sx={{ flex: '1 1 260px', minWidth: 0 }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography fontWeight={600}>{capabilityLabel(spec.capability)}</Typography>
                    <Chip size="small" label={status.label} color={status.color} />
                  </Stack>
                  <Typography variant="caption" color="text.secondary" component="div">
                    {CAPABILITY_LABELS[spec.capability]?.help}
                  </Typography>
                  {item?.configured && item.mode === 'BYO' && (
                    <Typography variant="caption" component="div" sx={{ mt: 0.5, wordBreak: 'break-all' }}>
                      {Object.entries(item.settings)
                        .map(([k, v]) => `${fieldLabel(k)}: ${v}`)
                        .concat(hints.map(([k, v]) => `${fieldLabel(k)}: ${v}`))
                        .join(' · ')}
                    </Typography>
                  )}
                  {item?.webhookPath && (
                    <Stack direction="row" spacing={0.5} alignItems="center" sx={{ mt: 0.5 }}>
                      <Typography variant="caption" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
                        Webhook URL: {webhookUrl(item.webhookPath)}
                      </Typography>
                      <Tooltip title={copied ? 'Copied' : 'Copy webhook URL'}>
                        <IconButton
                          size="small"
                          aria-label="Copy webhook URL"
                          onClick={() => {
                            navigator.clipboard?.writeText(webhookUrl(item.webhookPath!)).then(
                              () => setCopied(true),
                              () => undefined
                            );
                          }}
                        >
                          <ContentCopy fontSize="inherit" />
                        </IconButton>
                      </Tooltip>
                    </Stack>
                  )}
                </Box>
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" onClick={() => setEditing(spec.capability)}>
                    Change
                  </Button>
                  {item?.configured && (item.mode === 'BYO' || !item.enabled) && (
                    <Button size="small" color="error" onClick={() => setRemoving(spec.capability)}>
                      Use platform default
                    </Button>
                  )}
                </Stack>
              </Box>
            );
          })}
        </Stack>
      </CardContent>

      {editingSpec && (
        <EditIntegrationDialog
          key={editingSpec.capability}
          name={name}
          client={client}
          spec={editingSpec}
          current={byCapability.get(editingSpec.capability)}
          onClose={() => setEditing(null)}
        />
      )}

      <Dialog open={!!removing} onClose={() => setRemoving(null)}>
        <DialogTitle>Put {removing && capabilityLabel(removing)} back on the platform's account for {name}?</DialogTitle>
        <DialogContent>
          {remove.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {apiErrorMessage(remove.error, 'That did not work.')}
            </Alert>
          )}
          <DialogContentText>
            Any stored credentials are deleted, and from now on this runs on the platform's account.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="inherit" onClick={() => setRemoving(null)}>Cancel</Button>
          <Button
            variant="contained"
            color="error"
            disabled={remove.isPending}
            onClick={() => removing && remove.mutate(removing)}
          >
            {remove.isPending ? 'Switching…' : 'Use platform default'}
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
};

/** The operator console's view of one tenant. */
const TenantIntegrationsCard: React.FC<{ tenant: Tenantish }> = ({ tenant }) => (
  <IntegrationsPanel
    name={tenant.name}
    client={operatorIntegrations(tenant.tenantKey)}
    intro="Provider accounts this tenant's payments, messages and AI run through. Each uses the platform's account until the tenant brings its own; any can be switched off."
  />
);

export default TenantIntegrationsCard;
