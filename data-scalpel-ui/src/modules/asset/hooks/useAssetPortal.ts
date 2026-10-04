import { useMutation, useQuery } from '@tanstack/react-query';
import {
  fetchAssetPortalAssets,
  fetchAssetPortalDetail,
  fetchAssetPortalOverview,
  fetchAssetSourceNavigation,
} from '../api/assetPortalApi';
import type { AssetPortalFilters } from '../model/asset';

const ASSET_PORTAL_QUERY_KEY = ['asset-portal'] as const;
const PAGE_SIZE = 12;

export const useAssetPortalOverview = () => useQuery({
  queryKey: [...ASSET_PORTAL_QUERY_KEY, 'overview'],
  queryFn: fetchAssetPortalOverview,
});

export const useAssetPortalAssets = (filters: AssetPortalFilters, page = 0, size = PAGE_SIZE) => useQuery({
  queryKey: [...ASSET_PORTAL_QUERY_KEY, 'assets', filters, page, size],
  queryFn: () => fetchAssetPortalAssets(filters, page, size),
});

export const useAssetPortalDetail = (id: string | undefined) => useQuery({
  queryKey: [...ASSET_PORTAL_QUERY_KEY, 'assets', id],
  queryFn: () => fetchAssetPortalDetail(id as string),
  enabled: Boolean(id),
});

export const useAssetSourceNavigation = () => useMutation({
  mutationFn: fetchAssetSourceNavigation,
});
