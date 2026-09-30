import { useQuery } from '@tanstack/react-query';
import {
  fetchGatewayAccessLogs,
  fetchGatewayAccessRecent,
  fetchGatewayAccessOverview,
  fetchGatewayAccessRankings,
  fetchGatewayAccessTrend,
  type GatewayAccessLogQuery,
  type GatewayAccessRankingQuery,
  type GatewayAccessScope,
} from '../api/gatewayAccessApi';

const gatewayAccessQueryKey = ['gateway-access'] as const;

export const useGatewayAccessRecent = (query: { dataServiceId?: string; consumerId?: string }) => useQuery({
  queryKey: [...gatewayAccessQueryKey, 'recent', query],
  queryFn: () => fetchGatewayAccessRecent(query),
  staleTime: 10_000,
  refetchInterval: 15_000,
  refetchIntervalInBackground: false,
});
const AUTO_REFRESH_MS = 5 * 60 * 1000;

const queryOptions = {
  staleTime: 60_000,
  refetchInterval: AUTO_REFRESH_MS,
  refetchIntervalInBackground: false,
};

export const useGatewayAccessOverview = (query: GatewayAccessScope) => useQuery({
  queryKey: [...gatewayAccessQueryKey, 'overview', query],
  queryFn: () => fetchGatewayAccessOverview(query),
  ...queryOptions,
});

export const useGatewayAccessTrend = (query: GatewayAccessScope) => useQuery({
  queryKey: [...gatewayAccessQueryKey, 'trend', query],
  queryFn: () => fetchGatewayAccessTrend(query),
  ...queryOptions,
});

export const useGatewayAccessRankings = (query: GatewayAccessRankingQuery) => useQuery({
  queryKey: [...gatewayAccessQueryKey, 'rankings', query],
  queryFn: () => fetchGatewayAccessRankings(query),
  ...queryOptions,
});

export const useGatewayAccessLogs = (query: GatewayAccessLogQuery) => useQuery({
  queryKey: [...gatewayAccessQueryKey, 'logs', query],
  queryFn: () => fetchGatewayAccessLogs(query),
  ...queryOptions,
});
