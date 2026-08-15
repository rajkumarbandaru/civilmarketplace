import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import {
  Avatar,
  Box,
  Button,
  Chip,
  Fab,
  IconButton,
  InputAdornment,
  Paper,
  TextField,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  Close as CloseIcon,
  ConfirmationNumber as TicketIcon,
  QuestionAnswer as ChatIcon,
  Send as SendIcon,
  SupportAgent as AgentIcon,
} from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from '../hooks';
import { closeSupportChat, startSupportTicket, toggleSupportChat } from '../store/slices/uiSlice';
import { formatTranscript } from '../constants/chatTranscript';
import {
  FALLBACK_ANSWER,
  FaqEntry,
  GREETING_ANSWER,
  STARTER_QUESTION_IDS,
  faqById,
  isGreeting,
  matchFaq,
} from '../constants/supportFaq';

interface ChatMessage {
  id: string;
  author: 'visitor' | 'bot';
  text: string;
  /** Follow-up questions offered under a bot reply; tapping one asks it. */
  suggestions?: FaqEntry[];
  /** Marks the reply that failed to match, so the panel can offer the human handover. */
  unresolved?: boolean;
}

const STARTERS = STARTER_QUESTION_IDS
  .map(faqById)
  .filter((entry): entry is FaqEntry => Boolean(entry));

const GREETING: ChatMessage = {
  id: 'greeting',
  author: 'bot',
  text: 'Hello. I can answer questions about booking, payments, cancellations and accounts. Pick one below or type your own.',
  suggestions: STARTERS,
};

/**
 * Typing delay before a reply lands. The answer is already computed — this exists so replies do
 * not appear in the same frame as the question, which reads as the input being echoed rather than
 * answered. Short enough not to feel like latency.
 */
const REPLY_DELAY_MS = 420;

let messageCounter = 0;
const nextId = () => `msg-${++messageCounter}`;

/**
 * The floating support assistant, mounted once in the app shell and shown on every page.
 *
 * It answers from a fixed FAQ set (see `constants/supportFaq`) and never generates text, so it
 * cannot state a policy we do not honour. When it cannot match a question it says so and offers
 * the human handover rather than picking the closest entry regardless — a confidently wrong
 * answer about a refund window costs more than an admission of ignorance.
 */
const SupportChatWidget: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const open = useAppSelector((state) => state.ui.supportChatOpen);
  const isAuthenticated = useAppSelector((state) => state.auth.isAuthenticated);
  const theme = useTheme();
  const fullWidth = useMediaQuery(theme.breakpoints.down('sm'));

  const [messages, setMessages] = useState<ChatMessage[]>([GREETING]);
  const [draft, setDraft] = useState('');
  const [thinking, setThinking] = useState(false);

  const scrollRef = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);
  // Held so a reply still in flight can be dropped if the panel closes under it, which would
  // otherwise set state on an unmounted-in-spirit panel and land a stray message on reopen.
  const replyTimer = useRef<number | undefined>(undefined);

  useEffect(() => () => window.clearTimeout(replyTimer.current), []);

  // Pin to the newest message. Also runs on `thinking` so the typing indicator stays in view.
  useEffect(() => {
    if (!open) return;
    const node = scrollRef.current;
    if (node) node.scrollTop = node.scrollHeight;
  }, [messages, thinking, open]);

  useEffect(() => {
    if (open) {
      // The panel is useless without focus in the field, and opening it is always a deliberate act.
      const handle = window.setTimeout(() => inputRef.current?.focus(), 120);
      return () => window.clearTimeout(handle);
    }
    window.clearTimeout(replyTimer.current);
    setThinking(false);
    return undefined;
  }, [open]);

  // Escape closes the panel, matching every other dismissible surface in the app.
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') dispatch(closeSupportChat());
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, dispatch]);

  const ask = useCallback((text: string) => {
    const question = text.trim();
    if (!question) return;

    setMessages((prev) => [...prev, { id: nextId(), author: 'visitor', text: question }]);
    setDraft('');
    setThinking(true);

    window.clearTimeout(replyTimer.current);
    replyTimer.current = window.setTimeout(() => {
      // Greeting first: "hi" tokenises to nothing meaningful, so the matcher would otherwise
      // fall through to "I don't know" on the friendliest possible opener.
      const reply: ChatMessage = isGreeting(question)
        ? { id: nextId(), author: 'bot', text: GREETING_ANSWER, suggestions: STARTERS }
        : (() => {
            const { entry, alternatives } = matchFaq(question);
            if (!entry) {
              return {
                id: nextId(),
                author: 'bot' as const,
                text: FALLBACK_ANSWER,
                suggestions: STARTERS,
                unresolved: true,
              };
            }
            return {
              id: nextId(),
              author: 'bot' as const,
              text: entry.answer,
              suggestions: alternatives,
            };
          })();

      setMessages((prev) => [...prev, reply]);
      setThinking(false);
    }, REPLY_DELAY_MS);
  }, []);

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    ask(draft);
  };

  const unresolved = useMemo(
    () => messages.some((message) => message.unresolved),
    [messages]
  );

  /**
   * Hands the conversation to the ticket form.
   *
   * The transcript goes with it because support otherwise starts from nothing: the visitor has
   * already explained the problem once, and asking them to type it again is how a handover turns
   * into a dead end. The subject is the first thing they actually asked, not the greeting.
   */
  const raiseTicket = useCallback(() => {
    const firstQuestion = messages.find((message) => message.author === 'visitor');
    const lines = messages
      .filter((message) => message.id !== 'greeting')
      .map((message) => ({ author: message.author, text: message.text }));

    dispatch(
      startSupportTicket({
        subject: firstQuestion?.text.slice(0, 200) ?? 'Support request',
        description: lines.length > 0 ? formatTranscript(lines) : '',
      })
    );
    // Signed-out visitors cannot own a ticket, so they land on login; the draft survives in the
    // store and the form picks it up once they are through.
    navigate(isAuthenticated ? '/support' : '/login');
  }, [messages, dispatch, navigate, isAuthenticated]);

  return (
    <>
      {/* Sits above page content but below MUI modals (1300), so a dialog is never covered. */}
      <Box sx={{ position: 'fixed', bottom: 24, right: 24, zIndex: 1200 }}>
        <Fab
          color="primary"
          onClick={() => dispatch(toggleSupportChat())}
          aria-label={open ? 'Close support chat' : 'Open support chat'}
          aria-expanded={open}
          sx={{ boxShadow: 6 }}
        >
          {open ? <CloseIcon /> : <ChatIcon />}
        </Fab>
      </Box>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: 24, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 24, scale: 0.96 }}
            transition={{ duration: 0.18, ease: 'easeOut' }}
            style={{
              position: 'fixed',
              // Clears the launcher above it rather than overlapping.
              bottom: 96,
              right: fullWidth ? 12 : 24,
              left: fullWidth ? 12 : 'auto',
              zIndex: 1200,
            }}
          >
            <Paper
              elevation={12}
              role="dialog"
              aria-label="Support chat"
              sx={{
                width: fullWidth ? 'auto' : 380,
                height: 520,
                maxHeight: 'calc(100vh - 140px)',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
                borderRadius: 3,
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
                  <AgentIcon fontSize="small" />
                </Avatar>
                <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                    Support Assistant
                  </Typography>
                  <Typography variant="caption" sx={{ opacity: 0.85 }}>
                    Answers common questions instantly
                  </Typography>
                </Box>
                <IconButton
                  size="small"
                  onClick={() => dispatch(closeSupportChat())}
                  aria-label="Close support chat"
                  sx={{ color: 'inherit' }}
                >
                  <CloseIcon fontSize="small" />
                </IconButton>
              </Box>

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
                {messages.map((message) => (
                  <Box key={message.id}>
                    <Box
                      sx={{
                        display: 'flex',
                        justifyContent: message.author === 'visitor' ? 'flex-end' : 'flex-start',
                      }}
                    >
                      <Box
                        sx={{
                          maxWidth: '85%',
                          px: 1.75,
                          py: 1.25,
                          borderRadius: 2,
                          bgcolor: message.author === 'visitor' ? 'primary.main' : 'background.paper',
                          color: message.author === 'visitor' ? 'primary.contrastText' : 'text.primary',
                          border: message.author === 'visitor' ? 'none' : '1px solid',
                          borderColor: 'divider',
                        }}
                      >
                        <Typography variant="body2" sx={{ lineHeight: 1.55 }}>
                          {message.text}
                        </Typography>
                      </Box>
                    </Box>

                    {message.suggestions && message.suggestions.length > 0 && (
                      <Box sx={{ mt: 1, display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
                        {message.suggestions.map((entry) => (
                          <Chip
                            key={entry.id}
                            label={entry.question}
                            size="small"
                            variant="outlined"
                            onClick={() => ask(entry.question)}
                            sx={{ height: 'auto', py: 0.5, '& .MuiChip-label': { whiteSpace: 'normal' } }}
                          />
                        ))}
                      </Box>
                    )}
                  </Box>
                ))}

                {thinking && (
                  <Typography variant="caption" sx={{ color: 'text.secondary', pl: 0.5 }}>
                    Typing…
                  </Typography>
                )}
              </Box>

              {/*
                Always offered, never gated on the assistant admitting defeat.

                It used to appear only after an unmatched question, which in practice was almost
                never: a keyword matcher finds *some* match for nearly any input — "who pays" hits
                the payment entry off the word "pays" — so the assistant answers confidently, the
                unresolved flag stays false, and the one route to a human stays hidden behind a
                condition that does not fire. A wrong answer is exactly when someone needs the
                ticket, so it cannot be the case that hides the button.
              */}
              {(
                <Box
                  sx={{
                    px: 2,
                    py: 1.25,
                    borderTop: '1px solid',
                    borderColor: 'divider',
                    bgcolor: 'background.paper',
                  }}
                >
                  <Button
                    size="small"
                    fullWidth
                    variant={unresolved ? 'contained' : 'outlined'}
                    startIcon={<TicketIcon />}
                    onClick={raiseTicket}
                    sx={{ borderRadius: 2, textTransform: 'none' }}
                  >
                    {unresolved ? 'Raise a support ticket' : 'Still need help? Raise a ticket'}
                  </Button>
                </Box>
              )}

              <Box
                component="form"
                onSubmit={onSubmit}
                sx={{ p: 1.5, borderTop: '1px solid', borderColor: 'divider', bgcolor: 'background.paper' }}
              >
                <TextField
                  inputRef={inputRef}
                  value={draft}
                  onChange={(event) => setDraft(event.target.value)}
                  placeholder="Ask a question…"
                  size="small"
                  fullWidth
                  autoComplete="off"
                  inputProps={{ 'aria-label': 'Ask the support assistant a question' }}
                  InputProps={{
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          type="submit"
                          size="small"
                          color="primary"
                          disabled={!draft.trim()}
                          aria-label="Send question"
                        >
                          <SendIcon fontSize="small" />
                        </IconButton>
                      </InputAdornment>
                    ),
                    sx: { borderRadius: 2 },
                  }}
                />
              </Box>
            </Paper>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
};

export default SupportChatWidget;
