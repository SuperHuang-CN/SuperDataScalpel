import { describe, expect, it } from 'vitest';
import { buildServiceEngineSearch } from './serviceEngineSearch';

describe('service-engine search', () => {
  it('builds an engine search only from selected values', () => {
    expect(buildServiceEngineSearch({ keyword: 'east', enabled: true }))
      .toBe('(name:*"east"* OR code:*"east"*) AND enabled:"true"');
  });
});
