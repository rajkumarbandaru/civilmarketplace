import api from './api';

/**
 * The signed-in user's in-app notifications, and Super Admin's broadcast tools.
 *
 * The bell used to be decorative: nothing ever fetched a notification, and the admin shell's badge
 * was the literal number 3. These are the calls behind making it real.
 */

export interface AppNotification {
  id: number;
  userId: number;
  type: string;
  title: string;
  message: string;
  /** What the notification is about — "BOOKING", "PAYMENT" — used to work out where it links. */
  referenceType: string | null;
  referenceId: number | null;
  isRead: boolean;
  createdAt: string;
}

export interface NotificationPage {
  content: AppNotification[];
  totalElements: number;
  number: number;
  totalPages: number;
}

export const fetchNotificationPage = async (page = 0, size = 20): Promise<NotificationPage> => {
  const { data } = await api.get<NotificationPage>('/notifications', { params: { page, size } });
  return data;
};

/**
 * The unread total, from its own endpoint.
 *
 * The list is paged, so counting unread rows in the first page would undercount as soon as someone
 * had more than a page of them — which is exactly when the number matters.
 */
export const fetchUnreadCount = async (): Promise<number> => {
  const { data } = await api.get<{ unreadCount?: number; count?: number }>('/notifications/unread-count');
  return Number(data.unreadCount ?? data.count ?? 0);
};

export const markNotificationRead = async (id: number): Promise<void> => {
  await api.put(`/notifications/${id}/read`);
};

export const markAllNotificationsRead = async (): Promise<void> => {
  await api.put('/notifications/read-all');
};

export const deleteNotification = async (id: number): Promise<void> => {
  await api.delete(`/notifications/${id}`);
};

// --------------------------------------------------------------------- admin broadcast

export interface Announcement {
  id: number;
  title: string;
  body: string;
  /** Comma-joined role names, or "*" for everyone. */
  targetRoles: string;
  createdBy: number | null;
  recipientCount: number | null;
  createdAt: string;
}

export interface CreateAnnouncementRequest {
  title: string;
  body: string;
  /** Role names, or a single-element `['*']` for every active user. */
  targetRoles: string[];
}

export const publishAnnouncement = async (
  request: CreateAnnouncementRequest
): Promise<Announcement> => {
  const { data } = await api.post<Announcement>('/admin/announcements', request);
  return data;
};

export const fetchAnnouncements = async (page = 0, size = 20) => {
  const { data } = await api.get<{ content: Announcement[]; totalElements: number }>(
    '/admin/announcements',
    { params: { page, size } }
  );
  return data;
};

/** A one-off alert aimed at a single user, on whichever channels are named. */
export interface DispatchRequest {
  userId: number;
  type: string;
  title: string;
  message: string;
  email?: string;
  phone?: string;
  channels: string[];
}

export const dispatchNotification = async (request: DispatchRequest) => {
  const { data } = await api.post('/admin/notifications/dispatch', request);
  return data;
};
