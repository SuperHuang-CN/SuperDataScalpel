import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type { AlertAction, AlertChannel, AlertChannelWrite, AlertDelivery, AlertIncident, AlertRecipient, AlertRule, AlertRuleType, AlertRuleWrite,
  InAppNotification, RuntimeEngine, RuntimeOverview, RuntimeRun, RuntimeRunFilters, RuntimeStreaming } from '../model/operations';

const post = <T>(path: string, body?: unknown) => requestJson<T>(path, { method: 'POST', ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
const page = <T>(path: string, query: SearchRequest) => requestJson<PageResponse<T>>(`${path}?${toSearchParams(query)}`);
export const fetchRuntimeOverview = (range?: { from?: string; to?: string }) => {
  const params = new URLSearchParams();
  if (range?.from) params.set('from', range.from);
  if (range?.to) params.set('to', range.to);
  return requestJson<RuntimeOverview>(`/v1/operations/overview?${params}`);
};
export const fetchRuntimeRuns = (request: SearchRequest, filters: RuntimeRunFilters) => {
  const params = toSearchParams(request);
  const entries = { taskName: filters.taskName, directoryId: filters.directoryId, activeOnly: filters.activeOnly ?? false,
    mode: filters.mode ?? 'REAL', from: filters.from, to: filters.to, timeField: filters.timeField ?? 'queuedAt', batchOnly: filters.batchOnly ?? false };
  Object.entries(entries).forEach(([key, value]) => { if (value !== undefined && value !== '') params.set(key, String(value)); });
  return requestJson<PageResponse<RuntimeRun>>(`/v1/task-runs?${params}`);
};
export const fetchRuntimeStreaming = (query: SearchRequest) => page<RuntimeStreaming>('/v1/operations/streaming-deployments', query);
export const fetchRuntimeEngines = (query: SearchRequest) => page<RuntimeEngine>('/v1/operations/compute-engines', query);
export const fetchAlerts = (query: SearchRequest) => page<AlertIncident>('/v1/alert-incidents', query);
export const fetchAlert = (id: string) => requestJson<AlertIncident>(`/v1/alert-incidents/${id}`);
export const fetchAlertHistory = (id: string, query: SearchRequest) => page<AlertAction>(`/v1/alert-incidents/${id}/history`, query);
export const fetchDeliveries = (query: SearchRequest) => page<AlertDelivery>('/v1/alert-deliveries', query);
export const retryDelivery = (id: string) => post<AlertDelivery>(`/v1/alert-deliveries/${id}/actions/retry`);
export type AlertCommand =
  | { id: string; action: 'acknowledge'; reason?: string }
  | { id: string; action: 'close'; reason: string }
  | { id: string; action: 'silence'; reason: string; untilAt: string }
  | { id: string; action: 'unsilence' };
export const commandAlert = ({ id, action, ...body }: AlertCommand) => post<AlertIncident>(`/v1/alert-incidents/${id}/actions/${action}`, body);
export const fetchRules = (query: SearchRequest) => page<AlertRule>('/v1/alert-rules', query);
export const saveRule = ({ id, body }: { id?: string; body: AlertRuleWrite }) => post<AlertRule>(id ? `/v1/alert-rules/${id}/actions/update` : '/v1/alert-rules', body);
export const applyOverrides = (body: { subjectIds: string[]; configuration: AlertRuleWrite }) => post<AlertRule[]>('/v1/alert-rules/actions/apply-overrides', body);
export const commandRule = ({ id, action }: { id: string; action: 'enable' | 'disable' | 'reset-override' }) => post<AlertRule | undefined>(`/v1/alert-rules/${id}/actions/${action}`);
export const fetchChannels = (query: SearchRequest) => page<AlertChannel>('/v1/alert-channels', query);
export const saveChannel = ({ id, body }: { id?: string; body: AlertChannelWrite }) => post<AlertChannel>(id ? `/v1/alert-channels/${id}/actions/update` : '/v1/alert-channels', body);
export const commandChannel = ({ id, action }: { id: string; action: 'enable' | 'disable' | 'test' }) => post<AlertChannel | { deliveryId: string }>(`/v1/alert-channels/${id}/actions/${action}`);
export const fetchRecipients = (type: AlertRuleType, keyword = '') => requestJson<PageResponse<AlertRecipient>>(`/v1/alert-rules/recipients?${new URLSearchParams({ ruleType: type, keyword, size: '100' })}`);
export const fetchNotifications = (query: SearchRequest) => page<InAppNotification>('/v1/notifications', query);
export const fetchUnreadCount = () => requestJson<{ unreadCount: number }>('/v1/notifications/unread-count');
export const readNotification = (id: string) => post<InAppNotification>(`/v1/notifications/${id}/actions/read`);
