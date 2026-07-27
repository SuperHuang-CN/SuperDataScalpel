import { describe, expect, it } from 'vitest';
import { buildTaskSearch } from './taskSearch';

describe('buildTaskSearch', () => {
  it('combines keyword, status and directory conditions safely', () => {
    expect(buildTaskSearch({
      keyword: 'daily"task',
      status: 'DRAFT',
      type: 'SPARK_CANVAS',
      directoryIds: ['directory-a', 'directory-b'],
    })).toBe('name:*"daily\\"task"* AND status:"DRAFT" AND type:"SPARK_CANVAS" AND (directoryId:"directory-a" OR directoryId:"directory-b")');
  });

  it('uses explicit null matching for uncategorized tasks', () => {
    expect(buildTaskSearch({ uncategorized: true })).toBe('directoryId:null');
  });
});
