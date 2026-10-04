import { useQuery } from '@tanstack/react-query';
import { fetchAssetStatistics } from '../api/assetStatisticsApi';
export const useAssetStatistics = (enabled: boolean) => useQuery({
 queryKey: ['asset', 'statistics'], queryFn: fetchAssetStatistics, enabled, staleTime: 30_000,
 refetchInterval: 60_000, refetchIntervalInBackground: false,
});
