import { beforeEach, describe, expect, it, vi } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';

vi.mock('../../services/notificationApi', () => ({
  fetchNotificationPage: vi.fn(),
  fetchUnreadCount: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn(),
}));

import * as notificationApi from '../../services/notificationApi';
import reducer, {
  clearNotifications,
  fetchNotifications,
  markAllAsRead,
  markAsRead,
  refreshUnreadCount,
} from './notificationSlice';

const api = vi.mocked(notificationApi);
const makeStore = () => configureStore({ reducer: { notification: reducer } });
const note = (id: number, isRead = false) => ({ id, isRead, title: `n${id}` }) as never;

describe('notificationSlice', () => {
  beforeEach(() => vi.resetAllMocks());

  it('loads the list and the unread count together', async () => {
    api.fetchNotificationPage.mockResolvedValue({ content: [note(1), note(2, true)] } as never);
    api.fetchUnreadCount.mockResolvedValue(1);
    const store = makeStore();
    await store.dispatch(fetchNotifications());
    expect(store.getState().notification).toMatchObject({ unreadCount: 1, loading: false, loaded: true });
    expect(store.getState().notification.notifications).toHaveLength(2);
  });

  it('keeps what is on screen when a poll fails', async () => {
    api.fetchNotificationPage.mockResolvedValueOnce({ content: [note(1)] } as never);
    api.fetchUnreadCount.mockResolvedValueOnce(1);
    const store = makeStore();
    await store.dispatch(fetchNotifications());
    api.fetchNotificationPage.mockRejectedValueOnce(new Error('down'));
    await store.dispatch(fetchNotifications());
    expect(store.getState().notification.notifications).toHaveLength(1);
    expect(store.getState().notification.loading).toBe(false);
  });

  it('marking one read decrements the badge once', async () => {
    api.fetchNotificationPage.mockResolvedValue({ content: [note(1), note(2)] } as never);
    api.fetchUnreadCount.mockResolvedValue(2);
    const store = makeStore();
    await store.dispatch(fetchNotifications());
    await store.dispatch(markAsRead(1));
    await store.dispatch(markAsRead(1));
    expect(store.getState().notification.unreadCount).toBe(1);
  });

  it('mark all read and clear reset the badge', async () => {
    api.fetchNotificationPage.mockResolvedValue({ content: [note(1), note(2)] } as never);
    api.fetchUnreadCount.mockResolvedValue(2);
    const store = makeStore();
    await store.dispatch(fetchNotifications());
    await store.dispatch(markAllAsRead());
    expect(store.getState().notification.notifications.every((n) => n.isRead)).toBe(true);
    expect(store.getState().notification.unreadCount).toBe(0);

    api.fetchUnreadCount.mockResolvedValue(5);
    await store.dispatch(refreshUnreadCount());
    expect(store.getState().notification.unreadCount).toBe(5);
    store.dispatch(clearNotifications());
    expect(store.getState().notification).toMatchObject({ notifications: [], unreadCount: 0, loaded: false });
  });
});
