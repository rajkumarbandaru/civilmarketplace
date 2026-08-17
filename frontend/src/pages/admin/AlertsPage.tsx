import React, { useEffect, useState } from 'react';
import {
  Alert, Autocomplete, Box, Button, Card, CardContent, Chip, CircularProgress, Divider,
  FormControlLabel, Grid, Radio, RadioGroup, Snackbar, Switch, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import { Campaign, Schedule, Send } from '@mui/icons-material';
import {
  Announcement, cancelAnnouncement, fetchAnnouncements, publishAnnouncement,
} from '../../services/notificationApi';
import { apiErrorMessage } from '../../services/apiError';
import { SortableTableCell, useTableSort } from '../../components/admin/SortableTable';

/**
 * Super Admin's broadcast screen: send an alert to a role, several roles, or everyone.
 *
 * The backend for this already existed (`/api/v1/admin/announcements`) with nothing calling it —
 * an admin could not send a platform alert at all without a curl command. This is that endpoint's
 * console.
 *
 * An announcement writes one in-app notification per matching active user, so it lands in the bell
 * for every recipient; the history below records who was targeted and how many it reached.
 *
 * Sending can be immediate or booked for a later time — a maintenance notice for 2am should not
 * have to be typed at 2am. A scheduled alert resolves its audience when it goes out, not when it
 * is written, so it reaches whoever holds the role at delivery time; until then it can be
 * cancelled from the history table.
 */

/** Everyone the platform can address, as the roles table defines them. */
const ROLES = [
  'CUSTOMER', 'WORKER', 'LABOUR', 'LABOUR_CONTRACTOR', 'CIVIL_ENGINEER', 'STRUCTURAL_ENGINEER',
  'SITE_ENGINEER', 'ARCHITECT', 'INTERIOR_DESIGNER', 'EXTERIOR_DESIGNER', 'SURVEYOR',
  'MATERIAL_SUPPLIER', 'EQUIPMENT_RENTAL', 'PLUMBER', 'ELECTRICIAN', 'CARPENTER', 'PAINTER',
  'WELDER', 'FABRICATOR', 'CITY_MANAGER', 'REGIONAL_ADMIN', 'SUB_ADMIN', 'ADMIN', 'SUPER_ADMIN',
];

/**
 * `datetime-local` wants "YYYY-MM-DDTHH:mm" in the operator's own time, which is what they read
 * off the clock on the wall. Everything sent to the API is converted to an instant first.
 */
const toLocalInput = (date: Date): string => {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

/** Formats an instant from the API in the reader's own timezone. */
const formatInstant = (iso: string | null): string =>
  (iso ? new Date(iso).toLocaleString() : '—');

const STATUS_CHIP: Record<string, { label: string; color: 'default' | 'primary' | 'success' | 'warning' }> = {
  SCHEDULED: { label: 'Scheduled', color: 'warning' },
  SENDING: { label: 'Sending…', color: 'warning' },
  SENT: { label: 'Sent', color: 'success' },
  CANCELLED: { label: 'Cancelled', color: 'default' },
};

const AlertsPage: React.FC = () => {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [roles, setRoles] = useState<string[]>([]);
  const [everyone, setEveryone] = useState(true);
  const [when, setWhen] = useState<'now' | 'later'>('now');
  const [scheduledAt, setScheduledAt] = useState('');
  const [sending, setSending] = useState(false);
  const [cancelling, setCancelling] = useState<number | null>(null);
  const [history, setHistory] = useState<Announcement[]>([]);
  const [loading, setLoading] = useState(true);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>(
    { open: false, message: '', severity: 'success' }
  );

  // Newest first by default: the row an admin has just created — or is waiting on — is the one
  // they came to look at.
  //
  // The date each row actually shows: a scheduled alert is listed by when it is due, everything
  // else by when it went out. Sorting on the raw sentAt would leave scheduled rows blank and push
  // them to the bottom, away from the date the operator can see in the cell.
  const rowInstant = (a: Announcement): Date | null => {
    const iso = a.status === 'SCHEDULED' ? a.scheduledAt : (a.sentAt ?? a.createdAt);
    return iso ? new Date(iso) : null;
  };

  const { sorted, sort, onSort } = useTableSort(history, {
    title: (a: Announcement) => a.title,
    audience: (a: Announcement) => (a.targetRoles === '*' ? 'Everyone' : a.targetRoles ?? ''),
    status: (a: Announcement) => a.status,
    recipients: (a: Announcement) => (a.status === 'SENT' ? a.recipientCount ?? null : null),
    when: rowInstant,
  }, { key: 'when', direction: 'desc' });

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

  /** A scheduled send needs a time, and one that has not already passed. */
  const scheduleValid = when === 'now'
    || (!!scheduledAt && new Date(scheduledAt).getTime() > Date.now());

  const canSend = !!title.trim() && !!body.trim() && (everyone || roles.length > 0) && scheduleValid;

  const send = async () => {
    if (!canSend) return;
    setSending(true);
    try {
      const created = await publishAnnouncement({
        title: title.trim(),
        body: body.trim(),
        // "*" is the API's own everyone-marker, matching the convention the menu catalogue uses.
        targetRoles: everyone ? ['*'] : roles,
        // The picker's value is wall-clock local; toISOString pins it to an actual instant so the
        // UTC services fire it at the moment the operator meant.
        scheduledAt: when === 'later' ? new Date(scheduledAt).toISOString() : undefined,
      });
      setSnackbar({
        open: true,
        message: created.status === 'SCHEDULED'
          ? `Scheduled for ${formatInstant(created.scheduledAt)}.`
          : created.recipientCount != null
            ? `Sent to ${created.recipientCount} ${created.recipientCount === 1 ? 'person' : 'people'}.`
            : 'Alert sent.',
        severity: 'success',
      });
      setTitle('');
      setBody('');
      setRoles([]);
      setWhen('now');
      setScheduledAt('');
      loadHistory();
    } catch (error) {
      setSnackbar({ open: true, message: apiErrorMessage(error, 'Could not send the alert'), severity: 'error' });
    } finally {
      setSending(false);
    }
  };

  const cancel = async (id: number) => {
    setCancelling(id);
    try {
      await cancelAnnouncement(id);
      setSnackbar({ open: true, message: 'Scheduled alert cancelled.', severity: 'success' });
      loadHistory();
    } catch (error) {
      setSnackbar({ open: true, message: apiErrorMessage(error, 'Could not cancel'), severity: 'error' });
    } finally {
      setCancelling(null);
    }
  };

  return (
    <Box>
      <Box sx={{ mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 700 }}>Alerts &amp; Push Notifications</Typography>
        <Typography variant="body2" color="text.secondary">
          Send a message to everyone, or to specific roles — now, or at a time you choose. It
          appears in each recipient's notification bell.
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
            <Grid item xs={12}>
              <Divider sx={{ mb: 2 }} />
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                <Schedule fontSize="small" color="action" />
                <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>When to send</Typography>
              </Box>
              <RadioGroup row value={when} onChange={(e) => setWhen(e.target.value as 'now' | 'later')}>
                <FormControlLabel value="now" control={<Radio />} label="Send immediately" />
                <FormControlLabel value="later" control={<Radio />} label="Schedule for later" />
              </RadioGroup>
              {when === 'later' && (
                <TextField
                  type="datetime-local"
                  label="Send at"
                  value={scheduledAt}
                  onChange={(e) => setScheduledAt(e.target.value)}
                  InputLabelProps={{ shrink: true }}
                  // One minute ahead, so the picker cannot offer a time that is already gone by
                  // the time it is chosen.
                  inputProps={{ min: toLocalInput(new Date(Date.now() + 60_000)) }}
                  helperText={`Your local time (${Intl.DateTimeFormat().resolvedOptions().timeZone}). `
                    + 'Delivery is accurate to the minute.'}
                  sx={{ mt: 1, minWidth: 280 }}
                />
              )}
            </Grid>
          </Grid>

          {!canSend && (
            <Alert severity="info" sx={{ mt: 2, borderRadius: 2 }}>
              {when === 'later' && !scheduleValid && title.trim() && body.trim()
                ? 'Pick a send time in the future.'
                : 'A title, a message, and at least one audience are needed to send.'}
            </Alert>
          )}

          <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: 2 }}>
            <Button
              variant="contained"
              startIcon={sending
                ? <CircularProgress size={18} sx={{ color: '#fff' }} />
                : when === 'later' ? <Schedule /> : <Send />}
              disabled={!canSend || sending}
              onClick={send}
              sx={{ borderRadius: 3, px: 4 }}
            >
              {sending
                ? 'Saving…'
                : when === 'later' ? 'Schedule alert' : 'Send alert'}
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
                <SortableTableCell columnKey="title" sort={sort} onSort={onSort}>Title</SortableTableCell>
                <SortableTableCell columnKey="audience" sort={sort} onSort={onSort}>Audience</SortableTableCell>
                <SortableTableCell columnKey="status" sort={sort} onSort={onSort}>Status</SortableTableCell>
                <SortableTableCell columnKey="recipients" sort={sort} onSort={onSort} align="right">Recipients</SortableTableCell>
                <SortableTableCell columnKey="when" sort={sort} onSort={onSort}>Sent / due</SortableTableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {sorted.map((a) => (
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
                  <TableCell>
                    <Chip
                      size="small"
                      label={STATUS_CHIP[a.status]?.label ?? a.status}
                      color={STATUS_CHIP[a.status]?.color ?? 'default'}
                      variant={a.status === 'SENT' ? 'filled' : 'outlined'}
                    />
                  </TableCell>
                  {/* A count before the fan-out would be a guess — the audience is resolved at
                      send time, so who is in it is not known yet. */}
                  <TableCell align="right">
                    {a.status === 'SENT' ? a.recipientCount ?? '—' : '—'}
                  </TableCell>
                  <TableCell>
                    {a.status === 'SCHEDULED'
                      ? <Typography variant="body2" color="warning.main">
                          Due {formatInstant(a.scheduledAt)}
                        </Typography>
                      : formatInstant(a.sentAt ?? a.createdAt)}
                  </TableCell>
                  <TableCell align="right">
                    {a.status === 'SCHEDULED' && (
                      <Tooltip title="Cancel before it goes out">
                        <span>
                          <Button
                            size="small" color="inherit" disabled={cancelling === a.id}
                            onClick={() => cancel(a.id)}
                          >
                            {cancelling === a.id ? 'Cancelling…' : 'Cancel'}
                          </Button>
                        </span>
                      </Tooltip>
                    )}
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
