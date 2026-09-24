import api from './api';

/**
 * Client for media-service uploads (`backend/media-service`).
 *
 * An upload is three steps: ask for a slot (the server checks the purpose's rules and signs a form
 * bound to one key, type and size limit), POST the file straight to storage with that form — the
 * bytes never pass through the gateway — then ask the server to verify what landed. Only a verified
 * file has a URL, and only its id is handed to other endpoints: they re-check it server side.
 */

export type MediaPurpose =
  | 'AVATAR'
  | 'PORTFOLIO'
  | 'SERVICE_MEDIA'
  | 'CATEGORY_IMAGE'
  | 'BRAND_LOGO'
  | 'TENANT_LOGO'
  | 'KYC_DOCUMENT'
  | 'PROJECT_DOCUMENT'
  | 'SUPPORT_ATTACHMENT'
  | 'REVIEW_PHOTO';

export interface UploadTicket {
  mediaId: string;
  uploadUrl: string;
  fields: Record<string, string>;
  maxBytes: number;
  expiresAt: string;
}

export interface Media {
  id: string;
  purpose: MediaPurpose;
  visibility: 'PUBLIC' | 'PRIVATE';
  status: string;
  originalFilename: string;
  contentType: string;
  sizeBytes?: number;
  /** Permanent for public files; a signed link that expires at `urlExpiresAt` for private ones. */
  url?: string;
  urlExpiresAt?: string;
}

const MB = 1024 * 1024;
const IMAGES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
const IMAGES_AND_PDF = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];

/**
 * Mirrors `MediaPurpose.java`, so a wrong file is refused before anything is sent. The server
 * enforces the same rules (and checks the file's real bytes), so this is for the user's benefit only.
 */
export const PURPOSE_RULES: Record<MediaPurpose, { maxBytes: number; types: string[] }> = {
  AVATAR: { maxBytes: 5 * MB, types: IMAGES },
  PORTFOLIO: { maxBytes: 10 * MB, types: IMAGES },
  SERVICE_MEDIA: { maxBytes: 20 * MB, types: [...IMAGES, 'video/mp4', 'video/webm'] },
  CATEGORY_IMAGE: { maxBytes: 5 * MB, types: IMAGES },
  BRAND_LOGO: { maxBytes: 2 * MB, types: IMAGES },
  TENANT_LOGO: { maxBytes: 2 * MB, types: IMAGES },
  KYC_DOCUMENT: { maxBytes: 10 * MB, types: IMAGES_AND_PDF },
  PROJECT_DOCUMENT: { maxBytes: 20 * MB, types: IMAGES_AND_PDF },
  SUPPORT_ATTACHMENT: { maxBytes: 10 * MB, types: IMAGES_AND_PDF },
  REVIEW_PHOTO: { maxBytes: 10 * MB, types: IMAGES },
};

const TYPE_NAMES: Record<string, string> = {
  'image/jpeg': 'JPG',
  'image/png': 'PNG',
  'image/webp': 'WebP',
  'image/gif': 'GIF',
  'application/pdf': 'PDF',
  'video/mp4': 'MP4',
  'video/webm': 'WebM',
};

/** e.g. "JPG, PNG, WebP or PDF, up to 10 MB" */
export const describeRules = (purpose: MediaPurpose) => {
  const { maxBytes, types } = PURPOSE_RULES[purpose];
  const names = types.map((t) => TYPE_NAMES[t] ?? t);
  const list = names.length > 1 ? `${names.slice(0, -1).join(', ')} or ${names[names.length - 1]}` : names[0];
  return `${list}, up to ${Math.round(maxBytes / MB)} MB`;
};

/** Why this file cannot be uploaded for this purpose, or null if it can. */
export const validateFile = (file: File, purpose: MediaPurpose): string | null => {
  const rules = PURPOSE_RULES[purpose];
  if (!rules.types.includes(file.type)) return `This file type is not allowed. Use ${describeRules(purpose)}.`;
  if (file.size === 0) return 'This file is empty.';
  if (file.size > rules.maxBytes) {
    return `This file is too large. The limit is ${Math.round(rules.maxBytes / MB)} MB.`;
  }
  return null;
};

/** POSTs the signed form to storage. XHR rather than fetch, for upload progress. */
const sendToStorage = (ticket: UploadTicket, file: File, onProgress?: (percent: number) => void) =>
  new Promise<void>((resolve, reject) => {
    const form = new FormData();
    Object.entries(ticket.fields).forEach(([k, v]) => form.append(k, v));
    form.append('file', file); // must be the last field: S3 ignores anything after it
    const xhr = new XMLHttpRequest();
    xhr.open('POST', ticket.uploadUrl);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(Math.round((e.loaded / e.total) * 100));
    };
    xhr.onload = () =>
      xhr.status >= 200 && xhr.status < 300
        ? resolve()
        : reject(new Error('The file storage refused the upload. Try again, or pick another file.'));
    xhr.onerror = () => reject(new Error('Could not reach the file storage. Check your connection and try again.'));
    xhr.send(form);
  });

/** Uploads one file and returns it verified. Throws with a user-readable message on any failure. */
export const uploadFile = async (
  file: File,
  purpose: MediaPurpose,
  onProgress?: (percent: number) => void
): Promise<Media> => {
  const problem = validateFile(file, purpose);
  if (problem) throw new Error(problem);
  const { data: ticket } = await api.post<UploadTicket>('/media/uploads', {
    purpose,
    filename: file.name,
    contentType: file.type,
    sizeBytes: file.size,
  });
  await sendToStorage(ticket, file, onProgress);
  const { data } = await api.post<Media>(`/media/${ticket.mediaId}/complete`);
  return data;
};

export const fetchMedia = async (mediaId: string): Promise<Media> => {
  const { data } = await api.get<Media>(`/media/${mediaId}`);
  return data;
};
