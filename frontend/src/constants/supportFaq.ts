/**
 * The canned answers behind the support widget, and the matcher that picks one.
 *
 * This is deliberately not an LLM. Every answer here is a fixed string written by us, so the
 * widget can never invent a refund policy or a cancellation window that support does not honour —
 * the failure mode of a rule-based bot is "I don't know", which is recoverable, rather than a
 * confident wrong answer, which is not.
 */

export interface FaqEntry {
  id: string;
  /** Shown as a suggestion chip, and used as the question text when a chip is tapped. */
  question: string;
  answer: string;
  /**
   * Words that pull a question towards this entry. Stemmed by hand rather than by a stemmer:
   * the list is small enough to read, and "cancel" covering "cancelled"/"cancelling" is handled
   * by prefix matching in {@link scoreEntry}.
   */
  keywords: string[];
  /**
   * Keywords that name the *intent* rather than the subject, weighted above the rest.
   *
   * Without this, "how do I cancel my booking" loses to the booking entry: both questions are
   * about a booking, and the only thing distinguishing them is the verb. The noun is what the
   * two entries share, so it cannot be what decides between them.
   */
  intentKeywords?: string[];
}

const INTENT_WEIGHT = 3;
const PHRASE_WEIGHT = 2;
const KEYWORD_WEIGHT = 1;

export const FAQ_ENTRIES: FaqEntry[] = [
  {
    id: 'booking',
    question: 'How do I book a professional?',
    answer:
      'Search for the service you need, pick a professional from the results, then choose a date and time on their booking page. You will need to be signed in to confirm. Once the professional accepts, the booking shows up under Dashboard → Bookings.',
    keywords: ['book', 'booking', 'appointment', 'schedule', 'hire', 'reserve', 'slot'],
  },
  {
    id: 'cancel',
    question: 'Can I cancel or reschedule a booking?',
    answer:
      'Yes. Open the booking from your dashboard and use Cancel or Reschedule. Cancellation terms vary by professional and are shown on the booking before you confirm it, so check there for the exact window that applies to yours.',
    keywords: ['cancel', 'reschedul', 'postpone', 'change', 'refund', 'call off', 'booking'],
    intentKeywords: ['cancel', 'reschedul', 'postpone', 'refund'],
  },
  {
    id: 'payment',
    question: 'What payment methods can I use?',
    answer:
      'Payments run through Razorpay, which accepts UPI, major credit and debit cards, net banking and popular wallets. Payment is taken when the professional confirms the booking, not when you first request it.',
    keywords: ['pay', 'payment', 'card', 'upi', 'razorpay', 'netbanking', 'wallet', 'invoice', 'bill', 'price', 'cost', 'charge'],
  },
  {
    id: 'services',
    question: 'What services are available?',
    answer:
      'You can book architects, structural and civil engineers, surveyors, interior designers, labour contractors and material suppliers, among others. Browse the full list on the Services page.',
    keywords: ['service', 'offer', 'architect', 'engineer', 'surveyor', 'interior', 'contractor', 'supplier', 'labour', 'available', 'provide'],
  },
  {
    id: 'account',
    question: 'How do I create an account or sign in?',
    answer:
      'Use Register in the top navigation to create an account with your email and mobile number, or sign in with Google or Facebook. You can also sign in with a one-time password sent to your mobile using the OTP tab on the login page.',
    keywords: ['account', 'sign in', 'signin', 'login', 'log in', 'register', 'signup', 'sign up', 'password', 'otp', 'google', 'facebook'],
  },
  {
    id: 'professional',
    question: 'How do I join as a professional?',
    answer:
      'Register and pick the role that matches your work — engineer, architect, surveyor, contractor or supplier. Your profile goes to our team for verification, and once approved you start appearing in search results and can accept bookings.',
    keywords: ['join', 'partner', 'professional', 'verif', 'onboard', 'become', 'apply', 'work with', 'seller', 'vendor'],
    intentKeywords: ['join', 'partner', 'onboard', 'become', 'apply'],
  },
  {
    id: 'tracking',
    question: 'How do I track a booking?',
    answer:
      'Every booking has a status — requested, confirmed, in progress or completed — visible on your dashboard. You also get an email as the status changes, so you do not have to keep checking.',
    keywords: ['track', 'status', 'progress', 'where', 'update', 'notification', 'email', 'booking'],
    intentKeywords: ['track', 'status', 'progress'],
  },
];

/** Shown before the visitor has typed anything. Kept to four so the panel does not open crowded. */
export const STARTER_QUESTION_IDS = ['booking', 'payment', 'cancel', 'services'];

/**
 * What the widget says when nothing scores well enough. It offers the human path rather than
 * guessing, which is the whole reason the threshold exists.
 */
export const FALLBACK_ANSWER =
  "I don't have an answer for that one. Our support team can help — use the button below and they will pick it up from here.";

const GREETING_PATTERN = /^\s*(hi|hey|hello|yo|good\s+(morning|afternoon|evening)|namaste)\b/i;

export const GREETING_ANSWER =
  'Hello. I can help with booking, payments, cancellations and accounts. What do you need?';

/** Words too common to carry meaning; without this "how do I" matches everything a little. */
const STOP_WORDS = new Set([
  'the', 'a', 'an', 'is', 'are', 'was', 'do', 'does', 'did', 'i', 'my', 'me', 'you', 'your',
  'to', 'for', 'of', 'in', 'on', 'at', 'it', 'this', 'that', 'and', 'or', 'but', 'with',
  'how', 'what', 'when', 'where', 'why', 'who', 'can', 'could', 'would', 'should', 'will',
  'please', 'help', 'need', 'want', 'get', 'have', 'has', 'be', 'am', 'any', 'about',
]);

const tokenize = (text: string): string[] =>
  text
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .split(/\s+/)
    .filter((word) => word.length > 1 && !STOP_WORDS.has(word));

/**
 * Scores one entry against the visitor's words.
 *
 * Multi-word keywords ("sign in") are checked against the raw text, since tokenising splits them.
 * Single words match on prefix so "cancelled" hits the "cancel" keyword without needing every
 * inflection listed.
 *
 * Each token contributes **once**, at the weight of the best keyword it hits. Scoring per keyword
 * instead lets one word count twice wherever an entry lists overlapping stems — "book" and
 * "booking" both fire on the single word "booking" — which is enough to beat a genuine
 * single-keyword match on another entry and answer the wrong question.
 */
const scoreEntry = (entry: FaqEntry, rawText: string, tokens: string[]): number => {
  const lowered = rawText.toLowerCase();
  const intents = new Set(entry.intentKeywords ?? []);
  let score = 0;

  // Phrases are scored separately: they span tokens, so they cannot be attributed to one.
  for (const keyword of entry.keywords) {
    if (keyword.includes(' ') && lowered.includes(keyword)) score += PHRASE_WEIGHT;
  }

  for (const token of new Set(tokens)) {
    let best = 0;
    for (const keyword of entry.keywords) {
      if (keyword.includes(' ')) continue;
      if (token.startsWith(keyword) || keyword.startsWith(token)) {
        best = Math.max(best, intents.has(keyword) ? INTENT_WEIGHT : KEYWORD_WEIGHT);
      }
    }
    score += best;
  }

  return score;
};

export interface MatchResult {
  entry: FaqEntry | null;
  /** Other entries that also scored, offered as "did you mean" chips rather than guessed at. */
  alternatives: FaqEntry[];
}

/**
 * Picks the best entry for a question, or none.
 *
 * Returning `null` is a real outcome, not a failure to handle: a single weak keyword hit is not
 * evidence enough to answer, and saying so routes the visitor to a human instead of to a
 * plausible-sounding wrong page.
 */
export const matchFaq = (text: string): MatchResult => {
  const tokens = tokenize(text);
  if (tokens.length === 0) return { entry: null, alternatives: [] };

  const scored = FAQ_ENTRIES
    .map((entry) => ({ entry, score: scoreEntry(entry, text, tokens) }))
    .filter((row) => row.score > 0)
    .sort((a, b) => b.score - a.score);

  if (scored.length === 0) return { entry: null, alternatives: [] };

  return {
    entry: scored[0].entry,
    // Only entries that are genuinely close compete; a runaway best match should not drag two
    // unrelated topics onto the screen behind it.
    alternatives: scored
      .slice(1)
      .filter((row) => row.score >= scored[0].score - 1)
      .slice(0, 2)
      .map((row) => row.entry),
  };
};

export const isGreeting = (text: string): boolean => GREETING_PATTERN.test(text);

export const faqById = (id: string): FaqEntry | undefined =>
  FAQ_ENTRIES.find((entry) => entry.id === id);
