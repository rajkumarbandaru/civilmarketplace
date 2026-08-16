import api from './api';

/**
 * Client for the Civil AI Assistant (`backend/support-service`, `AiAssistantController`).
 *
 * The model is never called from the browser. The Gemini API key lives in support-service's
 * environment, because anything reachable from here is reachable by every visitor — a key in a
 * `VITE_` variable is inlined into the bundle and effectively published.
 */

export type AiRole = 'user' | 'assistant';

export interface AiTurn {
  role: AiRole;
  text: string;
}

export interface AiChatResponse {
  reply: string;
  /**
   * False when the assistant is off, unconfigured, rate-limited, or the upstream call failed. The
   * reply still holds text worth showing — it explains which of those happened — so the panel
   * renders it as a message but marks it as not coming from the model.
   */
  available: boolean;
}

export interface AiStatus {
  available: boolean;
  /**
   * Whether estimates can quote this site's registered provider rates. False when no provider
   * rates could be read, in which case answers carry market rates only — worth saying out loud,
   * since the site-versus-market comparison is the point of asking here rather than anywhere else.
   */
  siteRates: boolean;
}

/** Whether the assistant is usable at all, asked once when the panel opens. */
export const fetchAiStatus = async (): Promise<AiStatus> => {
  const { data } = await api.get<{ available: boolean; siteRates?: { siteRatesAvailable?: boolean } }>(
    '/support/ai/status',
  );
  return {
    available: Boolean(data?.available),
    siteRates: Boolean(data?.siteRates?.siteRatesAvailable),
  };
};

/**
 * @param history prior turns, oldest first, excluding `message` — the service is stateless, so the
 *                conversation only exists for as long as the browser keeps sending it back
 */
export const askAi = async (message: string, history: AiTurn[]): Promise<AiChatResponse> => {
  // Overrides the client's 30s default: a BOQ or a full cost breakdown is a few thousand tokens
  // of generation, which regularly outruns the timeout used for ordinary CRUD calls.
  const { data } = await api.post<AiChatResponse>(
    '/support/ai/chat',
    { message, history },
    { timeout: 120000 },
  );
  return data;
};
