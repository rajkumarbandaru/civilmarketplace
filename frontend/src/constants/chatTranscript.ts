/**
 * The chat transcript carried on a ticket raised from the assistant.
 *
 * It travels inside the ticket's `description` between two markers rather than in a column of its
 * own: support-service stores a ticket as subject + description + replies, and adding a field for
 * this would mean a schema change, a migration and a DTO change for something only the widget
 * writes and only the ticket screens read. The markers make it recoverable without any of that.
 *
 * Everything outside the markers is the reporter's own text and is shown as normal description.
 */

export const TRANSCRIPT_OPEN = '[chat-transcript]';
export const TRANSCRIPT_CLOSE = '[/chat-transcript]';

export interface TranscriptLine {
  author: 'visitor' | 'bot';
  text: string;
}

export interface ParsedDescription {
  /** The description with the transcript block removed. */
  body: string;
  /** Empty when the ticket was raised by hand rather than from the assistant. */
  transcript: TranscriptLine[];
}

const VISITOR_PREFIX = 'You: ';
const BOT_PREFIX = 'Assistant: ';

/** Renders the lines back into the marked block that {@link parseDescription} reads. */
export const formatTranscript = (lines: TranscriptLine[]): string =>
  [
    TRANSCRIPT_OPEN,
    ...lines.map((line) => `${line.author === 'visitor' ? VISITOR_PREFIX : BOT_PREFIX}${line.text}`),
    TRANSCRIPT_CLOSE,
  ].join('\n');

/**
 * Splits a stored description into the reporter's text and the assistant conversation.
 *
 * A ticket whose description has no markers — every hand-raised one — comes back with the whole
 * description as `body` and no transcript, so callers need no special case for it.
 */
export const parseDescription = (description: string): ParsedDescription => {
  const start = description.indexOf(TRANSCRIPT_OPEN);
  const end = description.indexOf(TRANSCRIPT_CLOSE);

  if (start === -1 || end === -1 || end < start) {
    return { body: description.trim(), transcript: [] };
  }

  const block = description.slice(start + TRANSCRIPT_OPEN.length, end);
  const body = (description.slice(0, start) + description.slice(end + TRANSCRIPT_CLOSE.length)).trim();

  const transcript: TranscriptLine[] = [];
  for (const raw of block.split('\n')) {
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith(VISITOR_PREFIX)) {
      transcript.push({ author: 'visitor', text: line.slice(VISITOR_PREFIX.length) });
    } else if (line.startsWith(BOT_PREFIX)) {
      transcript.push({ author: 'bot', text: line.slice(BOT_PREFIX.length) });
    } else if (transcript.length > 0) {
      // A wrapped continuation of the previous line — join it back rather than dropping it,
      // which would silently truncate a long answer in the transcript view.
      transcript[transcript.length - 1].text += ` ${line}`;
    }
  }

  return { body, transcript };
};
