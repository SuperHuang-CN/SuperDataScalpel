import type { GatewayProvider } from './apiConsumer';

export type GatewayAccessRange = '24h' | '7d' | '30d';
export type GatewayAccessTrendMetric = 'TRAFFIC' | 'ERROR_RATE' | 'LATENCY';
export type GatewayAccessRankingDimension = 'SERVICE' | 'CONSUMER';
export type GatewayAccessRankingMetric = 'REQUEST_COUNT' | 'SERVER_ERROR_COUNT' | 'P95_LATENCY';

export type GatewayAccessIdentityResolutionStatus =
  | 'RESOLVED'
  | 'ANONYMOUS'
  | 'CONSUMER_UNRESOLVED'
  | 'SERVICE_UNRESOLVED'
  | 'IDENTITY_MISMATCH';

export interface GatewayAccessOverview {
  fromInclusive: string;
  toExclusive: string;
  requestCount: number;
  status2xxCount: number;
  status3xxCount: number;
  status4xxCount: number;
  status5xxCount: number;
  status401Count: number;
  status403Count: number;
  status429Count: number;
  gatewayRejectedCount: number;
  gatewayErrorCount: number;
  upstreamErrorCount: number;
  successRate: number;
  clientErrorRate: number;
  serverErrorRate: number;
  requestBytes: number;
  responseBytes: number;
  averageRequestLatencyMs: number | null;
  maximumRequestLatencyMs: number | null;
  peakHourlyRequestLatencyP95Ms: number | null;
  peakHourlyRequestLatencyP99Ms: number | null;
  averageProxyLatencyMs: number | null;
  peakHourlyProxyLatencyP95Ms: number | null;
  peakHourlyProxyLatencyP99Ms: number | null;
}

export interface GatewayAccessTrendPoint {
  hourStart: string;
  requestCount: number;
  status2xxCount: number;
  status3xxCount: number;
  status4xxCount: number;
  status5xxCount: number;
  status401Count: number;
  status403Count: number;
  status429Count: number;
  gatewayRejectedCount: number;
  gatewayErrorCount: number;
  upstreamErrorCount: number;
  requestBytes: number;
  responseBytes: number;
  averageRequestLatencyMs: number | null;
  maximumRequestLatencyMs: number | null;
  peakGroupedRequestLatencyP95Ms: number | null;
  peakGroupedRequestLatencyP99Ms: number | null;
  averageProxyLatencyMs: number | null;
  peakGroupedProxyLatencyP95Ms: number | null;
  peakGroupedProxyLatencyP99Ms: number | null;
}

export interface GatewayAccessTrend {
  fromInclusive: string;
  toExclusive: string;
  points: GatewayAccessTrendPoint[];
}

export interface GatewayAccessRanking {
  dimension: GatewayAccessRankingDimension;
  subjectId: string;
  subjectCode: string | null;
  subjectName: string | null;
  requestCount: number;
  status2xxCount: number;
  status4xxCount: number;
  status5xxCount: number;
  serverErrorCount: number;
  peakHourlyRequestLatencyP95Ms: number | null;
}

export interface GatewayAccessLog {
  id: string;
  eventId: string;
  schemaVersion: string;
  gatewayProvider: GatewayProvider;
  occurredAt: string;
  observedAt: string | null;
  receivedAt: string;
  dataServiceId: string | null;
  dataServiceCode: string | null;
  dataServiceName: string | null;
  gatewayServiceId: string | null;
  gatewayServiceName: string | null;
  gatewayRouteId: string | null;
  gatewayRouteName: string | null;
  consumerId: string | null;
  gatewayConsumerId: string | null;
  consumerCode: string | null;
  consumerName: string | null;
  gatewayCredentialExternalId: string | null;
  gatewayRequestId: string;
  requestMethod: string;
  requestPath: string;
  responseStatus: number;
  upstreamStatus: string | null;
  requestSizeBytes: number | null;
  responseSizeBytes: number | null;
  requestLatencyMs: number | null;
  kongLatencyMs: number | null;
  proxyLatencyMs: number | null;
  receiveLatencyMs: number | null;
  clientIp: string | null;
  identityResolutionStatus: GatewayAccessIdentityResolutionStatus;
  gatewayRejected: boolean;
  gatewayError: boolean;
  upstreamError: boolean;
  kafkaTopic: string;
  kafkaPartition: number;
  kafkaOffset: number;
}

export interface GatewayAccessTimeWindow {
  from: string;
  to: string;
  rawFrom: string;
  rawTo: string;
  bucketHours: number;
  label: string;
}

export interface GatewayAccessTrendBucket {
  start: string;
  requestCount: number;
  status2xxCount: number;
  status4xxCount: number;
  status5xxCount: number;
  peakRequestLatencyP95Ms: number | null;
  peakRequestLatencyP99Ms: number | null;
}

const HOUR_MS = 60 * 60 * 1000;
const RAW_RETENTION_HOURS = 7 * 24;

const rangeConfiguration: Record<GatewayAccessRange, {
  hours: number;
  bucketHours: number;
  label: string;
}> = {
  '24h': { hours: 24, bucketHours: 1, label: '最近 24 小时' },
  '7d': { hours: 7 * 24, bucketHours: 6, label: '最近 7 天' },
  '30d': { hours: 30 * 24, bucketHours: 24, label: '最近 30 天' },
};

export const buildGatewayAccessTimeWindow = (
  range: GatewayAccessRange,
  now = new Date(),
): GatewayAccessTimeWindow => {
  const configuration = rangeConfiguration[range];
  const completedHour = Math.floor(now.getTime() / HOUR_MS) * HOUR_MS;
  const from = completedHour - configuration.hours * HOUR_MS;
  const rawHours = Math.min(configuration.hours, RAW_RETENTION_HOURS);
  return {
    from: new Date(from).toISOString(),
    to: new Date(completedHour).toISOString(),
    rawFrom: new Date(now.getTime() - rawHours * HOUR_MS).toISOString(),
    rawTo: now.toISOString(),
    bucketHours: configuration.bucketHours,
    label: configuration.label,
  };
};

const maximum = (left: number | null, right: number | null): number | null => {
  if (left === null) return right;
  if (right === null) return left;
  return Math.max(left, right);
};

export const bucketGatewayAccessTrend = (
  points: GatewayAccessTrendPoint[],
  window: GatewayAccessTimeWindow,
): GatewayAccessTrendBucket[] => {
  const from = new Date(window.from).getTime();
  const to = new Date(window.to).getTime();
  const bucketMs = window.bucketHours * HOUR_MS;
  const bucketCount = Math.max(0, Math.ceil((to - from) / bucketMs));
  const buckets = Array.from({ length: bucketCount }, (_, index): GatewayAccessTrendBucket => ({
    start: new Date(from + index * bucketMs).toISOString(),
    requestCount: 0,
    status2xxCount: 0,
    status4xxCount: 0,
    status5xxCount: 0,
    peakRequestLatencyP95Ms: null,
    peakRequestLatencyP99Ms: null,
  }));

  points.forEach((point) => {
    const pointTime = new Date(point.hourStart).getTime();
    const index = Math.floor((pointTime - from) / bucketMs);
    const bucket = buckets[index];
    if (!bucket || pointTime < from || pointTime >= to) return;
    bucket.requestCount += point.requestCount;
    bucket.status2xxCount += point.status2xxCount;
    bucket.status4xxCount += point.status4xxCount;
    bucket.status5xxCount += point.status5xxCount;
    bucket.peakRequestLatencyP95Ms = maximum(
      bucket.peakRequestLatencyP95Ms,
      point.peakGroupedRequestLatencyP95Ms,
    );
    bucket.peakRequestLatencyP99Ms = maximum(
      bucket.peakRequestLatencyP99Ms,
      point.peakGroupedRequestLatencyP99Ms,
    );
  });

  return buckets;
};

export const gatewayAccessRate = (count: number, total: number): number => (
  total > 0 ? count / total : 0
);

export const gatewayAccessRangeLabels: Record<GatewayAccessRange, string> = {
  '24h': '24 小时',
  '7d': '7 天',
  '30d': '30 天',
};

export const gatewayAccessIdentityStatusLabels: Record<GatewayAccessIdentityResolutionStatus, string> = {
  RESOLVED: '身份已解析',
  ANONYMOUS: '匿名调用',
  CONSUMER_UNRESOLVED: '消费者未解析',
  SERVICE_UNRESOLVED: '服务未解析',
  IDENTITY_MISMATCH: '服务身份不一致',
};
