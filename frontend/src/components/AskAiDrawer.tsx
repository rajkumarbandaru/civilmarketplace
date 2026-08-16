import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Avatar,
  Box,
  Chip,
  Drawer,
  IconButton,
  InputAdornment,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  AutoAwesome as AiIcon,
  Close as CloseIcon,
  CloseFullscreen as ExitFullIcon,
  OpenInFull as FullIcon,
  Send as SendIcon,
} from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from '../hooks';
import { AskAiSize, closeAskAi, setAskAiSize } from '../store/slices/uiSlice';
import { AiTurn, askAi, fetchAiStatus } from '../services/aiApi';
import AiMarkdown from './AiMarkdown';

interface AiMessage {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  /** Set on replies that did not come from the model — quota, outage, or not configured. */
  systemNotice?: boolean;
}

/**
 * Drawer widths per size. `full` is the whole viewport rather than a wide drawer, because the point
 * of the largest step is to stop the panel being a sidebar at all — a long answer with a diagram
 * described in it is unreadable in a 380px column.
 */
const WIDTH: Record<AskAiSize, string> = {
  small: '380px',
  medium: '520px',
  large: '760px',
  full: '100vw',
};

/** Only the three stepped sizes are buttons; `full` gets its own toggle, as an on/off state. */
const STEPPED: Exclude<AskAiSize, 'full'>[] = ['small', 'medium', 'large'];
const STEP_LABEL: Record<Exclude<AskAiSize, 'full'>, string> = {
  small: 'S',
  medium: 'M',
  large: 'L',
};

const OPENERS = [
  'Prepare a BOQ for a 1200 sq.ft ground-floor house',
  'Estimate cement, sand and steel for a 1000 sq.ft RCC slab',
  'Give me a construction schedule for a two-storey residence',
  'How do I choose between a structural and a geotechnical engineer?',
];

let counter = 0;
const nextId = () => `ai-${++counter}`;

/**
 * The Civil AI Assistant panel: an open-ended, LLM-backed assistant, mounted once in the app shell and opened
 * from the header icon in every portal.
 *
 * It is deliberately not the same thing as `SupportChatWidget`. That one answers from a fixed FAQ
 * and can therefore never misstate a refund window; this one is a language model and can, which is
 * why the service-side prompt forbids it from quoting figures and why the panel keeps the route to
 * a human ticket visible throughout.
 */
const AskAiDrawer: React.FC = () => {
  const dispatch = useAppDispatch();
  const open = useAppSelector((state) => state.ui.askAiOpen);
  const size = useAppSelector((state) => state.ui.askAiSize);
  const theme = useTheme();
  // Below sm every size collapses to the full viewport: a 760px drawer on a 390px phone is just a
  // full-screen panel with its controls pushed off the edge.
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const effectiveWidth = isMobile ? '100vw' : WIDTH[size];

  const [messages, setMessages] = useState<AiMessage[]>([]);
  const [draft, setDraft] = useState('');
  const [pending, setPending] = useState(false);
  const [available, setAvailable] = useState<boolean | null>(null);
  const [siteRates, setSiteRates] = useState(true);

  const scrollRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  // Guards against a reply landing after the panel closed, which would otherwise appear
  // unannounced the next time it opens.
  const liveRequest = useRef(0);

  // Asked once per open so the panel can state plainly that the assistant is unconfigured, rather
  // than letting someone type a question and only then discover there is nothing behind it.
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    fetchAiStatus()
      .then((status) => {
        if (cancelled) return;
        setAvailable(status.available);
        setSiteRates(status.siteRates);
      })
      // A failed status check is not proof the assistant is down — the chat call is the real test,
      // so the panel stays usable and lets the question itself surface any problem.
      .catch(() => { if (!cancelled) setAvailable(true); });
    return () => { cancelled = true; };
  }, [open]);

  useEffect(() => {
    if (!open) return undefined;
    const handle = window.setTimeout(() => inputRef.current?.focus(), 150);
    return () => window.clearTimeout(handle);
  }, [open]);

  // Pin to the newest message; also runs on `pending` so the thinking line stays in view.
  useEffect(() => {
    if (!open) return;
    const node = scrollRef.current;
    if (node) node.scrollTop = node.scrollHeight;
  }, [messages, pending, open]);

  const send = useCallback(async (text: string) => {
    const question = text.trim();
    if (!question || pending) return;

    // Captured before the question is appended: the service wants the prior turns only.
    const history: AiTurn[] = messages
      .filter((message) => !message.systemNotice)
      .map((message) => ({ role: message.role, text: message.text }));

    setMessages((prev) => [...prev, { id: nextId(), role: 'user', text: question }]);
    setDraft('');
    setPending(true);

    const ticket = ++liveRequest.current;
    try {
      const response = await askAi(question, history);
      if (ticket !== liveRequest.current) return;
      setMessages((prev) => [...prev, {
        id: nextId(),
        role: 'assistant',
        text: response.reply,
        systemNotice: !response.available,
      }]);
    } catch {
      if (ticket !== liveRequest.current) return;
      setMessages((prev) => [...prev, {
        id: nextId(),
        role: 'assistant',
        text: 'I could not reach the assistant. Check your connection and try again, or raise a support ticket from the Support page.',
        systemNotice: true,
      }]);
    } finally {
      if (ticket === liveRequest.current) setPending(false);
    }
  }, [messages, pending]);

  const close = () => {
    // Invalidates any in-flight reply, so a slow answer cannot arrive into a reopened panel.
    liveRequest.current++;
    setPending(false);
    dispatch(closeAskAi());
  };

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={close}
      // Above the app bars, below nothing else that matters — the panel is the foreground task
      // while it is up.
      PaperProps={{
        sx: {
          width: effectiveWidth,
          maxWidth: '100vw',
          display: 'flex',
          flexDirection: 'column',
          // Animating width keeps a size change from reading as the panel being closed and a
          // different one opened in its place.
          transition: theme.transitions.create('width', { duration: 220 }),
        },
      }}
    >
      <Box
        sx={{
          px: 2,
          py: 1.5,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          color: 'primary.contrastText',
          background: (t) =>
            `linear-gradient(135deg, ${t.palette.primary.main} 0%, ${t.palette.secondary.main} 100%)`,
        }}
      >
        <Avatar sx={{ bgcolor: 'rgba(255,255,255,0.22)', width: 36, height: 36 }}>
          <AiIcon fontSize="small" />
        </Avatar>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
            Civil AI Assistant
          </Typography>
          <Typography variant="caption" sx={{ opacity: 0.85 }}>
            Estimates, BOQs and construction planning
          </Typography>
        </Box>

        {/* Size controls are hidden on mobile, where every size is the full viewport anyway and
            the buttons would only be three ways to change nothing. */}
        {!isMobile && (
          <>
            <ToggleButtonGroup
              size="small"
              exclusive
              value={size === 'full' ? null : size}
              onChange={(_, value: AskAiSize | null) => value && dispatch(setAskAiSize(value))}
              aria-label="Panel size"
              sx={{
                bgcolor: 'rgba(255,255,255,0.16)',
                '& .MuiToggleButton-root': {
                  color: 'inherit',
                  border: 'none',
                  px: 1.25,
                  py: 0.25,
                  fontWeight: 700,
                  '&.Mui-selected': {
                    bgcolor: 'rgba(255,255,255,0.34)',
                    color: 'inherit',
                    '&:hover': { bgcolor: 'rgba(255,255,255,0.4)' },
                  },
                },
              }}
            >
              {STEPPED.map((step) => (
                <ToggleButton key={step} value={step} aria-label={`${step} panel`}>
                  {STEP_LABEL[step]}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>

            <Tooltip title={size === 'full' ? 'Exit full screen' : 'Full screen'}>
              <IconButton
                size="small"
                // Leaving full screen returns to medium rather than to whatever preceded it:
                // the previous size is not stored, and medium is the default the panel opens at.
                onClick={() => dispatch(setAskAiSize(size === 'full' ? 'medium' : 'full'))}
                aria-label={size === 'full' ? 'Exit full screen' : 'Full screen'}
                sx={{ color: 'inherit' }}
              >
                {size === 'full' ? <ExitFullIcon fontSize="small" /> : <FullIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
          </>
        )}

        <IconButton size="small" onClick={close} aria-label="Close Civil AI Assistant" sx={{ color: 'inherit' }}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Box>

      {available !== false && !siteRates && (
        <Alert severity="info" square sx={{ borderRadius: 0 }}>
          No registered provider rates are available right now, so estimates will quote market
          rates only.
        </Alert>
      )}

      {available === false && (
        <Alert severity="info" square sx={{ borderRadius: 0 }}>
          The Civil AI Assistant is not switched on for this environment. Support tickets still work as normal.
        </Alert>
      )}

      <Box
        ref={scrollRef}
        sx={{
          flexGrow: 1,
          overflowY: 'auto',
          px: 2,
          py: 2,
          display: 'flex',
          flexDirection: 'column',
          gap: 1.5,
          bgcolor: 'background.default',
        }}
      >
        {messages.length === 0 && (
          <Box sx={{ my: 'auto', textAlign: 'center', px: 2 }}>
            <AiIcon sx={{ fontSize: 40, color: 'primary.main', opacity: 0.5 }} />
            <Typography variant="body2" sx={{ mt: 1, color: 'text.secondary' }}>
              Ask for a BOQ, a quantity or cost estimate, a material take-off, or a
              construction plan — and anything else about civil engineering work.
            </Typography>
            <Box sx={{ mt: 2, display: 'flex', flexWrap: 'wrap', gap: 0.75, justifyContent: 'center' }}>
              {OPENERS.map((opener) => (
                <Chip
                  key={opener}
                  label={opener}
                  size="small"
                  variant="outlined"
                  onClick={() => send(opener)}
                  sx={{ height: 'auto', py: 0.5, '& .MuiChip-label': { whiteSpace: 'normal' } }}
                />
              ))}
            </Box>
          </Box>
        )}

        {messages.map((message) => (
          <Box
            key={message.id}
            sx={{ display: 'flex', justifyContent: message.role === 'user' ? 'flex-end' : 'flex-start' }}
          >
            <Box
              sx={{
                // Wider than the FAQ bubble's 85%: these answers are paragraphs, not one-liners,
                // and at the large sizes a narrow column wastes the width the user just asked for.
                maxWidth: message.role === 'user' ? '85%' : '95%',
                px: 1.75,
                py: 1.25,
                borderRadius: 2,
                bgcolor: message.role === 'user' ? 'primary.main' : 'background.paper',
                color: message.role === 'user' ? 'primary.contrastText' : 'text.primary',
                border: message.role === 'user' ? 'none' : '1px solid',
                borderColor: message.systemNotice ? 'warning.main' : 'divider',
              }}
            >
              {message.role === 'assistant' && !message.systemNotice ? (
                // Only model replies go through the Markdown renderer. A user's own text is shown
                // verbatim, so that typing a pipe or a hash cannot reformat their question.
                <AiMarkdown>{message.text}</AiMarkdown>
              ) : (
                <Typography variant="body2" sx={{ lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>
                  {message.text}
                </Typography>
              )}
            </Box>
          </Box>
        ))}

        {pending && (
          <Typography variant="caption" sx={{ color: 'text.secondary', pl: 0.5 }}>
            Thinking…
          </Typography>
        )}
      </Box>

      <Box
        component="form"
        onSubmit={(event: React.FormEvent) => { event.preventDefault(); send(draft); }}
        sx={{ p: 1.5, borderTop: '1px solid', borderColor: 'divider', bgcolor: 'background.paper' }}
      >
        <TextField
          inputRef={inputRef}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Ask about engineers, surveys, materials…"
          size="small"
          fullWidth
          multiline
          maxRows={5}
          autoComplete="off"
          onKeyDown={(event) => {
            // Enter sends, Shift+Enter breaks the line — the convention for every assistant panel
            // people have used before this one.
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault();
              send(draft);
            }
          }}
          inputProps={{ 'aria-label': 'Ask the Civil AI Assistant a question', maxLength: 2000 }}
          InputProps={{
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  type="submit"
                  size="small"
                  color="primary"
                  disabled={!draft.trim() || pending}
                  aria-label="Send question"
                >
                  <SendIcon fontSize="small" />
                </IconButton>
              </InputAdornment>
            ),
            sx: { borderRadius: 2, alignItems: 'flex-end' },
          }}
        />
        <Typography variant="caption" sx={{ display: 'block', mt: 0.75, color: 'text.secondary' }}>
          AI-generated preliminary estimates, based on stated assumptions — not a substitute for a
          qualified structural engineer. It cannot see your bookings or confirm prices.
        </Typography>
      </Box>
    </Drawer>
  );
};

export default AskAiDrawer;
