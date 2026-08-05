import { requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import type {
  GatewayAccessLog,
  GatewayAccessOverview,
  GatewayAccessRanking,
  GatewayAccessRankingDimension,
  GatewayAccessRankingMetric,
  GatewayAccessTrend,
} from '../model/gatewayAccess';

const GATEWAY_ACCESS_LOG_PATH = '/v1/gateway-access-logs';
const GATEWAY_ACCESS_STATISTICS_PATH = '/v1/gateway-access-statistics';

export interface GatewayAccessScope {
  from: string;
  to: string;
  dataServiceId?: string;
  consumerId?: string;
}

export interface GatewayAccessRankingQuery extends GatewayAccessScope {
  dimension: GatewayAccessRankingDimension;
  metric?: GatewayAccessRankingMetric;
  limit?: number;
}

export interface GatewayAccessLogQuery extends GatewayAccessScope {
  abnormalOnly?: boolean;
  gatewayRequestId?: string;
  page?: number;
  size?: number;
}

const queryPath = (
  path: string,
  values: Record<string, string | number | boolean | undefined>,
): string => {
  const query = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value));
  });
  const suffix = query.toString();
  return suffix ? `${path}?${suffix}` : path;
};

export const fetchGatewayAccessOverview = (
  query: GatewayAccessScope,
): Promise<GatewayAccessOverview> => requestJson<GatewayAccessOverview>(
  queryPath(`${GATEWAY_ACCESS_STATISTICS_PATH}/overview`, { ...query }),
);

export const fetchGatewayAccessTrend = (
  query: GatewayAccessScope,
): Promise<GatewayAccessTrend> => requestJson<GatewayAccessTrend>(
  queryPath(`${GATEWAY_ACCESS_STATISTICS_PATH}/hourly`, { ...query }),
);

export const fetchGatewayAccessRankings = (
  query: GatewayAccessRankingQuery,
): Promise<GatewayAccessRanking[]> => requestJson<GatewayAccessRanking[]>(
  queryPath(`${GATEWAY_ACCESS_STATISTICS_PATH}/rankings`, {
    ...query,
    metric: query.metric ?? 'REQUEST_COUNT',
    limit: query.limit ?? 10,
  }),
);

export const fetchGatewayAccessLogs = (
  query: GatewayAccessLogQuery,
): Promise<PageResponse<GatewayAccessLog>> => requestJson<PageResponse<GatewayAccessLog>>(
  queryPath(GATEWAY_ACCESS_LOG_PATH, {
    ...query,
    abnormalOnly: query.abnormalOnly ?? true,
    page: query.page ?? 0,
    size: query.size ?? 20,
  }),
);
