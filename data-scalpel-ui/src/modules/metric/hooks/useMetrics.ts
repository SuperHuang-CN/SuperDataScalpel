import { useQuery, type QueryClient } from '@tanstack/react-query';
import { fetchMetric, fetchMetrics } from '../api/metricApi';
import type { SearchRequest } from '../../../shared/search';
export const useMetrics = (request: SearchRequest) => useQuery({ queryKey: ['metrics', 'list', request], queryFn: () => fetchMetrics(request) });
export const useMetric = (id: string) => useQuery({ queryKey: ['metrics', 'detail', id], queryFn: () => fetchMetric(id), enabled: Boolean(id) });
export const invalidateMetrics = (client: QueryClient) => Promise.all([
 client.invalidateQueries({ queryKey: ['metrics'] }),
 client.invalidateQueries({ queryKey: ['directories', 'METRIC'] }),
 client.invalidateQueries({ predicate: query => query.queryKey.includes('references') }),
]);
