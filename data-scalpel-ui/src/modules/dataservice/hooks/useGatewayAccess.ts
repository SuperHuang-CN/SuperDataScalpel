import { useQuery } from '@tanstack/react-query';
import {
  fetchGatewayAccessLogs,
  fetchGatewayAccessOverview,
  fetchGatewayAccessRankings,
  fetchGatewayAccessTrend,
  type GatewayAccessLogQuery,
  type GatewayAccessRankingQuery,
  type GatewayAccessScope,
} from '../api/gatewayAccessApi';

const gatewayAccessQueryKey = ['gateway-access'] as const;
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
