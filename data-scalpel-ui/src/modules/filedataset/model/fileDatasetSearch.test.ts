import { describe, expect, it } from 'vitest';
import { buildFileDatasetSearch } from './fileDatasetSearch';
import {
  defaultFileDatasetParsingOptions,
  fileDatasetAccept,
  fileDatasetAllowsAdditionalUpload,
  formatFileSize,
} from './fileDataset';

describe('file dataset list model', () => {
  it('builds the common Search DSL for keyword, directory and scalar filters', () => {
    expect(buildFileDatasetSearch({
      keyword: '订单"明细',
      directoryIds: ['root-id', 'child-id'],
      type: 'CSV',
    })).toBe('name:*"订单\\"明细"* AND (directoryId:"root-id" OR directoryId:"child-id") AND type:"CSV"');
  });

  it('supports uncategorized datasets and omits empty filters', () => {
    expect(buildFileDatasetSearch({ uncategorized: true })).toBe('directoryId:null');
    expect(buildFileDatasetSearch({ keyword: '  ' })).toBeUndefined();
  });

  it('defines type-specific defaults and accepted file extensions', () => {
    expect(defaultFileDatasetParsingOptions('EXCEL')).toEqual({ kind: 'SPREADSHEET', headerRowIndex: 0, dataStartRowIndex: 1 });
    expect(defaultFileDatasetParsingOptions('GDB')).toEqual({ kind: 'GDB' });
    expect(defaultFileDatasetParsingOptions('SHP')).toEqual({ kind: 'SHP', dbfFallbackCharset: 'GB18030' });
    expect(fileDatasetAccept('EXCEL')).toBe('.xls,.xlsx');
    expect(fileDatasetAccept('GDB')).toBe('.zip');
    expect(fileDatasetAccept('SHP')).toBe('.zip');
    expect(fileDatasetAccept('CSV')).toContain('.csv.gz');
    expect(fileDatasetAllowsAdditionalUpload('EXCEL', 0)).toBe(true);
    expect(fileDatasetAllowsAdditionalUpload('EXCEL', 1)).toBe(false);
    expect(fileDatasetAllowsAdditionalUpload('GDB', 0)).toBe(true);
    expect(fileDatasetAllowsAdditionalUpload('GDB', 1)).toBe(false);
    expect(fileDatasetAllowsAdditionalUpload('SHP', 2)).toBe(true);
    expect(fileDatasetAllowsAdditionalUpload('CSV', 20)).toBe(true);
    expect(formatFileSize(1536)).toBe('1.50 KB');
  });
});
