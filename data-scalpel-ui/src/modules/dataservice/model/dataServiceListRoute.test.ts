import { describe, expect, it } from 'vitest';
import { parseDataServiceListRoute, serializeDataServiceListRoute } from './dataServiceListRoute';

describe('data-service list route', () => {
  it('round-trips filters, directory selection and paging', () => {
    const params = serializeDataServiceListRoute({
      keyword: '客户', status: 'DRAFT', type: 'SQL_QUERY', engineId: 'engine-1',
    }, 'directory-1', 2, 50);
    expect(parseDataServiceListRoute(params)).toEqual({
      filters: { keyword: '客户', status: 'DRAFT', type: 'SQL_QUERY', engineId: 'engine-1' },
      directorySelection: 'directory-1',
      page: 2,
      size: 50,
    });
  });

  it('uses safe defaults for invalid enum and paging values', () => {
    expect(parseDataServiceListRoute(new URLSearchParams(
      'status=UNKNOWN&type=SCRIPT&page=0&size=-1&directory=uncategorized',
    ))).toEqual({ filters: { uncategorized: true }, directorySelection: null, page: 0, size: 20 });
    expect(parseDataServiceListRoute(new URLSearchParams('size=999'))).toMatchObject({ page: 0, size: 20 });
  });
});
