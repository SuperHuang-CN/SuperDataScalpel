import { requestBlob, requestBlobResponse, requestJson } from '../../../shared/api/http';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { toSearchParams, type SearchRequest } from '../../../shared/search';
import type {
  CreateDataModelRequest,
  CreateManagedDataModelDraftRequest,
  CreatePhysicalTableChangePlanRequest,
  DataModel,
  DataModelReferences,
  DataModelPhysicalStatistics,
  DataModelDataQueryRequest,
  DataModelDataQueryResponse,
  DataModelDetail,
  DataModelPhysicalChange,
  DataModelPreview,
  DataModelSpatialPreview,
  DataModelSpatialPreviewMap,
  ExecutePhysicalTableChangePlanRequest,
  ExternalTableImportPreview,
  ManagedImportPreview,
  ManagedImportPreviewRequest,
  LineageDirection,
  LineageGraph,
  ModelFieldTemplate,
  CreateModelFieldTemplateRequest,
  UpdateModelFieldTemplateRequest,
  ModelWarehouseLayer,
  CreateModelWarehouseLayerRequest,
  UpdateModelWarehouseLayerRequest,
  ModelMetadataImportPreview,
  ImportModelMetadataRequest,
  ModelMetadataImportResult,
  PhysicalTableDdlPlan,
  PhysicalTableInspection,
  PlatformTypeCapability,
  UpdateDataModelFieldsRequest,
  UpdateDataModelRequest,
} from '../model/dataModel';

const DATA_MODEL_PATH = '/v1/models';
const MODEL_WAREHOUSE_LAYER_PATH = '/v1/model-warehouse-layers';
const MODEL_FIELD_TEMPLATE_PATH = '/v1/model-field-templates';

export const fetchModelFieldTemplates = async (
  request: SearchRequest,
): Promise<PageResponse<ModelFieldTemplate>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<ModelFieldTemplate>>(
    query ? `${MODEL_FIELD_TEMPLATE_PATH}?${query}` : MODEL_FIELD_TEMPLATE_PATH,
  );
};

export const fetchModelFieldTemplate = (id: string): Promise<ModelFieldTemplate> => (
  requestJson<ModelFieldTemplate>(`${MODEL_FIELD_TEMPLATE_PATH}/${id}`)
);

export const createModelFieldTemplate = (
  request: CreateModelFieldTemplateRequest,
): Promise<ModelFieldTemplate> => requestJson<ModelFieldTemplate>(MODEL_FIELD_TEMPLATE_PATH, {
  method: 'POST',
  body: JSON.stringify(request),
});

export const updateModelFieldTemplate = (
  id: string,
  request: UpdateModelFieldTemplateRequest,
): Promise<ModelFieldTemplate> => requestJson<ModelFieldTemplate>(
  `${MODEL_FIELD_TEMPLATE_PATH}/${id}/actions/update`,
  { method: 'POST', body: JSON.stringify(request) },
);

export type ModelFieldTemplateCommand = 'enable' | 'disable';

export const executeModelFieldTemplateCommand = (
  id: string,
  command: ModelFieldTemplateCommand,
): Promise<ModelFieldTemplate> => requestJson<ModelFieldTemplate>(
  `${MODEL_FIELD_TEMPLATE_PATH}/${id}/actions/${command}`,
  { method: 'POST' },
);

export const deleteModelFieldTemplate = (id: string): Promise<void> => requestJson<void>(
  `${MODEL_FIELD_TEMPLATE_PATH}/${id}/actions/delete`,
  { method: 'POST' },
);

export const fetchModelWarehouseLayers = async (
  request: SearchRequest,
): Promise<PageResponse<ModelWarehouseLayer>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<ModelWarehouseLayer>>(
    query ? `${MODEL_WAREHOUSE_LAYER_PATH}?${query}` : MODEL_WAREHOUSE_LAYER_PATH,
  );
};

export const createModelWarehouseLayer = (
  request: CreateModelWarehouseLayerRequest,
): Promise<ModelWarehouseLayer> => requestJson<ModelWarehouseLayer>(MODEL_WAREHOUSE_LAYER_PATH, {
  method: 'POST',
  body: JSON.stringify(request),
});

export const updateModelWarehouseLayer = (
  id: string,
  request: UpdateModelWarehouseLayerRequest,
): Promise<ModelWarehouseLayer> => requestJson<ModelWarehouseLayer>(
  `${MODEL_WAREHOUSE_LAYER_PATH}/${id}/actions/update`,
  { method: 'POST', body: JSON.stringify(request) },
);

export type ModelWarehouseLayerCommand = 'enable' | 'disable';

export const executeModelWarehouseLayerCommand = (
  id: string,
  command: ModelWarehouseLayerCommand,
): Promise<ModelWarehouseLayer> => requestJson<ModelWarehouseLayer>(
  `${MODEL_WAREHOUSE_LAYER_PATH}/${id}/actions/${command}`,
  { method: 'POST' },
);

export const deleteModelWarehouseLayer = (id: string): Promise<void> => requestJson<void>(
  `${MODEL_WAREHOUSE_LAYER_PATH}/${id}/actions/delete`,
  { method: 'POST' },
);

export const fetchDataModels = async (request: SearchRequest): Promise<PageResponse<DataModel>> => {
  const query = toSearchParams(request).toString();
  return requestJson<PageResponse<DataModel>>(query ? `${DATA_MODEL_PATH}?${query}` : DATA_MODEL_PATH);
};

export const fetchDataModel = (id: string): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(`${DATA_MODEL_PATH}/${id}`)
);

export const fetchDataModelTableLineage = (
  id: string,
  direction: LineageDirection,
  depth: 1 | 2,
): Promise<LineageGraph> => {
  const query = new URLSearchParams({ direction, depth: String(depth) });
  return requestJson<LineageGraph>(`${DATA_MODEL_PATH}/${id}/lineage/table?${query.toString()}`);
};

export const fetchDataModelFieldLineage = (
  id: string,
  fieldId: string,
  direction: LineageDirection,
  depth: 1 | 2,
): Promise<LineageGraph> => {
  const query = new URLSearchParams({ direction, depth: String(depth) });
  return requestJson<LineageGraph>(`${DATA_MODEL_PATH}/${id}/lineage/fields/${fieldId}?${query.toString()}`);
};

export const refreshDataModelPhysicalStatistics = (
  id: string,
): Promise<DataModelPhysicalStatistics> => requestJson<DataModelPhysicalStatistics>(
  `${DATA_MODEL_PATH}/${id}/actions/refresh-physical-statistics`,
  { method: 'POST' },
);

export const fetchExternalTableImportPreview = (
  storageDataSourceId: string,
  physicalTableName: string,
): Promise<ExternalTableImportPreview> => {
  const query = new URLSearchParams({ storageDataSourceId, physicalTableName });
  return requestJson<ExternalTableImportPreview>(`${DATA_MODEL_PATH}/external-table-import-preview?${query.toString()}`);
};

export const fetchPlatformTypeCapabilities = (storageDataSourceId: string): Promise<PlatformTypeCapability[]> => {
  const query = new URLSearchParams({ storageDataSourceId });
  return requestJson<PlatformTypeCapability[]>(`${DATA_MODEL_PATH}/platform-types?${query.toString()}`);
};

export const fetchPhysicalTableInspection = (id: string): Promise<PhysicalTableInspection> => (
  requestJson<PhysicalTableInspection>(`${DATA_MODEL_PATH}/${id}/physical-table`)
);

export const fetchDataModelReferences = (id: string): Promise<DataModelReferences> => (
  requestJson<DataModelReferences>(`${DATA_MODEL_PATH}/${id}/references`)
);

export const fetchManagedImportPreview = (
  request: ManagedImportPreviewRequest,
): Promise<ManagedImportPreview> => (
  requestJson<ManagedImportPreview>(`${DATA_MODEL_PATH}/managed-import-preview`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const downloadModelMetadataTemplate = (): Promise<Blob> => (
  requestBlob(`${DATA_MODEL_PATH}/metadata-import-template`)
);

export const exportModelMetadata = (modelIds: string[]): Promise<Blob> => (
  requestBlob(`${DATA_MODEL_PATH}/actions/export-metadata`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ modelIds }),
  })
);

export const previewModelMetadataImport = (
  file: File,
  targetStorageDataSourceId: string,
): Promise<ModelMetadataImportPreview> => {
  const body = new FormData();
  body.append('file', file);
  const query = new URLSearchParams({ targetStorageDataSourceId });
  return requestJson<ModelMetadataImportPreview>(
    `${DATA_MODEL_PATH}/actions/preview-metadata-import?${query.toString()}`,
    { method: 'POST', body },
    60_000,
  );
};

export const fetchPhysicalTableDdlPlan = (id: string): Promise<PhysicalTableDdlPlan> => (
  requestJson<PhysicalTableDdlPlan>(`${DATA_MODEL_PATH}/${id}/physical-table/ddl`)
);

export const importModelMetadata = (
  request: ImportModelMetadataRequest,
): Promise<ModelMetadataImportResult> => requestJson<ModelMetadataImportResult>(
  `${DATA_MODEL_PATH}/actions/import-metadata`,
  {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  },
  120_000,
);

export const fetchPhysicalTableChangePlans = async (
  id: string,
  request: SearchRequest,
): Promise<PageResponse<DataModelPhysicalChange>> => {
  const query = toSearchParams(request).toString();
  const path = `${DATA_MODEL_PATH}/${id}/physical-table-change-plans`;
  return requestJson<PageResponse<DataModelPhysicalChange>>(query ? `${path}?${query}` : path);
};

export const fetchPhysicalTableChangePlan = (modelId: string, planId: string): Promise<DataModelPhysicalChange> => (
  requestJson<DataModelPhysicalChange>(`${DATA_MODEL_PATH}/${modelId}/physical-table-change-plans/${planId}`)
);

export const createPhysicalTableChangePlan = (
  id: string,
  request: CreatePhysicalTableChangePlanRequest,
): Promise<DataModelPhysicalChange> => (
  requestJson<DataModelPhysicalChange>(`${DATA_MODEL_PATH}/${id}/physical-table-change-plans`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const cancelPhysicalTableChangePlan = (modelId: string, planId: string): Promise<DataModelPhysicalChange> => (
  requestJson<DataModelPhysicalChange>(`${DATA_MODEL_PATH}/${modelId}/physical-table-change-plans/${planId}/actions/cancel`, {
    method: 'POST',
  })
);

export const executePhysicalTableChangePlan = (
  modelId: string,
  planId: string,
  request: ExecutePhysicalTableChangePlanRequest,
): Promise<DataModelPhysicalChange> => (
  requestJson<DataModelPhysicalChange>(`${DATA_MODEL_PATH}/${modelId}/physical-table-change-plans/${planId}/actions/execute`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const createPhysicalTable = (id: string): Promise<PhysicalTableInspection> => (
  requestJson<PhysicalTableInspection>(`${DATA_MODEL_PATH}/${id}/actions/create-physical-table`, { method: 'POST' })
);

export const fetchDataModelPreview = (id: string): Promise<DataModelPreview> => (
  requestJson<DataModelPreview>(`${DATA_MODEL_PATH}/${id}/data-preview`)
);

export const fetchDataModelSpatialPreview = (id: string): Promise<DataModelSpatialPreview> => (
  requestJson<DataModelSpatialPreview>(`${DATA_MODEL_PATH}/${id}/spatial-preview`)
);

export const fetchDataModelSpatialPreviewMap = async (
  id: string,
  geometryField: string,
  bbox: [number, number, number, number],
  width: number,
  height: number,
  signal: AbortSignal,
): Promise<DataModelSpatialPreviewMap> => {
  const query = new URLSearchParams({
    geometryField,
    bbox: bbox.join(','),
    width: String(width),
    height: String(height),
  });
  const response = await requestBlobResponse(
    `${DATA_MODEL_PATH}/${id}/spatial-preview/map?${query.toString()}`,
    { signal },
    10_000,
  );
  return {
    blob: response.blob,
    featureCount: Number(response.headers.get('X-Spatial-Feature-Count') ?? 0),
    skippedCount: Number(response.headers.get('X-Spatial-Skipped-Count') ?? 0),
    truncated: response.headers.get('X-Spatial-Truncated') === 'true',
  };
};

export const queryDataModelData = (
  id: string,
  request: DataModelDataQueryRequest,
): Promise<DataModelDataQueryResponse> => (
  requestJson<DataModelDataQueryResponse>(`${DATA_MODEL_PATH}/${id}/actions/query-data`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const createDataModel = (request: CreateDataModelRequest): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(DATA_MODEL_PATH, { method: 'POST', body: JSON.stringify(request) })
);

export const createManagedDataModelDraft = (
  request: CreateManagedDataModelDraftRequest,
): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(`${DATA_MODEL_PATH}/managed-drafts`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const updateDataModel = (id: string, request: UpdateDataModelRequest): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(`${DATA_MODEL_PATH}/${id}/actions/update`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export const updateDataModelFields = (id: string, request: UpdateDataModelFieldsRequest): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(`${DATA_MODEL_PATH}/${id}/actions/update-fields`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
);

export type DataModelCommand = 'publish' | 'disable' | 'enable';

export const executeDataModelCommand = (id: string, command: DataModelCommand): Promise<DataModelDetail> => (
  requestJson<DataModelDetail>(`${DATA_MODEL_PATH}/${id}/actions/${command}`, { method: 'POST' })
);

export const deleteDataModel = (id: string): Promise<void> => (
  requestJson<void>(`${DATA_MODEL_PATH}/${id}/actions/delete`, { method: 'POST' })
);
