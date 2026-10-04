import { useQuery } from '@tanstack/react-query';
import { fetchModelStatistics } from '../api/modelStatisticsApi';
export const useModelStatistics = (enabled: boolean) => useQuery({
 queryKey: ['model', 'statistics'], queryFn: fetchModelStatistics, enabled, staleTime: 30_000,
 refetchInterval: 60_000, refetchIntervalInBackground: false,
});
