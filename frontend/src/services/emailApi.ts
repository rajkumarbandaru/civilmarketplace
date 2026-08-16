import api from './api';

/**
 * The transactional email console: the templates the platform sends, and the log of what it
 * actually sent.
 *
 * Both live in notification-service under `/api/v1/admin/notifications/**`, which the gateway
 * routes there ahead of the catch-all `/api/v1/admin/**` that belongs to admin-service.
 */

export interface EmailTemplate {
  id: number;
  /** Stable identifier the sender looks up by — never renamed once created. */
  templateKey: string;
  name: string;
  description: string | null;
  /** May itself contain `${...}` placeholders. */
  subject: string;
  htmlBody: string;
  /** Placeholder name to example value, used for the preview and the test send. */
  sampleVariables: Record<string, unknown>;
  /** Every placeholder found in the subject and body. */
  placeholders: string[];
  active: boolean;
  /** Built-ins are sent by key from the backend: editable and resettable, never deletable. */
  systemOwned: boolean;
  /** True while this row is what gets rendered instead of the file shipped in the jar. */
  overridingDefault: boolean;
  updatedBy: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface TemplateCommand {
  templateKey?: string;
  name: string;
  description?: string | null;
  subject: string;
  htmlBody: string;
  sampleVariables?: Record<string, unknown>;
  active?: boolean;
}

export interface TemplatePreview {
  subject?: string;
  html?: string;
  /** Set instead of `html` when the template does not compile. */
  error?: string;
}

export type EmailStatus =
  | 'PENDING'
  | 'SENT'
  | 'DELIVERED'
  | 'UNDELIVERED'
  | 'FAILED'
  | 'SKIPPED';

/** EMAIL | SMS | WHATSAPP | IN_APP */
export type NotificationChannel = 'EMAIL' | 'SMS' | 'WHATSAPP' | 'IN_APP';

export interface EmailLogEntry {
  id: number;
  /** An email template key, or the notification type for the other channels. */
  templateKey: string;
  templateName: string;
  channel: NotificationChannel;
  recipient: string;
  subject: string;
  /**
   * What was actually sent — HTML for email, plain text for the other channels. Only present when
   * a single entry is fetched; the list omits it. Null for messages sent before bodies were kept.
   */
  body: string | null;
  status: EmailStatus;
  /** smtp | brevo | log */
  provider: string;
  providerMessageId: string | null;
  errorMessage: string | null;
  triggeredBy: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface EmailLogSummary {
  total: number;
  byStatus: Record<string, number>;
  byChannel: Record<string, number>;
}

/** Spring's Page envelope, narrowed to what the table uses. */
export interface Paged<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const TEMPLATES = '/admin/notifications/email-templates';
const LOG = '/admin/notifications/emails';

// ------------------------------------------------------------------------------- templates

export const fetchEmailTemplates = async (): Promise<EmailTemplate[]> => {
  const { data } = await api.get<EmailTemplate[]>(TEMPLATES);
  return data;
};

export const fetchEmailTemplate = async (key: string): Promise<EmailTemplate> => {
  const { data } = await api.get<EmailTemplate>(`${TEMPLATES}/${key}`);
  return data;
};

export const createEmailTemplate = async (command: TemplateCommand): Promise<EmailTemplate> => {
  const { data } = await api.post<EmailTemplate>(TEMPLATES, command);
  return data;
};

export const updateEmailTemplate = async (
  key: string,
  command: TemplateCommand
): Promise<EmailTemplate> => {
  const { data } = await api.put<EmailTemplate>(`${TEMPLATES}/${key}`, command);
  return data;
};

export const deleteEmailTemplate = async (key: string): Promise<void> => {
  await api.delete(`${TEMPLATES}/${key}`);
};

/** Restores a built-in to the version shipped with the service, discarding local edits. */
export const resetEmailTemplate = async (key: string): Promise<EmailTemplate> => {
  const { data } = await api.post<EmailTemplate>(`${TEMPLATES}/${key}/reset`);
  return data;
};

/**
 * Renders whatever is currently in the editor — passing the unsaved subject and body is the point,
 * so a broken template can be found without saving it over a working one.
 */
export const previewEmailTemplate = async (
  key: string,
  body: { subject?: string; htmlBody?: string; variables?: Record<string, unknown> }
): Promise<TemplatePreview> => {
  const { data } = await api.post<TemplatePreview>(`${TEMPLATES}/${key}/preview`, body);
  return data;
};

export const testSendEmailTemplate = async (
  key: string,
  recipient: string,
  variables?: Record<string, unknown>
): Promise<{ success: boolean; status: EmailStatus; recipient: string }> => {
  const { data } = await api.post(`${TEMPLATES}/${key}/test-send`, { recipient, variables });
  return data;
};

// ------------------------------------------------------------------------------- delivery log

export const fetchEmailLog = async (params: {
  status?: string;
  channel?: string;
  templateKey?: string;
  search?: string;
  page?: number;
  size?: number;
}): Promise<Paged<EmailLogEntry>> => {
  const { data } = await api.get<Paged<EmailLogEntry>>(LOG, { params });
  return data;
};

/** One entry in full, including the body. What the eye icon opens. */
export const fetchEmailLogEntry = async (id: number): Promise<EmailLogEntry> => {
  const { data } = await api.get<EmailLogEntry>(`${LOG}/${id}`);
  return data;
};

export const fetchEmailLogSummary = async (): Promise<EmailLogSummary> => {
  const { data } = await api.get<EmailLogSummary>(`${LOG}/summary`);
  return data;
};

export const fetchLoggedTemplateKeys = async (): Promise<string[]> => {
  const { data } = await api.get<string[]>(`${LOG}/template-keys`);
  return data;
};
