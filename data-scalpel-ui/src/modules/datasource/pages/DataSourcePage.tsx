import {
  ApiOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  ImportOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  ShareAltOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Dropdown, Form, Modal, Select, Space, Table, Tooltip, message } from 'antd';
import { useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { ManagementCode, ManagementListCell, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { ManagementAdaptiveMoreFilters, ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useCurrentUser } from '../../system';
import { ConnectionTestResultModal } from '../components/ConnectionTestResultModal';
import { DataSourceDrawer } from '../components/DataSourceDrawer';
import { DataSourceTypeIcon } from '../components/DataSourceTypeIcon';
import { useDataSourceTypes, useDataSources, useDeleteDataSource, useTestSavedDataSourceConnection } from '../hooks/useDataSources';
import {
  dataSourceTypeLabels,
  dataSourcePurposeLabels,
  type DataSource,
  type ConnectionTestResult,
  type DataSourceFilters,
  type DataSourcePurpose,
  type DataSourcePurposeFilter,
  type DataSourceType,
} from '../model/dataSource';
import { buildDataSourceSearch } from '../model/dataSourceSearch';

const DEFAULT_PAGE_SIZE = 20;

const purposeFilterOptions: { value: DataSourcePurposeFilter; label: string }[] = [
  { value: 'SOURCE', label: '数据源' },
  { value: 'STORAGE', label: '数据存储' },
  { value: 'DISTRIBUTION', label: '数据分发' },
  { value: 'BOTH', label: '源端 + 存储' },
];

const purposeOrder: DataSourcePurpose[] = ['SOURCE', 'STORAGE', 'DISTRIBUTION'];

const purposeIcons = {
  SOURCE: <ImportOutlined />,
  STORAGE: <DatabaseOutlined />,
  DISTRIBUTION: <ShareAltOutlined />,
} satisfies Record<DataSourcePurpose, ReactNode>;

const dataSourceTypeIconTones = {
  MYSQL: 'orange',
  POSTGRESQL: 'blue',
  HIGHGO: 'violet',
  ORACLE: 'rose',
  SQL_SERVER: 'rose',
  CLICKHOUSE: 'slate',
  DAMENG: 'blue',
  KINGBASE: 'rose',
  OPENGAUSS: 'orange',
  TDENGINE_WEBSOCKET: 'cyan',
  TDENGINE_RESTFUL: 'slate',
  KAFKA: 'slate',
  S3: 'rose',
  HTTP_API: 'blue',
  ARCGIS_REST: 'green',
  WFS: 'cyan',
} satisfies Record<DataSourceType, 'blue' | 'violet' | 'cyan' | 'green' | 'orange' | 'rose' | 'slate'>;

export const DataSourcePage = () => {
  const navigate = useNavigate();
  const [filterForm] = Form.useForm<DataSourceFilters>();
  const [advancedFilterForm] = Form.useForm<DataSourceFilters>();
  const [advancedFilterOpen, setAdvancedFilterOpen] = useState(false);
  const [advancedFilters, setAdvancedFilters] = useState<DataSourceFilters>({});
  const [filters, setFilters] = useState<DataSourceFilters>({});
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(undefined);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingDataSource, setEditingDataSource] = useState<DataSource | null>(null);
  const [testFailure, setTestFailure] = useState<{
    result: ConnectionTestResult;
    targetLabel: string;
  } | null>(null);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [messageApi, messageContext] = message.useMessage();
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewDirectories = permissions.has('directory.view');
  const canManageDirectories = permissions.has('directory.manage');
  const canCreate = permissions.has('datasource.create');
  const canUpdate = permissions.has('datasource.update');
  const canDelete = permissions.has('datasource.delete');
  const canTest = permissions.has('datasource.test');
  const canReadMetadata = permissions.has('datasource.metadata');
  const directoriesQuery = useDirectoryTree('DATA_SOURCE', canViewDirectories);
  const dataSourceTypesQuery = useDataSourceTypes();
  const request = useMemo(() => ({
    search: buildDataSourceSearch(filters),
    page,
    size,
    sort: '-updatedAt,code',
  }), [filters, page, size]);
  const dataSourcesQuery = useDataSources(request);
  const deleteMutation = useDeleteDataSource();
  const testMutation = useTestSavedDataSourceConnection();

  const dataSourceTypeOptions = dataSourceTypesQuery.data?.map((definition) => ({
    value: definition.id,
    label: definition.displayName,
  })) ?? Object.entries(dataSourceTypeLabels).map(([value, label]) => ({ value, label }));
  const dataSourceTypeDefinition = new Map(dataSourceTypesQuery.data?.map((definition) => [definition.id, definition]) ?? []);
  const advancedFilterCount = Number(Boolean(advancedFilters.type)) + Number(typeof advancedFilters.enabled === 'boolean');
  const search = (nextFilters: DataSourceFilters) => {
    setFilters(nextFilters);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    advancedFilterForm.resetFields();
    advancedFilterForm.setFieldsValue({ type: undefined, enabled: undefined });
    setAdvancedFilters({});
    setAdvancedFilterOpen(false);
    setDirectorySelection(undefined);
    search({});
  };

  const clearAdvancedFilters = () => {
    advancedFilterForm.setFieldsValue({ type: undefined, enabled: undefined });
  };

  const applyDirectFilters = (values: DataSourceFilters) => {
    const advancedValues = advancedFilterForm.getFieldsValue();
    const nextAdvancedFilters = { type: advancedValues.type, enabled: advancedValues.enabled };
    setAdvancedFilters(nextAdvancedFilters);
    search({
      ...filters,
      keyword: values.keyword,
      purpose: values.purpose,
      ...nextAdvancedFilters,
    });
  };

  const confirmAdvancedFilters = () => {
    const values = advancedFilterForm.getFieldsValue();
    setAdvancedFilters({ type: values.type, enabled: values.enabled });
    setAdvancedFilterOpen(false);
  };

  const selectDirectory = (selection: DirectorySelection) => {
    setDirectorySelection(selection);
    if (selection === undefined) {
      search({ ...filters, directoryIds: undefined, uncategorized: undefined });
      return;
    }
    if (selection === null) {
      search({ ...filters, directoryIds: undefined, uncategorized: true });
      return;
    }
    search({
      ...filters,
      directoryIds: findDirectoryDescendantIds(directoriesQuery.data ?? [], selection),
      uncategorized: undefined,
    });
  };

  const closeDrawer = () => {
    setEditingDataSource(null);
    setCreateDrawerOpen(false);
    };

  const testConnection = async (dataSource: DataSource) => {
    try {
      setTestFailure(null);
      const result = await testMutation.mutateAsync(dataSource.id);
      if (result.success) {
        messageApi.success(`${dataSource.name}：${result.message}`);
      } else {
        const connection = dataSource.connection;
        const target = connection.kind === 'JDBC'
          ? `${connection.host}:${connection.port}/${connection.databaseName}`
          : dataSource.name;
        setTestFailure({
          result,
          targetLabel: `${dataSource.name} · ${target}`,
        });
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '测试连接失败');
    }
  };

  const remove = async (dataSource: DataSource) => {
    try {
      await deleteMutation.mutateAsync(dataSource.id);
      messageApi.success('数据源已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除数据源失败');
    }
  };
  const confirmRemove = (dataSource: DataSource) => Modal.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '删除数据源', content: `确认删除“${dataSource.name}”吗？`, okText: '删除', cancelText: '取消',
    okButtonProps: { danger: true }, onOk: () => remove(dataSource),
  });

  const columns: TableProps<DataSource>['columns'] = [
    {
      title: '数据源', dataIndex: 'name', width: 260,
      render: (value: string, dataSource) => (
        <ManagementListCell
          icon={<DataSourceTypeIcon type={dataSource.type} />}
          iconLabel={`数据源类型：${dataSourceTypeLabels[dataSource.type]}`}
          iconTone={dataSourceTypeIconTones[dataSource.type]}
          primary={(
            <Button
              type="link"
              size="small"
              className="data-source-name-button"
              onClick={() => navigate(`/datasource/${dataSource.id}`, { state: { fromDataSourceList: true } })}
            >
              {value}
            </Button>
          )}
          secondary={<ManagementCode value={dataSource.code} />}
        />
      ),
    },
    {
      title: '说明', dataIndex: 'description', width: 240,
      render: (value: string | null) => value ? (
        <Tooltip title={value}>
          <div className="data-source-description">{value}</div>
        </Tooltip>
      ) : <span className="data-source-description-empty">—</span>,
    },
    {
      title: '用途', width: 110,
      render: (_value: unknown, dataSource) => <Space size={4}>
        {purposeOrder.filter((purpose) => dataSource.purposes.includes(purpose)).map((purpose) => (
          <Tooltip key={purpose} title={dataSourcePurposeLabels[purpose]}>
            <span className="management-enum-icon" aria-label={dataSourcePurposeLabels[purpose]}>{purposeIcons[purpose]}</span>
          </Tooltip>
        ))}
      </Space>,
    },
    {
      title: '连接目标', width: 300,
      render: (_: unknown, dataSource: DataSource) => {
        let endpoint = '';
        let target = '';
        switch (dataSource.connection.kind) {
          case 'JDBC': endpoint = `${dataSource.connection.host}:${dataSource.connection.port}`; target = dataSource.connection.databaseName; break;
          case 'KAFKA': endpoint = dataSource.connection.bootstrapServers; target = 'Topic 在任务中选择'; break;
          case 'S3': endpoint = dataSource.connection.endpoint; target = dataSource.connection.rootPrefix ? `${dataSource.connection.bucket}/${dataSource.connection.rootPrefix}` : dataSource.connection.bucket; break;
          case 'HTTP_API': endpoint = dataSource.connection.configuration.baseUrl; target = 'API 资源中配置路径'; break;
        }
        return <ManagementListCell primary={<ManagementCode value={endpoint} />} secondary={target} />;
      },
    },
    {
      title: '状态 / 更新时间', width: 160,
      render: (_value: unknown, dataSource) => <ManagementListCell primary={<ManagementStatusIndicator label={dataSource.enabled ? '启用' : '停用'} tone={dataSource.enabled ? 'success' : 'default'} />} secondary={formatManagementDateTime(dataSource.updatedAt)} />,
    },
    {
      title: '操作',
      key: 'action',
      width: 112,
      render: (_: unknown, dataSource: DataSource) => {
        const definition = dataSourceTypeDefinition.get(dataSource.type);
        const canTestConnection = canTest && Boolean(definition?.connectionTestAvailable);
        const canBrowseResources = Boolean(definition && definition.resourceBrowserKind !== 'NONE'
          && (!['JDBC_TABLES', 'TDENGINE_SUPERTABLES'].includes(definition.resourceBrowserKind) || canReadMetadata));
        const resourceItem = canBrowseResources
          ? {
            key: 'resources',
            label: definition?.resourceBrowserKind === 'API_RESOURCES'
              ? 'API 资源'
              : definition?.resourceBrowserKind === 'SPATIAL_RESOURCES'
                ? '空间资源'
                : definition?.resourceBrowserKind === 'KAFKA_TOPICS'
                  ? 'Topic'
                  : definition?.resourceBrowserKind === 'TDENGINE_SUPERTABLES' ? '超级表' : '数据表',
            icon: definition?.resourceBrowserKind === 'API_RESOURCES' ? <ApiOutlined /> : <DatabaseOutlined />,
          }
          : undefined;
        const moreItems = [
          ...(resourceItem ? [resourceItem] : []),
          ...(canUpdate ? [{ key: 'edit', label: '修改', icon: <EditOutlined /> }] : []),
          ...(canTestConnection ? [{ key: 'test', label: '测试连接', icon: <ApiOutlined /> }] : []),
          ...(canDelete ? [{ type: 'divider' as const }] : []),
          ...(canDelete ? [{ key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true }] : []),
        ];
        const openResources = () => navigate(`/datasource/${dataSource.id}?tab=resources`, { state: { fromDataSourceList: true } });
        return <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            {resourceItem && <Tooltip title={String(resourceItem.label)}><Button type="text" size="small" aria-label={`${resourceItem.label}${dataSource.name}`} icon={resourceItem.icon} onClick={openResources} /></Tooltip>}
            {canUpdate && <Tooltip title="修改"><Button type="text" size="small" aria-label={`修改${dataSource.name}`} icon={<EditOutlined />} onClick={() => setEditingDataSource(dataSource)} /></Tooltip>}
          </div>
          {moreItems.length > 0 && <Dropdown trigger={['click']} menu={{ items: moreItems, onClick: ({ key }) => { if (key === 'resources') openResources(); if (key === 'edit') setEditingDataSource(dataSource); if (key === 'test') void testConnection(dataSource); if (key === 'delete') confirmRemove(dataSource); } }}>
            <Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" size="small" aria-label={`${dataSource.name}的更多操作`} icon={<MoreOutlined />} loading={testMutation.isPending && testMutation.variables === dataSource.id} /></Tooltip>
          </Dropdown>}
        </div>;
      },
    },
  ];

  return (
    <>
      {messageContext}
      <div className={canViewDirectories ? 'directory-management-layout' : 'page-stack'}>
        {canViewDirectories && <DirectoryTreePanel
          scope="DATA_SOURCE"
          tree={directoriesQuery.data ?? []}
          loading={directoriesQuery.isFetching}
          selection={directorySelection}
          canManage={canManageDirectories}
          onSelectionChange={selectDirectory}
        />}
        <section className="management-workbench">
          <div className="management-filter-strip">
            <Form<DataSourceFilters> autoComplete="off"
              form={filterForm}
              layout="inline"
              className="management-filter-form"
              onFinish={applyDirectFilters}
            >
              <Form.Item name="keyword">
                <ManagementSearchInput allowClear placeholder="搜索数据源名称或编码" className="data-source-keyword-input" />
              </Form.Item>
              <Form.Item name="purpose">
                <Select allowClear placeholder="全部用途" options={purposeFilterOptions} className="data-source-filter-select" />
              </Form.Item>
            </Form>
              <ManagementAdaptiveMoreFilters
                count={advancedFilterCount}
                open={advancedFilterOpen}
                onOpenChange={(open) => {
                  setAdvancedFilterOpen(open);
                  if (open) {
                    advancedFilterForm.resetFields();
                    advancedFilterForm.setFieldsValue({ type: advancedFilters.type, enabled: advancedFilters.enabled });
                  }
                }}
                onClear={clearAdvancedFilters}
                onCancel={() => {
                  advancedFilterForm.setFieldsValue({ type: advancedFilters.type, enabled: advancedFilters.enabled });
                  setAdvancedFilterOpen(false);
                }}
                onConfirm={confirmAdvancedFilters}
              >
                <Form<DataSourceFilters> form={advancedFilterForm} layout="vertical" autoComplete="off" initialValues={advancedFilters}>
                  <Form.Item name="type" label="连接类型"><Select allowClear placeholder="全部类型" options={dataSourceTypeOptions} className="advanced-filter-select" /></Form.Item>
                  <Form.Item name="enabled" label="状态"><Select allowClear placeholder="全部状态" className="advanced-filter-select" options={[{ value: true, label: '启用' }, { value: false, label: '停用' }]} /></Form.Item>
                </Form>
              </ManagementAdaptiveMoreFilters>
            <ManagementFilterActions form={filterForm} appliedFilters={filters} additionalActive={advancedFilterCount > 0 || directorySelection !== undefined} loading={dataSourcesQuery.isFetching} onReset={reset} />
          </div>
          <div className="management-results-surface">
            <div className="management-result-toolbar">
            <span className="management-result-title">数据源列表 <span className="management-result-count">共 {dataSourcesQuery.data?.totalElements ?? 0} 项</span></span>
            <div className="management-result-actions"><Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新数据源列表" onClick={() => void dataSourcesQuery.refetch()} /></Tooltip>{canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setCreateDrawerOpen(true); }}>新建</Button>}</div>
            </div>
            <Table<DataSource>
            size="small"
            className="management-table"
            rowKey="id"
            columns={columns}
            dataSource={dataSourcesQuery.data?.content ?? []}
            loading={dataSourcesQuery.isFetching}
            scroll={{ x: 1_152, y: '100%' }}
            pagination={{
              current: page + 1,
              pageSize: size,
              total: dataSourcesQuery.data?.totalElements ?? 0,
              size: 'small',
              position: ['bottomRight'],
              hideOnSinglePage: false,
              showSizeChanger: true,
              showTotal: (total) => `共 ${total} 项`,
            }}
            onChange={(pagination) => {
              setPage((pagination.current ?? 1) - 1);
              setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE);
            }}
            />
          </div>
        </section>
      </div>
      <DataSourceDrawer
        open={createDrawerOpen || Boolean(editingDataSource)}
        dataSource={editingDataSource}
        initialDirectoryId={typeof directorySelection === 'string' ? directorySelection : undefined}
        canViewDirectories={canViewDirectories}
        canTest={canTest}
        onClose={closeDrawer}
      />
      {testFailure && (
        <ConnectionTestResultModal
          open
          result={testFailure.result}
          targetLabel={testFailure.targetLabel}
          onClose={() => setTestFailure(null)}
        />
      )}
    </>
  );
};
