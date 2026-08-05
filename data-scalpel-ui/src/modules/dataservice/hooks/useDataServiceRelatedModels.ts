import { useQueries } from '@tanstack/react-query';
import { fetchDataModel, type DataModel } from '../../model';
import type { DataServiceDetail } from '../model/dataService';

export interface DataServiceRelatedModelView {
  modelId: string;
  role: 'PRIMARY' | 'REFERENCE';
  order: number;
  model?: DataModel;
  loading: boolean;
  error: boolean;
}

const relatedModelReferences = (dataService: DataServiceDetail | undefined) => {
  if (dataService?.standardDefinition) {
    return [{
      modelId: dataService.standardDefinition.modelId,
      role: 'PRIMARY' as const,
      order: 1,
    }];
  }
  return (dataService?.sqlDefinition?.modelIds ?? []).map((modelId, index) => ({
    modelId,
    role: 'REFERENCE' as const,
    order: index + 1,
  }));
};

export const useDataServiceRelatedModels = (
  dataService: DataServiceDetail | undefined,
  enabled: boolean,
): DataServiceRelatedModelView[] => {
  const references = relatedModelReferences(dataService);
  const queries = useQueries({
    queries: references.map(({ modelId }) => ({
      queryKey: ['data-models', modelId],
      queryFn: () => fetchDataModel(modelId),
      enabled,
      staleTime: 30_000,
    })),
  });

  return references.map((reference, index) => ({
    ...reference,
    model: queries[index]?.data?.model,
    loading: Boolean(queries[index]?.isPending && enabled),
    error: Boolean(queries[index]?.isError),
  }));
};
