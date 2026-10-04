import { requestJson } from '../../../shared/api/http';
export type GatewayValidityKind = 'keys' | 'subscriptions';
export interface GatewayValidity { validFrom: string | null; expiresAt: string | null; requestsPerSecond: number; state: string; targetRevision: number; loadedRevision: number }
export const getGatewayValidity = (kind: GatewayValidityKind, id: string) => requestJson<GatewayValidity>(`/v1/gateway-access-validities/${kind}/${id}`);
export const saveGatewayValidity = (kind: GatewayValidityKind, id: string, body: Pick<GatewayValidity, 'validFrom' | 'expiresAt' | 'requestsPerSecond'>) =>
  requestJson<GatewayValidity>(`/v1/gateway-access-validities/${kind}/${id}/actions/update`, { method: 'POST', body: JSON.stringify(body) });
