import React, { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import { ArrowBack as BackIcon } from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from '../../hooks';
import ChatTranscriptPanel from '../../components/ChatTranscriptPanel';
import { parseDescription } from '../../constants/chatTranscript';
import { showSnackbar } from '../../store/slices/uiSlice';
import {
  SupportTicket,
  TICKET_STATUSES,
  TicketStatus,
  assignTicket,
  changeTicketStatus,
  fetchAllTickets,
  fetchTicketMessages,
  humanStatus,
  priorityColor,
  replyToTicket,
  statusColor,
} from '../../services/supportApi';

const FILTERS: { label: string; value: TicketStatus | null }[] = [
  { label: 'All', value: null },
  { label: 'Open', value: 'OPEN' },
  { label: 'In progress', value: 'IN_PROGRESS' },
  { label: 'Resolved', value: 'RESOLVED' },
  { label: 'Closed', value: 'CLOSED' },
];

const QUEUE_KEY = ['support', 'tickets', 'queue'];

const formatWhen = (iso: string): string => {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString();
};

/** The staff view of one ticket: the thread, a reply box, status transitions and assignment. */
const QueueDetail: React.FC<{ ticket: SupportTicket; onBack: () => void }> = ({
  ticket,
  onBack,
}) => {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();
  const me = useAppSelector((state) => state.auth.user);
  const [body, setBody] = useState('');

  const messagesKey = useMemo(() => ['support', 'tickets', ticket.id, 'messages'], [ticket.id]);
  const parsed = useMemo(() => parseDescription(ticket.description), [ticket.description]);

  const { data: messages, isLoading } = useQuery({
    queryKey: messagesKey,
    queryFn: () => fetchTicketMessages(ticket.id),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: messagesKey });
    queryClient.invalidateQueries({ queryKey: QUEUE_KEY });
  };

  const reply = useMutation({
    mutationFn: () => replyToTicket(ticket.id, body.trim()),
    onSuccess: () => {
      setBody('');
      invalidate();
    },
    onError: () => dispatch(showSnackbar({ message: 'Could not send the reply', severity: 'error' })),
  });

  const transition = useMutation({
    mutationFn: (status: TicketStatus) => changeTicketStatus(ticket.id, status),
    onSuccess: (updated) => {
      invalidate();
      dispatch(
        showSnackbar({ message: `Ticket #${updated.id} is ${humanStatus(updated.status)}`, severity: 'success' })
      );
    },
    // The service rejects transitions the actor is not entitled to make (assignee or admin only),
    // so a failure here is a real answer from the backend, not a UI glitch to swallow.
    onError: () =>
      dispatch(showSnackbar({ message: 'Could not change the status', severity: 'error' })),
  });

  const claim = useMutation({
    mutationFn: () => assignTicket(ticket.id, Number(me?.id)),
    onSuccess: () => {
      invalidate();
      dispatch(showSnackbar({ message: 'Ticket assigned to you', severity: 'success' }));
    },
    onError: () => dispatch(showSnackbar({ message: 'Could not assign the ticket', severity: 'error' })),
  });

  const mineAlready = String(ticket.assigneeId ?? '') === String(me?.id ?? '');

  return (
    <Card sx={{ borderRadius: 3 }}>
      <CardContent>
        <Button startIcon={<BackIcon />} onClick={onBack} sx={{ mb: 2, textTransform: 'none' }}>
          Back to queue
        </Button>

        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" sx={{ mb: 1 }}>
          <Typography variant="h6" sx={{ fontWeight: 700 }}>
            #{ticket.id} · {ticket.subject}
          </Typography>
          <Chip size="small" label={humanStatus(ticket.status)} color={statusColor(ticket.status)} />
          <Chip
            size="small"
            variant="outlined"
            label={ticket.priority}
            color={priorityColor(ticket.priority)}
          />
        </Stack>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          Reporter #{ticket.reporterId} · opened {formatWhen(ticket.createdAt)} ·{' '}
          {ticket.assigneeId ? `assigned to #${ticket.assigneeId}` : 'unassigned'}
        </Typography>

        {parsed.body && (
          <Typography variant="body2" sx={{ mt: 2, whiteSpace: 'pre-wrap' }}>
            {parsed.body}
          </Typography>
        )}
        {/* What the bot already said, so staff do not repeat or contradict it. */}
        <ChatTranscriptPanel ticketId={ticket.id} lines={parsed.transcript} />

        <Stack direction="row" spacing={1} sx={{ mt: 2 }} flexWrap="wrap" useFlexGap>
          {!mineAlready && (
            <Button
              size="small"
              variant="outlined"
              onClick={() => claim.mutate()}
              disabled={claim.isPending || !me?.id}
              sx={{ textTransform: 'none' }}
            >
              Assign to me
            </Button>
          )}
          {TICKET_STATUSES.filter((status) => status !== ticket.status).map((status) => (
            <Button
              key={status}
              size="small"
              variant="outlined"
              onClick={() => transition.mutate(status)}
              disabled={transition.isPending}
              sx={{ textTransform: 'none' }}
            >
              Mark {humanStatus(status)}
            </Button>
          ))}
        </Stack>

        <Divider sx={{ my: 3 }} />

        {isLoading && <CircularProgress size={24} />}
        {messages && messages.length === 0 && (
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            No replies yet.
          </Typography>
        )}

        <Stack spacing={1.5}>
          {messages?.map((message) => {
            const staff = String(message.senderId) !== String(ticket.reporterId);
            return (
              <Box
                key={message.id}
                sx={{ display: 'flex', justifyContent: staff ? 'flex-end' : 'flex-start' }}
              >
                <Box
                  sx={{
                    maxWidth: '80%',
                    px: 2,
                    py: 1.25,
                    borderRadius: 2,
                    bgcolor: staff ? 'primary.main' : 'action.hover',
                    color: staff ? 'primary.contrastText' : 'text.primary',
                  }}
                >
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                    {message.body}
                  </Typography>
                  <Typography variant="caption" sx={{ opacity: 0.75 }}>
                    {staff ? 'Support' : 'Reporter'} · {formatWhen(message.createdAt)}
                  </Typography>
                </Box>
              </Box>
            );
          })}
        </Stack>

        <Box sx={{ mt: 3, display: 'flex', gap: 1 }}>
          <TextField
            value={body}
            onChange={(event) => setBody(event.target.value)}
            placeholder="Reply to the reporter…"
            size="small"
            fullWidth
            multiline
            maxRows={4}
          />
          <Button
            variant="contained"
            disabled={!body.trim() || reply.isPending}
            onClick={() => reply.mutate()}
            sx={{ textTransform: 'none', alignSelf: 'flex-end' }}
          >
            {reply.isPending ? 'Sending…' : 'Send'}
          </Button>
        </Box>
      </CardContent>
    </Card>
  );
};

/** Every ticket on the platform, for staff. Backed by `AdminSupportController`. */
const SupportQueuePage: React.FC = () => {
  const [filter, setFilter] = useState<TicketStatus | null>(null);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const { data, isLoading, isError } = useQuery({
    queryKey: [...QUEUE_KEY, filter, page, size],
    queryFn: () => fetchAllTickets(filter, page, size),
  });

  const selected = data?.content.find((ticket) => ticket.id === selectedId) ?? null;

  if (selected) {
    return (
      <Box sx={{ p: 3 }}>
        <QueueDetail ticket={selected} onBack={() => setSelectedId(null)} />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" sx={{ fontWeight: 700 }}>
        Support queue
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
        Every ticket raised on the platform
      </Typography>

      <Tabs
        value={filter ?? 'ALL'}
        onChange={(_, value) => {
          setFilter(value === 'ALL' ? null : (value as TicketStatus));
          // A filter change reshuffles the result set, so the old offset means nothing.
          setPage(0);
        }}
        sx={{ mb: 2 }}
        variant="scrollable"
        scrollButtons="auto"
      >
        {FILTERS.map((option) => (
          <Tab key={option.label} label={option.label} value={option.value ?? 'ALL'} />
        ))}
      </Tabs>

      {isLoading && <CircularProgress />}
      {isError && <Alert severity="error">Could not load the queue.</Alert>}

      {data && (
        <Paper sx={{ borderRadius: 3, overflow: 'hidden' }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>#</TableCell>
                <TableCell>Subject</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Priority</TableCell>
                <TableCell>Reporter</TableCell>
                <TableCell>Assignee</TableCell>
                <TableCell>Updated</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.content.map((ticket) => (
                <TableRow
                  key={ticket.id}
                  hover
                  onClick={() => setSelectedId(ticket.id)}
                  sx={{ cursor: 'pointer' }}
                >
                  <TableCell>{ticket.id}</TableCell>
                  <TableCell>{ticket.subject}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={humanStatus(ticket.status)}
                      color={statusColor(ticket.status)}
                    />
                  </TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      variant="outlined"
                      label={ticket.priority}
                      color={priorityColor(ticket.priority)}
                    />
                  </TableCell>
                  <TableCell>#{ticket.reporterId}</TableCell>
                  <TableCell>{ticket.assigneeId ? `#${ticket.assigneeId}` : '—'}</TableCell>
                  <TableCell>{formatWhen(ticket.updatedAt)}</TableCell>
                </TableRow>
              ))}
              {data.content.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} align="center" sx={{ py: 5, color: 'text.secondary' }}>
                    No tickets match this filter.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
          <TablePagination
            component="div"
            count={data.totalElements}
            page={page}
            onPageChange={(_, next) => setPage(next)}
            rowsPerPage={size}
            onRowsPerPageChange={(event) => {
              setSize(Number(event.target.value));
              setPage(0);
            }}
            rowsPerPageOptions={[10, 20, 50]}
          />
        </Paper>
      )}
    </Box>
  );
};

export default SupportQueuePage;
