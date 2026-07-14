import { describe, expect, it } from 'vitest';
import { buildDataSourceSearch } from './dataSourceSearch';

describe('buildDataSourceSearch', () => {
  it('combines keyword, purpose and scalar filters using the common Search DSL', () => {
    expect(buildDataSourceSearch({
      keyword: '业务',
      purpose: 'BOTH',
      databaseType: 'POSTGRESQL',
      enabled: true,
    })).toBe('(name:*"业务"* OR code:*"业务"*) AND sourceEnabled:"true" AND storageEnabled:"true" AND databaseType:"POSTGRESQL" AND enabled:"true"');
  });

  it('escapes keyword and omits empty filters', () => {
    expect(buildDataSourceSearch({ keyword: '名称"A\\B', purpose: 'SOURCE' }))
      .toBe('(name:*"名称\\"A\\\\B"* OR code:*"名称\\"A\\\\B"*) AND sourceEnabled:"true"');
    expect(buildDataSourceSearch({})).toBeUndefined();
  });

  it('maps the distribution purpose to its searchable boolean field', () => {
    expect(buildDataSourceSearch({ purpose: 'DISTRIBUTION' }))
      .toBe('distributionEnabled:"true"');
  });

  it('filters an entire selected directory subtree or unclassified data sources', () => {
    expect(buildDataSourceSearch({ directoryIds: ['root-id', 'child-id'] }))
      .toBe('(directoryId:"root-id" OR directoryId:"child-id")');
    expect(buildDataSourceSearch({ uncategorized: true }))
      .toBe('directoryId:null');
  });

});
