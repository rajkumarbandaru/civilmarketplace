import api from './api';

/**
 * The public site's editable copy — every heading, paragraph, link and image on the landing page
 * and in the footer, plus the shared logo.
 *
 * Public on the same reasoning as the catalogue: it is what a signed-out visitor lands on, so it
 * is routed at `/api/v1/content` outside the gateway's auth filter. Writes live under
 * `/api/v1/admin/content` and are Super Admin's alone.
 */

export interface ContentItem {
  id: number;
  title: string | null;
  subtitle: string | null;
  body: string | null;
  /** A Material-UI icon name, resolved by components/DynamicIcon. */
  icon: string | null;
  imageUrl: string | null;
  /** In-app path, absolute URL, or null for an item that is not a link. */
  linkUrl: string | null;
  /** Small leading label — the step number on How It Works, a tag elsewhere. */
  badge: string | null;
  sortOrder: number;
  enabled: boolean;
}

export interface ContentSection {
  id: number;
  /** HOME | FOOTER | GLOBAL */
  pageKey: string;
  sectionKey: string;
  title: string | null;
  subtitle: string | null;
  body: string | null;
  imageUrl: string | null;
  linkLabel: string | null;
  linkUrl: string | null;
  /** Footer only: which column the group is stacked into. */
  columnIndex: number;
  sortOrder: number;
  enabled: boolean;
  /** Built-in sections can be hidden and edited, never deleted. */
  systemOwned: boolean;
  items: ContentItem[];
}

export interface SiteContent {
  sections: ContentSection[];
  version: string | null;
}

export interface MediaAsset {
  id: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  /** Gateway-relative; run it through {@link resolveMediaUrl} before putting it in a `src`. */
  url: string;
  createdAt: string;
}

export interface SectionCommand {
  pageKey?: string;
  sectionKey?: string;
  title?: string | null;
  subtitle?: string | null;
  body?: string | null;
  imageUrl?: string | null;
  linkLabel?: string | null;
  linkUrl?: string | null;
  columnIndex?: number;
  sortOrder?: number;
  enabled?: boolean;
}

export interface ItemCommand {
  title?: string | null;
  subtitle?: string | null;
  body?: string | null;
  icon?: string | null;
  imageUrl?: string | null;
  linkUrl?: string | null;
  badge?: string | null;
  sortOrder?: number;
  enabled?: boolean;
}

/**
 * Turns a stored image reference into something a browser can load.
 *
 * Uploads are stored as gateway-relative paths (`/api/v1/content/media/7`) so the same row works
 * in dev, in docker and in production — but the frontend is served from a different origin than
 * the gateway in dev, so a bare path would resolve against :3000 and 404. External URLs an admin
 * pasted are returned untouched.
 */
export const resolveMediaUrl = (url: string | null | undefined): string | undefined => {
  if (!url) return undefined;
  if (/^(https?:)?\/\//i.test(url) || url.startsWith('data:')) return url;
  if (url.startsWith('/api/')) {
    const base = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
    return `${base}${url}`;
  }
  return url;
};

export const fetchSiteContent = async (): Promise<SiteContent> => {
  const { data } = await api.get<SiteContent>('/content/site');
  return data;
};

// ------------------------------------------------------------------------------- admin (write)

export const fetchAllSections = async (): Promise<ContentSection[]> => {
  const { data } = await api.get<ContentSection[]>('/admin/content/sections');
  return data;
};

export const createSection = async (command: SectionCommand): Promise<ContentSection> => {
  const { data } = await api.post<ContentSection>('/admin/content/sections', command);
  return data;
};

export const updateSection = async (id: number, command: SectionCommand): Promise<ContentSection> => {
  const { data } = await api.put<ContentSection>(`/admin/content/sections/${id}`, command);
  return data;
};

export const deleteSection = async (id: number): Promise<void> => {
  await api.delete(`/admin/content/sections/${id}`);
};

export const addItem = async (sectionId: number, command: ItemCommand): Promise<ContentItem> => {
  const { data } = await api.post<ContentItem>(`/admin/content/sections/${sectionId}/items`, command);
  return data;
};

export const updateItem = async (itemId: number, command: ItemCommand): Promise<ContentItem> => {
  const { data } = await api.put<ContentItem>(`/admin/content/items/${itemId}`, command);
  return data;
};

export const deleteItem = async (itemId: number): Promise<void> => {
  await api.delete(`/admin/content/items/${itemId}`);
};

export const reorderItems = async (sectionId: number, ids: number[]): Promise<ContentSection> => {
  const { data } = await api.put<ContentSection>(
    `/admin/content/sections/${sectionId}/items/order`,
    { ids }
  );
  return data;
};

export const fetchMedia = async (): Promise<MediaAsset[]> => {
  const { data } = await api.get<MediaAsset[]>('/admin/content/media');
  return data;
};

export const uploadMedia = async (file: File): Promise<MediaAsset> => {
  const form = new FormData();
  form.append('file', file);
  // Content-Type is left to the browser: axios' JSON default would omit the multipart boundary.
  const { data } = await api.post<MediaAsset>('/admin/content/media', form, {
    headers: { 'Content-Type': undefined as unknown as string },
  });
  return data;
};

export const deleteMedia = async (id: number): Promise<void> => {
  await api.delete(`/admin/content/media/${id}`);
};
