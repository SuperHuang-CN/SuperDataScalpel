import { describe, expect, it } from 'vitest';
import type { GatewayServiceBinding } from './dataService';
import { gatewayOperationError, publishedGatewayBinding } from './dataServiceGateway';

const binding = (
  publicationStatus: GatewayServiceBinding['publicationStatus'],
  publishedRevision: number,
  gatewayUrl: string | null,
  lastError: string | null = null,
): GatewayServiceBinding => ({
  id: 'binding-1',
  provider: 'KONG',
  externalServiceId: 'service-1',
  externalRouteId: 'route-1',
  gatewayRoutePath: '/open-api/v1/orders',
  accessMode: 'PUBLIC',
  publishedRevision,
  publicationStatus,
  gatewayUrl,
  lastError,
  operationStartedAt: null,
  publishedAt: null,
  reconciliationStatus: 'NOT_CHECKED',
  reconciliationReason: null,
  reconciliationMessage: null,
  reconciliationOperationId: null,
  reconciliationStartedAt: null,
  lastReconciledAt: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

describe('data-service gateway publication view', () => {
  it('returns only a successfully published binding for the current service revision', () => {
    const current = binding('PUBLISHED', 3, 'http://gateway.test/open-api/v1/orders');
    expect(publishedGatewayBinding({ revision: 3, gatewayBindings: [current] })).toBe(current);
    expect(publishedGatewayBinding({ revision: 4, gatewayBindings: [current] })).toBeUndefined();
    expect(publishedGatewayBinding({
      revision: 3,
      gatewayBindings: [binding('PUBLISH_FAILED', 3, null, 'failed')],
    })).toBeUndefined();
  });

  it('returns a persisted gateway error for page feedback', () => {
    expect(gatewayOperationError({
      gatewayBindings: [binding('REMOVE_FAILED', 3, null, '撤回失败')],
    })).toBe('撤回失败');
  });
});
