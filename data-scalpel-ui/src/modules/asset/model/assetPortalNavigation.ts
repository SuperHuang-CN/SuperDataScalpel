import type { AssetPortalFilters } from './asset';

export const assetCatalogUrl = (filters: AssetPortalFilters = {}) => {
  const query = new URLSearchParams();
  if (filters.keyword) query.set('keyword', filters.keyword);
  if (filters.assetType) query.set('assetType', filters.assetType);
  if (filters.directoryId) query.set('directoryId', filters.directoryId);
  if (filters.sort) query.set('sort', filters.sort);
  return `/assets/browse${query.size ? `?${query}` : ''}`;
};
