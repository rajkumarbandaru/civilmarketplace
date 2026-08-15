import React, { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  MenuItem,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
} from '@mui/material';
import {
  Add as AddIcon,
  ArrowBack as BackIcon,
  ConfirmationNumber as TicketIcon,
} from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from '../../hooks';
import ChatTranscriptPanel from '../../components/ChatTranscriptPanel';
import { parseDescription } from '../../constants/chatTranscript';
import { clearSupportTicketDraft, showSnackbar } from '../../store/slices/uiSlice';
import {
  SupportTicket,
  TICKET_PRIORITIES,
  TicketPriority,
  TicketStatus,
  createTicket,
  fetchMyTickets,
  fetchTicketMessages,
  humanStatus,
  priorityColor,
  replyToTicket,
  statusColor,
} from '../../services/supportApi';

/**
 * The tabs the customer filters by. `null` is "everything" rather than a fifth status, so the
 * default view does not have to name every status that exists — new ones would silently drop out.
 */
const FILTERS: { label: string; value: TicketStatus | null }[] = [
  { label: 'All', value: null },
  { label: 'Open', value: 'OPEN' },
  { label: 'In progress', value: 'IN_PROGRESS' },
  { label: 'Resolved', value: 'RESOLVED' },
  { label: 'Closed', value: 'CLOSED' },
];

const formatWhen = (iso: string): string => {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString();
};

const MY_TICKETS_KEY = ['support', 'tickets', 'mine'];

/** The reply thread for one ticket, plus the box to add to it. */
const TicketThread: React.FC<{ ticket: SupportTicket; onBack: () => void }> = ({
  ticket,
  onBack,
}) => {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();
  const me = useAppSelector((state) => state.auth.user);
  const [body, setBody] = useState('');

  const messagesKey = useMemo(() => ['support', 'tickets', ticket.id, 'messages'], [ticket.id]);
  const parsed = useMemo(() => parseDescription(ticket.description), [ticket.description]);

  const { data: messages, isLoading, isError } = useQuery({
    queryKey: messagesKey,
    queryFn: () => fetchTicketMessages(ticket.id),
  });

  const reply = useMutation({
    mutationFn: () => replyToTicket(ticket.id, body.trim()),
    onSuccess: () => {
      setBody('');
      queryClient.invalidateQueries({ queryKey: messagesKey });
      // A reply can move the ticket off OPEN, so the list behind this view is stale too.
      queryClient.invalidateQueries({ queryKey: MY_TICKETS_KEY });
    },
    onError: () => {
      dispatch(showSnackbar({ message: 'Could not send the reply', severity: 'error' }));
    },
  });

  // A closed ticket keeps its thread readable but takes no more replies — the alternative is a
  // message posted into something nobody is watching.
  const closed = ticket.status === 'CLOSED';

  return (
    <Card sx={{ borderRadius: 3 }}>
      <CardContent>
        <Button startIcon={<BackIcon />} onClick={onBack} sx={{ mb: 2, textTransform: 'none' }}>
          All tickets
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
          Opened {formatWhen(ticket.createdAt)}
          {ticket.category ? ` · ${ticket.category}` : ''}
        </Typography>

        {parsed.body && (
          <Typography variant="body2" sx={{ mt: 2, whiteSpace: 'pre-wrap' }}>
            {parsed.body}
          </Typography>
        )}
        <ChatTranscriptPanel ticketId={ticket.id} lines={parsed.transcript} />

        <Divider sx={{ my: 3 }} />

        {isLoading && <CircularProgress size={24} />}
        {isError && <Alert severity="error">Could not load the replies.</Alert>}

        {messages && messages.length === 0 && (
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            No replies yet. Our team will respond here.
          </Typography>
        )}

        <Stack spacing={1.5}>
          {messages?.map((message) => {
            const mine = String(message.senderId) === String(me?.id);
            return (
              <Box
                key={message.id}
                sx={{ display: 'flex', justifyContent: mine ? 'flex-end' : 'flex-start' }}
              >
                <Box
                  sx={{
                    maxWidth: '80%',
                    px: 2,
                    py: 1.25,
                    borderRadius: 2,
                    bgcolor: mine ? 'primary.main' : 'action.hover',
                    color: mine ? 'primary.contrastText' : 'text.primary',
                  }}
                >
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                    {message.body}
                  </Typography>
                  <Typography variant="caption" sx={{ opacity: 0.75 }}>
                    {mine ? 'You' : 'Support'} · {formatWhen(message.createdAt)}
                  </Typography>
                </Box>
              </Box>
            );
          })}
        </Stack>

        {closed ? (
          <Alert severity="info" sx={{ mt: 3 }}>
            This ticket is closed. Open a new one if you still need help.
          </Alert>
        ) : (
          <Box sx={{ mt: 3, display: 'flex', gap: 1 }}>
            <TextField
              value={body}
              onChange={(event) => setBody(event.target.value)}
              placeholder="Add a reply…"
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
        )}
      </CardContent>
    </Card>
  );
};

/**
 * The customer's own ticket list, the thread for any one of them, and the form to open a new one.
 *
 * The assistant hands its unanswered conversations here through `ui.supportTicketDraft`, which
 * this page consumes and clears — see the widget's escalation path.
 */
const SupportTicketsPage: React.FC = () => {
  const dispatch = useAppDispatch();
  const queryClient = useQueryClient();
  const draft = useAppSelector((state) => state.ui.supportTicketDraft);

  const [filter, setFilter] = useState<TicketStatus | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [composerOpen, setComposerOpen] = useState(false);
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM');

  const { data, isLoading, isError } = useQuery({
    queryKey: [...MY_TICKETS_KEY, filter],
    queryFn: () => fetchMyTickets(filter),
  });

  // Consume the assistant's handover exactly once: it opens the composer prefilled, then the
  // draft is cleared so returning to this page later does not reopen a form nobody asked for.
  useEffect(() => {
    if (!draft) return;
    setSubject(draft.subject);
    setDescription(draft.description);
    setComposerOpen(true);
    dispatch(clearSupportTicketDraft());
  }, [draft, dispatch]);

  const open = useMutation({
    mutationFn: () =>
      createTicket({ subject: subject.trim(), description: description.trim(), priority }),
    onSuccess: (ticket) => {
      setComposerOpen(false);
      setSubject('');
      setDescription('');
      setPriority('MEDIUM');
      queryClient.invalidateQueries({ queryKey: MY_TICKETS_KEY });
      setSelectedId(ticket.id);
      dispatch(showSnackbar({ message: `Ticket #${ticket.id} opened`, severity: 'success' }));
    },
    onError: () => {
      dispatch(showSnackbar({ message: 'Could not open the ticket', severity: 'error' }));
    },
  });

  const selected = data?.content.find((ticket) => ticket.id === selectedId) ?? null;

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            Support
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            Your tickets and their replies
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => setComposerOpen(true)}
          sx={{ textTransform: 'none', borderRadius: 2 }}
        >
          New ticket
        </Button>
      </Stack>

      {selected ? (
        <TicketThread ticket={selected} onBack={() => setSelectedId(null)} />
      ) : (
        <>
          <Tabs
            value={filter ?? 'ALL'}
            onChange={(_, value) => setFilter(value === 'ALL' ? null : (value as TicketStatus))}
            sx={{ mb: 2 }}
            variant="scrollable"
            scrollButtons="auto"
          >
            {FILTERS.map((option) => (
              <Tab key={option.label} label={option.label} value={option.value ?? 'ALL'} />
            ))}
          </Tabs>

          {isLoading && <CircularProgress />}
          {isError && <Alert severity="error">Could not load your tickets.</Alert>}

          {data && data.content.length === 0 && (
            <Card sx={{ borderRadius: 3 }}>
              <CardContent sx={{ textAlign: 'center', py: 6 }}>
                <TicketIcon sx={{ fontSize: 44, color: 'text.disabled' }} />
                <Typography variant="subtitle1" sx={{ mt: 1, fontWeight: 600 }}>
                  No tickets here
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  Open one and our team will pick it up.
                </Typography>
              </CardContent>
            </Card>
          )}

          <Stack spacing={1.5}>
            {data?.content.map((ticket) => (
              <Card
                key={ticket.id}
                onClick={() => setSelectedId(ticket.id)}
                sx={{ borderRadius: 3, cursor: 'pointer', '&:hover': { boxShadow: 4 } }}
              >
                <CardContent>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                    <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1 }}>
                      #{ticket.id} · {ticket.subject}
                    </Typography>
                    <Chip
                      size="small"
                      label={humanStatus(ticket.status)}
                      color={statusColor(ticket.status)}
                    />
                    <Chip
                      size="small"
                      variant="outlined"
                      label={ticket.priority}
                      color={priorityColor(ticket.priority)}
                    />
                  </Stack>
                  <Typography
                    variant="body2"
                    sx={{
                      color: 'text.secondary',
                      mt: 0.5,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                    }}
                  >
                    {/* The raw description would lead with the transcript markers on any ticket
                        raised from the assistant, which reads as noise in a one-line preview. */}
                    {parseDescription(ticket.description).body || 'Raised from the assistant chat'}
                  </Typography>
                  <Typography variant="caption" sx={{ color: 'text.disabled' }}>
                    Updated {formatWhen(ticket.updatedAt)}
                  </Typography>
                </CardContent>
              </Card>
            ))}
          </Stack>
        </>
      )}

      <Dialog open={composerOpen} onClose={() => setComposerOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>New support ticket</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Subject"
              value={subject}
              onChange={(event) => setSubject(event.target.value)}
              fullWidth
              inputProps={{ maxLength: 200 }}
            />
            <TextField
              label="Description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              fullWidth
              multiline
              minRows={5}
              inputProps={{ maxLength: 4000 }}
            />
            <TextField
              label="Priority"
              select
              value={priority}
              onChange={(event) => setPriority(event.target.value as TicketPriority)}
              fullWidth
            >
              {TICKET_PRIORITIES.map((value) => (
                <MenuItem key={value} value={value}>
                  {value}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setComposerOpen(false)} sx={{ textTransform: 'none' }}>
            Cancel
          </Button>
          <Button
            variant="contained"
            disabled={!subject.trim() || !description.trim() || open.isPending}
            onClick={() => open.mutate()}
            sx={{ textTransform: 'none' }}
          >
            {open.isPending ? 'Opening…' : 'Open ticket'}
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};

export default SupportTicketsPage;
