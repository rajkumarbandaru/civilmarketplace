import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import api from '../../services/api';
import { apiErrorMessage } from '../../services/apiError';
import {
  canRestoreRemembered,
  clearSession,
  forgetSession,
  persistSession,
  persistTokens,
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

interface AuthState {
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

/**
 * Signs a fresh tab back in from the "remember me" token.
 *
 * Runs only when this tab has no session of its own (see `canRestoreRemembered`), so a tab that is
 * already signed in as someone else is never rebuilt into the remembered account.
 *
 * The refresh endpoint rotates the token, so the new one is written straight back — reusing a
 * spent token on the next launch would sign the user out and look like "remember me forgot me".
 * A rejection means the token expired or was revoked: the remembered slot is cleared so the app
 * stops retrying a credential the server has already refused.
 */
export const restoreRememberedSession = createAsyncThunk(
  'auth/restoreRemembered',
  async (_: void, { rejectWithValue }) => {
    const refreshToken = readRememberedToken();
    if (!refreshToken) return rejectWithValue('No remembered session');

    try {
      const response = await api.post('/auth/refresh', { refreshToken });
      return response.data;
    } catch (error: any) {
      forgetSession();
      return rejectWithValue(apiErrorMessage(error, 'Could not restore your session'));
    }
  }
);

export const shouldRestoreRemembered = canRestoreRemembered;

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
    /**
     * Records (or drops) the "remember me" choice for the session that just started.
     *
     * Separate from the login thunks on purpose: password, OTP and social sign-in all arrive
     * through different paths, and threading a `remember` flag through each one would mean three
     * chances to forget it. The caller states the intent once, after whichever path succeeded.
     */
    setRememberMe(state, action: PayloadAction<boolean>) {
      if (action.payload && state.refreshToken) {
        rememberSession(state.refreshToken);
      } else {
        forgetSession();
      }
    },
    logout(state) {
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
      // The refresh rotated the token, so the remembered copy has to move with it.
      rememberSession(action.payload.refreshToken);
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
    builder.addCase(login.fulfilled, (state, action) => {
      state.loading = false;
      state.isAuthenticated = true;
      state.user = action.payload.user;
      state.accessToken = action.payload.accessToken;
      state.refreshToken = action.payload.refreshToken;
      persistSession(action.payload.user, action.payload.accessToken, action.payload.refreshToken);
    });
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
    builder.addCase(verifyOtp.fulfilled, (state, action) => {
      state.loading = false;
      state.isAuthenticated = true;
      state.user = action.payload.user;
      state.accessToken = action.payload.accessToken;
      state.refreshToken = action.payload.refreshToken;
      persistSession(action.payload.user, action.payload.accessToken, action.payload.refreshToken);
    });
  },
});

export const {
  logout,
  clearError,
  setCredentials,
  setSocialCredentials,
  setRememberMe,
} = authSlice.actions;
export default authSlice.reducer;
