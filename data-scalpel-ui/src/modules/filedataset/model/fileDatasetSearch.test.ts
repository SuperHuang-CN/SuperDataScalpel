import { describe, expect, it } from 'vitest';
import { buildFileDatasetSearch } from './fileDatasetSearch';
import { formatFileSize, inferFileDatasetFormat } from './fileDataset';

describe('file dataset list model', () => {
  it('builds the common Search DSL for keyword, directory and scalar filters', () => {
    expect(buildFileDatasetSearch({
      keyword: '订单"明细',
      directoryIds: ['root-id', 'child-id'],
      format: 'CSV',
      compression: 'GZIP',
      parseStatus: 'UNPARSED',
    })).toBe('(name:*"订单\\"明细"* OR originalFileName:*"订单\\"明细"*) AND (directoryId:"root-id" OR directoryId:"child-id") AND format:"CSV" AND compression:"GZIP" AND parseStatus:"UNPARSED"');
  });

  it('supports uncategorized datasets and omits empty filters', () => {
    expect(buildFileDatasetSearch({ uncategorized: true })).toBe('directoryId:null');
    expect(buildFileDatasetSearch({ keyword: '  ' })).toBeUndefined();
  });

  it('infers common formats and renders compact file sizes', () => {
    expect(inferFileDatasetFormat('orders.parquet')).toBe('PARQUET');
    expect(inferFileDatasetFormat('orders.tsv.gz')).toBe('TSV');
    expect(inferFileDatasetFormat('events.avro')).toBe('AVRO');
    expect(inferFileDatasetFormat('shape.zip')).toBe('OTHER');
    expect(formatFileSize(1536)).toBe('1.50 KB');
  });
});
