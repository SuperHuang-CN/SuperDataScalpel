import { describe, expect, it } from 'vitest';
import { parseDataModelListRoute, serializeDataModelListRoute } from './dataModelListRoute';
import { buildDataModelSearch } from './dataModelSearch';

describe('data model list route state', () => {
  it('preserves the homepage published and unassigned-layer scope', () => {
    const route = parseDataModelListRoute(new URLSearchParams('status=PUBLISHED&layer=unassigned'));
    expect(buildDataModelSearch(route.filters)).toBe('status:"PUBLISHED" AND warehouseLayerId:null');
    expect(serializeDataModelListRoute(route.filters, route.directorySelection, 0, 20).toString()).toBe('status=PUBLISHED&layer=unassigned');
  });
  it('round trips filters, directory and pagination', () => {
    const params = serializeDataModelListRoute({
      keyword: '订单',
      status: 'PUBLISHED',
      storageDataSourceId: 'storage-id',
    }, 'directory-id', 2, 50);

    expect(parseDataModelListRoute(params)).toEqual({
      filters: { keyword: '订单', status: 'PUBLISHED', storageDataSourceId: 'storage-id' },
      directorySelection: 'directory-id',
      page: 2,
      size: 50,
    });
  });

  it('supports uncategorized and safe defaults', () => {
    expect(parseDataModelListRoute(new URLSearchParams('directory=uncategorized&page=bad&size=0'))).toEqual({
      filters: { uncategorized: true },
      directorySelection: null,
      page: 0,
      size: 20,
    });
  });
});
