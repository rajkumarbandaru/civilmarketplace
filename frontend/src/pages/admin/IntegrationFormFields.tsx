import React from 'react';
import { FormControlLabel, MenuItem, Radio, RadioGroup, Stack, Switch, TextField } from '@mui/material';
import {
  CAPABILITY_LABELS,
  CapabilitySpec,
  DEFAULT_MODELS,
  IntegrationDraft,
  OPEN_MODEL_PRESETS,
  TenantIntegration,
  fieldLabel,
  missingFields,
  providerLabel,
} from '../../services/tenantIntegrationApi';

/**
 * One capability's settings — platform account or the tenant's own, the provider and its fields,
 * on or off. Controlled, so the same form sits in the edit dialog (saved on its own) and in the
 * new-tenant wizard (saved once the tenant exists).
 *
 * Secrets are never shown: a stored one appears as its masked hint, and leaving the box empty keeps it.
 */
const IntegrationFormFields: React.FC<{
  spec: CapabilitySpec;
  draft: IntegrationDraft;
  onChange: (draft: IntegrationDraft) => void;
  current?: TenantIntegration;
  touched?: boolean;
}> = ({ spec, draft, onChange, current, touched = false }) => {
  const provider = spec.providers[draft.provider];
  const missing = missingFields(spec, draft, current);
  const keepsStored = current?.configured && current.mode === 'BYO' && current.provider === draft.provider;
  const set = (group: 'settings' | 'secrets', key: string, value: string) =>
    onChange({ ...draft, [group]: { ...draft[group], [key]: value } });
  const cap = spec.capability;

  const secretField = (key: string, optional: boolean) => {
    const hint = keepsStored ? current?.secretHints[key] : undefined;
    return (
      <TextField
        key={key}
        type="password"
        autoComplete="new-password"
        label={optional ? `${fieldLabel(key)} (optional)` : fieldLabel(key)}
        value={draft.secrets[key] ?? ''}
        onChange={(e) => set('secrets', key, e.target.value)}
        placeholder={hint ? `${hint} — leave blank to keep` : undefined}
        InputLabelProps={hint || optional ? { shrink: true } : undefined}
        inputProps={{ 'data-testid': `${cap}-secret-${key}` }}
        error={touched && missing.includes(key)}
        helperText={
          touched && missing.includes(key)
            ? 'Required'
            : hint
              ? 'Stored encrypted. Type a new value only to replace it.'
              : optional
                ? 'Only if the server needs one. Stored encrypted and never shown again.'
                : 'Stored encrypted and never shown again.'
        }
        required={!hint && !optional}
      />
    );
  };

  return (
    <>
      <RadioGroup
        value={draft.mode}
        onChange={(e) => onChange({ ...draft, mode: e.target.value as IntegrationDraft['mode'] })}
        sx={{ mb: 2 }}
      >
        <FormControlLabel
          value="PLATFORM_SHARED"
          control={<Radio />}
          label={`Use the platform's account (default). ${CAPABILITY_LABELS[cap]?.platformNote ?? ''}`}
        />
        <FormControlLabel value="BYO" control={<Radio />} label="Use our own account" />
      </RadioGroup>

      {draft.mode === 'BYO' && (
        <Stack spacing={2}>
          <TextField
            select
            label="Provider"
            value={draft.provider}
            onChange={(e) => onChange({ ...draft, provider: e.target.value })}
            SelectProps={{ inputProps: { 'aria-label': 'Provider', 'data-testid': `${cap}-provider` } }}
          >
            {Object.keys(spec.providers).map((key) => (
              <MenuItem key={key} value={key}>{providerLabel(key)}</MenuItem>
            ))}
          </TextField>

          {draft.provider === 'openai_compatible' && (
            <TextField
              select
              label="Start from a known server"
              value=""
              onChange={(e) => {
                const preset = OPEN_MODEL_PRESETS.find((p) => p.key === e.target.value);
                if (preset) {
                  onChange({ ...draft, settings: { ...draft.settings, baseUrl: preset.baseUrl, model: preset.model } });
                }
              }}
              helperText="Fills the server URL and a typical model — Llama, Qwen, DeepSeek, Mistral and other open models."
            >
              {OPEN_MODEL_PRESETS.map((p) => <MenuItem key={p.key} value={p.key}>{p.label}</MenuItem>)}
            </TextField>
          )}

          {provider?.settings.map((key) => (
            <TextField
              key={key}
              label={fieldLabel(key)}
              value={draft.settings[key] ?? ''}
              onChange={(e) => set('settings', key, e.target.value)}
              inputProps={{ 'data-testid': `${cap}-setting-${key}` }}
              error={touched && missing.includes(key)}
              helperText={touched && missing.includes(key) ? 'Required'
                : key === 'baseUrl' ? 'The OpenAI-compatible API base, e.g. https://api.groq.com/openai/v1' : undefined}
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

          {provider?.secrets.map((key) => secretField(key, false))}
          {provider?.optionalSecrets?.map((key) => secretField(key, true))}
        </Stack>
      )}

      <FormControlLabel
        sx={{ mt: 2 }}
        control={
          <Switch
            checked={draft.enabled}
            onChange={(e) => onChange({ ...draft, enabled: e.target.checked })}
          />
        }
        label={draft.enabled ? 'On' : 'Off — this feature is switched off for this workspace'}
      />
    </>
  );
};

export default IntegrationFormFields;
