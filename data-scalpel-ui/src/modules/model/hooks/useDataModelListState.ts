import type { TableProps } from 'antd';
import { Form } from 'antd';
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  findDirectoryDescendantIds,
  useDirectoryTree,
  type DirectorySelection,
} from '../../directory';
import { useDataModels } from './useDataModels';
import type { DataModel, DataModelFilters } from '../model/dataModel';
import { parseDataModelListRoute, serializeDataModelListRoute } from '../model/dataModelListRoute';
import { buildDataModelSearch } from '../model/dataModelSearch';

export const DEFAULT_DATA_MODEL_PAGE_SIZE = 20;

export const useDataModelListState = (canViewDirectories: boolean) => {
  const [routeSearchParams, setRouteSearchParams] = useSearchParams();
  const [initialRouteState] = useState(() => parseDataModelListRoute(routeSearchParams));
  const [filterForm] = Form.useForm<DataModelFilters>();
  const [advancedFilterForm] = Form.useForm<DataModelFilters>();
  const [advancedFilterOpen, setAdvancedFilterOpen] = useState(false);
  const [advancedFilters, setAdvancedFilters] = useState<DataModelFilters>({
    storageDataSourceId: initialRouteState.filters.storageDataSourceId,
    warehouseLayerId: initialRouteState.filters.warehouseLayerId,
  });
  const [filters, setFilters] = useState<DataModelFilters>(initialRouteState.filters);
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(
    initialRouteState.directorySelection,
  );
  const [page, setPage] = useState(initialRouteState.page);
  const [size, setSize] = useState(initialRouteState.size);
  const [selectedModelIds, setSelectedModelIds] = useState<string[]>([]);
  const [selectedModelsById, setSelectedModelsById] = useState<Record<string, DataModel>>({});
  const directoriesQuery = useDirectoryTree('MODEL', canViewDirectories);

  const effectiveFilters = useMemo(() => (
    typeof directorySelection === 'string'
      ? {
        ...filters,
        directoryIds: findDirectoryDescendantIds(directoriesQuery.data ?? [], directorySelection),
        uncategorized: undefined,
      }
      : filters
  ), [directorySelection, directoriesQuery.data, filters]);
  const request = useMemo(() => ({
    search: buildDataModelSearch(effectiveFilters),
    page,
    size,
    sort: '-updatedAt,code',
  }), [effectiveFilters, page, size]);
  const modelsQuery = useDataModels(request);

  const visibleModelsById = new Map(
    (modelsQuery.data?.content ?? []).map((model) => [model.id, model]),
  );
  const selectedModels = selectedModelIds
    .map((id) => visibleModelsById.get(id) ?? selectedModelsById[id])
    .filter((model): model is DataModel => Boolean(model));
  const publishableSelectedModels = selectedModels.filter((model) => model.status !== 'PUBLISHED');
  const publishedSelectedModels = selectedModels.filter((model) => model.status === 'PUBLISHED');
  const selectedIncludesExternal = selectedModels.some(
    (model) => model.physicalTableMode === 'EXTERNAL',
  );

  const syncRoute = (
    nextFilters: DataModelFilters,
    nextDirectorySelection: DirectorySelection,
    nextPage: number,
    nextSize: number,
  ) => setRouteSearchParams(
    serializeDataModelListRoute(nextFilters, nextDirectorySelection, nextPage, nextSize),
    { replace: true },
  );

  const resetSelection = () => {
    setSelectedModelIds([]);
    setSelectedModelsById({});
  };

  const replaceSelection = (models: DataModel[]) => {
    setSelectedModelIds(models.map((model) => model.id));
    setSelectedModelsById(Object.fromEntries(models.map((model) => [model.id, model])));
  };

  const search = (
    nextFilters: DataModelFilters,
    nextDirectorySelection = directorySelection,
  ) => {
    setFilters(nextFilters);
    setPage(0);
    resetSelection();
    syncRoute(nextFilters, nextDirectorySelection, 0, size);
  };

  const reset = () => {
    filterForm.resetFields();
    filterForm.setFieldsValue({
      keyword: undefined,
      status: undefined,
      storageDataSourceId: undefined,
      warehouseLayerId: undefined,
    });
    advancedFilterForm.resetFields();
    advancedFilterForm.setFieldsValue({
      storageDataSourceId: undefined,
      warehouseLayerId: undefined,
    });
    setAdvancedFilters({});
    setAdvancedFilterOpen(false);
    setDirectorySelection(undefined);
    search({}, undefined);
  };

  const applyDirectFilters = (values: DataModelFilters) => {
    const advancedValues = advancedFilterForm.getFieldsValue();
    const nextAdvancedFilters = {
      storageDataSourceId: advancedValues.storageDataSourceId,
      warehouseLayerId: advancedValues.warehouseLayerId,
    };
    setAdvancedFilters(nextAdvancedFilters);
    search({
      ...filters,
      keyword: values.keyword,
      status: values.status,
      ...nextAdvancedFilters,
    });
  };

  const confirmAdvancedFilters = () => {
    const values = advancedFilterForm.getFieldsValue();
    setAdvancedFilters({
      storageDataSourceId: values.storageDataSourceId,
      warehouseLayerId: values.warehouseLayerId,
    });
    setAdvancedFilterOpen(false);
  };

  const clearAdvancedFilters = () => {
    advancedFilterForm.setFieldsValue({
      storageDataSourceId: undefined,
      warehouseLayerId: undefined,
    });
  };

  const selectDirectory = (selection: DirectorySelection) => {
    setDirectorySelection(selection);
    if (selection === undefined) {
      search({ ...filters, directoryIds: undefined, uncategorized: undefined }, selection);
    } else if (selection === null) {
      search({ ...filters, directoryIds: undefined, uncategorized: true }, selection);
    } else {
      search({
        ...filters,
        directoryIds: findDirectoryDescendantIds(directoriesQuery.data ?? [], selection),
        uncategorized: undefined,
      }, selection);
    }
  };

  const updateSelection: NonNullable<
    NonNullable<TableProps<DataModel>['rowSelection']>['onChange']
  > = (keys, rows) => {
    const modelIds = keys.map(String);
    const retainedIds = new Set(modelIds);
    setSelectedModelIds(modelIds);
    setSelectedModelsById((current) => {
      const next = Object.fromEntries(
        Object.entries(current).filter(([id]) => retainedIds.has(id)),
      ) as Record<string, DataModel>;
      rows.forEach((model) => {
        next[model.id] = model;
      });
      return next;
    });
  };

  const changePage = (nextPage: number, nextSize: number) => {
    setPage(nextPage);
    setSize(nextSize);
    syncRoute(filters, directorySelection, nextPage, nextSize);
  };

  return {
    initialRouteState,
    filterForm,
    advancedFilterForm,
    advancedFilterOpen,
    setAdvancedFilterOpen,
    advancedFilters,
    filters,
    directorySelection,
    page,
    size,
    selectedModelIds,
    selectedModels,
    publishableSelectedModels,
    publishedSelectedModels,
    selectedIncludesExternal,
    directoriesQuery,
    modelsQuery,
    reset,
    applyDirectFilters,
    confirmAdvancedFilters,
    clearAdvancedFilters,
    selectDirectory,
    updateSelection,
    changePage,
    replaceSelection,
  };
};

export type DataModelListState = ReturnType<typeof useDataModelListState>;
