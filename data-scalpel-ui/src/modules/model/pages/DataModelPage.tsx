import {
  DatabaseOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  FileExcelOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  TableOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Button,
  Dropdown,
  Form,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { ManagementCode, ManagementDateTime, ManagementListCell, ManagementStatusIndicator, type ManagementStatusTone } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementMoreFilters, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { useDataSources } from '../../datasource';
import {
  DirectoryTreePanel,
  findDirectoryDescendantIds,
  useDirectoryTree,
  type DirectorySelection,
} from '../../directory';
import { useCurrentUser } from '../../system';
import { DataModelDrawer } from '../components/DataModelDrawer';
import { ManagedTableModelImportDrawer } from '../components/ManagedTableModelImportDrawer';
import { ModelMetadataImportDrawer } from '../components/ModelMetadataImportDrawer';
import {
  useDataModelCommand,
  useDataModels,
  useDeleteDataModel,
  useExportModelMetadata,
  useModelWarehouseLayers,
} from '../hooks/useDataModels';
import {
  dataModelStatusLabels,
  physicalLocation,
  type DataModel,
  type DataModelFilters,
  type DataModelStatus,
} from '../model/dataModel';
import { buildDataModelSearch } from '../model/dataModelSearch';
import { parseDataModelListRoute, serializeDataModelListRoute } from '../model/dataModelListRoute';

const DEFAULT_PAGE_SIZE = 20;

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
  const [metadataImportDrawerOpen, setMetadataImportDrawerOpen] = useState(false);
  const [selectedModelIds, setSelectedModelIds] = useState<string[]>([]);
  const [editingModel, setEditingModel] = useState<DataModel | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewDirectories = permissions.has('directory.view');
  const canManageDirectories = permissions.has('directory.manage');
  const canCreate = permissions.has('model.create');
  const canViewDataSources = permissions.has('datasource.view');
  const canReadDataSourceMetadata = permissions.has('datasource.metadata');
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
  const publishMutation = useDataModelCommand('publish');
  const disableMutation = useDataModelCommand('disable');
  const enableMutation = useDataModelCommand('enable');
  const dataSourceOptions = dataSourcesQuery.data?.content
    .filter((source) => source.connection.kind === 'JDBC')
    .map((source) => ({ value: source.id, label: source.name })) ?? [];
  const warehouseLayerOptions = warehouseLayersQuery.data?.content.map((layer) => ({
    value: layer.id,
    label: `${layer.code} · ${layer.name}${layer.enabled ? '' : '（已停用）'}`,
  })) ?? [];
  const advancedFilterCount = Number(Boolean(advancedFilters.storageDataSourceId)) + Number(Boolean(advancedFilters.warehouseLayerId));
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
    setAdvancedFilters({});
    setAdvancedFilterOpen(false);
    setDirectorySelection(undefined);
    search({}, undefined);
  };

  const applyDirectFilters = (values: DataModelFilters) => search({
    ...filters,
    keyword: values.keyword,
    status: values.status,
    storageDataSourceId: advancedFilters.storageDataSourceId,
    warehouseLayerId: advancedFilters.warehouseLayerId,
  });
  const confirmAdvancedFilters = () => {
    const values = advancedFilterForm.getFieldsValue();
    setAdvancedFilters({ storageDataSourceId: values.storageDataSourceId, warehouseLayerId: values.warehouseLayerId });
    setAdvancedFilterOpen(false);
  };
  const clearAdvancedFilters = () => {
    advancedFilterForm.resetFields();
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

  const executeCommand = async (model: DataModel, command: 'publish' | 'disable' | 'enable') => {
    try {
      const mutation = command === 'publish'
        ? publishMutation
        : command === 'disable' ? disableMutation : enableMutation;
      await mutation.mutateAsync(model.id);
      const successMessage = command === 'publish'
        ? '模型已发布，物理表结构校验通过'
        : command === 'disable' ? '模型已停用' : '模型已启用';
      messageApi.success(successMessage);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '模型状态操作失败');
    }
  };

  const transition = (model: DataModel) => {
    if (model.status === 'DRAFT') {
      modalApi.confirm({
        title: '发布模型',
        content: '发布前会实时检查物理表是否存在且与模型字段一致；发布后字段结构将变为只读。',
        okText: '发布',
        cancelText: '取消',
        onOk: () => executeCommand(model, 'publish'),
      });
      return;
    }
    void executeCommand(model, model.status === 'PUBLISHED' ? 'disable' : 'enable');
  };

  const remove = (model: DataModel) => {
    modalApi.confirm({
      title: '删除模型',
      content: `确认删除“${model.name}”吗？只删除模型元数据，不操作物理表。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteMutation.mutateAsync(model.id);
          messageApi.success('模型已删除');
        } catch (error) {
          messageApi.error(error instanceof ApiError ? error.message : '删除模型失败');
          throw error;
        }
      },
    });
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

  const lifecycleIcon = (status: DataModelStatus) => {
    if (status === 'DRAFT') return <SendOutlined />;
    if (status === 'PUBLISHED') return <PauseCircleOutlined />;
    return <PlayCircleOutlined />;
  };

  const lifecycleLabel = (status: DataModelStatus) => {
    if (status === 'DRAFT') return '发布';
    if (status === 'PUBLISHED') return '停用';
    return '启用';
  };

  const lifecycleLoading = (model: DataModel) => (
    (model.status === 'DRAFT' && publishMutation.isPending && publishMutation.variables === model.id)
    || (model.status === 'PUBLISHED' && disableMutation.isPending && disableMutation.variables === model.id)
    || (model.status === 'DISABLED' && enableMutation.isPending && enableMutation.variables === model.id)
  );

  const columns: TableProps<DataModel>['columns'] = [
    {
      title: '模型',
      dataIndex: 'name',
      width: 245,
      render: (value: string, model: DataModel) => (
        <ManagementListCell icon={<TableOutlined />} iconTone="violet" primary={<Button type="link" size="small" className="data-model-name-button" onClick={() => openDetail(model)}>{value}</Button>} secondary={<><ManagementCode value={model.code} /> {model.description || ''}</>} />
      ),
    },
    {
      title: '分层 / 状态', width: 180,
      render: (_value, model) => <ManagementListCell primary={model.warehouseLayer ? (
        <Tooltip title={model.warehouseLayer.enabled ? undefined : '该分层已停用，现有模型仍保留该分层'}>
          <Tag color={model.warehouseLayer.enabled ? model.warehouseLayer.color ?? undefined : 'warning'}>
            {model.warehouseLayer.code} · {model.warehouseLayer.name}
          </Tag>
        </Tooltip>
      ) : '未分层'} secondary={<ManagementStatusIndicator label={dataModelStatusLabels[model.status]} tone={statusColor[model.status]} />} />,
    },
    {
      title: '存储位置', width: 310,
      render: (_value, model) => <ManagementListCell primary={model.storageDataSourceName} secondary={<ManagementCode value={physicalLocation(model)} />} />,
    },
    {
      title: '模式 / 版本', width: 120,
      render: (_value, model) => <ManagementListCell primary={model.physicalTableMode === 'MANAGED' ? '托管表' : '外部表'} secondary={`Schema v${model.schemaVersion}`} />,
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
                onClick={() => setEditingModel(model)}
              />
            </Tooltip>
          )}
          </div>
          {(canUpdate || canPublish || model.physicalTableMode === 'MANAGED' || canDelete) && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  ...(canUpdate ? [{ key: 'fields', label: model.status === 'DRAFT' ? '字段管理' : '查看字段', icon: <TableOutlined /> }, { key: 'edit', label: '修改模型', icon: <EditOutlined /> }] : []),
                  ...(canPublish ? [{ key: 'lifecycle', label: lifecycleLabel(model.status), icon: lifecycleIcon(model.status) }] : []),
                  ...(model.physicalTableMode === 'MANAGED'
                    ? [{ key: 'export', label: '导出 Excel 结构', icon: <DownloadOutlined /> }]
                    : []),
                  ...(canDelete ? [{ key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true }] : []),
                ],
                onClick: ({ key }) => {
                  if (key === 'fields') openDetail(model, 'fields');
                  if (key === 'edit') setEditingModel(model);
                  if (key === 'lifecycle') void transition(model);
                  if (key === 'export') void exportModels([model.id]);
                  if (key === 'delete') remove(model);
                },
              }}
            >
              <Tooltip title="更多操作">
                <Button className="management-row-actions-more" type="text" size="small" icon={<MoreOutlined />} aria-label={`${model.name}的更多操作`} loading={lifecycleLoading(model)} />
              </Tooltip>
            </Dropdown>
          )}
        </div>
      ),
    },
  ];

  return (
    <>
      {messageContext}
      {modalContext}
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
              <ManagementMoreFilters
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
                onCancel={() => setAdvancedFilterOpen(false)}
                onConfirm={confirmAdvancedFilters}
              >
                    <Form<DataModelFilters> form={advancedFilterForm} layout="vertical" autoComplete="off">
                    <Form.Item name="storageDataSourceId" label="JDBC 数据源">
                      <Select
                        allowClear
                        showSearch
                        optionFilterProp="label"
                        placeholder="全部 JDBC 数据源"
                        loading={dataSourcesQuery.isFetching}
                        options={dataSourceOptions}
                        className="advanced-filter-select"
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
              </ManagementMoreFilters>
            </Form>
            <ManagementFilterActions form={filterForm} appliedFilters={filters} additionalActive={advancedFilterCount > 0 || directorySelection !== undefined} loading={modelsQuery.isFetching} onReset={reset} />
          </div>
          <div className="management-results-surface">
            <div className="management-result-toolbar">
            <div className="management-result-title">模型列表 <span className="management-result-count">共 {modelsQuery.data?.totalElements ?? 0} 项</span></div>
            <Space size={4} className="management-result-actions">
              <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新模型列表" onClick={() => void modelsQuery.refetch()} /></Tooltip>
              {canCreate && canViewDataSources && canReadDataSourceMetadata && (
                <Button icon={<DatabaseOutlined />} onClick={() => setImportDrawerOpen(true)}>
                  从数据源导入
                </Button>
              )}
              {canCreate && canViewDataSources && (
                <Button icon={<FileExcelOutlined />} onClick={() => setMetadataImportDrawerOpen(true)}>
                  导入 Excel
                </Button>
              )}
              <Button
                icon={<DownloadOutlined />}
                loading={exportMutation.isPending}
                disabled={selectedModelIds.length === 0}
                onClick={() => void exportModels(selectedModelIds)}
              >
                导出结构{selectedModelIds.length ? `（${selectedModelIds.length}）` : ''}
              </Button>
              {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}
            </Space>
            </div>
            <Table<DataModel>
            size="small"
            className="management-table"
            rowKey="id"
            columns={columns}
            dataSource={modelsQuery.data?.content ?? []}
            loading={modelsQuery.isFetching}
            rowSelection={{
              preserveSelectedRowKeys: true,
              selectedRowKeys: selectedModelIds,
              onChange: (keys) => setSelectedModelIds(keys.map(String)),
              getCheckboxProps: (model) => ({
                disabled: model.physicalTableMode === 'EXTERNAL',
                title: model.physicalTableMode === 'EXTERNAL' ? 'EXTERNAL 模型暂不支持导出' : undefined,
              }),
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
          canViewDirectories={canViewDirectories}
          initialDirectoryId={typeof directorySelection === 'string' ? directorySelection : undefined}
          initialTargetStorageDataSourceId={filters.storageDataSourceId}
          onClose={() => setMetadataImportDrawerOpen(false)}
          onAdjustFields={(modelId) => {
            setMetadataImportDrawerOpen(false);
            navigate(`/model/${modelId}?tab=fields`, { state: { fromModelList: true } });
          }}
        />
      )}
    </>
  );
};
