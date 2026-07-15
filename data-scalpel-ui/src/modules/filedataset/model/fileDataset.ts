export type FileDatasetFormat = 'CSV' | 'TSV' | 'TXT' | 'JSON' | 'JSONL' | 'XLS' | 'XLSX' | 'PARQUET' | 'AVRO' | 'SHP' | 'GDB' | 'OTHER';

export type FileDatasetCompression = 'NONE' | 'GZIP';

export type FileDatasetParseStatus = 'UNPARSED' | 'PARSING' | 'READY' | 'FAILED';

export type FileDatasetLogicalType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'TIME' | 'DATETIME' | 'BINARY' | 'JSON' | 'ARRAY' | 'OTHER';

export interface FileDatasetField {
  name: string;
  sortOrder: number;
  logicalType: FileDatasetLogicalType;
  nullable: boolean;
}

export interface FileDataset {
  id: string;
  directoryId: string | null;
  name: string;
  format: FileDatasetFormat;
  compression: FileDatasetCompression;
  originalFileName: string;
  contentType: string | null;
  sizeBytes: number;
  parseStatus: FileDatasetParseStatus;
  parsingConfigured: boolean;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export type FileRecordDelimiter = 'AUTO' | 'LF' | 'CRLF' | 'CR';

export type FileDatasetParsingOptions =
  | { kind: 'CSV'; charset: string; fieldDelimiter: string; recordDelimiter: FileRecordDelimiter; quoteCharacter?: string; escapeCharacter?: string; firstRowHeader: boolean }
  | { kind: 'TEXT'; charset: string; recordDelimiter: FileRecordDelimiter }
  | { kind: 'JSON'; charset: string; rootPointer?: string }
  | { kind: 'JSON_LINES'; charset: string; recordDelimiter: FileRecordDelimiter }
  | { kind: 'SPREADSHEET'; sheetName?: string; headerRowIndex: number; dataStartRowIndex: number }
  | { kind: 'PARQUET' }
  | { kind: 'AVRO' }
  | { kind: 'SHAPEFILE'; charset: string; layerName?: string }
  | { kind: 'FILE_GDB'; layerName?: string };

export interface FileDatasetParsing {
  id: string;
  format: FileDatasetFormat;
  compression: FileDatasetCompression;
  parseStatus: FileDatasetParseStatus;
  configured: boolean;
  options: FileDatasetParsingOptions | null;
  parseError: string | null;
  sampledRecordCount: number;
  truncated: boolean;
  fields: FileDatasetField[];
}

export interface FileDatasetPreview {
  fields: FileDatasetField[];
  rows: unknown[][];
  limit: number;
  truncated: boolean;
}

export interface CreateFileDatasetRequest {
  name: string;
  directoryId?: string;
  format: FileDatasetFormat;
  description?: string;
}

export type UpdateFileDatasetRequest = CreateFileDatasetRequest;

export interface FileDatasetFilters {
  keyword?: string;
  format?: FileDatasetFormat;
  compression?: FileDatasetCompression;
  parseStatus?: FileDatasetParseStatus;
  directoryIds?: string[];
  uncategorized?: boolean;
}

export const fileDatasetFormatLabels: Record<FileDatasetFormat, string> = {
  CSV: 'CSV',
  TSV: 'TSV',
  TXT: '文本',
  JSON: 'JSON',
  JSONL: 'JSON Lines',
  XLS: 'Excel 97-2003',
  XLSX: 'Excel',
  PARQUET: 'Parquet',
  AVRO: 'Avro',
  SHP: 'Shapefile（ZIP）',
  GDB: 'FileGDB（ZIP）',
  OTHER: '其他',
};

export const fileDatasetParseStatusLabels: Record<FileDatasetParseStatus, string> = {
  UNPARSED: '待解析',
  PARSING: '解析中',
  READY: '已就绪',
  FAILED: '解析失败',
};

export const fileDatasetCompressionLabels: Record<FileDatasetCompression, string> = {
  NONE: '未压缩',
  GZIP: 'GZIP',
};

export const fileDatasetFormatOptions = Object.entries(fileDatasetFormatLabels).map(([value, label]) => ({
  value: value as FileDatasetFormat,
  label,
}));

export const fileDatasetCompressionOptions = Object.entries(fileDatasetCompressionLabels).map(([value, label]) => ({
  value: value as FileDatasetCompression,
  label,
}));

const formatByExtension: Record<string, FileDatasetFormat> = {
  csv: 'CSV', tsv: 'TSV', txt: 'TXT', json: 'JSON', jsonl: 'JSONL', ndjson: 'JSONL',
  xls: 'XLS', xlsx: 'XLSX', parquet: 'PARQUET', avro: 'AVRO',
};

export const inferFileDatasetFormat = (fileName: string): FileDatasetFormat => {
  const baseFileName = inferFileDatasetCompression(fileName) === 'GZIP' ? fileName.slice(0, -3) : fileName;
  const extension = baseFileName.split('.').pop()?.toLowerCase() ?? '';
  return formatByExtension[extension] ?? 'OTHER';
};

export const inferFileDatasetCompression = (fileName: string): FileDatasetCompression => (
  fileName.toLowerCase().endsWith('.gz') ? 'GZIP' : 'NONE'
);

export const formatFileSize = (sizeBytes: number): string => {
  if (sizeBytes < 1024) return `${sizeBytes} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let value = sizeBytes / 1024;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  return `${value >= 10 ? value.toFixed(1) : value.toFixed(2)} ${units[unitIndex]}`;
};
