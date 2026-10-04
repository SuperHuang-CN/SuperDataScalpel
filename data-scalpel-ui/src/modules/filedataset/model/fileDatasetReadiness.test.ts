import { describe, expect, it } from 'vitest';
import { fileDatasetReadiness } from './fileDatasetReadiness';

describe('fileDatasetReadiness', () => {
  it('distinguishes an empty dataset from uploaded files without discovered tables', () => {
    expect(fileDatasetReadiness({ fileCount: 0, tableCount: 0, readyTableCount: 0 }).label).toBe('尚未上传');
    expect(fileDatasetReadiness({ fileCount: 1, tableCount: 0, readyTableCount: 0 }).label).toBe('暂无数据表');
  });

  it('does not infer a running parse job from incomplete table counts', () => {
    expect(fileDatasetReadiness({ fileCount: 1, tableCount: 2, readyTableCount: 0 })).toMatchObject({ label: '未就绪', detail: '0 / 2 张表已就绪', tone: 'warning' });
    expect(fileDatasetReadiness({ fileCount: 1, tableCount: 2, readyTableCount: 1 })).toMatchObject({ label: '部分就绪', detail: '1 / 2 张表已就绪', tone: 'warning' });
  });

  it('marks a dataset ready only when all existing tables are ready', () => {
    expect(fileDatasetReadiness({ fileCount: 1, tableCount: 2, readyTableCount: 2 })).toMatchObject({ label: '全部就绪', detail: '2 / 2 张表已就绪', tone: 'success' });
    expect(fileDatasetReadiness({ fileCount: 0, tableCount: 0, readyTableCount: 0 }).tone).not.toBe('success');
  });
});
