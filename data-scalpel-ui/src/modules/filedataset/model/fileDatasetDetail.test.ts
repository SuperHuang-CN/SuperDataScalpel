import { describe, expect, it } from 'vitest';
import type { FileDatasetTable } from './fileDataset';
import {
  fileDatasetParsingOptionEntries,
  normalizeFileDatasetDetailTab,
  resolveFileDatasetTableId,
} from './fileDatasetDetail';

const table = (id: string): FileDatasetTable => ({
  id,
  fileDatasetId: 'dataset-1',
  code: id,
  name: id,
  parseStatus: 'READY',
  sourceCount: 1,
  totalRowCount: 1,
  currentLoadJobId: null,
  sampledRecordCount: 1,
  truncated: false,
  previewSupported: true,
  sourceMetadata: {},
  spatialReferenceOverride: null,
  createdAt: '2026-07-20T00:00:00Z',
  updatedAt: '2026-07-20T00:00:00Z',
});

describe('file dataset detail model', () => {
  it('normalizes supported detail tabs', () => {
    expect(normalizeFileDatasetDetailTab('files')).toBe('files');
    expect(normalizeFileDatasetDetailTab('tables')).toBe('tables');
    expect(normalizeFileDatasetDetailTab('unknown')).toBe('overview');
    expect(normalizeFileDatasetDetailTab(null)).toBe('overview');
  });

  it('keeps a valid table deep link and falls back when it becomes stale', () => {
    const tables = [table('table-1'), table('table-2')];
    expect(resolveFileDatasetTableId('table-2', tables)).toBe('table-2');
    expect(resolveFileDatasetTableId('missing', tables)).toBe('table-1');
    expect(resolveFileDatasetTableId(null, [])).toBeUndefined();
  });

  it('describes shared parsing options for display', () => {
    expect(fileDatasetParsingOptionEntries({
      kind: 'CSV',
      charset: 'UTF-8',
      fieldDelimiter: '\t',
      recordDelimiter: 'AUTO',
      firstRowHeader: true,
    })).toEqual(expect.arrayContaining([
      { label: '字段分隔符', value: 'Tab (\\t)' },
      { label: '首行为表头', value: '是' },
    ]));
    expect(fileDatasetParsingOptionEntries({ kind: 'SPREADSHEET', headerRowIndex: 0, dataStartRowIndex: 2 }))
      .toEqual([{ label: '表头行', value: '第 1 行' }, { label: '数据起始行', value: '第 3 行' }]);
    expect(fileDatasetParsingOptionEntries({
      kind: 'SHP', dbfCharsetOverride: 'UTF-8', dbfFallbackCharset: 'GB18030',
    })).toEqual(expect.arrayContaining([
      { label: '强制 DBF 编码', value: 'UTF-8' },
      { label: 'DBF 回退编码', value: 'GB18030' },
    ]));
  });
});
