import { useQuery } from '@tanstack/react-query';
import { fetchDataServiceRelatedModels } from '../api/dataServiceApi';
import type { DataServiceDetail, DataServiceRelatedModel } from '../model/dataService';

export interface DataServiceRelatedModelView extends DataServiceRelatedModel {
  loading: boolean;
  error: boolean;
}

const relatedModelReferences = (dataService: DataServiceDetail | undefined): DataServiceRelatedModel[] => {
  const primaryModelId = dataService?.standardDefinition?.modelId ?? dataService?.spatialDefinition?.modelId;
  if (primaryModelId) {
    return [unresolvedModel(primaryModelId, 'PRIMARY', 1)];
  }
  return (dataService?.sqlDefinition?.modelIds ?? []).map((modelId, index) => (
    unresolvedModel(modelId, 'REFERENCE', index + 1)
  ));
};

const unresolvedModel = (
  modelId: string,
  role: DataServiceRelatedModel['role'],
  order: number,
): DataServiceRelatedModel => ({
  modelId,
  role,
  order,
  resolved: false,
  code: null,
  name: null,
  status: null,
  directoryId: null,
  directoryName: null,
  warehouseLayerId: null,
  warehouseLayerCode: null,
  warehouseLayerName: null,
  storageDataSourceId: null,
  storageDataSourceCode: null,
  storageDataSourceName: null,
  catalogName: null,
  schemaName: null,
  physicalTableName: null,
  schemaVersion: null,
  updatedAt: null,
});

export const useDataServiceRelatedModels = (
  dataService: DataServiceDetail | undefined,
  enabled: boolean,
): DataServiceRelatedModelView[] => {
  const references = relatedModelReferences(dataService);
  const query = useQuery({
    queryKey: ['data-services', dataService?.id, 'related-models'],
    queryFn: () => fetchDataServiceRelatedModels(dataService?.id as string),
    enabled: enabled && Boolean(dataService?.id),
    staleTime: 30_000,
  });
  const models = query.data ?? references;

  return models.map((model) => ({
    ...model,
    loading: enabled && query.isPending,
    error: enabled && query.isError,
  }));
};
