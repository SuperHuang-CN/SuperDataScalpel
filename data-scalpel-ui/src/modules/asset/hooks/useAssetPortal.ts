import { useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query';
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

export const useAssetPortalAssets = (filters: AssetPortalFilters) => useInfiniteQuery({
  queryKey: [...ASSET_PORTAL_QUERY_KEY, 'assets', filters],
  queryFn: ({ pageParam }) => fetchAssetPortalAssets(filters, pageParam, PAGE_SIZE),
  initialPageParam: 0,
  getNextPageParam: (lastPage) => (
    lastPage.page + 1 < lastPage.totalPages ? lastPage.page + 1 : undefined
  ),
});

export const useAssetPortalDetail = (id: string | undefined) => useQuery({
  queryKey: [...ASSET_PORTAL_QUERY_KEY, 'assets', id],
  queryFn: () => fetchAssetPortalDetail(id as string),
  enabled: Boolean(id),
});

export const useAssetSourceNavigation = () => useMutation({
  mutationFn: fetchAssetSourceNavigation,
});
