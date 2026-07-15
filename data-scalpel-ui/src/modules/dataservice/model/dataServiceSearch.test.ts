import { describe, expect, it } from 'vitest';
import { buildDataServiceSearch } from './dataServiceSearch';

describe('data-service search', () => {
  it('combines service filters with the stable Search DSL', () => {
    expect(buildDataServiceSearch({
      keyword: '订单',
      status: 'PUBLISHED',
      engineId: 'engine-1',
      directoryIds: ['dir-1', 'dir-2'],
    })).toBe('(name:*"订单"* OR code:*"订单"*) AND status:"PUBLISHED" AND engineId:"engine-1" AND (directoryId:"dir-1" OR directoryId:"dir-2")');
  });

});
