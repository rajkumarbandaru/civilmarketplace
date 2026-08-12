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

const initialState: UiState = {
  sidebarOpen: false,
  theme: 'light',
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
  setSearchQuery,
  setSelectedCity,
  showSnackbar,
  hideSnackbar,
} = uiSlice.actions;
export default uiSlice.reducer;
