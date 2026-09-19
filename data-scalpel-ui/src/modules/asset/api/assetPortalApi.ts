import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import type {
  AssetPortalAssetDetail,
  AssetPortalAssetSummary,
  AssetPortalFilters,
  AssetPortalOverview,
  AssetSourceNavigation,
} from '../model/asset';

const PORTAL_PATH = '/v1/asset-portal';
const MANAGEMENT_PATH = '/v1/assets';

export const fetchAssetPortalOverview = (): Promise<AssetPortalOverview> => requestJson<AssetPortalOverview>(
  `${PORTAL_PATH}/overview`,
  { skipAuthentication: true },
);

export const fetchAssetPortalAssets = (
  filters: AssetPortalFilters,
  page: number,
  size: number,
): Promise<PageResponse<AssetPortalAssetSummary>> => {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters.keyword) query.set('keyword', filters.keyword);
  if (filters.assetType) query.set('assetType', filters.assetType);
  if (filters.directoryId) query.set('directoryId', filters.directoryId);
  return requestJson<PageResponse<AssetPortalAssetSummary>>(
    `${PORTAL_PATH}/assets?${query.toString()}`,
    { skipAuthentication: true },
  );
};

export const fetchAssetPortalDetail = (id: string): Promise<AssetPortalAssetDetail> => (
  requestJson<AssetPortalAssetDetail>(`${PORTAL_PATH}/assets/${id}`, { skipAuthentication: true })
);

export const fetchAssetSourceNavigation = (id: string): Promise<AssetSourceNavigation> => (
  requestJson<AssetSourceNavigation>(`${MANAGEMENT_PATH}/${id}/source-navigation`)
);
