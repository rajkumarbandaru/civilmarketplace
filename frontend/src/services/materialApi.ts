import api from './api';

/**
 * Client for the supplier price list (`backend/user-service`, `MaterialPriceController`).
 *
 * Suppliers publish a rate per material per city here; the Civil AI Assistant reads the ranges
 * back out, which is why a rate published on this page turns up in someone else's estimate.
 */

export type MaterialUnit =
  | 'BAG' | 'KG' | 'QUINTAL' | 'TONNE' | 'CUBIC_FEET' | 'CUBIC_METRE'
  | 'SQUARE_FEET' | 'SQUARE_METRE' | 'RUNNING_METRE' | 'LITRE' | 'NUMBER';

/** How each unit is written in a BOQ, mirroring `MaterialUnit.getLabel()` on the service. */
export const UNIT_LABEL: Record<MaterialUnit, string> = {
  BAG: 'bag',
  KG: 'kg',
  QUINTAL: 'quintal',
  TONNE: 'tonne',
  CUBIC_FEET: 'cft',
  CUBIC_METRE: 'm³',
  SQUARE_FEET: 'sq.ft',
  SQUARE_METRE: 'sq.m',
  RUNNING_METRE: 'rmt',
  LITRE: 'litre',
  NUMBER: 'nos',
};

export interface MaterialItem {
  id: number;
  name: string;
  slug: string;
  category?: string;
  unit: MaterialUnit;
  specification?: string;
}

export interface SupplierMaterialPrice {
  id: number;
  materialItem: MaterialItem;
  price: number;
  currency: string;
  city: string;
  brand?: string;
  minOrderQuantity?: number;
  deliveryIncluded: boolean;
  notes?: string;
  isActive: boolean;
  validUntil?: string;
  updatedAt?: string;
}

/** What a supplier sends when publishing or editing a rate. */
export interface MaterialPriceInput {
  materialItem: { id: number };
  price: number;
  city: string;
  currency?: string;
  brand?: string;
  minOrderQuantity?: number | null;
  deliveryIncluded?: boolean;
  notes?: string;
  isActive?: boolean;
  validUntil?: string | null;
}

/** One material's published range, with the supplier behind each end. */
export interface MaterialRateRange {
  materialId: number;
  material: string;
  category?: string;
  unit: string;
  specification?: string;
  supplierCount: number;
  currency: string;
  low: number;
  high: number;
  median: number;
  lowSupplierUserId: number;
  highSupplierUserId: number;
  lowCity: string;
  highCity: string;
  lastUpdated?: string;
}

export const fetchMaterialCatalogue = async (): Promise<MaterialItem[]> => {
  const { data } = await api.get<MaterialItem[]>('/users/materials/catalogue');
  return data ?? [];
};

export const fetchMyMaterialPrices = async (): Promise<SupplierMaterialPrice[]> => {
  const { data } = await api.get<SupplierMaterialPrice[]>('/users/materials/my-prices');
  return data ?? [];
};

export const createMaterialPrice = async (
  input: MaterialPriceInput,
): Promise<SupplierMaterialPrice> => {
  const { data } = await api.post<SupplierMaterialPrice>('/users/materials/my-prices', input);
  return data;
};

export const updateMaterialPrice = async (
  id: number,
  changes: Partial<MaterialPriceInput>,
): Promise<SupplierMaterialPrice> => {
  const { data } = await api.put<SupplierMaterialPrice>(`/users/materials/my-prices/${id}`, changes);
  return data;
};

export const deleteMaterialPrice = async (id: number): Promise<void> => {
  await api.delete(`/users/materials/my-prices/${id}`);
};

/** @param city narrows the ranges to one place; omitted, it returns every published rate */
export const fetchMaterialRates = async (city?: string): Promise<MaterialRateRange[]> => {
  const { data } = await api.get<{ data: MaterialRateRange[] }>('/users/materials/rates', {
    params: city ? { city } : undefined,
  });
  return data?.data ?? [];
};
