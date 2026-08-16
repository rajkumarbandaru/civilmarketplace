import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import {
  AppNotification,
  fetchNotificationPage,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../services/notificationApi';

/**
 * The bell's data.
 *
 * Two things were wrong before. Nothing ever dispatched the fetch, so the list was permanently
 * empty; and the unread count was read from `payload.unreadCount`, a field the paged endpoint does
 * not return — so the badge could only ever have been zero. The count now comes from the endpoint
 * that actually serves it.
 */

interface NotificationState {
  notifications: AppNotification[];
  unreadCount: number;
  loading: boolean;
  /** True once a fetch has resolved, so the menu can tell "empty" from "not loaded yet". */
  loaded: boolean;
}

const initialState: NotificationState = {
  notifications: [],
  unreadCount: 0,
  loading: false,
  loaded: false,
};

export const fetchNotifications = createAsyncThunk(
  'notification/fetch',
  async (_, { rejectWithValue }) => {
    try {
      // Both in one go: opening the bell should not leave the badge disagreeing with the list.
      const [page, unreadCount] = await Promise.all([
        fetchNotificationPage(0, 20),
        fetchUnreadCount(),
      ]);
      return { notifications: page.content ?? [], unreadCount };
    } catch (error: any) {
      return rejectWithValue(error.response?.data);
    }
  }
);

/** Just the badge — cheap enough to poll, unlike the whole list. */
export const refreshUnreadCount = createAsyncThunk(
  'notification/unreadCount',
  async () => fetchUnreadCount()
);

export const markAsRead = createAsyncThunk(
  'notification/markRead',
  async (notificationId: number) => {
    await markNotificationRead(notificationId);
    return notificationId;
  }
);

export const markAllAsRead = createAsyncThunk('notification/markAllRead', async () => {
  await markAllNotificationsRead();
});

const notificationSlice = createSlice({
  name: 'notification',
  initialState,
  reducers: {
    /** Signing out must not leave the next account looking at the previous one's alerts. */
    clearNotifications: (state) => {
      state.notifications = [];
      state.unreadCount = 0;
      state.loaded = false;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(fetchNotifications.pending, (state) => {
      state.loading = true;
    });
    builder.addCase(fetchNotifications.fulfilled, (state, action) => {
      state.notifications = action.payload.notifications;
      state.unreadCount = action.payload.unreadCount;
      state.loading = false;
      state.loaded = true;
    });
    builder.addCase(fetchNotifications.rejected, (state) => {
      // A failed poll leaves whatever was already on screen rather than blanking the menu.
      state.loading = false;
      state.loaded = true;
    });
    builder.addCase(refreshUnreadCount.fulfilled, (state, action) => {
      state.unreadCount = action.payload;
    });
    builder.addCase(markAsRead.fulfilled, (state, action) => {
      const notification = state.notifications.find((n) => n.id === action.payload);
      if (notification && !notification.isRead) {
        notification.isRead = true;
        state.unreadCount = Math.max(0, state.unreadCount - 1);
      }
    });
    builder.addCase(markAllAsRead.fulfilled, (state) => {
      state.notifications.forEach((n) => {
        n.isRead = true;
      });
      state.unreadCount = 0;
    });
  },
});

export const { clearNotifications } = notificationSlice.actions;
export default notificationSlice.reducer;
