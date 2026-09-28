import { beforeEach, describe, expect, it, vi } from 'vitest';
import { requestJson } from '../../../shared/api/http';
import { deactivateComputeEngine } from './computeEngineApi';

vi.mock('../../../shared/api/http', () => ({ requestJson: vi.fn().mockResolvedValue({}) }));

describe('compute engine lifecycle request timeout', () => {
  beforeEach(() => vi.clearAllMocks());

  it('waits for forced cancellation and cleanup instead of the ordinary 20-second timeout', async () => {
    await deactivateComputeEngine('engine-id', true);
    expect(requestJson).toHaveBeenCalledWith('/v1/compute-engines/engine-id/actions/deactivate',
      { method: 'POST', body: '{"force":true}' }, 90_000);
  });

  it('keeps the ordinary timeout for a safe stop', async () => {
    await deactivateComputeEngine('engine-id', false);
    expect(requestJson).toHaveBeenCalledWith('/v1/compute-engines/engine-id/actions/deactivate',
      { method: 'POST', body: '{"force":false}' }, undefined);
  });
});
