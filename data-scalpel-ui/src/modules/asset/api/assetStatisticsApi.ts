import { requestJson } from '../../../shared/api/http';
import type { AssetStatistics } from '../model/assetStatistics';
export const fetchAssetStatistics = () => requestJson<AssetStatistics>('/v1/assets/statistics');
