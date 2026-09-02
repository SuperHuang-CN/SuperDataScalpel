import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  DatabaseOutlined,
  DeleteOutlined,
  DownOutlined,
  DownloadOutlined,
  EditOutlined,
  FileExcelOutlined,
  FileTextOutlined,
  LoadingOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  TableOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Dropdown, Form, Modal, Select, Space, Table, Tooltip, Typography, message } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { ManagementCode, ManagementDateTime, ManagementListCell, ManagementStatusIndicator, type ManagementStatusTone } from '../../../shared/components/ManagementListCells';
import { ManagementAdaptiveMoreFilters, ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { useDataSources } from '../../datasource';
import {
  DirectoryTreePanel,
  findDirectoryDescendantIds,
  useDirectoryTree,
  type DirectorySelection,
} from '../../directory';
import { useCurrentUser } from '../../system';
import { DataModelDrawer } from '../components/DataModelDrawer';
import { FileDatasetModelImportDrawer } from '../components/FileDatasetModelImportDrawer';
import { DataModelPhysicalStatisticsCell } from '../components/DataModelPhysicalStatisticsCell';
import { DataModelPublishConfirmationContent } from '../components/DataModelPublishConfirmationContent';
import { DataModelReferenceModalContent } from '../components/DataModelReferenceModalContent';
import { ManagedTableModelImportDrawer } from '../components/ManagedTableModelImportDrawer';
import { ModelMetadataImportDrawer } from '../components/ModelMetadataImportDrawer';
import { ModelWarehouseLayerIcon } from '../components/ModelWarehouseLayerIcon';
import {
  useBatchPublishDataModels,
  useDataModelCommand,
  useDataModels,
  useDataModelReferences,
  useDeleteDataModel,
  useExportModelMetadata,
  useModelWarehouseLayers,
  useRefreshDataModelPhysicalStatistics,
} from '../hooks/useDataModels';
import {
  dataModelStatusLabels,
  type DataModel,
  type DataModelFilters,
  type DataModelPhysicalStatistics,
  type DataModelStatus,
} from '../model/dataModel';
import { buildDataModelSearch } from '../model/dataModelSearch';
import { parseDataModelListRoute, serializeDataModelListRoute } from '../model/dataModelListRoute';

const DEFAULT_PAGE_SIZE = 20;
const MAX_STATISTICS_REFRESH_COUNT = 20;
const STATISTICS_REFRESH_CONCURRENCY = 3;
const MAX_BATCH_PUBLISH_COUNT = 50;

const jdbcDataSourceRequest = {
  page: 0,
  size: 500,
  sort: 'code',
} as const;

const statusColor: Record<DataModelStatus, ManagementStatusTone> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  DISABLED: 'warning',
};

export const DataModelPage = () => {
  const navigate = useNavigate();
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
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(initialRouteState.directorySelection);
  const [page, setPage] = useState(initialRouteState.page);
  const [size, setSize] = useState(initialRouteState.size);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [importDrawerOpen, setImportDrawerOpen] = useState(false);
  const [fileDatasetImportDrawerOpen, setFileDatasetImportDrawerOpen] = useState(false);
  const [metadataImportDrawerOpen, setMetadataImportDrawerOpen] = useState(false);
  const [selectedModelIds, setSelectedModelIds] = useState<string[]>([]);
  const [selectedModelsById, setSelectedModelsById] = useState<Record<string, DataModel>>({});
  const [refreshingModelIds, setRefreshingModelIds] = useState<Set<string>>(() => new Set());
  const [batchRefreshingStatistics, setBatchRefreshingStatistics] = useState(false);
  const [publishingModelIds, setPublishingModelIds] = useState<Set<string>>(() => new Set());
  const [editingModel, setEditingModel] = useState<DataModel | null>(null);
  const [referenceModel, setReferenceModel] = useState<DataModel | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewDirectories = permissions.has('directory.view');
  const canManageDirectories = permissions.has('directory.manage');
  const canCreate = permissions.has('model.create');
  const canViewDataSources = permissions.has('datasource.view');
  const canReadDataSourceMetadata = permissions.has('datasource.metadata');
  const canViewFileDatasets = permissions.has('filedataset.view');
  const canUpdate = permissions.has('model.update');
  const canDelete = permissions.has('model.delete');
  const canPublish = permissions.has('model.publish');
  const directoriesQuery = useDirectoryTree('MODEL', canViewDirectories);
  const dataSourcesQuery = useDataSources(jdbcDataSourceRequest);
  const warehouseLayersQuery = useModelWarehouseLayers({
    page: 0,
    size: 500,
    sort: 'sortOrder,code',
  });
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
  const deleteMutation = useDeleteDataModel();
  const exportMutation = useExportModelMetadata();
  const refreshStatisticsMutation = useRefreshDataModelPhysicalStatistics();
  const publishMutation = useDataModelCommand('publish');
  const disableMutation = useDataModelCommand('disable');
  const batchPublishMutation = useBatchPublishDataModels();
  const referencesQuery = useDataModelReferences(referenceModel?.id, Boolean(referenceModel));
  const dataSourceOptions = dataSourcesQuery.data?.content
    .filter((source) => source.connection.kind === 'JDBC')
    .map((source) => ({ value: source.id, label: source.name })) ?? [];
  const warehouseLayerOptions = warehouseLayersQuery.data?.content.map((layer) => ({
    value: layer.id,
    label: `${layer.code} · ${layer.name}${layer.enabled ? '' : '（已停用）'}`,
  })) ?? [];
  const advancedFilterCount = Number(Boolean(advancedFilters.storageDataSourceId)) + Number(Boolean(advancedFilters.warehouseLayerId));
  const visibleModelsById = new Map((modelsQuery.data?.content ?? []).map((model) => [model.id, model]));
  const selectedModels = selectedModelIds
    .map((id) => visibleModelsById.get(id) ?? selectedModelsById[id])
    .filter((model): model is DataModel => Boolean(model));
  const publishableSelectedModels = selectedModels.filter((model) => model.status !== 'PUBLISHED');
  const publishedSelectedModels = selectedModels.filter((model) => model.status === 'PUBLISHED');
  const selectedIncludesExternal = selectedModels.some((model) => model.physicalTableMode === 'EXTERNAL');
  const createMenuItems: MenuProps['items'] = [
    {
      key: 'manual',
      icon: <PlusOutlined />,
      label: (
        <div className="model-create-menu-label">
          <Typography.Text strong>手动创建</Typography.Text>
          <Typography.Text type="secondary">从空白模型开始配置</Typography.Text>
        </div>
      ),
      onClick: () => setCreateDrawerOpen(true),
    },
  ];
  if (canViewDataSources && canReadDataSourceMetadata) {
    createMenuItems.push({
      key: 'jdbc',
      icon: <DatabaseOutlined />,
      label: (
        <div className="model-create-menu-label">
          <Typography.Text strong>从数据源表创建</Typography.Text>
          <Typography.Text type="secondary">复制 JDBC 表结构，不导入数据</Typography.Text>
        </div>
      ),
      onClick: () => setImportDrawerOpen(true),
    });
  }
  if (canViewDataSources && canViewFileDatasets) {
    createMenuItems.push({
      key: 'file-dataset',
      icon: <FileTextOutlined />,
      label: (
        <div className="model-create-menu-label">
          <Typography.Text strong>从文件数据集创建</Typography.Text>
          <Typography.Text type="secondary">复制已解析逻辑表 Schema</Typography.Text>
        </div>
      ),
      onClick: () => setFileDatasetImportDrawerOpen(true),
    });
  }
  if (canViewDataSources) {
    createMenuItems.push(
      { type: 'divider' },
      {
        key: 'excel',
        icon: <FileExcelOutlined />,
        label: (
          <div className="model-create-menu-label">
            <Typography.Text strong>从 Excel 模板导入</Typography.Text>
            <Typography.Text type="secondary">按照固定模板批量创建模型</Typography.Text>
          </div>
        ),
        onClick: () => setMetadataImportDrawerOpen(true),
      },
    );
  }
  const syncRoute = (
    nextFilters: DataModelFilters,
    nextDirectorySelection: DirectorySelection,
    nextPage: number,
    nextSize: number,
  ) => setRouteSearchParams(
    serializeDataModelListRoute(nextFilters, nextDirectorySelection, nextPage, nextSize),
    { replace: true },
  );

  const openDetail = (model: DataModel, tab: 'basic' | 'fields' = 'basic') => {
    navigate(`/model/${model.id}?tab=${tab}`, { state: { fromModelList: true } });
  };

  const search = (nextFilters: DataModelFilters, nextDirectorySelection = directorySelection) => {
    setFilters(nextFilters);
    setPage(0);
    setSelectedModelIds([]);
    setSelectedModelsById({});
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
    advancedFilterForm.setFieldsValue({ storageDataSourceId: undefined, warehouseLayerId: undefined });
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
    setAdvancedFilters({ storageDataSourceId: values.storageDataSourceId, warehouseLayerId: values.warehouseLayerId });
    setAdvancedFilterOpen(false);
  };
  const clearAdvancedFilters = () => {
    advancedFilterForm.setFieldsValue({ storageDataSourceId: undefined, warehouseLayerId: undefined });
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

  const executeCommand = async (model: DataModel, command: 'publish' | 'disable') => {
    try {
      const mutation = command === 'publish' ? publishMutation : disableMutation;
      await mutation.mutateAsync(model.id);
      const successMessage = command === 'publish'
        ? '模型已发布，物理表已就绪'
        : '模型已停用';
      messageApi.success(successMessage);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '模型状态操作失败');
    }
  };

  const transition = (model: DataModel) => {
    if (publishingModelIds.has(model.id)) return;
    if (model.status !== 'PUBLISHED') {
      modalApi.confirm({
        rootClassName: 'business-overlay business-modal-overlay',
        title: '发布模型',
        content: <DataModelPublishConfirmationContent model={model} />,
        okText: '发布',
        cancelText: '取消',
        onOk: () => executeCommand(model, 'publish'),
      });
      return;
    }
    void executeCommand(model, 'disable');
  };

  const remove = (model: DataModel) => {
    setReferenceModel(model);
  };

  const confirmRemove = async (model: DataModel) => {
    try {
      await deleteMutation.mutateAsync(model.id);
      messageApi.success('模型已删除');
      setReferenceModel(null);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除模型失败');
      throw error;
    }
  };

  const exportModels = async (modelIds: string[]) => {
    try {
      const blob = await exportMutation.mutateAsync(modelIds);
      downloadBlob(blob, `DataScalpel-模型元数据-${new Date().toISOString().slice(0, 10)}.xlsx`);
      messageApi.success('模型元数据导出已开始');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '导出模型元数据失败');
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

  const markModelsPublishing = (modelIds: string[], publishing: boolean) => {
    setPublishingModelIds((current) => {
      const next = new Set(current);
      modelIds.forEach((modelId) => {
        if (publishing) next.add(modelId);
        else next.delete(modelId);
      });
      return next;
    });
  };

  const batchPublish = async (models: DataModel[], skippedModels: DataModel[]) => {
    const modelIds = models.map((model) => model.id);
    markModelsPublishing(modelIds, true);
    try {
      const results = await batchPublishMutation.mutateAsync(
        models.map((model) => ({ id: model.id, name: model.name })),
      );
      const failures = results.filter((result) => !result.detail);
      const successCount = results.length - failures.length;
      const failedIds = new Set(failures.map((failure) => failure.id));
      setSelectedModelIds(failures.map((failure) => failure.id));
      setSelectedModelsById(Object.fromEntries(
        models.filter((model) => failedIds.has(model.id)).map((model) => [model.id, model]),
      ));

      if (failures.length === 0) {
        const skippedText = skippedModels.length > 0 ? `，跳过已发布模型 ${skippedModels.length} 个` : '';
        messageApi.success(`批量发布完成：成功 ${successCount} 个${skippedText}`);
        return;
      }

      const allFailed = successCount === 0;
      const failureRows = failures.map((failure) => ({
        id: failure.id,
        name: failure.name,
        reason: failure.error instanceof ApiError
          ? failure.error.message
          : failure.error instanceof Error ? failure.error.message : '发布失败',
      }));
      const resultModal = {
        rootClassName: 'business-overlay business-modal-overlay',
        title: allFailed ? '批量发布失败' : '批量发布部分成功',
        width: 760,
        okText: '关闭',
        content: (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Typography.Text>
              成功 {successCount} 个，失败 {failures.length} 个，跳过已发布模型 {skippedModels.length} 个。
              已成功发布的模型不会回滚，失败项已保留选中。
            </Typography.Text>
            <Table
              size="small"
              rowKey="id"
              pagination={false}
              scroll={{ y: 320 }}
              dataSource={failureRows}
              columns={[
                { title: '模型', dataIndex: 'name', width: 220 },
                { title: '失败原因', dataIndex: 'reason', ellipsis: true },
              ]}
            />
          </Space>
        ),
      };
      if (allFailed) modalApi.error(resultModal);
      else modalApi.warning(resultModal);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '批量发布请求失败');
    } finally {
      markModelsPublishing(modelIds, false);
    }
  };

  const confirmBatchPublish = () => {
    if (selectedModelIds.length === 0 || selectedModelIds.length > MAX_BATCH_PUBLISH_COUNT) return;
    if (publishableSelectedModels.length === 0) {
      messageApi.info('所选模型均已发布，无需重复发布');
      return;
    }
    const models = [...publishableSelectedModels];
    const skippedModels = [...publishedSelectedModels];
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '批量发布模型',
      width: 620,
      okText: `发布 ${models.length} 个模型`,
      cancelText: '取消',
      content: (
        <Alert
          type="warning"
          showIcon
          message={`将发布 ${models.length} 个模型${skippedModels.length ? `，跳过已发布模型 ${skippedModels.length} 个` : ''}`}
          description="发布会实时检查物理表；缺失的受管物理表将自动创建，外部表只校验、不创建。各模型独立执行，允许部分成功。"
        />
      ),
      onOk: () => batchPublish(models, skippedModels),
    });
  };

  const markStatisticsRefreshing = (modelIds: string[], refreshing: boolean) => {
    setRefreshingModelIds((current) => {
      const next = new Set(current);
      modelIds.forEach((modelId) => {
        if (refreshing) next.add(modelId);
        else next.delete(modelId);
      });
      return next;
    });
  };

  const showStatisticsRefreshResult = (statistics: DataModelPhysicalStatistics) => {
    const detail = statistics.message ? `：${statistics.message}` : '';
    switch (statistics.lastRefreshStatus) {
      case 'SUCCESS':
        messageApi.success('数据统计刷新成功');
        break;
      case 'PARTIAL':
        messageApi.warning(`仅获取到部分数据统计${detail}`);
        break;
      case 'NOT_FOUND':
        messageApi.warning(`物理表未创建或不存在${detail}`);
        break;
      case 'UNSUPPORTED':
        messageApi.warning(`当前数据库或物理对象无法提供统计${detail}`);
        break;
      case 'FAILED':
        messageApi.error(`数据统计刷新失败${detail}`);
        break;
    }
  };

  const refreshModelStatistics = async (model: DataModel) => {
    if (refreshingModelIds.has(model.id)) return;
    markStatisticsRefreshing([model.id], true);
    try {
      const statistics = await refreshStatisticsMutation.mutateAsync(model.id);
      await modelsQuery.refetch();
      showStatisticsRefreshResult(statistics);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '数据统计刷新失败');
    } finally {
      markStatisticsRefreshing([model.id], false);
    }
  };

  const refreshSelectedStatistics = async () => {
    if (selectedModelIds.length === 0) return;
    if (selectedModelIds.length > MAX_STATISTICS_REFRESH_COUNT) {
      messageApi.warning(`一次最多刷新 ${MAX_STATISTICS_REFRESH_COUNT} 个模型的数据统计`);
      return;
    }

    const modelIds = [...selectedModelIds];
    const results: Array<DataModelPhysicalStatistics | Error | undefined> = new Array(modelIds.length);
    let nextIndex = 0;
    setBatchRefreshingStatistics(true);
    markStatisticsRefreshing(modelIds, true);
    try {
      const workers = Array.from(
        { length: Math.min(STATISTICS_REFRESH_CONCURRENCY, modelIds.length) },
        async () => {
          while (nextIndex < modelIds.length) {
            const index = nextIndex;
            nextIndex += 1;
            try {
              results[index] = await refreshStatisticsMutation.mutateAsync(modelIds[index]);
            } catch (error) {
              results[index] = error instanceof Error ? error : new Error('数据统计刷新失败');
            }
          }
        },
      );
      await Promise.all(workers);
      await modelsQuery.refetch();

      const successCount = results.filter((result) => (
        result && !(result instanceof Error) && result.lastRefreshStatus === 'SUCCESS'
      )).length;
      const partialCount = results.filter((result) => (
        result && !(result instanceof Error) && result.lastRefreshStatus === 'PARTIAL'
      )).length;
      const unavailableCount = modelIds.length - successCount - partialCount;
      if (successCount === modelIds.length) {
        messageApi.success(`已刷新 ${successCount} 个模型的数据统计`);
      } else if (successCount + partialCount > 0) {
        messageApi.warning(
          `统计刷新完成：成功 ${successCount} 个，部分获取 ${partialCount} 个，未获取 ${unavailableCount} 个`,
        );
      } else {
        messageApi.error(`所选 ${modelIds.length} 个模型均未能获取数据统计`);
      }
    } finally {
      markStatisticsRefreshing(modelIds, false);
      setBatchRefreshingStatistics(false);
    }
  };

  const lifecycleIcon = (status: DataModelStatus) => {
    return status === 'PUBLISHED' ? <PauseCircleOutlined /> : <SendOutlined />;
  };

  const lifecycleLabel = (status: DataModelStatus) => {
    return status === 'PUBLISHED' ? '停用' : '发布';
  };

  const lifecycleLoading = (model: DataModel) => (
    publishingModelIds.has(model.id)
    || (model.status === 'DRAFT' && publishMutation.isPending && publishMutation.variables === model.id)
    || (model.status === 'PUBLISHED' && disableMutation.isPending && disableMutation.variables === model.id)
    || (model.status === 'DISABLED' && publishMutation.isPending && publishMutation.variables === model.id)
  );

  const columns: TableProps<DataModel>['columns'] = [
    {
      title: '模型',
      dataIndex: 'name',
      width: 245,
      render: (value: string, model: DataModel) => (
        <ManagementListCell
          icon={model.warehouseLayer
            ? <ModelWarehouseLayerIcon code={model.warehouseLayer.code} color={model.warehouseLayer.color} />
            : <TableOutlined />}
          iconLabel={model.warehouseLayer
            ? `${model.name}所属数仓分层：${model.warehouseLayer.code}`
            : `${model.name}尚未设置数仓分层`}
          iconTone="slate"
          primary={<Button type="link" size="small" className="data-model-name-button" onClick={() => openDetail(model)}>{value}</Button>}
          secondary={<><ManagementCode value={model.code} /> {model.description || ''}</>}
        />
      ),
    },
    {
      title: '状态', width: 100,
      render: (_value, model) => (
        <ManagementStatusIndicator
          label={dataModelStatusLabels[model.status]}
          tone={statusColor[model.status]}
        />
      ),
    },
    {
      title: '存储位置', width: 310,
      render: (_value, model) => <ManagementListCell primary={model.storageDataSourceName} secondary={<ManagementCode value={model.physicalTableName} />} />,
    },
    {
      title: '模式 / 版本', width: 120,
      render: (_value, model) => <ManagementListCell primary={model.physicalTableMode === 'MANAGED' ? '托管表' : '外部表'} secondary={`Schema v${model.schemaVersion}`} />,
    },
    {
      title: '数据统计',
      width: 180,
      align: 'right',
      render: (_value, model) => (
        <DataModelPhysicalStatisticsCell statistics={model.physicalStatistics} />
      ),
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 160, render: (value: string) => <ManagementDateTime value={value} /> },
    {
      title: '操作',
      key: 'actions',
      width: 112,
      render: (_value, model) => (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
          {canUpdate && (
            <Tooltip title={model.status === 'DRAFT' ? '字段管理' : '查看字段'}>
              <Button
                type="text"
                size="small"
                icon={<TableOutlined />}
                aria-label={`${model.status === 'DRAFT' ? '管理' : '查看'}${model.name}字段`}
                disabled={refreshingModelIds.has(model.id)}
                onClick={() => openDetail(model, 'fields')}
              />
            </Tooltip>
          )}
          {canUpdate && (
            <Tooltip title="修改模型">
              <Button
                type="text"
                size="small"
                icon={<EditOutlined />}
                aria-label={`修改${model.name}`}
                disabled={refreshingModelIds.has(model.id)}
                onClick={() => setEditingModel(model)}
              />
            </Tooltip>
          )}
          </div>
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'refresh-statistics',
                  label: refreshingModelIds.has(model.id) ? '刷新统计中…' : '刷新统计',
                  icon: <ReloadOutlined />,
                  disabled: refreshingModelIds.has(model.id),
                },
                ...(canUpdate ? [{ key: 'fields', label: model.status === 'DRAFT' ? '字段管理' : '查看字段', icon: <TableOutlined /> }, { key: 'edit', label: '修改模型', icon: <EditOutlined /> }] : []),
                ...(canPublish ? [{
                  key: 'lifecycle',
                  label: lifecycleLoading(model) ? `${lifecycleLabel(model.status)}中…` : lifecycleLabel(model.status),
                  icon: lifecycleLoading(model) ? <LoadingOutlined spin /> : lifecycleIcon(model.status),
                  disabled: lifecycleLoading(model),
                }] : []),
                ...(model.physicalTableMode === 'MANAGED'
                  ? [{ key: 'export', label: '导出 Excel 结构', icon: <DownloadOutlined /> }]
                  : []),
                ...(canDelete ? [{ key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true }] : []),
              ],
              onClick: ({ key }) => {
                if (key === 'refresh-statistics') void refreshModelStatistics(model);
                if (key === 'fields') openDetail(model, 'fields');
                if (key === 'edit') setEditingModel(model);
                if (key === 'lifecycle') void transition(model);
                if (key === 'export') void exportModels([model.id]);
                if (key === 'delete') remove(model);
              },
            }}
          >
            <Tooltip title="更多操作">
              <Button
                className="management-row-actions-more"
                type="text"
                size="small"
                icon={lifecycleLoading(model) ? <LoadingOutlined spin /> : <MoreOutlined />}
                aria-label={`${model.name}的更多操作`}
                loading={refreshingModelIds.has(model.id)}
              />
            </Tooltip>
          </Dropdown>
        </div>
      ),
    },
  ];

  return (
    <>
      {messageContext}
      {modalContext}
      <Modal
        rootClassName="business-overlay business-modal-overlay"
        open={Boolean(referenceModel)}
        title={referenceModel ? `删除模型：${referenceModel.name}` : '删除模型'}
        width={760}
        okText="确认删除"
        okButtonProps={{
          danger: true,
          disabled: referencesQuery.isPending || referencesQuery.isError || !referencesQuery.data?.deletable,
          loading: deleteMutation.isPending,
        }}
        cancelText="取消"
        onCancel={() => setReferenceModel(null)}
        onOk={() => referenceModel && confirmRemove(referenceModel)}
      >
        {referencesQuery.data?.deletable && referenceModel && (
          <Alert
            type="warning"
            showIcon
            message={`确认删除“${referenceModel.name}”吗？`}
            description="只删除模型元数据，不操作物理表。"
          />
        )}
        {referencesQuery.isPending && <Typography.Text>正在检查模型引用…</Typography.Text>}
        {referencesQuery.isError && (
          <Alert
            type="error"
            showIcon
            message="模型引用检查失败"
            description={referencesQuery.error instanceof ApiError ? referencesQuery.error.message : '请稍后重试。'}
            action={<Button size="small" onClick={() => void referencesQuery.refetch()}>重试</Button>}
          />
        )}
        {referencesQuery.data && <DataModelReferenceModalContent references={referencesQuery.data} />}
      </Modal>
      <div className={canViewDirectories ? 'directory-management-layout' : 'page-stack'}>
        {canViewDirectories && (
          <DirectoryTreePanel
            scope="MODEL"
            tree={directoriesQuery.data ?? []}
            loading={directoriesQuery.isFetching}
            selection={directorySelection}
            canManage={canManageDirectories}
            onSelectionChange={selectDirectory}
          />
        )}
        <section className="management-workbench">
          <div className="management-filter-strip">
            <Form<DataModelFilters> autoComplete="off"
              form={filterForm}
              layout="inline"
              className="management-filter-form"
              initialValues={initialRouteState.filters}
              onFinish={applyDirectFilters}
            >
              <Form.Item name="keyword">
                <ManagementSearchInput allowClear placeholder="搜索模型名称或编码" className="data-model-keyword-input" />
              </Form.Item>
              <Form.Item name="status">
                <Select
                  allowClear
                  placeholder="全部状态"
                  className="data-model-status-select"
                  options={(Object.entries(dataModelStatusLabels) as [DataModelStatus, string][])
                    .map(([value, label]) => ({ value, label }))}
                />
              </Form.Item>
            </Form>
              <ManagementAdaptiveMoreFilters
                count={advancedFilterCount}
                open={advancedFilterOpen}
                onOpenChange={(open) => {
                  setAdvancedFilterOpen(open);
                  if (open) {
                    advancedFilterForm.resetFields();
                    advancedFilterForm.setFieldsValue({ storageDataSourceId: advancedFilters.storageDataSourceId, warehouseLayerId: advancedFilters.warehouseLayerId });
                  }
                }}
                onClear={clearAdvancedFilters}
                onCancel={() => {
                  advancedFilterForm.setFieldsValue({ storageDataSourceId: advancedFilters.storageDataSourceId, warehouseLayerId: advancedFilters.warehouseLayerId });
                  setAdvancedFilterOpen(false);
                }}
                onConfirm={confirmAdvancedFilters}
              >
                    <Form<DataModelFilters> form={advancedFilterForm} layout="vertical" autoComplete="off" initialValues={advancedFilters}>
                    <Form.Item name="storageDataSourceId" label="JDBC 数据源">
                      <Select
                        allowClear
                        showSearch
                        optionFilterProp="label"
                        placeholder="全部 JDBC 数据源"
                        loading={dataSourcesQuery.isFetching}
                        options={dataSourceOptions}
                        className="advanced-filter-select management-inline-filter-wide"
                      />
                    </Form.Item>
                    <Form.Item name="warehouseLayerId" label="数仓分层">
                      <Select
                        allowClear
                        showSearch
                        optionFilterProp="label"
                        placeholder="全部分层"
                        loading={warehouseLayersQuery.isFetching}
                        options={warehouseLayerOptions}
                        className="advanced-filter-select"
                      />
                    </Form.Item>
                    </Form>
              </ManagementAdaptiveMoreFilters>
            <ManagementFilterActions form={filterForm} appliedFilters={filters} additionalActive={advancedFilterCount > 0 || directorySelection !== undefined} loading={modelsQuery.isFetching} onReset={reset} />
          </div>
          <div className="management-results-surface">
            <div className="management-result-toolbar">
            <div className="management-result-title">模型列表 <span className="management-result-count">共 {modelsQuery.data?.totalElements ?? 0} 项</span></div>
            <Space size={4} className="management-result-actions">
              <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新模型列表" onClick={() => void modelsQuery.refetch()} /></Tooltip>
              {canPublish && (
                <Tooltip title={
                  selectedModelIds.length === 0
                    ? '请先选择模型'
                    : selectedModelIds.length > MAX_BATCH_PUBLISH_COUNT
                      ? `一次最多批量发布 ${MAX_BATCH_PUBLISH_COUNT} 个模型`
                      : publishableSelectedModels.length === 0
                        ? '所选模型均已发布'
                        : batchPublishMutation.isPending ? '正在批量发布模型' : '发布草稿和已停用模型'
                }>
                  <span>
                    <Button
                      icon={<SendOutlined />}
                      loading={batchPublishMutation.isPending}
                      disabled={
                        selectedModelIds.length === 0
                        || selectedModelIds.length > MAX_BATCH_PUBLISH_COUNT
                        || publishableSelectedModels.length === 0
                        || batchPublishMutation.isPending
                      }
                      onClick={confirmBatchPublish}
                    >
                      批量发布{selectedModelIds.length ? `（${selectedModelIds.length}）` : ''}
                    </Button>
                  </span>
                </Tooltip>
              )}
              <Tooltip title={
                selectedModelIds.length === 0
                  ? '请先选择模型'
                  : selectedModelIds.length > MAX_STATISTICS_REFRESH_COUNT
                    ? `一次最多刷新 ${MAX_STATISTICS_REFRESH_COUNT} 个模型`
                    : refreshingModelIds.size > 0 ? '数据统计正在刷新' : '从目标数据库快速刷新统计快照'
              }>
                <span>
                  <Button
                    icon={<ReloadOutlined />}
                    loading={batchRefreshingStatistics}
                    disabled={
                      selectedModelIds.length === 0
                      || selectedModelIds.length > MAX_STATISTICS_REFRESH_COUNT
                      || refreshingModelIds.size > 0
                    }
                    onClick={() => void refreshSelectedStatistics()}
                  >
                    刷新统计{selectedModelIds.length ? `（${selectedModelIds.length}）` : ''}
                  </Button>
                </span>
              </Tooltip>
              <Tooltip title={
                selectedIncludesExternal
                  ? '只能导出受管模型，当前选择中包含外部模型'
                  : selectedModelIds.length === 0 ? '请先选择受管模型' : '导出所选模型结构'
              }>
                <span>
                  <Button
                    icon={<DownloadOutlined />}
                    loading={exportMutation.isPending}
                    disabled={selectedModelIds.length === 0 || selectedIncludesExternal}
                    onClick={() => void exportModels(selectedModelIds)}
                  >
                    导出结构{selectedModelIds.length ? `（${selectedModelIds.length}）` : ''}
                  </Button>
                </span>
              </Tooltip>
              {canCreate && (
                <Dropdown menu={{ items: createMenuItems }} trigger={['click']} placement="bottomRight">
                  <Button type="primary" icon={<PlusOutlined />}>
                    新建模型 <DownOutlined />
                  </Button>
                </Dropdown>
              )}
            </Space>
            </div>
            <Table<DataModel>
            size="small"
            className="management-table"
            rowKey="id"
            columns={columns}
            dataSource={modelsQuery.data?.content ?? []}
            loading={modelsQuery.isFetching && refreshingModelIds.size === 0}
            rowSelection={{
              preserveSelectedRowKeys: true,
              selectedRowKeys: selectedModelIds,
              onChange: updateSelection,
            }}
            scroll={{ y: '100%' }}
            pagination={{
              current: page + 1,
              pageSize: size,
              total: modelsQuery.data?.totalElements ?? 0,
              size: 'small',
              placement: ['bottomEnd'],
              hideOnSinglePage: false,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 项`,
            }}
            onChange={(pagination) => {
              const nextPage = (pagination.current ?? 1) - 1;
              const nextSize = pagination.pageSize ?? DEFAULT_PAGE_SIZE;
              setPage(nextPage);
              setSize(nextSize);
              syncRoute(filters, directorySelection, nextPage, nextSize);
            }}
            />
          </div>
        </section>
      </div>
      <DataModelDrawer
        open={createDrawerOpen || Boolean(editingModel)}
        model={editingModel}
        initialDirectoryId={typeof directorySelection === 'string' ? directorySelection : undefined}
        canViewDirectories={canViewDirectories}
        onClose={() => { setCreateDrawerOpen(false); setEditingModel(null); }}
        onSaved={(savedModel, created) => created && navigate(`/model/${savedModel.id}?tab=fields`, { state: { fromModelList: true } })}
      />
      {importDrawerOpen && (
        <ManagedTableModelImportDrawer
          open
          canViewDirectories={canViewDirectories}
          initialDirectoryId={typeof directorySelection === 'string' ? directorySelection : undefined}
          initialTargetStorageDataSourceId={filters.storageDataSourceId}
          onClose={() => setImportDrawerOpen(false)}
          onAdjustFields={(modelId) => {
            setImportDrawerOpen(false);
            navigate(`/model/${modelId}?tab=fields`, { state: { fromModelList: true } });
          }}
        />
      )}
      {metadataImportDrawerOpen && (
        <ModelMetadataImportDrawer
          open
          initialTargetStorageDataSourceId={filters.storageDataSourceId}
          onClose={() => setMetadataImportDrawerOpen(false)}
          onAdjustFields={(modelId) => {
            setMetadataImportDrawerOpen(false);
            navigate(`/model/${modelId}?tab=fields`, { state: { fromModelList: true } });
          }}
        />
      )}
      {fileDatasetImportDrawerOpen && (
        <FileDatasetModelImportDrawer
          open
          canViewDirectories={canViewDirectories}
          initialDirectoryId={typeof directorySelection === 'string' ? directorySelection : undefined}
          initialTargetStorageDataSourceId={filters.storageDataSourceId}
          onClose={() => setFileDatasetImportDrawerOpen(false)}
          onViewModel={(modelId) => {
            setFileDatasetImportDrawerOpen(false);
            navigate(`/model/${modelId}?tab=basic`, { state: { fromModelList: true } });
          }}
          onAdjustFields={(modelId) => {
            setFileDatasetImportDrawerOpen(false);
            navigate(`/model/${modelId}?tab=fields`, { state: { fromModelList: true } });
          }}
        />
      )}
    </>
  );
};
