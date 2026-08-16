/**
 * Where the signed-in session lives: sessionStorage, which is per-tab.
 *
 * It used to be localStorage, which is per-origin — every tab shared one set of keys. Signing
 * into a second account in a second tab overwrote the first tab's token and user, so refreshing
 * the first tab silently turned it into the other account, workspace theme and all. Worse, a
 * token refresh in either tab rotated the shared refresh token and logged the other one out at
 * what looked like random.
 *
 * sessionStorage is scoped to the tab, so two accounts can be open side by side and each tab
 * keeps its own identity across a reload. The trade-off is deliberate: a session does not
 * survive closing the tab, and a brand-new tab opens signed out. (A tab *duplicated* from
 * another, or opened through target="_blank", inherits a copy of the session — that is the
 * browser's behaviour, and a copy is still independent from that point on.)
 */

const ACCESS_TOKEN = 'accessToken';
const REFRESH_TOKEN = 'refreshToken';
const USER = 'user';

const KEYS = [ACCESS_TOKEN, REFRESH_TOKEN, USER];

/**
 * Moves a pre-existing localStorage session into this tab, once.
 *
 * Without this, shipping the change would sign out everyone who was already signed in. The old
 * keys are deleted as they are adopted so the shared copy stops being a second source of truth —
 * a tab that reloads later gets a clean signed-out state rather than silently adopting whichever
 * account happened to write last.
 */
const adoptLegacySession = (): void => {
  if (sessionStorage.getItem(ACCESS_TOKEN)) return;

  const legacyToken = localStorage.getItem(ACCESS_TOKEN);
  if (legacyToken) {
    KEYS.forEach((key) => {
      const value = localStorage.getItem(key);
      if (value !== null) sessionStorage.setItem(key, value);
    });
  }
  KEYS.forEach((key) => localStorage.removeItem(key));
};

adoptLegacySession();

export interface StoredSession {
  user: unknown | null;
  accessToken: string | null;
  refreshToken: string | null;
}

/** What this tab was signed in as, for rehydrating the store on load. */
export const readSession = (): StoredSession => {
  let user: unknown | null = null;
  try {
    user = JSON.parse(sessionStorage.getItem(USER) || 'null');
  } catch {
    // A half-written or hand-edited value must not take the whole app down on boot; the tab
    // simply starts signed out, which is the safe reading of "the stored user is unusable".
    user = null;
  }
  return {
    user,
    accessToken: sessionStorage.getItem(ACCESS_TOKEN),
    refreshToken: sessionStorage.getItem(REFRESH_TOKEN),
  };
};

export const persistTokens = (accessToken: string, refreshToken: string): void => {
  sessionStorage.setItem(ACCESS_TOKEN, accessToken);
  sessionStorage.setItem(REFRESH_TOKEN, refreshToken);
};

export const persistSession = (user: unknown, accessToken: string, refreshToken: string): void => {
  sessionStorage.setItem(USER, JSON.stringify(user));
  persistTokens(accessToken, refreshToken);
};

export const clearSession = (): void => {
  KEYS.forEach((key) => sessionStorage.removeItem(key));
};

// ---------------------------------------------------------------------------- remember me

/**
 * "Remember me" — the only thing that outlives the tab.
 *
 * This is deliberately *not* a return to storing the session in localStorage. What is kept there
 * is a single refresh token and nothing else: no access token, no user object. A fresh tab
 * exchanges it for its own short-lived session, which then lives in sessionStorage like any other.
 *
 * That distinction is what keeps the earlier bug fixed. The live session stays per-tab, so two
 * accounts can still be open side by side and neither can overwrite the other's identity. The
 * remembered token only ever answers one question — "who should a brand-new tab sign in as" —
 * and is read exactly once, at boot, before any tab has a session of its own.
 */
const REMEMBERED_TOKEN = 'civeng.rememberedSession';

/** Namespaced unlike the session keys, which are bare, so the two can never be confused. */
export const rememberSession = (refreshToken: string): void => {
  try {
    localStorage.setItem(REMEMBERED_TOKEN, refreshToken);
  } catch {
    // Private browsing refuses writes. The tab session still works; only the "stay signed in"
    // promise is quietly not kept, which is better than failing the sign-in itself.
  }
};

export const readRememberedToken = (): string | null => {
  try {
    return localStorage.getItem(REMEMBERED_TOKEN);
  } catch {
    return null;
  }
};

export const forgetSession = (): void => {
  try {
    localStorage.removeItem(REMEMBERED_TOKEN);
  } catch {
    // Nothing to clean up if storage is unavailable.
  }
};

/**
 * True when this tab has no session of its own but a remembered token exists.
 *
 * Both halves matter: a tab that is already signed in must not be re-bootstrapped into whichever
 * account was remembered, which is how the second tab would lose its own identity again.
 */
export const canRestoreRemembered = (): boolean =>
  !sessionStorage.getItem(ACCESS_TOKEN) && !!readRememberedToken();

/**
 * Carries the "remember me" choice across a social sign-in redirect.
 *
 * The OAuth round trip leaves the page entirely and comes back as a fresh load, so component state
 * holding the checkbox is gone by the time the tokens arrive. sessionStorage survives that within
 * the same tab, which is exactly the scope the choice was made in — and it is cleared on read so a
 * later social login does not inherit a decision from an earlier one.
 */
const REMEMBER_INTENT = 'civeng.rememberIntent';

export const stashRememberIntent = (remember: boolean): void => {
  try {
    if (remember) sessionStorage.setItem(REMEMBER_INTENT, '1');
    else sessionStorage.removeItem(REMEMBER_INTENT);
  } catch {
    // Falls back to not remembering, which is the safer default of the two.
  }
};

export const takeRememberIntent = (): boolean => {
  try {
    const intent = sessionStorage.getItem(REMEMBER_INTENT) === '1';
    sessionStorage.removeItem(REMEMBER_INTENT);
    return intent;
  } catch {
    return false;
  }
};
