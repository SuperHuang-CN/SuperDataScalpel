import { describe, expect, it, vi } from 'vitest';
import {
  calculateDataModelPreviewColumnWidths,
  dataModelPreviewCellText,
  effectiveDataModelPreviewColumnWidth,
  formatDataModelPreviewValue,
  normalizeDataModelPreviewManualWidth,
  type DataModelPreviewSizingColumn,
} from './dataModelPreviewColumnSizing';

const column: DataModelPreviewSizingColumn = {
  code: 'name',
  name: '名称',
  fieldType: 'STRING',
};

describe('data model preview column sizing', () => {
  it('clamps automatically measured widths to the configured range', () => {
    expect(calculateDataModelPreviewColumnWidths([column], [{ name: '短' }], () => 1)).toEqual({
      name: 80,
    });
    expect(calculateDataModelPreviewColumnWidths([column], [{ name: '很长的内容' }], () => 500)).toEqual({
      name: 320,
    });
  });

  it('measures the two header lines independently and includes first-page cell values', () => {
    const measure = vi.fn((text: string) => text.length * 10);
    const widths = calculateDataModelPreviewColumnWidths(
      [{ ...column, name: 'abcdefghij' }],
      [{ name: 'value' }],
      measure,
    );

    expect(widths.name).toBe(134);
    expect(measure).toHaveBeenCalledWith('abcdefghij', 'header');
    expect(measure).toHaveBeenCalledWith('字符串', 'type');
    expect(measure).toHaveBeenCalledWith('value', 'cell');
  });

  it('formats cell values consistently with the preview table', () => {
    expect(dataModelPreviewCellText(null)).toBe('—');
    expect(dataModelPreviewCellText(undefined)).toBe('—');
    expect(dataModelPreviewCellText(12.5)).toBe('12.5');
    expect(dataModelPreviewCellText('0'.padEnd(100))).toBe('0');
    expect(dataModelPreviewCellText({ enabled: true })).toBe('{"enabled":true}');
  });

  it('formats platform temporal values for Chinese data-table reading', () => {
    expect(formatDataModelPreviewValue('2026-08-30T00:00:00', 'DATE')).toBe('2026-08-30');
    expect(formatDataModelPreviewValue('1994-03-09T00:00', 'TIMESTAMP_NTZ')).toBe('1994-03-09 00:00:00');
    expect(formatDataModelPreviewValue('1988-04-16 16:00:12.123', 'TIMESTAMP_NTZ')).toBe('1988-04-16 16:00:12');
    expect(formatDataModelPreviewValue('2026-08-29T16:30:45Z', 'TIMESTAMP')).toBe('2026-08-30 00:30:45');
  });

  it('ignores fixed-width character padding when calculating an automatic width', () => {
    const widths = calculateDataModelPreviewColumnWidths(
      [{ code: 'is_deleted', name: 'is_deleted', fieldType: 'STRING' }],
      [{ is_deleted: '0'.padEnd(100) }],
      (text) => text.length * 10,
    );

    expect(widths.is_deleted).toBe(134);
  });

  it('keeps manual widths above the automatic maximum and enforces only the minimum', () => {
    expect(normalizeDataModelPreviewManualWidth(640.4)).toBe(640);
    expect(normalizeDataModelPreviewManualWidth(20)).toBe(80);
    expect(effectiveDataModelPreviewColumnWidth('name', { name: 200 }, { name: 640 })).toBe(640);
    expect(effectiveDataModelPreviewColumnWidth('name', { name: 200 }, {})).toBe(200);
  });
});
