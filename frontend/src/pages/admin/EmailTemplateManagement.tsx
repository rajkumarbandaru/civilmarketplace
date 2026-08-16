import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
  Divider, FormControlLabel, Grid, IconButton, InputAdornment, List, ListItemButton, ListItemText,
  Paper, Snackbar, Stack, Switch, Tab, Tabs, TextField, Tooltip, Typography,
} from '@mui/material';
import {
  Add, Delete, Refresh, Save, Search, Send, Visibility,
} from '@mui/icons-material';
import {
  EmailTemplate, TemplateCommand, TemplatePreview, createEmailTemplate, deleteEmailTemplate,
  fetchEmailTemplates, previewEmailTemplate, resetEmailTemplate, testSendEmailTemplate,
  updateEmailTemplate,
} from '../../services/emailApi';

/**
 * Super Admin's editor for the transactional email the platform sends.
 *
 * Master/detail rather than a table: an email is a document, and the only way to judge an edit is
 * to see it rendered, so the preview sits beside the body instead of behind a separate screen.
 * Preview renders the unsaved draft, which is what makes it safe to experiment on a live template
 * — nothing a customer receives changes until Save.
 */

const errorMessage = (err: unknown, fallback: string): string => {
  const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return message || fallback;
};

/** The editor's working copy — a saved template plus whatever has been typed over it. */
interface Draft {
  name: string;
  description: string;
  subject: string;
  htmlBody: string;
  active: boolean;
  /** Sample data, edited as JSON text so a placeholder can hold a number or a boolean. */
  sampleJson: string;
}

const draftOf = (template: EmailTemplate): Draft => ({
  name: template.name,
  description: template.description ?? '',
  subject: template.subject,
  htmlBody: template.htmlBody,
  active: template.active,
  sampleJson: JSON.stringify(template.sampleVariables ?? {}, null, 2),
});

const EMPTY_BODY = `<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" th:replace="~{email/_layout :: page(~{::content})}">
<body>
<div th:fragment="content" th:remove="tag">
    <h1 style="margin:0 0 12px; font-size:20px; color:#1f2933;">Hello <span th:text="\${name}">there</span></h1>
    <p style="margin:0 0 20px; font-size:14px; line-height:22px; color:#3e4c59;">
        Your message goes here.
    </p>
</div>
</body>
</html>`;

const EmailTemplateManagement: React.FC = () => {
  const [templates, setTemplates] = useState<EmailTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [tab, setTab] = useState(0);
  const [filter, setFilter] = useState('');
  const [saving, setSaving] = useState(false);
  const [preview, setPreview] = useState<TemplatePreview | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [testOpen, setTestOpen] = useState(false);
  const [testRecipient, setTestRecipient] = useState('');
  const [testSending, setTestSending] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [newTemplate, setNewTemplate] = useState({ templateKey: '', name: '', subject: '' });
  const [confirmDelete, setConfirmDelete] = useState<EmailTemplate | null>(null);
  const [confirmReset, setConfirmReset] = useState<EmailTemplate | null>(null);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>(
    { open: false, message: '', severity: 'success' }
  );

  const notify = (message: string, severity: 'success' | 'error' = 'success') =>
    setSnackbar({ open: true, message, severity });

  const selected = useMemo(
    () => templates.find((t) => t.templateKey === selectedKey) ?? null,
    [templates, selectedKey]
  );

  const visible = useMemo(() => {
    const needle = filter.trim().toLowerCase();
    if (!needle) return templates;
    return templates.filter(
      (t) =>
        t.name.toLowerCase().includes(needle) ||
        t.templateKey.toLowerCase().includes(needle) ||
        (t.description ?? '').toLowerCase().includes(needle)
    );
  }, [templates, filter]);

  const load = async (keepKey?: string) => {
    try {
      setLoading(true);
      const rows = await fetchEmailTemplates();
      setTemplates(rows);
      const next = rows.find((t) => t.templateKey === (keepKey ?? selectedKey)) ?? rows[0];
      if (next) {
        setSelectedKey(next.templateKey);
        setDraft(draftOf(next));
      }
    } catch (err) {
      notify(errorMessage(err, 'Could not load the email templates'), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const select = (template: EmailTemplate) => {
    setSelectedKey(template.templateKey);
    setDraft(draftOf(template));
    setPreview(null);
    setTab(0);
  };

  /** The sample JSON, or null when it does not parse — the caller decides what to do about it. */
  const parsedSamples = (): Record<string, unknown> | null => {
    if (!draft) return null;
    try {
      const parsed = JSON.parse(draft.sampleJson || '{}');
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null;
    } catch {
      return null;
    }
  };

  const commandOf = (): TemplateCommand | null => {
    if (!draft) return null;
    const samples = parsedSamples();
    if (samples === null) {
      notify('Sample data must be a JSON object', 'error');
      return null;
    }
    return {
      name: draft.name,
      description: draft.description,
      subject: draft.subject,
      htmlBody: draft.htmlBody,
      sampleVariables: samples,
      active: draft.active,
    };
  };

  const save = async () => {
    if (!selected) return;
    const command = commandOf();
    if (!command) return;
    try {
      setSaving(true);
      const saved = await updateEmailTemplate(selected.templateKey, command);
      setTemplates((prev) => prev.map((t) => (t.id === saved.id ? saved : t)));
      setDraft(draftOf(saved));
      notify('Saved — the next email sent uses this version');
    } catch (err) {
      notify(errorMessage(err, 'Could not save the template'), 'error');
    } finally {
      setSaving(false);
    }
  };

  const runPreview = async () => {
    if (!selected || !draft) return;
    try {
      setPreviewing(true);
      setTab(2);
      const result = await previewEmailTemplate(selected.templateKey, {
        subject: draft.subject,
        htmlBody: draft.htmlBody,
        variables: parsedSamples() ?? undefined,
      });
      setPreview(result);
    } catch (err) {
      notify(errorMessage(err, 'Could not render the preview'), 'error');
    } finally {
      setPreviewing(false);
    }
  };

  const sendTest = async () => {
    if (!selected) return;
    try {
      setTestSending(true);
      const result = await testSendEmailTemplate(
        selected.templateKey,
        testRecipient,
        parsedSamples() ?? undefined
      );
      setTestOpen(false);
      // SKIPPED means no mail provider is configured, so nothing left the building. Saying "sent"
      // there would send an admin looking for an email that was only ever written to a log file.
      if (result.status === 'SKIPPED') {
        notify('No email provider is configured — the message was logged, not sent', 'error');
      } else if (result.success) {
        notify(`Test email sent to ${result.recipient} (${result.status})`);
      } else {
        notify(`The provider rejected the test email — see Emails for the reason`, 'error');
      }
    } catch (err) {
      notify(errorMessage(err, 'Could not send the test email'), 'error');
    } finally {
      setTestSending(false);
    }
  };

  const create = async () => {
    try {
      const saved = await createEmailTemplate({
        templateKey: newTemplate.templateKey.trim(),
        name: newTemplate.name.trim(),
        subject: newTemplate.subject.trim() || newTemplate.name.trim(),
        htmlBody: EMPTY_BODY,
        sampleVariables: { name: 'Anita Rao' },
        active: true,
      });
      setCreateOpen(false);
      setNewTemplate({ templateKey: '', name: '', subject: '' });
      await load(saved.templateKey);
      notify('Template created');
    } catch (err) {
      notify(errorMessage(err, 'Could not create the template'), 'error');
    }
  };

  const remove = async () => {
    if (!confirmDelete) return;
    try {
      await deleteEmailTemplate(confirmDelete.templateKey);
      setConfirmDelete(null);
      setSelectedKey(null);
      await load();
      notify('Template deleted');
    } catch (err) {
      notify(errorMessage(err, 'Could not delete the template'), 'error');
    }
  };

  const reset = async () => {
    if (!confirmReset) return;
    try {
      const restored = await resetEmailTemplate(confirmReset.templateKey);
      setConfirmReset(null);
      setTemplates((prev) => prev.map((t) => (t.id === restored.id ? restored : t)));
      setDraft(draftOf(restored));
      setPreview(null);
      notify('Restored to the version shipped with the service');
    } catch (err) {
      notify(errorMessage(err, 'Could not reset the template'), 'error');
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 6 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Typography variant="h5" fontWeight={600}>
          Email Templates
        </Typography>
        <Button startIcon={<Add />} variant="contained" onClick={() => setCreateOpen(true)}>
          New template
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Every transactional email the platform sends. Edits go live on the next send — use Preview
        to check one before saving, and Emails to confirm it was delivered.
      </Typography>

      <Grid container spacing={2}>
        <Grid item xs={12} md={4} lg={3}>
          <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
            <Box sx={{ p: 1.5 }}>
              <TextField
                size="small"
                fullWidth
                placeholder="Search templates"
                value={filter}
                onChange={(e) => setFilter(e.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <Search fontSize="small" />
                    </InputAdornment>
                  ),
                }}
              />
            </Box>
            <Divider />
            <List dense disablePadding sx={{ maxHeight: 560, overflowY: 'auto' }}>
              {visible.map((template) => (
                <ListItemButton
                  key={template.templateKey}
                  selected={template.templateKey === selectedKey}
                  onClick={() => select(template)}
                  sx={{ alignItems: 'flex-start', py: 1.2 }}
                >
                  <ListItemText
                    primary={
                      <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap">
                        <Typography variant="body2" fontWeight={600}>
                          {template.name}
                        </Typography>
                        {!template.active && <Chip size="small" label="Inactive" color="warning" />}
                        {!template.systemOwned && <Chip size="small" label="Custom" variant="outlined" />}
                      </Stack>
                    }
                    secondary={template.templateKey}
                    secondaryTypographyProps={{ variant: 'caption' }}
                  />
                </ListItemButton>
              ))}
              {visible.length === 0 && (
                <Box sx={{ p: 2 }}>
                  <Typography variant="body2" color="text.secondary">
                    No template matches “{filter}”.
                  </Typography>
                </Box>
              )}
            </List>
          </Paper>
        </Grid>

        <Grid item xs={12} md={8} lg={9}>
          {!selected || !draft ? (
            <Paper variant="outlined" sx={{ p: 4 }}>
              <Typography color="text.secondary">Select a template to edit it.</Typography>
            </Paper>
          ) : (
            <Paper variant="outlined">
              <Box sx={{ p: 2 }}>
                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  spacing={1}
                  alignItems={{ sm: 'center' }}
                  justifyContent="space-between"
                >
                  <Box>
                    <Typography variant="h6">{selected.name}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {selected.templateKey}
                      {selected.systemOwned && ' · built-in'}
                    </Typography>
                  </Box>
                  <Stack direction="row" spacing={1}>
                    <Button
                      size="small"
                      startIcon={<Visibility />}
                      onClick={runPreview}
                      disabled={previewing}
                    >
                      Preview
                    </Button>
                    <Button size="small" startIcon={<Send />} onClick={() => setTestOpen(true)}>
                      Test send
                    </Button>
                    {selected.systemOwned ? (
                      <Tooltip title="Restore the version shipped with the service">
                        <IconButton size="small" onClick={() => setConfirmReset(selected)}>
                          <Refresh fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    ) : (
                      <Tooltip title="Delete this template">
                        <IconButton size="small" color="error" onClick={() => setConfirmDelete(selected)}>
                          <Delete fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    )}
                    <Button
                      size="small"
                      variant="contained"
                      startIcon={<Save />}
                      onClick={save}
                      disabled={saving}
                    >
                      Save
                    </Button>
                  </Stack>
                </Stack>
              </Box>
              <Divider />

              <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ px: 2 }}>
                <Tab label="Content" />
                <Tab label="Sample data" />
                <Tab label="Preview" />
              </Tabs>
              <Divider />

              {tab === 0 && (
                <Box sx={{ p: 2 }}>
                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <TextField
                        label="Name"
                        fullWidth
                        size="small"
                        value={draft.name}
                        onChange={(e) => setDraft({ ...draft, name: e.target.value })}
                      />
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <FormControlLabel
                        control={
                          <Switch
                            checked={draft.active}
                            onChange={(e) => setDraft({ ...draft, active: e.target.checked })}
                          />
                        }
                        label={
                          draft.active
                            ? 'Active — this version is sent'
                            : 'Inactive — the version shipped in the service is sent instead'
                        }
                      />
                    </Grid>
                    <Grid item xs={12}>
                      <TextField
                        label="Description"
                        fullWidth
                        size="small"
                        value={draft.description}
                        onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                        helperText="When this email goes out. Shown in this list only."
                      />
                    </Grid>
                    <Grid item xs={12}>
                      <TextField
                        label="Subject"
                        fullWidth
                        size="small"
                        value={draft.subject}
                        onChange={(e) => setDraft({ ...draft, subject: e.target.value })}
                        helperText="Placeholders work here too, e.g. Booking confirmed - ${bookingCode}"
                      />
                    </Grid>
                    <Grid item xs={12}>
                      <Stack direction="row" spacing={0.75} flexWrap="wrap" sx={{ mb: 1 }}>
                        <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5 }}>
                          Available placeholders:
                        </Typography>
                        {selected.placeholders.length === 0 && (
                          <Typography variant="caption" color="text.secondary">
                            none yet
                          </Typography>
                        )}
                        {selected.placeholders.map((name) => (
                          <Chip key={name} size="small" label={`\${${name}}`} variant="outlined" />
                        ))}
                      </Stack>
                      <TextField
                        label="HTML body (Thymeleaf)"
                        fullWidth
                        multiline
                        minRows={18}
                        value={draft.htmlBody}
                        onChange={(e) => setDraft({ ...draft, htmlBody: e.target.value })}
                        InputProps={{ sx: { fontFamily: 'monospace', fontSize: 13 } }}
                      />
                    </Grid>
                  </Grid>
                </Box>
              )}

              {tab === 1 && (
                <Box sx={{ p: 2 }}>
                  <Alert severity="info" sx={{ mb: 2 }}>
                    Example values used by Preview and Test send. They never appear in a real
                    email — a live send fills the same placeholders from the booking or user it is
                    about.
                  </Alert>
                  <TextField
                    label="Sample data (JSON)"
                    fullWidth
                    multiline
                    minRows={12}
                    value={draft.sampleJson}
                    onChange={(e) => setDraft({ ...draft, sampleJson: e.target.value })}
                    error={parsedSamples() === null}
                    helperText={parsedSamples() === null ? 'This is not a JSON object' : ' '}
                    InputProps={{ sx: { fontFamily: 'monospace', fontSize: 13 } }}
                  />
                </Box>
              )}

              {tab === 2 && (
                <Box sx={{ p: 2 }}>
                  {previewing && <CircularProgress size={24} />}
                  {!previewing && !preview && (
                    <Typography color="text.secondary">
                      Press Preview to render the current draft.
                    </Typography>
                  )}
                  {!previewing && preview?.error && (
                    <Alert severity="error">
                      <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                        {preview.error}
                      </Typography>
                    </Alert>
                  )}
                  {!previewing && preview?.html && (
                    <>
                      <Typography variant="caption" color="text.secondary">
                        Subject
                      </Typography>
                      <Typography variant="subtitle1" sx={{ mb: 2 }}>
                        {preview.subject}
                      </Typography>
                      {/*
                        An iframe, not dangerouslySetInnerHTML: email HTML carries its own <body>
                        styling and would otherwise repaint the console around it. sandbox="" also
                        keeps any script in a pasted template from running with the admin's session.
                      */}
                      <Box
                        component="iframe"
                        title="Email preview"
                        sandbox=""
                        srcDoc={preview.html}
                        sx={{
                          width: '100%',
                          height: 640,
                          border: '1px solid',
                          borderColor: 'divider',
                          borderRadius: 1,
                          bgcolor: '#fff',
                        }}
                      />
                    </>
                  )}
                </Box>
              )}
            </Paper>
          )}
        </Grid>
      </Grid>

      <Dialog open={testOpen} onClose={() => setTestOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Send a test email</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Sends the saved version of “{selected?.name}” using the sample data. Unsaved edits are
            not included — save first to test them.
          </Typography>
          <TextField
            autoFocus
            fullWidth
            size="small"
            type="email"
            label="Recipient"
            value={testRecipient}
            onChange={(e) => setTestRecipient(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTestOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={sendTest} disabled={testSending || !testRecipient.trim()}>
            Send
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>New email template</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Key"
              size="small"
              value={newTemplate.templateKey}
              onChange={(e) =>
                setNewTemplate({
                  ...newTemplate,
                  templateKey: e.target.value.toLowerCase().replace(/[^a-z0-9-]/g, '-'),
                })
              }
              helperText="Lowercase, hyphens only. Permanent — it is how the code finds this template."
            />
            <TextField
              label="Name"
              size="small"
              value={newTemplate.name}
              onChange={(e) => setNewTemplate({ ...newTemplate, name: e.target.value })}
            />
            <TextField
              label="Subject"
              size="small"
              value={newTemplate.subject}
              onChange={(e) => setNewTemplate({ ...newTemplate, subject: e.target.value })}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={create}
            disabled={!newTemplate.templateKey.trim() || !newTemplate.name.trim()}
          >
            Create
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(confirmDelete)} onClose={() => setConfirmDelete(null)}>
        <DialogTitle>Delete “{confirmDelete?.name}”?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            Any code sending <code>{confirmDelete?.templateKey}</code> will fall back to the version
            shipped with the service, or fail if there is none. This cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDelete(null)}>Cancel</Button>
          <Button color="error" variant="contained" onClick={remove}>
            Delete
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(confirmReset)} onClose={() => setConfirmReset(null)}>
        <DialogTitle>Reset “{confirmReset?.name}”?</DialogTitle>
        <DialogContent>
          <Typography variant="body2">
            The subject, body and sample data go back to the version shipped with the service. Your
            edits are discarded.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmReset(null)}>Cancel</Button>
          <Button color="warning" variant="contained" onClick={reset}>
            Reset
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={5000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default EmailTemplateManagement;
