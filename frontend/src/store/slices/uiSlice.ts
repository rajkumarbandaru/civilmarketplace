import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface UiState {
  sidebarOpen: boolean;
  theme: 'light' | 'dark';
  snackbar: {
    open: boolean;
    message: string;
    severity: 'success' | 'error' | 'info' | 'warning';
  };
  searchQuery: string;
  selectedCity: string;
}

/**
 * The colour mode for signed-out visitors, who have no server-side appearance record to save one
 * against. It is deliberately separate from the member setting: once you sign in, the workspace
 * theme and your own `colorMode` decide what you see, and this local preference stops applying.
 */
export const GUEST_THEME_KEY = 'civeng.guestColorMode';

const storedGuestTheme = (): 'light' | 'dark' => {
  try {
    return localStorage.getItem(GUEST_THEME_KEY) === 'dark' ? 'dark' : 'light';
  } catch {
    // Private-mode Safari and friends throw on access; a visitor who cannot store a preference
    // still gets a usable site in the shipped light theme.
    return 'light';
  }
};

const initialState: UiState = {
  sidebarOpen: false,
  theme: storedGuestTheme(),
  snackbar: {
    open: false,
    message: '',
    severity: 'info',
  },
  searchQuery: '',
  selectedCity: '',
};

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    toggleSidebar(state) {
      state.sidebarOpen = !state.sidebarOpen;
    },
    toggleGuestTheme(state) {
      state.theme = state.theme === 'dark' ? 'light' : 'dark';
      try {
        localStorage.setItem(GUEST_THEME_KEY, state.theme);
      } catch {
        // Not persisting is survivable — the choice still holds for this page view.
      }
    },
    setSearchQuery(state, action: PayloadAction<string>) {
      state.searchQuery = action.payload;
    },
    setSelectedCity(state, action: PayloadAction<string>) {
      state.selectedCity = action.payload;
    },
    showSnackbar(state, action: PayloadAction<{
      message: string;
      severity?: 'success' | 'error' | 'info' | 'warning';
    }>) {
      state.snackbar = {
        open: true,
        message: action.payload.message,
        severity: action.payload.severity || 'info',
      };
    },
    hideSnackbar(state) {
      state.snackbar.open = false;
    },
  },
});

export const {
  toggleSidebar,
  toggleGuestTheme,
  setSearchQuery,
  setSelectedCity,
  showSnackbar,
  hideSnackbar,
} = uiSlice.actions;
export default uiSlice.reducer;
