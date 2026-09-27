import React from 'react';
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Box, Chip, FormControlLabel, Stack, Switch, Typography,
} from '@mui/material';
import { ExpandMore } from '@mui/icons-material';
import {
  CAPABILITY_LABELS,
  CapabilitySpec,
  IntegrationDraft,
  capabilityLabel,
  draftFrom,
  missingFields,
  providerLabel,
} from '../../services/tenantIntegrationApi';
import IntegrationFormFields from './IntegrationFormFields';

export type IntegrationDrafts = Record<string, IntegrationDraft>;

/** The wizard's starting point: every capability on the platform's account, switched on. */
export const defaultIntegrationDrafts = (catalog: CapabilitySpec[]): IntegrationDrafts =>
  Object.fromEntries(catalog.map((spec) => [spec.capability, draftFrom(spec)]));

/**
 * Only choices that differ from what a new tenant gets anyway need saving: its own account, or the
 * capability switched off. Everything else already runs on the platform's account.
 */
export const changedIntegrations = (drafts: IntegrationDrafts) =>
  Object.entries(drafts).filter(([, d]) => d.mode === 'BYO' || !d.enabled);

/** Capabilities set to the tenant's own account that still lack a required field. */
export const incompleteIntegrations = (catalog: CapabilitySpec[], drafts: IntegrationDrafts) =>
  catalog.filter((spec) => drafts[spec.capability] && missingFields(spec, drafts[spec.capability]).length > 0);

/** For the draft document: everything but the secrets, which are never autosaved. */
export const withoutSecrets = (drafts: IntegrationDrafts) =>
  Object.fromEntries(Object.entries(drafts).map(([k, d]) => [k, { ...d, secrets: {} }]));

export const integrationSummary = (draft: IntegrationDraft | undefined) =>
  !draft ? 'Platform account'
    : !draft.enabled ? 'Off'
      : draft.mode === 'BYO' ? `Own account · ${providerLabel(draft.provider)}`
        : 'Platform account';

const WIDGETS: { key: 'support' | 'ai' | 'messaging'; label: string; help: string }[] = [
  { key: 'support', label: 'Support chat widget', help: 'The chat bubble on every page: FAQs, tickets and hand-off to staff.' },
  { key: 'ai', label: 'AI assistant', help: 'The Civil AI Assistant (Ask AI) — answered by the AI provider below.' },
  { key: 'messaging', label: 'In-app messaging', help: 'Chat between customers, professionals and staff.' },
];

/**
 * Everything a tenant talks to the outside world through: the chat widgets its users see, and the
 * provider accounts behind payments, email, SMS, WhatsApp and the AI assistant.
 *
 * The support and messaging widgets are the `support` and `messaging` modules (switching one off
 * takes its screens and API with it); the AI assistant is the AI capability's on/off switch.
 */
const TenantIntegrationsStep: React.FC<{
  catalog: CapabilitySpec[];
  catalogError?: string | null;
  drafts: IntegrationDrafts;
  onDraft: (capability: string, draft: IntegrationDraft) => void;
  modules: Set<string>;
  onModules: (modules: Set<string>) => void;
  touched?: boolean;
}> = ({ catalog, catalogError, drafts, onDraft, modules, onModules, touched }) => {
  const widgetOn = (key: 'support' | 'ai' | 'messaging') =>
    key === 'ai' ? drafts.ai?.enabled ?? true : modules.has(key);

  const setWidget = (key: 'support' | 'ai' | 'messaging', on: boolean) => {
    if (key === 'ai') {
      if (drafts.ai) onDraft('ai', { ...drafts.ai, enabled: on });
      return;
    }
    const next = new Set(modules);
    if (on) next.add(key);
    else next.delete(key);
    onModules(next);
  };

  return (
    <Box data-testid="integrations-step">
      <Typography variant="subtitle2">Chat & assistant widgets</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        What this tenant's users see on their screens.
      </Typography>
      <Stack spacing={0.5} sx={{ mb: 3 }}>
        {WIDGETS.map((w) => (
          <FormControlLabel
            key={w.key}
            control={
              <Switch
                checked={widgetOn(w.key)}
                disabled={w.key === 'ai' && !drafts.ai}
                onChange={(e) => setWidget(w.key, e.target.checked)}
                inputProps={{ 'aria-label': w.label }}
              />
            }
            label={
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>{w.label}</Typography>
                <Typography variant="caption" color="text.secondary">{w.help}</Typography>
              </Box>
            }
          />
        ))}
      </Stack>

      <Typography variant="subtitle2">Provider accounts</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Each starts on the platform's own account. Choose the tenant's own account for any of them —
        payment gateway, email, SMS, WhatsApp, and the AI model (Gemini, OpenAI, Anthropic or an open
        model on any OpenAI-compatible server). Keys and secrets are not kept in the draft: they are
        sent once, when the tenant is created, and stored encrypted.
      </Typography>

      {catalogError && <Alert severity="warning" sx={{ mb: 2 }}>{catalogError}</Alert>}

      {catalog.map((spec) => {
        const draft = drafts[spec.capability];
        if (!draft) return null;
        const incomplete = missingFields(spec, draft).length > 0;
        return (
          <Accordion key={spec.capability} disableGutters data-testid={`integration-${spec.capability}`}>
            <AccordionSummary expandIcon={<ExpandMore />}>
              <Stack direction="row" spacing={1} alignItems="center" sx={{ width: '100%', pr: 1 }}>
                <Typography sx={{ fontWeight: 600, flexGrow: 1 }}>{capabilityLabel(spec.capability)}</Typography>
                {incomplete && touched && <Chip size="small" color="error" label="Needs details" />}
                <Chip size="small" variant="outlined" data-testid={`integration-summary-${spec.capability}`}
                  color={!draft.enabled ? 'default' : draft.mode === 'BYO' ? 'success' : 'info'}
                  label={integrationSummary(draft)} />
              </Stack>
            </AccordionSummary>
            <AccordionDetails>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                {CAPABILITY_LABELS[spec.capability]?.help}
              </Typography>
              <IntegrationFormFields spec={spec} draft={draft} touched={touched}
                onChange={(next) => onDraft(spec.capability, next)} />
            </AccordionDetails>
          </Accordion>
        );
      })}
    </Box>
  );
};

export default TenantIntegrationsStep;
