import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import api from '../../services/api';

interface Notification {
  id: number;
  userId: number;
  type: string;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  loading: boolean;
}

const initialState: NotificationState = {
  notifications: [],
  unreadCount: 0,
  loading: false,
};

export const fetchNotifications = createAsyncThunk(
  'notification/fetch',
  async (_, { rejectWithValue }) => {
    try {
      const response = await api.get('/notifications');
      return response.data;
    } catch (error: any) {
      return rejectWithValue(error.response?.data);
    }
  }
);

export const markAsRead = createAsyncThunk(
  'notification/markRead',
  async (notificationId: number) => {
    await api.put(`/notifications/${notificationId}/read`);
    return notificationId;
  }
);

export const markAllAsRead = createAsyncThunk('notification/markAllRead', async () => {
  await api.put('/notifications/read-all');
});

const notificationSlice = createSlice({
  name: 'notification',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(fetchNotifications.fulfilled, (state, action) => {
      state.notifications = action.payload.content || [];
      state.unreadCount = action.payload.unreadCount || 0;
    });
    builder.addCase(markAsRead.fulfilled, (state, action) => {
      const notification = state.notifications.find(n => n.id === action.payload);
      if (notification) {
        notification.isRead = true;
        state.unreadCount = Math.max(0, state.unreadCount - 1);
      }
    });
    builder.addCase(markAllAsRead.fulfilled, (state) => {
      state.notifications.forEach(n => { n.isRead = true; });
      state.unreadCount = 0;
    });
  },
});

export default notificationSlice.reducer;
