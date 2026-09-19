import { describe, expect, it } from 'vitest';
import { toSearchParams } from './searchRequest';

describe('toSearchParams', () => {
  it('keeps zero-based paging and the backend search DSL intact', () => {
    const params = toSearchParams({
      search: 'state:"RUNNING" AND name:*"测试"*',
      page: 0,
      size: 20,
      sort: '-updatedAt,name',
    });

    expect(params.get('search')).toBe('state:"RUNNING" AND name:*"测试"*');
    expect(params.get('page')).toBe('0');
    expect(params.get('size')).toBe('20');
    expect(params.get('sort')).toBe('-updatedAt,name');
  });

  it('omits fields that should use backend defaults', () => {
    expect(toSearchParams({ search: '  ', sort: '' }).toString()).toBe('');
  });
});
