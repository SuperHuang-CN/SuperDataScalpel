import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { QueryClient } from '@tanstack/react-query';
import type { SearchRequest } from '../../../shared/search';
import { invalidateDirectoryTree } from '../../directory';
import { invalidateStandardDictionaries } from '../../standard';
import {
  createDataModel,
  createModelFieldTemplate,
  createModelWarehouseLayer,
  createManagedDataModelDraft,
  createPhysicalTableChangePlan,
  cancelPhysicalTableChangePlan,
  createPhysicalTable,
  deleteDataModel,
  deleteModelFieldTemplate,
  deleteModelWarehouseLayer,
  executeDataModelCommand,
  executeModelFieldTemplateCommand,
  executeModelWarehouseLayerCommand,
  executePhysicalTableChangePlan,
  downloadModelMetadataTemplate,
  exportModelMetadata,
  fetchDataModelPreview,
  fetchDataModelSpatialPreview,
  fetchDataModel,
  fetchDataModelReferences,
  fetchDataModelFieldLineage,
  fetchDataModelTableLineage,
  fetchExternalTableImportPreview,
  fetchManagedImportPreview,
  fetchModelFieldTemplate,
  fetchModelFieldTemplates,
  fetchModelWarehouseLayers,
  fetchDataModels,
  fetchPhysicalTableChangePlan,
  fetchPhysicalTableChangePlans,
  fetchPhysicalTableInspection,
  fetchPlatformTypeCapabilities,
  importModelMetadata,
  previewModelMetadataImport,
  queryDataModelData,
  refreshDataModelPhysicalStatistics,
  updateDataModel,
  updateModelFieldTemplate,
  updateDataModelFields,
  updateModelWarehouseLayer,
  type DataModelCommand,
  type ModelFieldTemplateCommand,
  type ModelWarehouseLayerCommand,
} from '../api/dataModelApi';
import type {
  CreateDataModelRequest,
  CreateManagedDataModelDraftRequest,
  CreatePhysicalTableChangePlanRequest,
  ExecutePhysicalTableChangePlanRequest,
  DataModelDataQueryRequest,
  ManagedImportPreview,
  ManagedImportPreviewRequest,
  ImportModelMetadataRequest,
  LineageDirection,
  LineageGranularity,
  CreateModelFieldTemplateRequest,
  UpdateModelFieldTemplateRequest,
  CreateModelWarehouseLayerRequest,
  UpdateModelWarehouseLayerRequest,
  UpdateDataModelFieldsRequest,
  UpdateDataModelRequest,
} from '../model/dataModel';
import { runManagedImportTasks } from '../model/managedTableImport';
import { modelQualityRulesQueryKey } from './useModelQualityRules';

export interface ManagedImportPreviewItem {
  key: string;
  request: ManagedImportPreviewRequest;
}

export interface ManagedImportPreviewResult extends ManagedImportPreviewItem {
  preview?: ManagedImportPreview;
  error?: unknown;
}

export interface ManagedDataModelDraftItem {
  key: string;
  request: CreateManagedDataModelDraftRequest;
}

export interface ManagedDataModelDraftResult extends ManagedDataModelDraftItem {
  detail?: Awaited<ReturnType<typeof createManagedDataModelDraft>>;
  error?: unknown;
}

const dataModelsQueryKey = 'data-models';

export const invalidateDataModelLineage = (queryClient: QueryClient) => queryClient.invalidateQueries({
  predicate: (query) => query.queryKey[0] === dataModelsQueryKey && query.queryKey.includes('lineage'),
});
const modelWarehouseLayersQueryKey = 'model-warehouse-layers';
const modelFieldTemplatesQueryKey = 'model-field-templates';
const physicalTableChangePlansQueryKey = 'physical-table-change-plans';
const taskModelRelationsQueryKey = 'task-model-relations';

const invalidateTaskModelRelations = (
  queryClient: ReturnType<typeof useQueryClient>,
) => queryClient.invalidateQueries({ queryKey: [taskModelRelationsQueryKey] });

const invalidateDataModels = async (queryClient: ReturnType<typeof useQueryClient>) => {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey] }),
    invalidateDirectoryTree(queryClient, 'MODEL'),
  ]);
};

const invalidateModelWarehouseLayers = (
  queryClient: ReturnType<typeof useQueryClient>,
) => queryClient.invalidateQueries({ queryKey: [modelWarehouseLayersQueryKey] });

const invalidateModelFieldTemplates = (
  queryClient: ReturnType<typeof useQueryClient>,
) => queryClient.invalidateQueries({ queryKey: [modelFieldTemplatesQueryKey] });

export const useModelFieldTemplates = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [modelFieldTemplatesQueryKey, 'list', request],
  queryFn: () => fetchModelFieldTemplates(request),
  enabled,
});

export const useModelFieldTemplate = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [modelFieldTemplatesQueryKey, 'detail', id],
  queryFn: () => fetchModelFieldTemplate(id as string),
  enabled: enabled && Boolean(id),
});

export const useCreateModelFieldTemplate = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateModelFieldTemplateRequest) => createModelFieldTemplate(request),
    onSuccess: () => Promise.all([
      invalidateModelFieldTemplates(queryClient),
      invalidateStandardDictionaries(queryClient),
    ]),
  });
};

export const useUpdateModelFieldTemplate = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateModelFieldTemplateRequest }) => (
      updateModelFieldTemplate(id, request)
    ),
    onSuccess: () => Promise.all([
      invalidateModelFieldTemplates(queryClient),
      invalidateStandardDictionaries(queryClient),
    ]),
  });
};

export const useModelFieldTemplateCommand = (command: ModelFieldTemplateCommand) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => executeModelFieldTemplateCommand(id, command),
    onSuccess: () => invalidateModelFieldTemplates(queryClient),
  });
};

export const useDeleteModelFieldTemplate = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteModelFieldTemplate,
    onSuccess: () => Promise.all([
      invalidateModelFieldTemplates(queryClient),
      invalidateStandardDictionaries(queryClient),
    ]),
  });
};

export const useModelWarehouseLayers = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [modelWarehouseLayersQueryKey, request],
  queryFn: () => fetchModelWarehouseLayers(request),
  enabled,
});

export const useCreateModelWarehouseLayer = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateModelWarehouseLayerRequest) => createModelWarehouseLayer(request),
    onSuccess: () => invalidateModelWarehouseLayers(queryClient),
  });
};

export const useUpdateModelWarehouseLayer = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateModelWarehouseLayerRequest }) => (
      updateModelWarehouseLayer(id, request)
    ),
    onSuccess: () => Promise.all([
      invalidateModelWarehouseLayers(queryClient),
      queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey] }),
    ]),
  });
};

export const useModelWarehouseLayerCommand = (command: ModelWarehouseLayerCommand) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => executeModelWarehouseLayerCommand(id, command),
    onSuccess: () => Promise.all([
      invalidateModelWarehouseLayers(queryClient),
      queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey] }),
    ]),
  });
};

export const useDeleteModelWarehouseLayer = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteModelWarehouseLayer,
    onSuccess: () => Promise.all([
      invalidateModelWarehouseLayers(queryClient),
      queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey] }),
    ]),
  });
};

export const useDataModels = (request: SearchRequest, enabled = true) => useQuery({
  queryKey: [dataModelsQueryKey, request],
  queryFn: () => fetchDataModels(request),
  enabled,
});

export const useDataModel = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, id],
  queryFn: () => fetchDataModel(id as string),
  enabled: enabled && Boolean(id),
});

export const useDataModelReferences = (id: string | undefined, enabled = true) => useQuery({
  queryKey: [dataModelsQueryKey, id, 'references'],
  queryFn: () => fetchDataModelReferences(id as string),
  enabled: enabled && Boolean(id),
  staleTime: 0,
});

export const useDataModelLineage = (
  modelId: string,
  granularity: LineageGranularity,
  fieldId: string | undefined,
  direction: LineageDirection,
  depth: 1 | 2,
  enabled = true,
) => useQuery({
  queryKey: [dataModelsQueryKey, modelId, 'lineage', granularity, fieldId ?? null, direction, depth],
  queryFn: () => granularity === 'TABLE'
    ? fetchDataModelTableLineage(modelId, direction, depth)
    : fetchDataModelFieldLineage(modelId, fieldId as string, direction, depth),
  enabled: enabled && (granularity === 'TABLE' || Boolean(fieldId)),
});

/** Callers decide when to refresh the list so a batch can finish with exactly one refetch. */
export const useRefreshDataModelPhysicalStatistics = () => useMutation({
  mutationFn: refreshDataModelPhysicalStatistics,
});

export const useExternalTableImportPreview = (
  storageDataSourceId: string | undefined,
  physicalTableName: string | undefined,
  enabled: boolean,
) => useQuery({
  queryKey: [dataModelsQueryKey, 'external-table-import-preview', storageDataSourceId, physicalTableName],
  queryFn: () => fetchExternalTableImportPreview(storageDataSourceId as string, physicalTableName as string),
  enabled: enabled && Boolean(storageDataSourceId) && Boolean(physicalTableName),
});

export const usePlatformTypeCapabilities = (storageDataSourceId: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, 'platform-types', storageDataSourceId],
  queryFn: () => fetchPlatformTypeCapabilities(storageDataSourceId as string),
  enabled: enabled && Boolean(storageDataSourceId),
  staleTime: 5 * 60 * 1000,
});

export const usePhysicalTableInspection = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, id, 'physical-table'],
  queryFn: () => fetchPhysicalTableInspection(id as string),
  enabled: enabled && Boolean(id),
});

export const usePhysicalTableChangePlans = (id: string | undefined, request: SearchRequest, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, id, physicalTableChangePlansQueryKey, request],
  queryFn: () => fetchPhysicalTableChangePlans(id as string, request),
  enabled: enabled && Boolean(id),
});

export const usePhysicalTableChangePlan = (modelId: string | undefined, planId: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, modelId, physicalTableChangePlansQueryKey, planId],
  queryFn: () => fetchPhysicalTableChangePlan(modelId as string, planId as string),
  enabled: enabled && Boolean(modelId) && Boolean(planId),
});

export const useDataModelPreview = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, id, 'data-preview'],
  queryFn: () => fetchDataModelPreview(id as string),
  enabled: enabled && Boolean(id),
});

export const useDataModelSpatialPreview = (id: string | undefined, enabled: boolean) => useQuery({
  queryKey: [dataModelsQueryKey, id, 'spatial-preview'],
  queryFn: () => fetchDataModelSpatialPreview(id as string),
  enabled: enabled && Boolean(id),
});

export const useDataModelDataQuery = () => useMutation({
  mutationFn: ({ id, request }: { id: string; request: DataModelDataQueryRequest }) => queryDataModelData(id, request),
});

export const useCreateDataModel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateDataModelRequest) => createDataModel(request),
    onSuccess: (detail) => {
      queryClient.setQueryData([dataModelsQueryKey, detail.model.id], detail);
      return Promise.all([
        invalidateDataModels(queryClient),
        invalidateModelWarehouseLayers(queryClient),
      ]);
    },
  });
};

const fetchManagedImportPreviews = async (
  items: ManagedImportPreviewItem[],
): Promise<ManagedImportPreviewResult[]> => (
  (await runManagedImportTasks(items, (item) => fetchManagedImportPreview(item.request)))
    .map(({ item, result, error }) => ({ ...item, preview: result, error }))
);

export const useManagedImportPreviews = () => useMutation({
  mutationFn: fetchManagedImportPreviews,
});

export const useDownloadModelMetadataTemplate = () => useMutation({
  mutationFn: downloadModelMetadataTemplate,
});

export const useExportModelMetadata = () => useMutation({
  mutationFn: exportModelMetadata,
});

export const usePreviewModelMetadataImport = () => useMutation({
  mutationFn: ({ file, targetStorageDataSourceId }: { file: File; targetStorageDataSourceId: string }) => (
    previewModelMetadataImport(file, targetStorageDataSourceId)
  ),
});

export const useImportModelMetadata = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: ImportModelMetadataRequest) => importModelMetadata(request),
    onSuccess: () => Promise.all([
      invalidateDataModels(queryClient),
      invalidateModelWarehouseLayers(queryClient),
      invalidateStandardDictionaries(queryClient),
    ]),
  });
};

const createManagedDataModelDrafts = async (
  items: ManagedDataModelDraftItem[],
): Promise<ManagedDataModelDraftResult[]> => (
  (await runManagedImportTasks(items, (item) => createManagedDataModelDraft(item.request)))
    .map(({ item, result, error }) => ({ ...item, detail: result, error }))
);

export const useCreateManagedDataModelDrafts = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createManagedDataModelDrafts,
    onSuccess: () => Promise.all([
      invalidateDataModels(queryClient),
      invalidateModelWarehouseLayers(queryClient),
      invalidateStandardDictionaries(queryClient),
    ]),
  });
};

export const useUpdateDataModel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateDataModelRequest }) => updateDataModel(id, request),
    onSuccess: (detail) => {
      queryClient.setQueryData([dataModelsQueryKey, detail.model.id], detail);
      return Promise.all([
        invalidateDataModels(queryClient),
        invalidateStandardDictionaries(queryClient),
        invalidateTaskModelRelations(queryClient),
        invalidateModelWarehouseLayers(queryClient),
        queryClient.invalidateQueries({ queryKey: [modelQualityRulesQueryKey] }),
      ]);
    },
  });
};

export const useUpdateDataModelFields = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateDataModelFieldsRequest }) => updateDataModelFields(id, request),
    onSuccess: (detail) => {
      queryClient.setQueryData([dataModelsQueryKey, detail.model.id], detail);
      return Promise.all([
        invalidateDataModels(queryClient),
        invalidateStandardDictionaries(queryClient),
        invalidateTaskModelRelations(queryClient),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, detail.model.id, 'physical-table'] }),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, detail.model.id, 'data-preview'] }),
        queryClient.invalidateQueries({ queryKey: [modelQualityRulesQueryKey] }),
      ]);
    },
  });
};

export const useCreatePhysicalTableChangePlan = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: CreatePhysicalTableChangePlanRequest }) => (
      createPhysicalTableChangePlan(id, request)
    ),
    onSuccess: async (change) => {
      queryClient.setQueryData([dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey, change.id], change);
      await queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey] });
    },
  });
};

export const useCancelPhysicalTableChangePlan = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ modelId, planId }: { modelId: string; planId: string }) => cancelPhysicalTableChangePlan(modelId, planId),
    onSuccess: async (change) => {
      queryClient.setQueryData([dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey, change.id], change);
      await queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey] });
    },
  });
};

export const useExecutePhysicalTableChangePlan = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      modelId,
      planId,
      request,
    }: {
      modelId: string;
      planId: string;
      request: ExecutePhysicalTableChangePlanRequest;
    }) => executePhysicalTableChangePlan(modelId, planId, request),
    onSuccess: async (change) => {
      queryClient.setQueryData([dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey, change.id], change);
      await Promise.all([
        invalidateDataModels(queryClient),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId] }),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId, physicalTableChangePlansQueryKey] }),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId, 'physical-table'] }),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, change.modelId, 'data-preview'] }),
        queryClient.invalidateQueries({ queryKey: [modelQualityRulesQueryKey] }),
        invalidateTaskModelRelations(queryClient),
        invalidateStandardDictionaries(queryClient),
      ]);
    },
  });
};

export const useDataModelCommand = (command: DataModelCommand) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => executeDataModelCommand(id, command),
    onSuccess: (detail) => {
      queryClient.setQueryData([dataModelsQueryKey, detail.model.id], detail);
      return Promise.all([
        invalidateDataModels(queryClient),
        invalidateTaskModelRelations(queryClient),
        invalidateStandardDictionaries(queryClient),
        queryClient.invalidateQueries({ queryKey: [modelQualityRulesQueryKey] }),
      ]);
    },
  });
};

export const useCreatePhysicalTable = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createPhysicalTable,
    onSuccess: async (inspection, id) => {
      queryClient.setQueryData([dataModelsQueryKey, id, 'physical-table'], inspection);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey] }),
        queryClient.invalidateQueries({ queryKey: [dataModelsQueryKey, id, 'data-preview'] }),
      ]);
    },
  });
};

export const useDeleteDataModel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteDataModel,
    onSuccess: () => Promise.all([
      invalidateDataModels(queryClient),
      invalidateTaskModelRelations(queryClient),
      invalidateModelWarehouseLayers(queryClient),
      invalidateStandardDictionaries(queryClient),
      queryClient.invalidateQueries({ queryKey: [modelQualityRulesQueryKey] }),
    ]),
  });
};
