import { describe, expect, it } from 'vitest';
import { buildFileDatasetParsingOptions, parsingFormValues } from './fileDatasetParsingForm';

describe('file dataset parsing form model', () => {
  it('forces the TSV delimiter while preserving table-specific settings', () => {
    expect(buildFileDatasetParsingOptions('TSV', {
      charset: 'UTF-8',
      fieldDelimiter: ',',
      recordDelimiter: 'LF',
      quoteCharacter: '"',
      escapeCharacter: '\\',
      firstRowHeader: false,
    })).toEqual({
      kind: 'CSV',
      charset: 'UTF-8',
      fieldDelimiter: '\t',
      recordDelimiter: 'LF',
      quoteCharacter: '"',
      escapeCharacter: '\\',
      firstRowHeader: false,
    });
  });

  it('does not expose a sheet selector in spreadsheet parsing options', () => {
    const options = buildFileDatasetParsingOptions('EXCEL', { headerRowIndex: 2, dataStartRowIndex: 3 });
    expect(options).toEqual({ kind: 'SPREADSHEET', headerRowIndex: 2, dataStartRowIndex: 3 });
    expect(parsingFormValues(options)).toEqual({ headerRowIndex: 2, dataStartRowIndex: 3 });
  });

  it('round-trips the optional SHP charset override and required fallback', () => {
    const options = buildFileDatasetParsingOptions('SHP', {
      dbfCharsetOverride: 'UTF-8',
      dbfFallbackCharset: 'GB18030',
    });
    expect(options).toEqual({
      kind: 'SHP',
      dbfCharsetOverride: 'UTF-8',
      dbfFallbackCharset: 'GB18030',
    });
    expect(parsingFormValues(options)).toEqual({
      dbfCharsetOverride: 'UTF-8',
      dbfFallbackCharset: 'GB18030',
    });
  });
});
