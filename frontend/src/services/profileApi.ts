import api from './api';

/**
 * The signed-in user's own profile settings (auth-service `ProfileController`), plus their KYC
 * documents and work portfolio (user-service). Files are always referenced by the id of an upload
 * made with `uploadFile`; the services re-check each one's purpose and uploader.
 */

export interface ProfileUser {
  id: number;
  name: string;
  email: string;
  phone: string;
  profilePicture: string;
  role: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  status: string;
}

export const setProfilePicture = async (mediaId: string): Promise<ProfileUser> => {
  const { data } = await api.put<ProfileUser>('/auth/me/profile-picture', { mediaId });
  return data;
};

export const clearProfilePicture = async (): Promise<ProfileUser> => {
  const { data } = await api.delete<ProfileUser>('/auth/me/profile-picture');
  return data;
};

export type KycDocumentType = 'AADHAAR' | 'PAN' | 'GST' | 'TRADE_LICENSE' | 'DEGREE_CERTIFICATE' | 'OTHER';
export type KycStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export const KYC_DOCUMENT_TYPES: Array<{ value: KycDocumentType; label: string }> = [
  { value: 'AADHAAR', label: 'Aadhaar' },
  { value: 'PAN', label: 'PAN card' },
  { value: 'GST', label: 'GST certificate' },
  { value: 'TRADE_LICENSE', label: 'Trade licence' },
  { value: 'DEGREE_CERTIFICATE', label: 'Degree certificate' },
  { value: 'OTHER', label: 'Other' },
];

export const kycTypeLabel = (type: string) => KYC_DOCUMENT_TYPES.find((t) => t.value === type)?.label ?? type;

export interface KycDocument {
  id: number;
  userId: number;
  documentType: KycDocumentType;
  documentNumber: string | null;
  mediaId: string | null;
  status: KycStatus;
  rejectionReason: string | null;
  createdAt: string;
  reviewedAt: string | null;
}

export interface FileLink {
  url: string;
  expiresAt: string | null;
  filename: string | null;
  contentType: string | null;
}

export const fetchMyKycDocuments = async (): Promise<KycDocument[]> => {
  const { data } = await api.get<KycDocument[]>('/users/kyc');
  return data;
};

export const submitKycDocument = async (request: {
  documentType: KycDocumentType;
  documentNumber?: string;
  mediaId: string;
}): Promise<KycDocument> => {
  const { data } = await api.post<KycDocument>('/users/kyc', request);
  return data;
};

export const fetchMyKycFile = async (documentId: number): Promise<FileLink> => {
  const { data } = await api.get<FileLink>(`/users/kyc/${documentId}/file`);
  return data;
};

export interface PortfolioItem {
  id: number;
  userId: number;
  title: string;
  description: string | null;
  imageUrl: string;
  category: string | null;
  completionDate: string | null;
  createdAt: string;
}

export const fetchMyPortfolio = async (): Promise<PortfolioItem[]> => {
  const { data } = await api.get<PortfolioItem[]>('/users/portfolio');
  return data;
};

export const addPortfolioItem = async (request: {
  title: string;
  description?: string;
  category?: string;
  completionDate?: string;
  mediaId: string;
}): Promise<PortfolioItem> => {
  const { data } = await api.post<PortfolioItem>('/users/portfolio', request);
  return data;
};

export const deletePortfolioItem = async (id: number) => {
  await api.delete(`/users/portfolio/${id}`);
};

/**
 * Opens a private file in a new tab. The tab is opened before the link is fetched: a window opened
 * after an await is no longer tied to the click and popup blockers stop it.
 */
export const openFileLink = async (fetchLink: () => Promise<FileLink>) => {
  const tab = window.open('', '_blank');
  try {
    const link = await fetchLink();
    if (tab) {
      tab.opener = null;
      tab.location.href = link.url;
    } else {
      window.location.assign(link.url);
    }
  } catch (e) {
    tab?.close();
    throw e;
  }
};

/** Staff review of KYC documents (user-service `AdminKycController`). */
export interface KycPage {
  data: KycDocument[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const fetchPendingKyc = async (page = 0, size = 20): Promise<KycPage> => {
  const { data } = await api.get<KycPage>('/users/admin/kyc/pending', { params: { page, size } });
  return data;
};

export const fetchKycFileForReview = async (documentId: number): Promise<FileLink> => {
  const { data } = await api.get<FileLink>(`/users/admin/kyc/${documentId}/file`);
  return data;
};

export const approveKyc = async (documentId: number): Promise<KycDocument> => {
  const { data } = await api.put<KycDocument>(`/users/admin/kyc/${documentId}/approve`);
  return data;
};

export const rejectKyc = async (documentId: number, reason: string): Promise<KycDocument> => {
  const { data } = await api.put<KycDocument>(`/users/admin/kyc/${documentId}/reject`, { reason });
  return data;
};
