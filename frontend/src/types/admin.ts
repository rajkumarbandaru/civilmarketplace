export interface PaginationMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface PageParams {
  page?: number;
  size?: number;
  search?: string;
}
