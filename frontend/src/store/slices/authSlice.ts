import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import api from '../../services/api';
import { apiErrorMessage } from '../../services/apiError';
import {
  canRestoreRemembered,
  clearSession,
  forgetSession,
  persistSession,
  persistTokens,
  persistUser,
  readRememberedToken,
  rememberSession,
  readSession,
} from '../../services/authStorage';

interface User {
  id: number;
  name: string;
  email: string;
  phone: string;
  profilePicture: string;
  role: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  status: string;
  /** Set when the account was created through a social provider. */
  provider?: string;
}

/**
 * A sign-in that passed its first step and still owes a second factor. Held only in memory: a
 * reload drops it and the user signs in again, which is the point of a five-minute ticket.
 */
export interface PendingMfa {
  token: string;
  /** No authenticator yet: enrol first (QR code, then a code), then sign-in completes. */
  setup: boolean;
}

interface AuthState {
  mfa: PendingMfa | null;
  /** Shown once after enrolling, then cleared. */
  recoveryCodes: string[] | null;
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

// Read once at module load: the session is this tab's own (see services/authStorage), so it is
// rehydrated before the first render and cannot be changed underneath the tab by another one.
const stored = readSession();

const initialState: AuthState = {
  mfa: null,
  recoveryCodes: null,
  user: stored.user as User | null,
  accessToken: stored.accessToken,
  refreshToken: stored.refreshToken,
  isAuthenticated: !!stored.accessToken,
  loading: false,
  error: null,
};

export const login = createAsyncThunk(
  'auth/login',
  async (credentials: { email: string; password: string }, { rejectWithValue }) => {
    try {
      const response = await api.post('/auth/login', credentials);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(apiErrorMessage(error, 'Login failed'));
    }
  }
);

export const register = createAsyncThunk(
  'auth/register',
  async (userData: {
    name: string;
    email: string;
    password: string;
    phone?: string;
    role?: string;
    /** Where the account-verification code is sent; the backend defaults to EMAIL. */
    verificationChannel?: 'EMAIL' | 'SMS' | 'WHATSAPP';
  }, { rejectWithValue }) => {
    try {
      const response = await api.post('/auth/register', userData);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(apiErrorMessage(error, 'Registration failed'));
    }
  }
);

/** Exactly one of email/phone identifies the account; phone must be E.164. */
export interface OtpIdentifier {
  email?: string;
  phone?: string;
  /**
   * Delivery route. Optional — the backend defaults to the one the identifier implies
   * (email address → EMAIL, phone number → SMS). Only meaningful on the send call;
   * verification is keyed on the identifier, not the channel it arrived over.
   */
  channel?: 'EMAIL' | 'SMS' | 'WHATSAPP';
}

export const sendOtp = createAsyncThunk(
  'auth/sendOtp',
  async (data: OtpIdentifier, { rejectWithValue }) => {
    try {
      const response = await api.post('/auth/otp/send', data);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(apiErrorMessage(error, 'Failed to send OTP'));
    }
  }
);

export const verifyOtp = createAsyncThunk(
  'auth/verifyOtp',
  async (data: OtpIdentifier & { otp: string }, { rejectWithValue }) => {
    try {
      const response = await api.post('/auth/otp/verify', data);
      return response.data;
    } catch (error: any) {
      return rejectWithValue(apiErrorMessage(error, 'OTP verification failed'));
    }
  }
);

/** Finishes a sign-in that asked for a second factor, with an authenticator or recovery code. */
export const verifyMfa = createAsyncThunk<any, string, { state: { auth: AuthState } }>(
  'auth/verifyMfa',
  async (code, { getState, rejectWithValue }) => {
    try {
      const response = await api.post('/auth/mfa/verify', { mfaToken: getState().auth.mfa?.token, code });
      return response.data;
    } catch (error: any) {
      return rejectWithValue(apiErrorMessage(error, 'That code did not work'));
    }
  }
);

/** Confirms a newly scanned authenticator with its first code; completes the sign-in. */
export const enableMfa = createAsyncThunk<any, string, { state: { auth: AuthState } }>(
  'auth/enableMfa',
  async (code, { getState, rejectWithValue }) => {
    try {
      const response = await api.post('/auth/mfa/enable', { mfaToken: getState().auth.mfa?.token, code });
      return response.data;
    } catch (error: any) {
      return rejectWithValue(apiErrorMessage(error, 'That code did not work'));
    }
  }
);

/**
 * Signs a fresh tab back in from the "remember me" token.
 *
 * Runs only when this tab has no session of its own (see `canRestoreRemembered`), so a tab that is
 * already signed in as someone else is never rebuilt into the remembered account.
 *
 * The refresh endpoint rotates the token, so the remembered slot is replaced with a fresh device
 * token of its own (not the tab's new one — see `issueDeviceToken`). A rejection means the token expired or was revoked: the remembered slot is cleared so the app
 * stops retrying a credential the server has already refused.
 */
export const restoreRememberedSession = createAsyncThunk(
  'auth/restoreRemembered',
  async (_: void, { rejectWithValue }) => {
    const refreshToken = readRememberedToken();
    if (!refreshToken) return rejectWithValue('No remembered session');

    try {
      const response = await api.post('/auth/refresh', { refreshToken });
      // The exchange spent the remembered token. The tab keeps the pair it got back; the device
      // needs its own replacement, or the next new tab would present a spent token.
      const deviceToken = await issueDeviceToken(response.data.refreshToken);
      return { ...response.data, deviceToken };
    } catch (error: any) {
      forgetSession();
      return rejectWithValue(apiErrorMessage(error, 'Could not restore your session'));
    }
  }
);

/**
 * A refresh token of the device's own, independent of the tab's.
 *
 * The tab and the remembered slot must never share a token. The server rotates a token on every
 * use and treats a spent one presented again as theft — revoking every session the user has — so
 * if both held the same token, whichever used it second would sign the user out everywhere.
 * Returns null when the server will not issue one; the caller then simply does not remember.
 */
const issueDeviceToken = async (refreshToken: string | null): Promise<string | null> => {
  if (!refreshToken) return null;
  try {
    const response = await api.post('/auth/refresh/device', { refreshToken });
    return response.data?.refreshToken ?? null;
  } catch {
    return null;
  }
};

/**
 * Records (or drops) the "remember me" choice for the session that just started.
 *
 * Dispatched once, after whichever sign-in path succeeded — password, OTP and social all arrive
 * differently, and threading a flag through each would mean three chances to forget it. Must run
 * within two minutes of sign-in: the server only issues a device token for a freshly minted one.
 */
export const setRememberMe = createAsyncThunk<void, boolean, { state: { auth: AuthState } }>(
  'auth/rememberMe',
  async (remember, { getState }) => {
    if (!remember) {
      forgetSession();
      return;
    }
    const deviceToken = await issueDeviceToken(getState().auth.refreshToken);
    if (deviceToken) rememberSession(deviceToken);
    else forgetSession();
  }
);

/**
 * Signs out on the server as well as in the browser.
 *
 * Local state is cleared first so the UI responds at once; the server call then blacklists the
 * access token and revokes both refresh tokens this device holds. Its outcome is deliberately
 * ignored — an unreachable server must not leave someone stuck signed in on their own screen.
 */
export const signOut = createAsyncThunk<void, void, { state: { auth: AuthState } }>(
  'auth/signOut',
  async (_, { getState, dispatch }) => {
    const { accessToken, refreshToken } = getState().auth;
    const remembered = readRememberedToken();
    dispatch(authSlice.actions.logout());
    const refreshTokens = [refreshToken, remembered].filter((t): t is string => !!t);
    if (!accessToken && refreshTokens.length === 0) return;
    try {
      await api.post(
        '/auth/logout',
        { refreshTokens },
        accessToken ? { headers: { Authorization: `Bearer ${accessToken}` } } : undefined
      );
    } catch {
      // See above: best effort.
    }
  }
);

export const shouldRestoreRemembered = canRestoreRemembered;

/**
 * Applies a sign-in response: a session, or — when the account needs a second factor — the MFA
 * ticket in its place, with the user still signed out.
 */
const signedIn = (state: AuthState, payload: any) => {
  state.loading = false;
  if (payload?.mfaRequired) {
    state.mfa = { token: payload.mfaToken, setup: !!payload.mfaSetupRequired };
    return;
  }
  state.mfa = null;
  state.isAuthenticated = true;
  state.user = payload.user;
  state.accessToken = payload.accessToken;
  state.refreshToken = payload.refreshToken;
  persistSession(payload.user, payload.accessToken, payload.refreshToken);
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials(state, action: PayloadAction<{ accessToken: string; refreshToken: string }>) {
      state.accessToken = action.payload.accessToken;
      state.refreshToken = action.payload.refreshToken;
      persistTokens(action.payload.accessToken, action.payload.refreshToken);
    },
    /**
     * Completes a social (OAuth2) login. Unlike password/OTP login there is no
     * thunk here — the tokens arrive on the redirect back from the provider.
     */
    setSocialCredentials(
      state,
      action: PayloadAction<{ user: User; accessToken: string; refreshToken: string }>
    ) {
      state.user = action.payload.user;
      state.accessToken = action.payload.accessToken;
      state.refreshToken = action.payload.refreshToken;
      state.isAuthenticated = true;
      state.loading = false;
      state.error = null;
      persistSession(action.payload.user, action.payload.accessToken, action.payload.refreshToken);
    },
    logout(state) {
      state.mfa = null;
      state.recoveryCodes = null;
      state.user = null;
      state.accessToken = null;
      state.refreshToken = null;
      state.isAuthenticated = false;
      state.error = null;
      clearSession();
      // An explicit sign-out revokes the standing "remember me" too. Leaving it would have the
      // next new tab sign straight back in as the account the user just left, which reads as the
      // sign-out having failed.
      forgetSession();
    },
    clearError(state) {
      state.error = null;
    },
    /** A second factor is due (social sign-in hands its ticket over in the redirect). */
    beginMfa(state, action: PayloadAction<PendingMfa>) {
      state.mfa = action.payload;
      state.error = null;
    },
    cancelMfa(state) {
      state.mfa = null;
      state.error = null;
    },
    /** The recovery codes have been shown; they are never kept. */
    clearRecoveryCodes(state) {
      state.recoveryCodes = null;
    },
    /** The server's copy of the signed-in user after a profile change (e.g. a new photo). */
    setUser(state, action: PayloadAction<User>) {
      if (!state.user || state.user.id !== action.payload.id) return;
      state.user = { ...state.user, ...action.payload };
      persistUser(state.user);
    },
  },
  extraReducers: (builder) => {
    // Remembered-session restore
    builder.addCase(restoreRememberedSession.pending, (state) => {
      state.loading = true;
    });
    builder.addCase(restoreRememberedSession.fulfilled, (state, action) => {
      state.loading = false;
      state.isAuthenticated = true;
      state.user = action.payload.user ?? state.user;
      state.accessToken = action.payload.accessToken;
      state.refreshToken = action.payload.refreshToken;
      persistSession(
        action.payload.user ?? state.user,
        action.payload.accessToken,
        action.payload.refreshToken
      );
      // The old remembered token is spent now; keep the device signed in only if it got a new one.
      if (action.payload.deviceToken) rememberSession(action.payload.deviceToken);
      else forgetSession();
    });
    builder.addCase(restoreRememberedSession.rejected, (state) => {
      // Deliberately no error surfaced: the user did not ask for this, they just opened the app.
      // A failed silent restore should look like being signed out, not like something broke.
      state.loading = false;
      state.isAuthenticated = false;
    });

    // Login
    builder.addCase(login.pending, (state) => {
      state.loading = true;
      state.error = null;
    });
    builder.addCase(login.fulfilled, (state, action) => signedIn(state, action.payload));
    builder.addCase(login.rejected, (state, action) => {
      state.loading = false;
      state.error = action.payload as string;
    });

    // Register
    builder.addCase(register.pending, (state) => {
      state.loading = true;
      state.error = null;
    });
    builder.addCase(register.fulfilled, (state, action) => {
      state.loading = false;
      state.isAuthenticated = true;
      state.user = action.payload.user;
      state.accessToken = action.payload.accessToken;
      state.refreshToken = action.payload.refreshToken;
      persistSession(action.payload.user, action.payload.accessToken, action.payload.refreshToken);
    });
    builder.addCase(register.rejected, (state, action) => {
      state.loading = false;
      state.error = action.payload as string;
    });

    // OTP send
    builder.addCase(sendOtp.pending, (state) => {
      state.loading = true;
      state.error = null;
    });
    builder.addCase(sendOtp.fulfilled, (state) => { state.loading = false; });
    builder.addCase(sendOtp.rejected, (state, action) => {
      state.loading = false;
      state.error = action.payload as string;
    });

    // OTP verify
    builder.addCase(verifyOtp.pending, (state) => {
      state.loading = true;
      state.error = null;
    });
    builder.addCase(verifyOtp.rejected, (state, action) => {
      state.loading = false;
      state.error = action.payload as string;
    });
    builder.addCase(verifyOtp.fulfilled, (state, action) => signedIn(state, action.payload));

    // Second factor
    for (const thunk of [verifyMfa, enableMfa]) {
      builder.addCase(thunk.pending, (state) => {
        state.loading = true;
        state.error = null;
      });
      builder.addCase(thunk.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });
    }
    builder.addCase(verifyMfa.fulfilled, (state, action) => signedIn(state, action.payload));
    builder.addCase(enableMfa.fulfilled, (state, action) => {
      signedIn(state, action.payload);
      state.recoveryCodes = action.payload.recoveryCodes ?? null;
    });
  },
});

export const {
  logout,
  clearError,
  setCredentials,
  setSocialCredentials,
  setUser,
  beginMfa,
  cancelMfa,
  clearRecoveryCodes,
} = authSlice.actions;
export default authSlice.reducer;
