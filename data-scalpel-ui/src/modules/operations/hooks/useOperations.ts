import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { hasAccessToken } from '../../../shared/api/http';
import type { SearchRequest } from '../../../shared/search';
import * as api from '../api/operationsApi';
import type { AlertRuleType, RuntimeRunFilters } from '../model/operations';

const live = { refetchInterval: 10_000, refetchIntervalInBackground: false } as const;
export const useRuntimeOverview = (range?: { from?: string; to?: string }) => useQuery({
  queryKey: ['operations', 'overview', range ?? {}], queryFn: () => api.fetchRuntimeOverview(range),
  ...live, refetchInterval: 30_000,
});
export const useRuntimeRuns = (request: SearchRequest, filters: RuntimeRunFilters) => useQuery({
  queryKey: ['operations', 'runs', request, filters], queryFn: () => api.fetchRuntimeRuns(request, filters), ...live,
});
export const useRuntimeStreaming = (request: SearchRequest) => useQuery({ queryKey: ['operations', 'streaming', request], queryFn: () => api.fetchRuntimeStreaming(request), ...live });
export const useRuntimeEngines = (request: SearchRequest) => useQuery({ queryKey: ['operations', 'engines', request], queryFn: () => api.fetchRuntimeEngines(request), ...live, refetchInterval: 30_000 });
export const useAlerts = (request: SearchRequest) => useQuery({ queryKey: ['operations', 'alerts', request], queryFn: () => api.fetchAlerts(request), ...live });
export const useAlert = (id: string | null) => useQuery({ queryKey: ['operations', 'alert', id], queryFn: () => api.fetchAlert(id!), enabled: Boolean(id), ...live });
export const useAlertHistory = (id: string | null, request: SearchRequest) => useQuery({ queryKey: ['operations', 'history', id, request], queryFn: () => api.fetchAlertHistory(id!, request), enabled: Boolean(id), ...live });
export const useDeliveries = (request: SearchRequest, enabled = true) => useQuery({ queryKey: ['operations', 'deliveries', request], queryFn: () => api.fetchDeliveries(request), enabled, ...live });
export const useRules = (request: SearchRequest) => useQuery({ queryKey: ['operations', 'rules', request], queryFn: () => api.fetchRules(request) });
export const useChannels = (request: SearchRequest, enabled = true) => useQuery({ queryKey: ['operations', 'channels', request], queryFn: () => api.fetchChannels(request), enabled });
export const useRecipients = (type: AlertRuleType, keyword: string, enabled: boolean) => useQuery({ queryKey: ['operations', 'recipients', type, keyword], queryFn: () => api.fetchRecipients(type, keyword), enabled });
export const useNotifications = (request: SearchRequest, enabled = true) => useQuery({ queryKey: ['operations', 'notifications', request], queryFn: () => api.fetchNotifications(request), enabled, ...live });
export const useUnreadNotifications = () => useQuery({ queryKey: ['operations', 'unread'], queryFn: api.fetchUnreadCount, enabled: hasAccessToken(), ...live });
export const useOperationsMutation = <T, V>(mutationFn: (variables: V) => Promise<T>) => {
  const client = useQueryClient();
  return useMutation({ mutationFn, onSuccess: () => client.invalidateQueries({ queryKey: ['operations'] }) });
};
