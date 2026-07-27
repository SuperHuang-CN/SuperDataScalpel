import type { FileDatasetParsingOptions, FileDatasetType, FileRecordDelimiter } from './fileDataset';

export interface ParsingFormValues {
  charset?: string;
  fieldDelimiter?: string;
  recordDelimiter?: FileRecordDelimiter;
  quoteCharacter?: string;
  escapeCharacter?: string;
  firstRowHeader?: boolean;
  rootPointer?: string;
  headerRowIndex?: number;
  dataStartRowIndex?: number;
  dbfCharsetOverride?: string;
  dbfFallbackCharset?: string;
}

export const parsingFormValues = (options: FileDatasetParsingOptions): ParsingFormValues => {
  switch (options.kind) {
    case 'CSV': return {
      charset: options.charset,
      fieldDelimiter: options.fieldDelimiter,
      recordDelimiter: options.recordDelimiter,
      quoteCharacter: options.quoteCharacter,
      escapeCharacter: options.escapeCharacter,
      firstRowHeader: options.firstRowHeader,
    };
    case 'TEXT': return { charset: options.charset, recordDelimiter: options.recordDelimiter };
    case 'JSON': return { charset: options.charset, rootPointer: options.rootPointer };
    case 'JSON_LINES': return { charset: options.charset, recordDelimiter: options.recordDelimiter };
    case 'SPREADSHEET': return {
      headerRowIndex: options.headerRowIndex,
      dataStartRowIndex: options.dataStartRowIndex,
    };
    case 'PARQUET':
    case 'AVRO':
    case 'GDB': return {};
    case 'SHP': return {
      dbfCharsetOverride: options.dbfCharsetOverride,
      dbfFallbackCharset: options.dbfFallbackCharset,
    };
  }
};

export const buildFileDatasetParsingOptions = (
  type: FileDatasetType,
  values: ParsingFormValues,
): FileDatasetParsingOptions => {
  switch (type) {
    case 'CSV': return {
      kind: 'CSV', charset: values.charset as string, fieldDelimiter: values.fieldDelimiter as string,
      recordDelimiter: values.recordDelimiter as FileRecordDelimiter, quoteCharacter: values.quoteCharacter || undefined,
      escapeCharacter: values.escapeCharacter || undefined, firstRowHeader: values.firstRowHeader ?? true,
    };
    case 'TSV': return {
      kind: 'CSV', charset: values.charset as string, fieldDelimiter: '\t',
      recordDelimiter: values.recordDelimiter as FileRecordDelimiter, quoteCharacter: values.quoteCharacter || undefined,
      escapeCharacter: values.escapeCharacter || undefined, firstRowHeader: values.firstRowHeader ?? true,
    };
    case 'TXT': return { kind: 'TEXT', charset: values.charset as string, recordDelimiter: values.recordDelimiter as FileRecordDelimiter };
    case 'JSON': return { kind: 'JSON', charset: values.charset as string, rootPointer: values.rootPointer || undefined };
    case 'JSONL': return { kind: 'JSON_LINES', charset: values.charset as string, recordDelimiter: values.recordDelimiter as FileRecordDelimiter };
    case 'EXCEL': return {
      kind: 'SPREADSHEET',
      headerRowIndex: values.headerRowIndex as number,
      dataStartRowIndex: values.dataStartRowIndex as number,
    };
    case 'PARQUET': return { kind: 'PARQUET' };
    case 'AVRO': return { kind: 'AVRO' };
    case 'GDB': return { kind: 'GDB' };
    case 'SHP': return {
      kind: 'SHP',
      dbfCharsetOverride: values.dbfCharsetOverride || undefined,
      dbfFallbackCharset: values.dbfFallbackCharset as string,
    };
  }
};
