export type MetricExportMode = 'DRAFT' | 'PUBLISHED';
export interface MetricImportIssue { rowNumber: number; column: string; message: string; blocking: boolean }
export interface MetricImportChange { column: string; before: string; after: string }
export interface MetricImportRow {
  rowNumber: number;
  code: string;
  name: string;
  action: 'CREATE' | 'UPDATE' | 'UNCHANGED' | 'ERROR';
  changes: MetricImportChange[];
  issues: MetricImportIssue[];
}
export interface MetricImportPreview {
  fingerprint: string;
  totalRows: number;
  createCount: number;
  updateCount: number;
  unchangedCount: number;
  errorCount: number;
  warningCount: number;
  canImport: boolean;
  rows: MetricImportRow[];
}
export interface MetricImportResult { created: number; updated: number; unchanged: number }
