import React, { useEffect, useState } from 'react';
import {
  Alert, Autocomplete, Box, Button, Card, CardContent, Chip, CircularProgress, Divider,
  FormControlLabel, Grid, Snackbar, Switch, Table, TableBody, TableCell, TableContainer,
  TableHead, TableRow, TextField, Typography,
} from '@mui/material';
import { Campaign, Send } from '@mui/icons-material';
import {
  Announcement, fetchAnnouncements, publishAnnouncement,
} from '../../services/notificationApi';
import { apiErrorMessage } from '../../services/apiError';

/**
 * Super Admin's broadcast screen: send an alert to a role, several roles, or everyone.
 *
 * The backend for this already existed (`/api/v1/admin/announcements`) with nothing calling it —
 * an admin could not send a platform alert at all without a curl command. This is that endpoint's
 * console.
 *
 * An announcement writes one in-app notification per matching active user, so it lands in the bell
 * for every recipient; the history below records who was targeted and how many it reached.
 */

/** Everyone the platform can address, as the roles table defines them. */
const ROLES = [
  'CUSTOMER', 'WORKER', 'LABOUR', 'LABOUR_CONTRACTOR', 'CIVIL_ENGINEER', 'STRUCTURAL_ENGINEER',
  'SITE_ENGINEER', 'ARCHITECT', 'INTERIOR_DESIGNER', 'EXTERIOR_DESIGNER', 'SURVEYOR',
  'MATERIAL_SUPPLIER', 'EQUIPMENT_RENTAL', 'PLUMBER', 'ELECTRICIAN', 'CARPENTER', 'PAINTER',
  'WELDER', 'FABRICATOR', 'CITY_MANAGER', 'REGIONAL_ADMIN', 'SUB_ADMIN', 'ADMIN', 'SUPER_ADMIN',
];

const AlertsPage: React.FC = () => {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [roles, setRoles] = useState<string[]>([]);
  const [everyone, setEveryone] = useState(true);
  const [sending, setSending] = useState(false);
  const [history, setHistory] = useState<Announcement[]>([]);
  const [loading, setLoading] = useState(true);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>(
    { open: false, message: '', severity: 'success' }
  );

  const loadHistory = async () => {
    try {
      setLoading(true);
      const page = await fetchAnnouncements(0, 20);
      setHistory(page.content ?? []);
    } catch (error) {
      setSnackbar({ open: true, message: apiErrorMessage(error, 'Could not load history'), severity: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadHistory();
  }, []);

  const canSend = title.trim() && body.trim() && (everyone || roles.length > 0);

  const send = async () => {
    if (!canSend) return;
    setSending(true);
    try {
      const created = await publishAnnouncement({
        title: title.trim(),
        body: body.trim(),
        // "*" is the API's own everyone-marker, matching the convention the menu catalogue uses.
        targetRoles: everyone ? ['*'] : roles,
      });
      setSnackbar({
        open: true,
        message: created.recipientCount != null
          ? `Sent to ${created.recipientCount} ${created.recipientCount === 1 ? 'person' : 'people'}.`
          : 'Alert sent.',
        severity: 'success',
      });
      setTitle('');
      setBody('');
      setRoles([]);
      loadHistory();
    } catch (error) {
      setSnackbar({ open: true, message: apiErrorMessage(error, 'Could not send the alert'), severity: 'error' });
    } finally {
      setSending(false);
    }
  };

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>Alerts &amp; Push Notifications</Typography>
        <Typography variant="body2" color="text.secondary">
          Send a message to everyone, or to specific roles. It appears in each recipient's
          notification bell straight away.
        </Typography>
      </Box>

      <Card sx={{ borderRadius: 3, mb: 4 }}>
        <CardContent sx={{ p: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
            <Campaign color="primary" />
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>New alert</Typography>
          </Box>

          <Grid container spacing={2}>
            <Grid item xs={12}>
              <TextField
                fullWidth label="Title" value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Scheduled maintenance on Sunday"
              />
            </Grid>
            <Grid item xs={12}>
              <TextField
                fullWidth multiline rows={4} label="Message" value={body}
                onChange={(e) => setBody(e.target.value)}
                placeholder="The platform will be unavailable from 2am to 4am IST while we upgrade."
              />
            </Grid>
            <Grid item xs={12}>
              <FormControlLabel
                control={<Switch checked={everyone} onChange={(e) => setEveryone(e.target.checked)} />}
                label="Send to everyone"
              />
              {!everyone && (
                <Autocomplete
                  multiple
                  options={ROLES}
                  value={roles}
                  onChange={(_, value) => setRoles(value)}
                  renderTags={(value, getTagProps) =>
                    value.map((option, index) => (
                      <Chip label={option} size="small" {...getTagProps({ index })} key={option} />
                    ))
                  }
                  renderInput={(params) => (
                    <TextField {...params} label="Roles" placeholder="Choose one or more roles" />
                  )}
                  sx={{ mt: 1 }}
                />
              )}
            </Grid>
          </Grid>

          {!canSend && (
            <Alert severity="info" sx={{ mt: 2, borderRadius: 2 }}>
              A title, a message, and at least one audience are needed to send.
            </Alert>
          )}

          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 2 }}>
            <Button
              variant="contained"
              startIcon={sending ? <CircularProgress size={18} sx={{ color: '#fff' }} /> : <Send />}
              disabled={!canSend || sending}
              onClick={send}
              sx={{ borderRadius: 3, px: 4 }}
            >
              {sending ? 'Sending…' : 'Send alert'}
            </Button>
          </Box>
        </CardContent>
      </Card>

      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>Recent alerts</Typography>
      <Divider sx={{ mb: 2 }} />

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}><CircularProgress /></Box>
      ) : history.length === 0 ? (
        <Alert severity="info">No alerts have been sent yet.</Alert>
      ) : (
        <TableContainer component={Card} sx={{ borderRadius: 3 }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Title</TableCell>
                <TableCell>Audience</TableCell>
                <TableCell align="right">Recipients</TableCell>
                <TableCell>Sent</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {history.map((a) => (
                <TableRow key={a.id} hover>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{a.title}</Typography>
                    <Typography variant="caption" color="text.secondary">{a.body}</Typography>
                  </TableCell>
                  <TableCell>
                    {a.targetRoles === '*'
                      ? <Chip size="small" label="Everyone" color="primary" />
                      : a.targetRoles?.split(',').map((r) => (
                          <Chip key={r} size="small" label={r.trim()} sx={{ mr: 0.5, mb: 0.5 }} />
                        ))}
                  </TableCell>
                  <TableCell align="right">{a.recipientCount ?? '—'}</TableCell>
                  <TableCell>
                    {a.createdAt ? new Date(a.createdAt).toLocaleString() : '—'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Snackbar
        open={snackbar.open} autoHideDuration={5000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} onClose={() => setSnackbar({ ...snackbar, open: false })}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default AlertsPage;
