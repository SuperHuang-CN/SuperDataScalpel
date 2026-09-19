import { describe, expect, it } from 'vitest';
import { buildTaskRunSearch } from './taskRunSearch';

describe('buildTaskRunSearch', () => {
  it('combines the selected run filters for the server search API', () => {
    expect(buildTaskRunSearch({
      status: 'SUCCESS',
      triggerType: 'SCHEDULED',
      executionMode: 'SIMULATED',
    })).toBe('status:"SUCCESS" AND triggerType:"SCHEDULED" AND executionMode:"SIMULATED"');
  });

  it('omits empty filters', () => {
    expect(buildTaskRunSearch({})).toBeUndefined();
  });
});
