import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as dataServiceApi from '../api/dataServiceApi';
import type { DataServiceDetail, DataServiceRelatedModel } from '../model/dataService';
import { useDataServiceRelatedModels } from './useDataServiceRelatedModels';

const sqlService = (): DataServiceDetail => ({
  id: 'service-1',
  code: 'customer_query',
  name: '客户查询',
  directoryId: null,
  type: 'SQL_QUERY',
  definitionConfigured: true,
  definitionVersion: 1,
  engineId: 'engine-1',
  routePath: '/open-api/v1/customers',
  accessMode: 'PUBLIC',
  status: 'DRAFT',
  revision: 0,
  deploymentStatus: null,
  deploymentError: null,
  deployedAt: null,
  gatewayBindings: [],
  description: null,
  createdAt: '2026-08-05T10:00:00Z',
  updatedAt: '2026-08-05T10:00:00Z',
  standardDefinition: null,
  sqlDefinition: {
    dataSourceId: 'source-1',
    modelIds: ['model-1', 'model-2'],
    sqlText: 'select 1',
    parameters: [],
    version: 1,
  },
  scriptDefinition: null,
});

const relatedModel = (modelId: string, order: number): DataServiceRelatedModel => ({
  modelId,
  role: 'REFERENCE',
  order,
  resolved: true,
  code: `code-${order}`,
  name: `模型 ${order}`,
  status: 'PUBLISHED',
  directoryId: null,
  directoryName: null,
  warehouseLayerId: null,
  warehouseLayerCode: null,
  warehouseLayerName: null,
  storageDataSourceId: 'source-1',
  storageDataSourceCode: 'source_one',
  storageDataSourceName: '数据源一',
  catalogName: 'app',
  schemaName: 'public',
  physicalTableName: `table_${order}`,
  schemaVersion: 1,
  updatedAt: '2026-08-05T10:00:00Z',
});

const createWrapper = () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { queryClient, wrapper };
};

describe('useDataServiceRelatedModels', () => {
  afterEach(() => vi.restoreAllMocks());

  it('loads all ordered model summaries with one data-service request', async () => {
    const fetchRelatedModels = vi.spyOn(dataServiceApi, 'fetchDataServiceRelatedModels')
      .mockResolvedValue([relatedModel('model-1', 1), relatedModel('model-2', 2)]);
    const { queryClient, wrapper } = createWrapper();
    const { result } = renderHook(() => useDataServiceRelatedModels(sqlService(), true), { wrapper });

    await waitFor(() => expect(result.current.every((model) => !model.loading)).toBe(true));
    expect(fetchRelatedModels).toHaveBeenCalledTimes(1);
    expect(fetchRelatedModels).toHaveBeenCalledWith('service-1');
    expect(result.current.map((model) => model.name)).toEqual(['模型 1', '模型 2']);
    queryClient.clear();
  });

  it('keeps ordered unresolved references without requesting summaries when model view is unavailable', () => {
    const fetchRelatedModels = vi.spyOn(dataServiceApi, 'fetchDataServiceRelatedModels');
    const { queryClient, wrapper } = createWrapper();
    const { result } = renderHook(() => useDataServiceRelatedModels(sqlService(), false), { wrapper });

    expect(fetchRelatedModels).not.toHaveBeenCalled();
    expect(result.current.map(({ modelId, order, resolved }) => ({ modelId, order, resolved }))).toEqual([
      { modelId: 'model-1', order: 1, resolved: false },
      { modelId: 'model-2', order: 2, resolved: false },
    ]);
    queryClient.clear();
  });
});
