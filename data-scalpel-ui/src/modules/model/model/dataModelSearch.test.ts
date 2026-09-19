import { describe, expect, it } from 'vitest';
import { buildDataModelSearch } from './dataModelSearch';

describe('buildDataModelSearch', () => {
  it('combines keyword, status, physical mode, storage and directory filters', () => {
    expect(buildDataModelSearch({
      keyword: '订单',
      status: 'DRAFT',
      physicalTableModes: ['MANAGED', 'EXTERNAL'],
      storageDataSourceId: 'storage-id',
      directoryIds: ['root-id', 'child-id'],
    })).toBe('(name:*"订单"* OR code:*"订单"*) AND status:"DRAFT" AND (physicalTableMode:"MANAGED" OR physicalTableMode:"EXTERNAL") AND storageDataSourceId:"storage-id" AND (directoryId:"root-id" OR directoryId:"child-id")');
  });

  it('escapes text and supports uncategorized models', () => {
    expect(buildDataModelSearch({ keyword: 'A"B\\C', uncategorized: true }))
      .toBe('(name:*"A\\"B\\\\C"* OR code:*"A\\"B\\\\C"*) AND directoryId:null');
    expect(buildDataModelSearch({})).toBeUndefined();
  });
});
