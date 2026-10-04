import { useQuery } from '@tanstack/react-query';
import { fetchServiceStatistics } from '../api/serviceStatisticsApi';
export const useServiceStatistics = (enabled: boolean) => useQuery({
 queryKey: ['dataservice', 'statistics'], queryFn: fetchServiceStatistics, enabled, staleTime: 30_000,
 refetchInterval: 60_000, refetchIntervalInBackground: false,
});
