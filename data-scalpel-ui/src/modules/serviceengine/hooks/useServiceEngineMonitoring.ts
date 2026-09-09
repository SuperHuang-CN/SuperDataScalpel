import { useQuery } from '@tanstack/react-query';
import { fetchDataSourcePoolMonitor, fetchDataSourcePoolSummaries } from '../api/serviceEngineMonitoringApi';

// Registration changes invalidate this prefix too; monitor failure never fails the registration query.
export const useDataSourcePoolSummaries = (engineId: string, enabled: boolean, autoRefresh: boolean) => useQuery({
  queryKey: ['service-engine-data-sources', 'pool-summaries', engineId],
  queryFn: ({ signal }) => fetchDataSourcePoolSummaries(engineId, signal),
  enabled,
  retry: false,
  gcTime: 0,
  refetchOnWindowFocus: false,
  refetchInterval: autoRefresh ? 5000 : false,
  refetchIntervalInBackground: false,
});

export const useDataSourcePoolMonitor = (registrationId: string, autoRefresh: boolean) => useQuery({
  queryKey: ['service-engine-data-sources', 'pool-monitor', registrationId],
  queryFn: ({ signal }) => fetchDataSourcePoolMonitor(registrationId, signal),
  retry: false,
  gcTime: 0,
  refetchOnWindowFocus: false,
  refetchInterval: autoRefresh ? 5000 : false,
  refetchIntervalInBackground: false,
});
