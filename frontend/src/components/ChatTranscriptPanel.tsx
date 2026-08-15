import React, { useState } from 'react';
import { Box, Button, Chip, Paper, Stack, Typography } from '@mui/material';
import {
  ExpandLess as CollapseIcon,
  ExpandMore as ExpandIcon,
  SmartToy as BotIcon,
} from '@mui/icons-material';
import { TranscriptLine } from '../constants/chatTranscript';

/**
 * The assistant conversation that produced a ticket, shown against the ticket's own id.
 *
 * It is rendered apart from the reporter's description, not merged into it: staff need to see at a
 * glance what the assistant already told this person, because repeating an answer the bot gave —
 * or contradicting it — is the failure this panel exists to prevent.
 *
 * Collapsed past a few lines so a long conversation does not push the reply box off the screen.
 */
const ChatTranscriptPanel: React.FC<{ ticketId: number; lines: TranscriptLine[] }> = ({
  ticketId,
  lines,
}) => {
  const [expanded, setExpanded] = useState(false);

  if (lines.length === 0) return null;

  const COLLAPSED_LINES = 4;
  const overflowing = lines.length > COLLAPSED_LINES;
  const shown = expanded ? lines : lines.slice(0, COLLAPSED_LINES);

  return (
    <Paper
      variant="outlined"
      sx={{ borderRadius: 2, p: 2, mt: 2, bgcolor: 'action.hover', borderStyle: 'dashed' }}
    >
      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1.5 }}>
        <BotIcon fontSize="small" sx={{ color: 'text.secondary' }} />
        <Typography variant="subtitle2" sx={{ fontWeight: 700, flexGrow: 1 }}>
          Assistant conversation
        </Typography>
        <Chip size="small" variant="outlined" label={`Ticket #${ticketId}`} />
      </Stack>

      <Stack spacing={1}>
        {shown.map((line, index) => (
          <Box
            key={`${ticketId}-${index}`}
            sx={{ display: 'flex', justifyContent: line.author === 'visitor' ? 'flex-end' : 'flex-start' }}
          >
            <Box
              sx={{
                maxWidth: '85%',
                px: 1.5,
                py: 1,
                borderRadius: 2,
                bgcolor: line.author === 'visitor' ? 'primary.main' : 'background.paper',
                color: line.author === 'visitor' ? 'primary.contrastText' : 'text.primary',
                border: line.author === 'visitor' ? 'none' : '1px solid',
                borderColor: 'divider',
              }}
            >
              <Typography variant="caption" sx={{ display: 'block', opacity: 0.75, fontWeight: 600 }}>
                {line.author === 'visitor' ? 'User' : 'Bot'}
              </Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                {line.text}
              </Typography>
            </Box>
          </Box>
        ))}
      </Stack>

      {overflowing && (
        <Button
          size="small"
          onClick={() => setExpanded((value) => !value)}
          endIcon={expanded ? <CollapseIcon /> : <ExpandIcon />}
          sx={{ mt: 1, textTransform: 'none' }}
        >
          {expanded ? 'Show less' : `Show all ${lines.length} messages`}
        </Button>
      )}
    </Paper>
  );
};

export default ChatTranscriptPanel;
