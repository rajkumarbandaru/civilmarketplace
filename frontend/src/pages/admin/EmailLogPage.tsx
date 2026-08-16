import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
  Grid, IconButton,
  InputAdornment, MenuItem, Paper, Snackbar, Stack, Table, TableBody, TableCell, TableContainer,
  TableHead, TablePagination, TableRow, TextField, Tooltip, Typography,
} from '@mui/material';
import { Info, NotificationsActive, Refresh, Search, Visibility } from '@mui/icons-material';
import {
  EmailLogEntry, EmailLogSummary, EmailStatus, NotificationChannel, fetchEmailLog,
  fetchEmailLogEntry, fetchEmailLogSummary, fetchLoggedTemplateKeys,
} from '../../services/emailApi';

/**
 * Every notification the platform tried to send — email, SMS, WhatsApp or in-app — and how far it
 * got.
 *
 * Read-only on purpose: this is the record of what a customer was told, and support reads it to
 * answer "did they get the confirmation?". Nothing here can rewrite a delivery.
 *
 * The status vocabulary is deliberately not "sent / not sent". SENT means the provider accepted
 * the message and has said nothing since — on SMTP that is where a successful send permanently
 * rests, because there is no delivery callback. Only Brevo's webhook can move a row on to
 * DELIVERED or UNDELIVERED, so a wall of SENT rows means the webhook is not wired up, not that
 * mail is stuck.
 */

const errorMessage = (err: unknown, fallback: string): string => {
  const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
  return message || fallback;
};

const STATUS_META: Record<EmailStatus, { label: string; color: 'default' | 'info' | 'success' | 'warning' | 'error'; help: string }> = {
  PENDING: { label: 'Pending', color: 'info', help: 'Queued — handed to the sender, outcome not known yet.' },
  SENT: { label: 'Sent', color: 'success', help: 'The provider accepted it. No delivery confirmation received (SMTP never sends one).' },
  DELIVERED: { label: 'Delivered', color: 'success', help: 'The provider confirmed it arrived. In-app notifications are delivered the moment they are created.' },
  UNDELIVERED: { label: 'Undelivered', color: 'error', help: 'Bounced, blocked, or rejected as spam.' },
  FAILED: { label: 'Failed', color: 'error', help: 'Could not be handed over at all — a render error or a rejected request.' },
  SKIPPED: { label: 'Not sent', color: 'warning', help: 'No provider configured for that channel, so the message was only written to the service log.' },
};

const STATUS_ORDER: EmailStatus[] = [
  'DELIVERED', 'SENT', 'PENDING', 'UNDELIVERED', 'FAILED', 'SKIPPED',
];

const CHANNEL_META: Record<NotificationChannel, { label: string; color: 'primary' | 'info' | 'success' | 'default' }> = {
  EMAIL: { label: 'Email', color: 'primary' },
  SMS: { label: 'SMS', color: 'info' },
  WHATSAPP: { label: 'WhatsApp', color: 'success' },
  IN_APP: { label: 'In-app', color: 'default' },
};

const CHANNEL_ORDER: NotificationChannel[] = ['EMAIL', 'SMS', 'WHATSAPP', 'IN_APP'];

const formatWhen = (iso: string): string => {
  if (!iso) return '—';
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString();
};

/**
 * The non-email channels rendered as the recipient saw them.
 *
 * Email gets an iframe because it arrives as a styled HTML document. The other three arrive as
 * plain text into a surface the recipient already knows — a phone's message list, a WhatsApp
 * thread, the app's notification bell — and showing that text in a grey box loses the thing an
 * admin is checking for: whether it reads right at the size and width it was actually read at.
 * So each is drawn in its own shape, at a phone-ish width, with nothing invented that was not in
 * the stored body.
 */

/** Only the emphasis marks WhatsApp itself supports, applied to already-escaped text. */
const whatsAppFormatted = (text: string): React.ReactNode =>
  text.split(/(\*[^*\n]+\*|_[^_\n]+_|~[^~\n]+~)/g).map((part, i) => {
    if (/^\*[^*\n]+\*$/.test(part)) {
      return <strong key={i}>{part.slice(1, -1)}</strong>;
    }
    if (/^_[^_\n]+_$/.test(part)) {
      return <em key={i}>{part.slice(1, -1)}</em>;
    }
    if (/^~[^~\n]+~$/.test(part)) {
      return <s key={i}>{part.slice(1, -1)}</s>;
    }
    return <React.Fragment key={i}>{part}</React.Fragment>;
  });

const formatClock = (iso: string): string => {
  const date = new Date(iso);
  return Number.isNaN(date.getTime())
    ? ''
    : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

/** A phone frame, so every preview sits at the width these messages are really read at. */
const PhoneFrame: React.FC<{ children: React.ReactNode; bg?: string }> = ({ children, bg }) => (
  <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
    <Box
      sx={{
        width: 330,
        minHeight: 260,
        p: 1.5,
        borderRadius: 4,
        border: '8px solid',
        borderColor: 'grey.800',
        bgcolor: bg ?? 'background.default',
        boxShadow: 3,
      }}
    >
      {children}
    </Box>
  </Box>
);

/**
 * An SMS, with the segment count — the one thing about a text message that costs money and that
 * nobody notices until the bill. 160 GSM-7 characters per segment, 70 if any character forces
 * UCS-2, which is what a single emoji or curly quote does to an otherwise plain message.
 */
const SmsPreview: React.FC<{ body: string; recipient: string }> = ({ body, recipient }) => {
  const unicode = /[^\u0000-\u007F\u00A0-\u00FF\u20AC]/.test(body);
  const perSegment = unicode ? 70 : 160;
  const segments = Math.max(1, Math.ceil(body.length / perSegment));
  return (
    <>
      <PhoneFrame>
        <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1, textAlign: 'center' }}>
          {recipient}
        </Typography>
        <Box
          sx={{
            bgcolor: 'grey.300',
            color: 'grey.900',
            px: 1.75,
            py: 1.25,
            borderRadius: '18px 18px 18px 4px',
            maxWidth: '85%',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-word',
            fontSize: 14,
            lineHeight: 1.45,
          }}
        >
          {body}
        </Box>
      </PhoneFrame>
      <Typography variant="caption" color="text.secondary" display="block" textAlign="center">
        {body.length} characters · {segments} SMS segment{segments > 1 ? 's' : ''}
        {unicode && ' · contains non-GSM characters, so segments are 70 chars'}
      </Typography>
    </>
  );
};

/** A WhatsApp thread: outgoing bubble, its own emphasis marks applied, on the familiar ground. */
const WhatsAppPreview: React.FC<{ body: string; sentAt: string }> = ({ body, sentAt }) => (
  <PhoneFrame bg="#0b141a">
    <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
      <Box
        sx={{
          bgcolor: '#005c4b',
          color: '#e9edef',
          px: 1.5,
          py: 1,
          borderRadius: '8px 8px 2px 8px',
          maxWidth: '88%',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
          fontSize: 14,
          lineHeight: 1.5,
        }}
      >
        {whatsAppFormatted(body)}
        <Typography
          component="span"
          sx={{ display: 'block', textAlign: 'right', fontSize: 11, opacity: 0.65, mt: 0.5 }}
        >
          {formatClock(sentAt)}
        </Typography>
      </Box>
    </Box>
  </PhoneFrame>
);

/** The bell card: title above message, which is exactly what the in-app list renders. */
const InAppPreview: React.FC<{ title: string; body: string; sentAt: string }> = ({
  title,
  body,
  sentAt,
}) => (
  <PhoneFrame>
    <Paper variant="outlined" sx={{ p: 1.75, display: 'flex', gap: 1.25 }}>
      <NotificationsActive fontSize="small" color="primary" sx={{ mt: 0.25 }} />
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="subtitle2" sx={{ lineHeight: 1.3 }}>
          {title}
        </Typography>
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word', mt: 0.5 }}
        >
          {body}
        </Typography>
        <Typography variant="caption" color="text.disabled" display="block" sx={{ mt: 0.75 }}>
          {formatWhen(sentAt)}
        </Typography>
      </Box>
    </Paper>
  </PhoneFrame>
);

const EmailLogPage: React.FC = () => {
  const [rows, setRows] = useState<EmailLogEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [summary, setSummary] = useState<EmailLogSummary | null>(null);
  const [templateKeys, setTemplateKeys] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [status, setStatus] = useState<string>('ALL');
  const [channel, setChannel] = useState<string>('ALL');
  const [templateKey, setTemplateKey] = useState<string>('ALL');
  const [search, setSearch] = useState('');
  /** Debounced copy of `search`, so typing does not fire a request per keystroke. */
  const [appliedSearch, setAppliedSearch] = useState('');
  const [detail, setDetail] = useState<EmailLogEntry | null>(null);
  /** The entry whose message is open in the viewer, fetched in full for its body. */
  const [viewing, setViewing] = useState<EmailLogEntry | null>(null);
  const [viewLoading, setViewLoading] = useState(false);
  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string }>({ open: false, message: '' });

  useEffect(() => {
    const timer = setTimeout(() => {
      setAppliedSearch(search.trim());
      setPage(0);
    }, 400);
    return () => clearTimeout(timer);
  }, [search]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const [paged, counts] = await Promise.all([
        fetchEmailLog({
          status: status === 'ALL' ? undefined : status,
          channel: channel === 'ALL' ? undefined : channel,
          templateKey: templateKey === 'ALL' ? undefined : templateKey,
          search: appliedSearch || undefined,
          page,
          size,
        }),
        fetchEmailLogSummary(),
      ]);
      setRows(paged.content ?? []);
      setTotal(paged.totalElements ?? 0);
      setSummary(counts);
    } catch (err) {
      setSnackbar({ open: true, message: errorMessage(err, 'Could not load the notification log') });
    } finally {
      setLoading(false);
    }
  }, [status, channel, templateKey, appliedSearch, page, size]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    fetchLoggedTemplateKeys()
      .then(setTemplateKeys)
      .catch(() => setTemplateKeys([]));
  }, []);

  /**
   * The list omits bodies to stay small, so the eye fetches the one row being looked at. The
   * summary row is shown immediately and filled in when the body arrives, rather than holding an
   * empty dialog until the request lands.
   */
  const viewMessage = async (row: EmailLogEntry) => {
    setViewing(row);
    setViewLoading(true);
    try {
      setViewing(await fetchEmailLogEntry(row.id));
    } catch (err) {
      setSnackbar({ open: true, message: errorMessage(err, 'Could not load the message') });
    } finally {
      setViewLoading(false);
    }
  };

  const tiles = useMemo(() => {
    const byStatus = summary?.byStatus ?? {};
    return STATUS_ORDER.map((key) => ({
      key,
      count: byStatus[key] ?? 0,
      ...STATUS_META[key],
    }));
  }, [summary]);

  return (
    <Box>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Typography variant="h5" fontWeight={600}>
          Notifications
        </Typography>
        <Tooltip title="Refresh">
          <IconButton onClick={load}>
            <Refresh />
          </IconButton>
        </Tooltip>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
        Every message the platform tried to send, on every channel — what produced it, who it went
        to, and what the provider said about it. {summary ? `${summary.total} in total.` : ''}
      </Typography>

      <Grid container spacing={1.5} sx={{ mb: 2 }}>
        {tiles.map((tile) => (
          <Grid item xs={6} sm={4} md={2} key={tile.key}>
            <Paper
              variant="outlined"
              onClick={() => {
                setStatus(status === tile.key ? 'ALL' : tile.key);
                setPage(0);
              }}
              sx={{
                p: 1.5,
                cursor: 'pointer',
                borderColor: status === tile.key ? 'primary.main' : 'divider',
                borderWidth: status === tile.key ? 2 : 1,
              }}
            >
              <Tooltip title={tile.help}>
                <Box>
                  <Typography variant="h6" fontWeight={700}>
                    {tile.count}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {tile.label}
                  </Typography>
                </Box>
              </Tooltip>
            </Paper>
          </Grid>
        ))}
      </Grid>

      <Paper variant="outlined" sx={{ mb: 2, p: 1.5 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
          <TextField
            size="small"
            fullWidth
            placeholder="Search by recipient or subject"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <Search fontSize="small" />
                </InputAdornment>
              ),
            }}
          />
          <TextField
            size="small"
            select
            label="Type"
            value={channel}
            onChange={(e) => {
              setChannel(e.target.value);
              setPage(0);
            }}
            sx={{ minWidth: 150 }}
          >
            <MenuItem value="ALL">All types</MenuItem>
            {CHANNEL_ORDER.map((key) => (
              <MenuItem key={key} value={key}>
                {CHANNEL_META[key].label}
                {summary?.byChannel?.[key] ? ` (${summary.byChannel[key]})` : ''}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            size="small"
            select
            label="Status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
            sx={{ minWidth: 180 }}
          >
            <MenuItem value="ALL">All statuses</MenuItem>
            {STATUS_ORDER.map((key) => (
              <MenuItem key={key} value={key}>
                {STATUS_META[key].label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            size="small"
            select
            label="Source"
            value={templateKey}
            onChange={(e) => {
              setTemplateKey(e.target.value);
              setPage(0);
            }}
            sx={{ minWidth: 220 }}
          >
            <MenuItem value="ALL">All sources</MenuItem>
            {templateKeys.map((key) => (
              <MenuItem key={key} value={key}>
                {key}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </Paper>

      <Paper variant="outlined">
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>When</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Recipient</TableCell>
                <TableCell>Source</TableCell>
                <TableCell>Subject</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Provider</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {loading && (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                    <CircularProgress size={24} />
                  </TableCell>
                </TableRow>
              )}
              {!loading && rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                    <Typography variant="body2" color="text.secondary">
                      No notification matches these filters.
                    </Typography>
                  </TableCell>
                </TableRow>
              )}
              {!loading &&
                rows.map((row) => {
                  const meta = STATUS_META[row.status] ?? STATUS_META.PENDING;
                  return (
                    <TableRow key={row.id} hover>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatWhen(row.createdAt)}</TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          variant="outlined"
                          label={CHANNEL_META[row.channel]?.label ?? row.channel}
                          color={CHANNEL_META[row.channel]?.color ?? 'default'}
                        />
                      </TableCell>
                      <TableCell>{row.recipient}</TableCell>
                      <TableCell>
                        <Tooltip title={row.templateKey}>
                          <span>{row.templateName}</span>
                        </Tooltip>
                      </TableCell>
                      <TableCell sx={{ maxWidth: 320 }}>
                        <Typography variant="body2" noWrap title={row.subject}>
                          {row.subject}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Tooltip title={meta.help}>
                          <Chip size="small" label={meta.label} color={meta.color} />
                        </Tooltip>
                      </TableCell>
                      <TableCell>{row.provider}</TableCell>
                      <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                        <Tooltip title="View the message that was sent">
                          <IconButton size="small" onClick={() => viewMessage(row)}>
                            <Visibility fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Delivery details">
                          <IconButton size="small" onClick={() => setDetail(row)}>
                            <Info fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  );
                })}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={total}
          page={page}
          rowsPerPage={size}
          onPageChange={(_, next) => setPage(next)}
          onRowsPerPageChange={(e) => {
            setSize(parseInt(e.target.value, 10));
            setPage(0);
          }}
          rowsPerPageOptions={[10, 25, 50, 100]}
        />
      </Paper>

      <Dialog open={Boolean(viewing)} onClose={() => setViewing(null)} fullWidth maxWidth="md">
        <DialogTitle sx={{ pb: 0.5 }}>
          {viewing?.subject}
          <Typography variant="caption" color="text.secondary" display="block">
            {CHANNEL_META[viewing?.channel as NotificationChannel]?.label ?? viewing?.channel}
            {' to '}
            {viewing?.recipient}
            {' · '}
            {formatWhen(viewing?.createdAt ?? '')}
          </Typography>
        </DialogTitle>
        <DialogContent dividers>
          {viewLoading && !viewing?.body && (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress size={24} />
            </Box>
          )}
          {!viewLoading && !viewing?.body && (
            <Alert severity="info">
              This message was sent before the platform started keeping message bodies, so only its
              subject was recorded.
            </Alert>
          )}
          {viewing?.body && viewing.channel === 'EMAIL' && (
            /* Sandboxed, exactly as in the template preview: this is email HTML with its own
               <body> styling, and it is content that was composed elsewhere. */
            <Box
              component="iframe"
              title="Sent message"
              sandbox=""
              srcDoc={viewing.body}
              sx={{
                width: '100%',
                height: 600,
                border: 'none',
                bgcolor: '#fff',
              }}
            />
          )}
          {viewing?.body && viewing.channel === 'SMS' && (
            <SmsPreview body={viewing.body} recipient={viewing.recipient} />
          )}
          {viewing?.body && viewing.channel === 'WHATSAPP' && (
            <WhatsAppPreview body={viewing.body} sentAt={viewing.createdAt} />
          )}
          {viewing?.body && viewing.channel === 'IN_APP' && (
            <InAppPreview title={viewing.subject} body={viewing.body} sentAt={viewing.createdAt} />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setViewing(null)}>Close</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(detail)} onClose={() => setDetail(null)} fullWidth maxWidth="sm">
        <DialogTitle>Notification #{detail?.id}</DialogTitle>
        <DialogContent dividers>
          {detail && (
            <Stack spacing={1.5}>
              {detail.errorMessage && (
                <Alert severity={detail.status === 'SKIPPED' ? 'warning' : 'error'}>
                  {detail.errorMessage}
                </Alert>
              )}
              <Field
                label="Type"
                value={CHANNEL_META[detail.channel]?.label ?? detail.channel}
              />
              <Field label="Recipient" value={detail.recipient} />
              <Field label="Subject" value={detail.subject} />
              <Field label="Source" value={`${detail.templateName} (${detail.templateKey})`} />
              <Field label="Status" value={STATUS_META[detail.status]?.label ?? detail.status} />
              <Field label="Provider" value={detail.provider} />
              <Field label="Provider message id" value={detail.providerMessageId ?? '—'} />
              <Field
                label="Triggered by"
                value={detail.triggeredBy ? `Admin #${detail.triggeredBy}` : 'Application event'}
              />
              <Field label="Queued" value={formatWhen(detail.createdAt)} />
              <Field label="Last update" value={formatWhen(detail.updatedAt)} />
            </Stack>
          )}
        </DialogContent>
      </Dialog>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={5000}
        onClose={() => setSnackbar({ open: false, message: '' })}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="error" onClose={() => setSnackbar({ open: false, message: '' })}>
          {snackbar.message}
        </Alert>
      </Snackbar>
    </Box>
  );
};

const Field: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <Box>
    <Typography variant="caption" color="text.secondary">
      {label}
    </Typography>
    <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>
      {value}
    </Typography>
  </Box>
);

export default EmailLogPage;
