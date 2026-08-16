import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Collapse,
  Grid,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Clear as ClearIcon,
  ExpandLess,
  ExpandMore,
  GppGood as IntegrityIcon,
  WarningAmber as AnomalyIcon,
} from '@mui/icons-material';
import {
  AuditEvent,
  AuditFilters,
  actionColor,
  fetchAnomalies,
  fetchAuditEvents,
  verifyIntegrity,
} from '../../services/auditApi';
import { useDateTime } from '../../providers/UiConfigProvider';
import { SortableTableCell, useTableSort } from '../../components/admin/SortableTable';

/**
 * Common actions, offered as a dropdown rather than a free-text box.
 *
 * The backend upper-cases whatever it is given and matches exactly, so a typo silently returns an
 * empty page that looks identical to "this user did nothing" — the one answer an audit tool must
 * never give by accident.
 */
const ACTIONS = ['CREATE', 'READ', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'EXPORT'];

/** One event, expandable to its before/after state. */
const EventRow: React.FC<{ event: AuditEvent }> = ({ event }) => {
  const { formatDateTime: formatWhen } = useDateTime();
  const [open, setOpen] = useState(false);
  const hasState = Boolean(event.beforeState || event.afterState || event.reason);

  return (
    <>
      <TableRow hover>
        <TableCell sx={{ whiteSpace: 'nowrap' }}>{formatWhen(event.occurredAt)}</TableCell>
        <TableCell>{event.actorId ? `#${event.actorId}` : 'system'}</TableCell>
        <TableCell>{event.actorRole ?? '—'}</TableCell>
        <TableCell>
          <Chip size="small" label={event.action} color={actionColor(event.action)} />
        </TableCell>
        <TableCell>{event.entityType}</TableCell>
        <TableCell>{event.entityId ?? '—'}</TableCell>
        <TableCell>{event.subjectUserId ? `#${event.subjectUserId}` : '—'}</TableCell>
        <TableCell>{event.sourceService}</TableCell>
        <TableCell padding="none">
          {hasState && (
            <IconButton size="small" onClick={() => setOpen((value) => !value)} aria-label="Details">
              {open ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
            </IconButton>
          )}
        </TableCell>
      </TableRow>
      {hasState && (
        <TableRow>
          <TableCell colSpan={9} sx={{ py: 0, borderBottom: open ? undefined : 'none' }}>
            <Collapse in={open} unmountOnExit>
              <Box sx={{ py: 2 }}>
                {event.reason && (
                  <Typography variant="body2" sx={{ mb: 1 }}>
                    <strong>Reason:</strong> {event.reason}
                  </Typography>
                )}
                <Grid container spacing={2}>
                  {event.beforeState && (
                    <Grid item xs={12} md={6}>
                      <Typography variant="caption" sx={{ fontWeight: 700 }}>
                        Before
                      </Typography>
                      <Paper
                        variant="outlined"
                        sx={{ p: 1, fontSize: '0.75rem', overflowX: 'auto', maxHeight: 200 }}
                      >
                        <pre style={{ margin: 0 }}>{event.beforeState}</pre>
                      </Paper>
                    </Grid>
                  )}
                  {event.afterState && (
                    <Grid item xs={12} md={6}>
                      <Typography variant="caption" sx={{ fontWeight: 700 }}>
                        After
                      </Typography>
                      <Paper
                        variant="outlined"
                        sx={{ p: 1, fontSize: '0.75rem', overflowX: 'auto', maxHeight: 200 }}
                      >
                        <pre style={{ margin: 0 }}>{event.afterState}</pre>
                      </Paper>
                    </Grid>
                  )}
                </Grid>
              </Box>
            </Collapse>
          </TableCell>
        </TableRow>
      )}
    </>
  );
};

/**
 * Platform-wide user activity, backed by audit-service.
 *
 * This answers "what has this user done" and "who touched this record" — questions the Users
 * screen cannot, because it shows current state only and keeps no history of how it got there.
 */
const UserActivityPage: React.FC = () => {
  const [filters, setFilters] = useState<AuditFilters>({});
  const [applied, setApplied] = useState<AuditFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);

  const events = useQuery({
    queryKey: ['audit', 'events', applied, page, size],
    queryFn: () => fetchAuditEvents(applied, page, size),
  });

  const anomalies = useQuery({
    queryKey: ['audit', 'anomalies'],
    queryFn: () => fetchAnomalies(),
  });

  const integrity = useQuery({
    queryKey: ['audit', 'integrity'],
    queryFn: () => verifyIntegrity(),
    // The chain check walks the whole log, so it is not something to re-run on every focus.
    staleTime: 5 * 60 * 1000,
  });

  const setField = (key: keyof AuditFilters) => (value: string) =>
    setFilters((prev) => ({ ...prev, [key]: value }));

  const apply = () => {
    setApplied(filters);
    // A new filter set means a different result set; keeping the offset would land the admin on
    // page 4 of something they have not seen page 1 of.
    setPage(0);
  };

  const clear = () => {
    setFilters({});
    setApplied({});
    setPage(0);
  };

  const integrityOk =
    integrity.data && (integrity.data.valid === true || integrity.data.intact === true);

  // Sorts the page in hand. The log is server-paged, so this reorders the rows currently loaded
  // rather than the whole audit history — narrow with the filters above first.
  const { sorted, sort, onSort } = useTableSort(events.data?.data ?? [], {
    occurredAt: (e) => (e.occurredAt ? new Date(e.occurredAt) : null),
    actorId: (e) => e.actorId ?? null,
    actorRole: (e) => e.actorRole,
    action: (e) => e.action,
    entityType: (e) => e.entityType,
    entityId: (e) => e.entityId ?? null,
    subjectUserId: (e) => e.subjectUserId ?? null,
    sourceService: (e) => e.sourceService,
  }, { key: 'occurredAt', direction: 'desc' });

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>
        User Activity
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
        Every recorded action across the platform, by user
      </Typography>

      <Stack direction="row" spacing={1} sx={{ mb: 2 }} flexWrap="wrap" useFlexGap>
        {integrity.data && (
          <Tooltip title="Each event is hash-chained to the one before it; this verifies the chain has not been broken.">
            <Chip
              icon={<IntegrityIcon />}
              size="small"
              color={integrityOk ? 'success' : 'warning'}
              label={integrityOk ? 'Audit chain verified' : 'Audit chain: check report'}
            />
          </Tooltip>
        )}
        {anomalies.data && anomalies.data.totalElements > 0 && (
          <Chip
            icon={<AnomalyIcon />}
            size="small"
            color="warning"
            label={`${anomalies.data.totalElements} unacknowledged access anomal${
              anomalies.data.totalElements === 1 ? 'y' : 'ies'
            }`}
          />
        )}
      </Stack>

      {anomalies.data && anomalies.data.data.length > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
            Bulk access detected
          </Typography>
          {anomalies.data.data.slice(0, 3).map((anomaly) => (
            <Typography key={anomaly.id} variant="body2">
              Actor {anomaly.actorId ? `#${anomaly.actorId}` : 'unknown'} read{' '}
              {anomaly.recordsAccessed} {anomaly.entityType} records in {anomaly.windowMinutes} min
              {anomaly.detail ? ` — ${anomaly.detail}` : ''}
            </Typography>
          ))}
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2, borderRadius: 2 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              label="Actor user ID"
              value={filters.actorId ?? ''}
              onChange={(e) => setField('actorId')(e.target.value)}
              helperText="Who acted"
            />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              label="Subject user ID"
              value={filters.subjectUserId ?? ''}
              onChange={(e) => setField('subjectUserId')(e.target.value)}
              helperText="Who was affected"
            />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              select
              label="Action"
              value={filters.action ?? ''}
              onChange={(e) => setField('action')(e.target.value)}
            >
              <MenuItem value="">Any</MenuItem>
              {ACTIONS.map((action) => (
                <MenuItem key={action} value={action}>
                  {action}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              label="Entity type"
              value={filters.entityType ?? ''}
              onChange={(e) => setField('entityType')(e.target.value)}
              placeholder="Booking"
            />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              type="date"
              label="From"
              InputLabelProps={{ shrink: true }}
              value={filters.from ?? ''}
              onChange={(e) => setField('from')(e.target.value)}
            />
          </Grid>
          <Grid item xs={6} md={2}>
            <TextField
              fullWidth
              size="small"
              type="date"
              label="To"
              InputLabelProps={{ shrink: true }}
              value={filters.to ?? ''}
              onChange={(e) => setField('to')(e.target.value)}
            />
          </Grid>
        </Grid>
        <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
          <Button variant="contained" onClick={apply} sx={{ textTransform: 'none' }}>
            Apply filters
          </Button>
          <Button startIcon={<ClearIcon />} onClick={clear} sx={{ textTransform: 'none' }}>
            Clear
          </Button>
        </Stack>
      </Paper>

      {events.isLoading && <CircularProgress />}
      {events.isError && (
        <Alert severity="error">
          Could not load the audit log. It is admin-only — check that your role still allows it.
        </Alert>
      )}

      {events.data && (
        <Paper sx={{ borderRadius: 2, overflow: 'hidden' }}>
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <SortableTableCell columnKey="occurredAt" sort={sort} onSort={onSort}>When</SortableTableCell>
                  <SortableTableCell columnKey="actorId" sort={sort} onSort={onSort}>Actor</SortableTableCell>
                  <SortableTableCell columnKey="actorRole" sort={sort} onSort={onSort}>Role</SortableTableCell>
                  <SortableTableCell columnKey="action" sort={sort} onSort={onSort}>Action</SortableTableCell>
                  <SortableTableCell columnKey="entityType" sort={sort} onSort={onSort}>Entity</SortableTableCell>
                  <SortableTableCell columnKey="entityId" sort={sort} onSort={onSort}>Entity ID</SortableTableCell>
                  <SortableTableCell columnKey="subjectUserId" sort={sort} onSort={onSort}>Subject</SortableTableCell>
                  <SortableTableCell columnKey="sourceService" sort={sort} onSort={onSort}>Service</SortableTableCell>
                  <TableCell padding="none" />
                </TableRow>
              </TableHead>
              <TableBody>
                {sorted.map((event) => (
                  <EventRow key={event.id} event={event} />
                ))}
                {events.data.data.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={9} align="center" sx={{ py: 5, color: 'text.secondary' }}>
                      No activity matches these filters.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Box>
          <TablePagination
            component="div"
            count={events.data.totalElements}
            page={page}
            onPageChange={(_, next) => setPage(next)}
            rowsPerPage={size}
            onRowsPerPageChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
            rowsPerPageOptions={[25, 50, 100]}
          />
        </Paper>
      )}
    </Box>
  );
};

export default UserActivityPage;
