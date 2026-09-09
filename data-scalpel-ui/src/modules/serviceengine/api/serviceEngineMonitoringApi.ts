import { requestJson } from '../../../shared/api/http';
import type { DataSourcePoolMonitor, DataSourcePoolSummaries } from '../model/serviceEngineMonitoring';

export const fetchDataSourcePoolSummaries = (engineId: string, signal: AbortSignal) => (
  requestJson<DataSourcePoolSummaries>(`/v1/service-engines/${engineId}/data-source-pools`, { signal, cache: 'no-store' }, 10_000)
);

export const fetchDataSourcePoolMonitor = (registrationId: string, signal: AbortSignal) => (
  requestJson<DataSourcePoolMonitor>(`/v1/service-engine-data-sources/${registrationId}/pool-monitor`, { signal, cache: 'no-store' }, 10_000)
);
