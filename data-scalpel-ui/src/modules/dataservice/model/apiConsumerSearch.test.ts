import { describe, expect, it } from 'vitest';
import { buildApiConsumerSearch } from './apiConsumerSearch';

describe('API consumer search', () => {
  it('searches name and immutable code with escaped DSL text', () => {
    expect(buildApiConsumerSearch({ keyword: ' customer "east"\\api ' }))
      .toBe('(name:*"customer \\"east\\"\\\\api"* OR code:*"customer \\"east\\"\\\\api"*)');
  });

  it('omits an empty search', () => {
    expect(buildApiConsumerSearch({ keyword: '   ' })).toBeUndefined();
  });
});
