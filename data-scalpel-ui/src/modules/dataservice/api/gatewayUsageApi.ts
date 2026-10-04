import { requestJson } from '../../../shared/api/http';
import type { GatewayUsage } from '../model/gatewayUsage';
export const fetchGatewayUsage = (range: { from: string; to: string }) =>
 requestJson<GatewayUsage>('/v1/gateway-access-statistics/usage?' + new URLSearchParams(range));
