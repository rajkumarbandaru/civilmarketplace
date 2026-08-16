import api from './api';
import { ServiceEntry } from '../constants/serviceCatalogue';

/**
 * The public service catalogue, as served by booking-service.
 *
 * Public on purpose — the services list is the first thing a signed-out visitor sees, so it is
 * routed at `/api/v1/catalogue` outside the gateway's auth filter.
 */

export interface CatalogueCategory {
  id: number;
  name: string;
  slug: string;
  icon?: string | null;
  description?: string | null;
  sortOrder?: number | null;
}

interface CatalogueServiceRow {
  slug: string;
  title: string;
  category: string;
  icon?: string | null;
  price?: string | null;
  mediaUrl?: string | null;
  mediaType?: 'IMAGE' | 'VIDEO' | 'ANIMATION' | null;
  rating?: number | null;
  reviews?: number | null;
  /** Comma-separated, as typed in the admin form. */
  aliases?: string | null;
}

export interface Catalogue {
  categories: CatalogueCategory[];
  services: ServiceEntry[];
}

/**
 * Normalises an API row into the shape the pages already render.
 *
 * Every optional field gets a defined default here rather than at each call site: a card that has
 * to cope with a null price, a null icon and a null rating in three different components is how
 * "₹undefined" ends up on screen.
 */
const toEntry = (row: CatalogueServiceRow): ServiceEntry => ({
  slug: row.slug,
  title: row.title,
  category: row.category,
  icon: row.icon || 'Handyman',
  price: row.price || 'Quote',
  rating: row.rating ?? 0,
  reviews: row.reviews ?? 0,
  aliases: (row.aliases || '')
    .split(',')
    .map((alias) => alias.trim())
    .filter(Boolean),
  mediaUrl: row.mediaUrl || null,
  mediaType: row.mediaType || null,
});

export const fetchCatalogue = async (): Promise<Catalogue> => {
  const response = await api.get<{
    success: boolean;
    data: { categories: CatalogueCategory[]; services: CatalogueServiceRow[] };
  }>('/catalogue');

  const data = response.data?.data;
  return {
    categories: data?.categories ?? [],
    services: (data?.services ?? []).map(toEntry),
  };
};
