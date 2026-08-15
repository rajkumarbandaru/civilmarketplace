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
  /**
   * Whether the support chat panel is open. It lives here rather than inside the widget so that
   * "Chat with Support" buttons elsewhere in the app can open it without the two having to be
   * rendered near each other — the widget sits in the app shell, the buttons on their own pages.
   */
  supportChatOpen: boolean;
  /**
   * A ticket the assistant could not answer, handed to the support page to prefill its form.
   *
   * It is a handover, not storage: the page consumes it and clears it, so a draft cannot reappear
   * behind an unrelated later visit to /support.
   */
  supportTicketDraft: { subject: string; description: string } | null;
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
  supportChatOpen: false,
  supportTicketDraft: null,
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
    openSupportChat(state) {
      state.supportChatOpen = true;
    },
    closeSupportChat(state) {
      state.supportChatOpen = false;
    },
    toggleSupportChat(state) {
      state.supportChatOpen = !state.supportChatOpen;
    },
    /** Escalation: the panel closes as the draft is handed over, so the two cannot both be up. */
    startSupportTicket(state, action: PayloadAction<{ subject: string; description: string }>) {
      state.supportTicketDraft = action.payload;
      state.supportChatOpen = false;
    },
    clearSupportTicketDraft(state) {
      state.supportTicketDraft = null;
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
  openSupportChat,
  closeSupportChat,
  toggleSupportChat,
  startSupportTicket,
  clearSupportTicketDraft,
} = uiSlice.actions;
export default uiSlice.reducer;
