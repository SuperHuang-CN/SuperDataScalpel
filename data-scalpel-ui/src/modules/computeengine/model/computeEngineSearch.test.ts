import { describe, expect, it } from 'vitest';
import { buildComputeEngineSearch } from './computeEngineSearch';

describe('compute-engine search', () => {
  it('builds stable search conditions', () => {
    expect(buildComputeEngineSearch({
      keyword: '本地"引擎',
      expectedBackendType: 'LOCAL_DOCKER',
      registrationState: 'ACTIVE',
      healthState: 'UP',
    })).toBe('name:*"本地\\"引擎"* AND expectedBackendType:"LOCAL_DOCKER" AND registrationState:"ACTIVE" AND healthState:"UP"');
  });

  it('omits blank filters', () => {
    expect(buildComputeEngineSearch({ keyword: '  ' })).toBeUndefined();
  });
});
