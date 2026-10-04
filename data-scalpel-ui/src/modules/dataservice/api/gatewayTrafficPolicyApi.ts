import { requestJson } from '../../../shared/api/http';

export interface GatewayTrafficPolicy {
  requestsPerSecond: number;
  consumerRequestsPerSecond: number;
  maxConcurrentRequests: number;
  maxRequestBytes: number;
  allowedCidrs: string[];
  deniedCidrs: string[];
}
export interface GatewayTrafficPolicyState {
  policy: GatewayTrafficPolicy;
  targetRevision: number;
  loadedRevision: number;
  scope: 'NODE';
}
export const getGatewayTrafficPolicy = (id: string) => requestJson<GatewayTrafficPolicyState>(`/v1/data-services/${id}/gateway-traffic-policy`);
export const updateGatewayTrafficPolicy = (id: string, policy: GatewayTrafficPolicy) => requestJson<GatewayTrafficPolicyState>(
  `/v1/data-services/${id}/actions/update-gateway-traffic-policy`, { method: 'POST', body: JSON.stringify(policy) },
);
