import { requestBlob, requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateDataServiceRequest,
  DataServiceDetail,
  DataServiceRelatedModel,
  DataServiceSummary,
  PublishDataServiceRequest,
  SqlServiceTestRequest,
  SqlServiceTestResponse,
  StandardDataServiceModelCandidate,
  SpatialDataServiceModelCandidate,
  SpatialDataServicePreview,
  SpatialDataServiceStyle,
  UpdateDataServiceDefinitionRequest,
  UpdateDataServiceRequest,
} from '../model/dataService';
import type { FieldProfile, FieldProfileRequest, SpatialStyleDocument, SpatialStyleMode } from '../../cartography';
import type { LineageFieldGraph, LineageGraph } from '../../model';
import type {
  ScriptCompletionData,
  ScriptExecutionResult,
  ScriptRunRequest,
} from '@superhuang/super-api-studio-script-workbench';

const DATA_SERVICE_PATH = '/v1/data-services';

const searchPath = (path: string, request: SearchRequest) => {
  const query = toSearchParams(request).toString();
  return query ? `${path}?${query}` : path;
};

export const fetchDataServices = (request: SearchRequest): Promise<PageResponse<DataServiceSummary>> => (
  requestJson<PageResponse<DataServiceSummary>>(searchPath(DATA_SERVICE_PATH, request))
);

export const fetchDataService = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}`)
);

export const fetchDataServiceSpatialPreview = (id: string): Promise<SpatialDataServicePreview> => (
  requestJson<SpatialDataServicePreview>(`${DATA_SERVICE_PATH}/${id}/spatial-preview`)
);

export const fetchDataServiceSpatialStyle = (id: string): Promise<SpatialDataServiceStyle> => (
  requestJson<SpatialDataServiceStyle>(`${DATA_SERVICE_PATH}/${id}/spatial-style`)
);

/** Read-only draft compilation; does not save a style or request a WMS image. */
export const queryDataServiceSpatialStyleSld = (
  id: string,
  styleDocument: SpatialStyleDocument,
  signal: AbortSignal,
): Promise<{ sldText: string }> => requestJson<{ sldText: string }>(
  `${DATA_SERVICE_PATH}/${id}/actions/query-spatial-style-sld`,
  { method: 'POST', body: JSON.stringify({ styleDocument }), signal },
);

export const updateDataServiceSpatialStyle = (
  id: string,
  styleDocument: SpatialStyleDocument | null,
  mode: SpatialStyleMode = 'CARTOGRAPHY',
): Promise<SpatialDataServiceStyle> => requestJson<SpatialDataServiceStyle>(
  `${DATA_SERVICE_PATH}/${id}/actions/update-spatial-style`,
  { method: 'POST', body: JSON.stringify({ mode, styleDocument }) },
);

export const queryDataServiceSpatialStyleFieldProfile = (
  id: string,
  request: FieldProfileRequest,
): Promise<FieldProfile> => requestJson<FieldProfile>(
  `${DATA_SERVICE_PATH}/${id}/actions/query-spatial-style-field-profile`,
  { method: 'POST', body: JSON.stringify(request) },
  20_000,
);

export const uploadDataServiceSpatialSld = (
  id: string,
  file: File,
): Promise<SpatialDataServiceStyle> => {
  const body = new FormData();
  body.append('file', file);
  return requestJson<SpatialDataServiceStyle>(
    `${DATA_SERVICE_PATH}/${id}/actions/upload-spatial-sld`,
    { method: 'POST', body },
  );
};

export const applyDataServiceSpatialStyle = (id: string): Promise<SpatialDataServiceStyle> => (
  requestJson<SpatialDataServiceStyle>(`${DATA_SERVICE_PATH}/${id}/actions/apply-spatial-style`, { method: 'POST' })
);

export const fetchDataServiceSpatialPreviewMap = (
  id: string,
  bbox: [number, number, number, number],
  width: number,
  height: number,
  signal: AbortSignal,
): Promise<Blob> => {
  const query = new URLSearchParams({
    bbox: bbox.join(','),
    width: String(width),
    height: String(height),
  });
  return requestBlob(
    `${DATA_SERVICE_PATH}/${id}/spatial-preview/map?${query.toString()}`,
    { signal },
    30_000,
  );
};

export const renderDataServiceSpatialStylePreview = (
  id: string,
  styleDocument: SpatialStyleDocument,
  bbox: [number, number, number, number],
  width: number,
  height: number,
  signal: AbortSignal,
): Promise<Blob> => requestBlob(
  `${DATA_SERVICE_PATH}/${id}/actions/render-spatial-style-preview`,
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'image/png' },
    body: JSON.stringify({ styleDocument, bbox, width, height }),
    signal,
  },
  30_000,
);

export const renderUploadedDataServiceSpatialStylePreview = (
  id: string,
  file: File,
  bbox: [number, number, number, number],
  width: number,
  height: number,
  signal: AbortSignal,
): Promise<Blob> => {
  const body = new FormData();
  body.append('file', file);
  body.append('bbox', bbox.join(','));
  body.append('width', String(width));
  body.append('height', String(height));
  return requestBlob(
    `${DATA_SERVICE_PATH}/${id}/actions/render-spatial-style-preview`,
    { method: 'POST', body, signal },
    30_000,
  );
};

export const fetchDataServiceSpatialPreviewLegend = (id: string, signal?: AbortSignal): Promise<Blob> => requestBlob(
  `${DATA_SERVICE_PATH}/${id}/spatial-preview/legend`,
  { signal },
  30_000,
);

export const fetchDataServiceRelatedModels = (id: string): Promise<DataServiceRelatedModel[]> => (
  requestJson<DataServiceRelatedModel[]>(`${DATA_SERVICE_PATH}/${id}/related-models`)
);

export const fetchDataServiceTableLineage = (id: string, depth: 1 | 2): Promise<LineageGraph> => {
  const query = new URLSearchParams({ depth: String(depth) });
  return requestJson<LineageGraph>(`${DATA_SERVICE_PATH}/${id}/lineage/table?${query.toString()}`);
};

export const fetchDataServiceFieldLineage = (
  id: string,
  fieldId: string,
  depth: 1 | 2,
): Promise<LineageGraph> => {
  const query = new URLSearchParams({ depth: String(depth) });
  return requestJson<LineageGraph>(`${DATA_SERVICE_PATH}/${id}/lineage/fields/${fieldId}?${query.toString()}`);
};

export const queryDataServiceFieldLineage = (
  id: string,
  fieldIds: string[] | null,
  depth: 1 | 2,
  signal?: AbortSignal,
): Promise<LineageFieldGraph> => requestJson<LineageFieldGraph>(
  `${DATA_SERVICE_PATH}/${id}/lineage/actions/query-fields`,
  { method: 'POST', body: JSON.stringify({ fieldIds, depth }), signal },
);

export const createDataService = (request: CreateDataServiceRequest): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(DATA_SERVICE_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const updateDataService = (id: string, request: UpdateDataServiceRequest): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/update`, { method: 'POST', body: JSON.stringify(request) })
);

export const updateDataServiceDefinition = (
  id: string,
  request: UpdateDataServiceDefinitionRequest,
): Promise<DataServiceDetail> => requestJson<DataServiceDetail>(
  `${DATA_SERVICE_PATH}/${id}/actions/update-definition`,
  { method: 'POST', body: JSON.stringify(request) },
);

export const fetchStandardDataServiceModelCandidates = (
  id: string,
  request: SearchRequest,
  includeUnavailable: boolean,
): Promise<PageResponse<StandardDataServiceModelCandidate>> => {
  const query = toSearchParams(request);
  if (includeUnavailable) query.set('includeUnavailable', 'true');
  const suffix = query.toString();
  return requestJson<PageResponse<StandardDataServiceModelCandidate>>(
    `${DATA_SERVICE_PATH}/${id}/standard-model-candidates${suffix ? `?${suffix}` : ''}`,
  );
};

export const fetchSpatialDataServiceModelCandidates = (
  id: string,
  request: SearchRequest,
  includeUnavailable: boolean,
): Promise<PageResponse<SpatialDataServiceModelCandidate>> => {
  const query = toSearchParams(request);
  if (includeUnavailable) query.set('includeUnavailable', 'true');
  const suffix = query.toString();
  return requestJson<PageResponse<SpatialDataServiceModelCandidate>>(
    `${DATA_SERVICE_PATH}/${id}/spatial-model-candidates${suffix ? `?${suffix}` : ''}`,
  );
};

export const testSqlDataService = (request: SqlServiceTestRequest): Promise<SqlServiceTestResponse> => (
  requestJson<SqlServiceTestResponse>(`${DATA_SERVICE_PATH}/actions/test-sql`, {
    method: 'POST', body: JSON.stringify(request),
  })
);

export interface ExecuteScriptDraftRequest extends ScriptRunRequest {
  engineId: string;
  dataSourceId: string;
  routePath: string;
  script: string;
}

export const executeScriptDraft = (request: ExecuteScriptDraftRequest): Promise<ScriptExecutionResult> => (
  requestJson<ScriptExecutionResult>(`${DATA_SERVICE_PATH}/actions/execute-script-draft`, {
    method: 'POST',
    body: JSON.stringify(request),
  }, 60_000)
);

export const fetchScriptCompletion = (
  engineId: string,
  dataSourceId: string,
): Promise<ScriptCompletionData> => {
  const query = new URLSearchParams({ engineId, dataSourceId });
  return requestJson<ScriptCompletionData>(`${DATA_SERVICE_PATH}/script-completion?${query.toString()}`, {}, 60_000);
};

export const enableDataService = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/enable`, { method: 'POST' })
);

export const publishDataService = (id: string, request: PublishDataServiceRequest): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/publish`, {
    method: 'POST', body: JSON.stringify(request),
  })
);

export const reconcileDataServiceGateway = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/reconcile-gateway`, { method: 'POST' })
);

export const unpublishDataService = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/unpublish`, { method: 'POST' })
);

export const disableDataService = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/disable`, { method: 'POST' })
);

export const cleanupDataServiceDeployment = (id: string): Promise<DataServiceDetail> => (
  requestJson<DataServiceDetail>(`${DATA_SERVICE_PATH}/${id}/actions/cleanup-deployment`, { method: 'POST' })
);

export const deleteDataService = (id: string): Promise<void> => (
  requestJson<void>(`${DATA_SERVICE_PATH}/${id}/actions/delete`, { method: 'POST' })
);
