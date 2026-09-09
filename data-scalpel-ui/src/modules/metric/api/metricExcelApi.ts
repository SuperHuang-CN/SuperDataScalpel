import { requestBlob, requestJson } from '../../../shared/api/http';
import type { MetricExportMode, MetricImportPreview, MetricImportResult } from '../model/metricExcel';

const base = '/v1/metrics/actions';
const upload = (file: File): FormData => {
  const body = new FormData();
  body.append('file', file);
  return body;
};
export const downloadMetricTemplate = (): Promise<Blob> => requestBlob(`${base}/download-import-template`);
export const exportMetrics = (mode: MetricExportMode, ids: string[], search?: string): Promise<Blob> => requestBlob(
  `${base}/query-export`,
  { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ mode, ids, search }) },
  60_000,
);
export const previewMetricImport = (file: File): Promise<MetricImportPreview> => requestJson(
  `${base}/query-import-preview`, { method: 'POST', body: upload(file) }, 60_000,
);
export const downloadMetricImportErrors = (file: File): Promise<Blob> => requestBlob(
  `${base}/query-import-errors`, { method: 'POST', body: upload(file) }, 60_000,
);
export const importMetrics = (file: File, expectedPreviewFingerprint: string): Promise<MetricImportResult> => {
  const body = upload(file);
  body.append('expectedPreviewFingerprint', expectedPreviewFingerprint);
  return requestJson(`${base}/import`, { method: 'POST', body }, 60_000);
};
