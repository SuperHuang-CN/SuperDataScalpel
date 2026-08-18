import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  Asset,
  AssetBatchOperationResult,
  AssetCandidate,
  AssetType,
  UpdateAssetRequest,
} from '../model/asset';

const PATH = '/v1/assets';

export const fetchAssets = (request: SearchRequest): Promise<PageResponse<Asset>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<Asset>>(query ? `${PATH}?${query}` : PATH);
};

export const fetchAssetCandidates = (
  assetType: AssetType,
  keyword: string | undefined,
  page: number,
  size: number,
): Promise<PageResponse<AssetCandidate>> => {
  const query = new URLSearchParams({ assetType, page: String(page), size: String(size) });
  if (keyword?.trim()) query.set('keyword', keyword.trim());
  return requestJson<PageResponse<AssetCandidate>>(`${PATH}/candidates?${query.toString()}`);
};

export const registerAssets = (assetType: AssetType, resourceIds: string[]): Promise<Asset[]> => requestJson<Asset[]>(
  `${PATH}/actions/register`,
  { method: 'POST', body: JSON.stringify({ assetType, resourceIds }) },
);

export const updateAsset = (id: string, request: UpdateAssetRequest): Promise<Asset> => requestJson<Asset>(
  `${PATH}/${id}/actions/update`,
  { method: 'POST', body: JSON.stringify(request) },
);

export type AssetCommand = 'publish' | 'offline' | 'check' | 'sync';

export const executeAssetCommand = (id: string, command: AssetCommand): Promise<Asset> => requestJson<Asset>(
  `${PATH}/${id}/actions/${command}`,
  { method: 'POST' },
);

export const deleteAsset = (id: string): Promise<void> => requestJson<void>(
  `${PATH}/${id}/actions/delete`,
  { method: 'POST' },
);

export const executeAssetBatchCommand = (command: 'check-all' | 'sync-all'): Promise<AssetBatchOperationResult> => (
  requestJson<AssetBatchOperationResult>(`${PATH}/actions/${command}`, { method: 'POST' }, 120_000)
);
