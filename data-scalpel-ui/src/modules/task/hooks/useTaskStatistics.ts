import { useQuery } from '@tanstack/react-query';
import { fetchTaskStatistics } from '../api/taskStatisticsApi';
export const useTaskStatistics = (enabled: boolean) => useQuery({
 queryKey: ['task', 'statistics'], queryFn: fetchTaskStatistics, enabled, staleTime: 30_000,
 refetchInterval: 60_000, refetchIntervalInBackground: false,
});
