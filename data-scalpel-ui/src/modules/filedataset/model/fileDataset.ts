export type FileDatasetType = 'CSV' | 'TSV' | 'TXT' | 'JSON' | 'JSONL' | 'PARQUET' | 'AVRO' | 'EXCEL' | 'GDB' | 'SHP';

export type FileDatasetFormat = 'CSV' | 'TSV' | 'TXT' | 'JSON' | 'JSONL' | 'XLS' | 'XLSX' | 'PARQUET' | 'AVRO' | 'GDB' | 'SHP';

export type FileDatasetCompression = 'NONE' | 'GZIP' | 'ZIP';

export type FileDatasetFileStatus = 'PREPARING' | 'READY';

export type FileDatasetStorageKind = 'SINGLE_OBJECT' | 'GDB_DIRECTORY' | 'SHAPEFILE_COMPONENT_SET';

export type FileDatasetParseStatus = 'QUEUED' | 'PARSING' | 'SCHEMA_READY' | 'READY';

export type FileDatasetTableSourceLoadMode = 'INITIAL' | 'APPEND' | 'REPLACE_ALL' | 'REPLACE_SOURCE';

export type FileRecordDelimiter = 'AUTO' | 'LF' | 'CRLF' | 'CR';

export type FileDatasetParsingOptions =
  | { kind: 'CSV'; charset: string; fieldDelimiter: string; recordDelimiter: FileRecordDelimiter; quoteCharacter?: string; escapeCharacter?: string; firstRowHeader: boolean }
  | { kind: 'TEXT'; charset: string; recordDelimiter: FileRecordDelimiter }
  | { kind: 'JSON'; charset: string; rootPointer?: string }
  | { kind: 'JSON_LINES'; charset: string; recordDelimiter: FileRecordDelimiter }
  | { kind: 'SPREADSHEET'; headerRowIndex: number; dataStartRowIndex: number }
  | { kind: 'PARQUET' }
  | { kind: 'AVRO' }
  | { kind: 'GDB' }
  | { kind: 'SHP'; dbfCharsetOverride?: string; dbfFallbackCharset: string };

export interface FileDatasetField {
  name: string;
  sortOrder: number;
  fieldType: PlatformDataType;
  length: number | null;
  precision: number | null;
  scale: number | null;
  nullable: boolean;
}

export interface FileDatasetCanvasTableMetadata {
  fileDatasetTableId: string;
  fileDatasetId: string;
  fileDatasetName: string;
  datasetType: FileDatasetType;
  code: string;
  name: string;
  parseStatus: FileDatasetParseStatus;
  fileStatus: FileDatasetFileStatus;
  fields: FileDatasetField[];
}

export interface FileDatasetCanvasMetadata {
  tables: FileDatasetCanvasTableMetadata[];
}

export interface FileDataset {
  id: string;
  directoryId: string | null;
  name: string;
  type: FileDatasetType;
  parsingOptions: FileDatasetParsingOptions;
  fileCount: number;
  tableCount: number;
  readyTableCount: number;
  parsingOptionsLocked: boolean;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FileDatasetFile {
  id: string;
  fileDatasetId: string;
  originalFileName: string;
  format: FileDatasetFormat;
  compression: FileDatasetCompression;
  contentType: string | null;
  sizeBytes: number;
  status: FileDatasetFileStatus;
  storageKind: FileDatasetStorageKind;
  materializedSizeBytes: number | null;
  materializedEntryCount: number | null;
  currentPreparationJobId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface FileDatasetTable {
  id: string;
  fileDatasetId: string;
  code: string;
  name: string;
  parseStatus: FileDatasetParseStatus;
  sourceCount: number;
  totalRowCount: number;
  currentLoadJobId: string | null;
  sampledRecordCount: number;
  truncated: boolean;
  previewSupported: boolean;
  sourceMetadata: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface FileDatasetTableSource {
  id: string;
  tableId: string;
  sourceFileId: string;
  sourceName: string;
  sourceKey: string;
  sourceOrder: number;
  rowCount: number;
  schemaFingerprint: string;
  activatedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface FileDatasetTableLoadSubmission {
  jobId: string;
  file: FileDatasetFile;
  table: FileDatasetTable;
}

export interface FileDatasetUploadResult {
  files: FileDatasetFile[];
  tables: FileDatasetTable[];
  jobIds: string[];
}

export interface FileDatasetSchema {
  tableId: string;
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
  type: FileDatasetType;
  parsingOptions: FileDatasetParsingOptions;
  description?: string;
}

export interface UpdateFileDatasetRequest {
  name: string;
  directoryId?: string;
  parsingOptions: FileDatasetParsingOptions;
  description?: string;
}

export interface FileDatasetFilters {
  keyword?: string;
  type?: FileDatasetType;
  directoryIds?: string[];
  uncategorized?: boolean;
}

export const fileDatasetTypeLabels: Record<FileDatasetType, string> = {
  CSV: 'CSV',
  TSV: 'TSV',
  TXT: '文本',
  JSON: 'JSON',
  JSONL: 'JSON Lines',
  PARQUET: 'Parquet',
  AVRO: 'Avro',
  EXCEL: 'Excel',
  GDB: 'FileGDB',
  SHP: 'Shapefile',
};

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
  GDB: 'FileGDB ZIP',
  SHP: 'Shapefile ZIP',
};

export const fileDatasetParseStatusLabels: Record<FileDatasetParseStatus, string> = {
  QUEUED: '排队中',
  PARSING: '解析中',
  SCHEMA_READY: '仅 Schema',
  READY: '已就绪',
};

export const isActiveFileDatasetParseStatus = (status: FileDatasetParseStatus): boolean => (
  status === 'QUEUED' || status === 'PARSING'
);

export const fileDatasetCompressionLabels: Record<FileDatasetCompression, string> = {
  NONE: '未压缩',
  GZIP: 'GZIP',
  ZIP: 'ZIP',
};

export const fileDatasetTypeOptions = Object.entries(fileDatasetTypeLabels).map(([value, label]) => ({
  value: value as FileDatasetType,
  label,
}));

export const defaultFileDatasetParsingOptions = (type: FileDatasetType): FileDatasetParsingOptions => {
  switch (type) {
    case 'CSV': return { kind: 'CSV', charset: 'UTF-8', fieldDelimiter: ',', recordDelimiter: 'AUTO', quoteCharacter: '"', escapeCharacter: '\\', firstRowHeader: true };
    case 'TSV': return { kind: 'CSV', charset: 'UTF-8', fieldDelimiter: '\t', recordDelimiter: 'AUTO', quoteCharacter: '"', escapeCharacter: '\\', firstRowHeader: true };
    case 'TXT': return { kind: 'TEXT', charset: 'UTF-8', recordDelimiter: 'AUTO' };
    case 'JSON': return { kind: 'JSON', charset: 'UTF-8' };
    case 'JSONL': return { kind: 'JSON_LINES', charset: 'UTF-8', recordDelimiter: 'AUTO' };
    case 'EXCEL': return { kind: 'SPREADSHEET', headerRowIndex: 0, dataStartRowIndex: 1 };
    case 'PARQUET': return { kind: 'PARQUET' };
    case 'AVRO': return { kind: 'AVRO' };
    case 'GDB': return { kind: 'GDB' };
    case 'SHP': return { kind: 'SHP', dbfFallbackCharset: 'GB18030' };
  }
};

export const fileDatasetAccept = (type: FileDatasetType): string => {
  switch (type) {
    case 'CSV': return '.csv,.csv.gz';
    case 'TSV': return '.tsv,.tsv.gz';
    case 'TXT': return '.txt,.txt.gz';
    case 'JSON': return '.json';
    case 'JSONL': return '.jsonl,.ndjson,.jsonl.gz,.ndjson.gz';
    case 'PARQUET': return '.parquet';
    case 'AVRO': return '.avro';
    case 'EXCEL': return '.xls,.xlsx';
    case 'GDB': return '.zip';
    case 'SHP': return '.zip';
  }
};

export const fileDatasetAllowsAdditionalUpload = (
  type: FileDatasetType,
  existingFileCount: number,
): boolean => (type !== 'EXCEL' && type !== 'GDB') || existingFileCount === 0;

export const inferFileDatasetCompression = (fileName: string): FileDatasetCompression => (
  fileName.toLowerCase().endsWith('.gz')
    ? 'GZIP'
    : fileName.toLowerCase().endsWith('.zip') ? 'ZIP' : 'NONE'
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
import type { PlatformDataType } from '../../model';
