import {
  ApiOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  FilterOutlined,
  ImportOutlined,
  PlusOutlined,
  ReloadOutlined,
  ShareAltOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Badge, Button, Card, Form, Input, Popconfirm, Popover, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState, type ReactNode } from 'react';
import { ApiError } from '../../../shared/api/http';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useCurrentUser } from '../../system';
import { ConnectionTestResultModal } from '../components/ConnectionTestResultModal';
import { DataSourceDrawer } from '../components/DataSourceDrawer';
import { DataSourceMetadataDrawer } from '../components/DataSourceMetadataDrawer';
import { ApiResourceListDrawer } from '../components/ApiResourceListDrawer';
import { useDataSourceTypes, useDataSources, useDeleteDataSource, useTestSavedDataSourceConnection } from '../hooks/useDataSources';
import {
  dataSourceTypeLabels,
  dataSourcePurposeLabels,
  type DataSource,
  type ConnectionTestResult,
  type DataSourceFilters,
  type DataSourcePurpose,
  type DataSourcePurposeFilter,
} from '../model/dataSource';
import { buildDataSourceSearch } from '../model/dataSourceSearch';

const DEFAULT_PAGE_SIZE = 20;

const purposeFilterOptions: { value: DataSourcePurposeFilter; label: string }[] = [
  { value: 'SOURCE', label: '数据源' },
  { value: 'STORAGE', label: '数据存储' },
  { value: 'DISTRIBUTION', label: '数据分发' },
  { value: 'BOTH', label: '源端 + 存储' },
];

const purposeColors: Record<DataSourcePurpose, string> = {
  SOURCE: 'blue',
  STORAGE: 'purple',
  DISTRIBUTION: 'cyan',
};

const purposeOrder: DataSourcePurpose[] = ['SOURCE', 'STORAGE', 'DISTRIBUTION'];

const purposeIcons = {
  SOURCE: <ImportOutlined />,
  STORAGE: <DatabaseOutlined />,
  DISTRIBUTION: <ShareAltOutlined />,
} satisfies Record<DataSourcePurpose, ReactNode>;

const formatDateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'medium',
  hour12: false,
}).format(new Date(value));

export const DataSourcePage = () => {
  const [filterForm] = Form.useForm<DataSourceFilters>();
  const selectedType = Form.useWatch('type', filterForm);
  const selectedEnabled = Form.useWatch('enabled', filterForm);
  const [filters, setFilters] = useState<DataSourceFilters>({});
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(undefined);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingDataSource, setEditingDataSource] = useState<DataSource | null>(null);
  const [metadataDataSource, setMetadataDataSource] = useState<DataSource | null>(null);
  const [apiResourceDataSource, setApiResourceDataSource] = useState<DataSource | null>(null);
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
  const dataSourceTypeName = new Map(dataSourceTypesQuery.data?.map((definition) => [definition.id, definition.displayName]) ?? []);
  const dataSourceTypeDefinition = new Map(dataSourceTypesQuery.data?.map((definition) => [definition.id, definition]) ?? []);
  const advancedFilterCount = Number(Boolean(selectedType)) + Number(typeof selectedEnabled === 'boolean');
  const search = (nextFilters: DataSourceFilters) => {
    setFilters(nextFilters);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    setDirectorySelection(undefined);
    search({});
  };

  const clearAdvancedFilters = () => {
    filterForm.setFieldsValue({ type: undefined, enabled: undefined });
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

  const columns: TableProps<DataSource>['columns'] = [
    { title: '名称', dataIndex: 'name', width: 180, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 180, ellipsis: true, render: (value: string) => <code>{value}</code> },
    {
      title: '用途',
      dataIndex: 'purposes',
      width: 100,
      render: (purposes: DataSource['purposes']) => (
        <Space size={2}>
          {purposeOrder.filter((purpose) => purposes.includes(purpose)).map((purpose) => (
            <Tooltip key={purpose} title={dataSourcePurposeLabels[purpose]}>
              <Tag
                className="data-source-purpose-tag"
                color={purposeColors[purpose]}
                icon={purposeIcons[purpose]}
                aria-label={dataSourcePurposeLabels[purpose]}
              />
            </Tooltip>
          ))}
        </Space>
      ),
    },
    { title: '连接类型', dataIndex: 'type', width: 150, render: (value: DataSource['type']) => dataSourceTypeName.get(value) ?? dataSourceTypeLabels[value] },
    {
      title: '地址',
      key: 'endpoint',
      width: 200,
      render: (_: unknown, dataSource: DataSource) => {
        switch (dataSource.connection.kind) {
          case 'JDBC': return `${dataSource.connection.host}:${dataSource.connection.port}`;
          case 'KAFKA': return dataSource.connection.bootstrapServers;
          case 'S3': return dataSource.connection.endpoint;
          case 'HTTP_API': return dataSource.connection.configuration.baseUrl;
        }
      },
    },
    {
      title: '目标',
      key: 'target',
      width: 180,
      ellipsis: true,
      render: (_: unknown, dataSource: DataSource) => {
        switch (dataSource.connection.kind) {
          case 'JDBC': return dataSource.connection.databaseName;
          case 'KAFKA': return 'Topic 在任务中选择';
          case 'S3': return dataSource.connection.rootPrefix
            ? `${dataSource.connection.bucket}/${dataSource.connection.rootPrefix}` : dataSource.connection.bucket;
          case 'HTTP_API': return 'API 资源中配置路径';
        }
      },
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (value: boolean) => <Tag color={value ? 'success' : 'default'}>{value ? '启用' : '停用'}</Tag>,
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDateTime(value) },
    {
      title: '操作',
      key: 'action',
      width: 132,
      fixed: 'right',
      render: (_: unknown, dataSource: DataSource) => (
        <Space size={2}>
          {canUpdate && <Tooltip title="修改">
            <Button
              type="text"
              size="small"
              aria-label={`修改${dataSource.name}`}
              icon={<EditOutlined />}
              onClick={() => setEditingDataSource(dataSource)}
            />
          </Tooltip>}
          {canTest && (dataSource.connectionKind === 'JDBC' || dataSource.connectionKind === 'HTTP_API') && dataSourceTypeDefinition.get(dataSource.type)?.connectionTestAvailable && <Tooltip title="测试连接">
            <Button
              type="text"
              size="small"
              aria-label={`测试${dataSource.name}连接`}
              icon={<ApiOutlined />}
              loading={testMutation.isPending && testMutation.variables === dataSource.id}
              onClick={() => void testConnection(dataSource)}
            />
          </Tooltip>}
          {dataSource.connectionKind === 'HTTP_API' && <Tooltip title="API 资源">
            <Button
              type="text"
              size="small"
              aria-label={`管理${dataSource.name}API资源`}
              icon={<ApiOutlined />}
              onClick={() => setApiResourceDataSource(dataSource)}
            />
          </Tooltip>}
          {canReadMetadata && dataSource.connectionKind === 'JDBC' && <Tooltip title="表结构">
            <Button
              type="text"
              size="small"
              aria-label={`查看${dataSource.name}表结构`}
              icon={<DatabaseOutlined />}
              onClick={() => setMetadataDataSource(dataSource)}
            />
          </Tooltip>}
          {canDelete && <Popconfirm title="删除数据源" description={`确认删除“${dataSource.name}”吗？`} onConfirm={() => remove(dataSource)} okText="删除" cancelText="取消">
            <Tooltip title="删除">
              <Button type="text" size="small" danger aria-label={`删除${dataSource.name}`} icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>}
        </Space>
      ),
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
        <Card className="management-card">
          <div className="management-toolbar">
            <Form<DataSourceFilters>
              form={filterForm}
              layout="inline"
              className="management-filter-form"
              onFinish={search}
            >
              <Form.Item name="keyword" label="名称/编码">
                <Input allowClear placeholder="按名称或编码筛选" className="data-source-keyword-input" />
              </Form.Item>
              <Form.Item name="purpose" label="用途">
                <Select allowClear placeholder="全部" options={purposeFilterOptions} className="data-source-filter-select" />
              </Form.Item>
              <Popover
                trigger="click"
                placement="bottomLeft"
                content={(
                  <div className="advanced-filter-popover">
                    <div className="advanced-filter-title">更多筛选</div>
                    <Form.Item name="type" label="连接类型">
                      <Select allowClear placeholder="全部类型" options={dataSourceTypeOptions} className="advanced-filter-select" />
                    </Form.Item>
                    <Form.Item name="enabled" label="状态">
                      <Select
                        allowClear
                        placeholder="全部状态"
                        className="advanced-filter-select"
                        options={[{ value: true, label: '启用' }, { value: false, label: '停用' }]}
                      />
                    </Form.Item>
                    <div className="advanced-filter-actions">
                      <Button type="link" size="small" htmlType="button" disabled={!advancedFilterCount} onClick={clearAdvancedFilters}>
                        清空更多条件
                      </Button>
                    </div>
                  </div>
                )}
              >
                <Badge count={advancedFilterCount} size="small" offset={[-2, 2]}>
                  <Button icon={<FilterOutlined />}>更多</Button>
                </Badge>
              </Popover>
            </Form>
            <Space size={4} className="management-toolbar-actions">
              <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
              <Button onClick={reset}>重置</Button>
              <Button icon={<ReloadOutlined />} onClick={() => void dataSourcesQuery.refetch()}>刷新</Button>
              {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>新建</Button>}
            </Space>
          </div>
          <Table<DataSource>
            size="small"
            className="management-table"
            rowKey="id"
            columns={columns}
            dataSource={dataSourcesQuery.data?.content ?? []}
            loading={dataSourcesQuery.isFetching}
            scroll={{ x: 1282, y: '100%' }}
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
        </Card>
      </div>
      <DataSourceDrawer
        open={createDrawerOpen || Boolean(editingDataSource)}
        dataSource={editingDataSource}
        canViewDirectories={canViewDirectories}
        canTest={canTest}
        onClose={closeDrawer}
      />
      <DataSourceMetadataDrawer
        open={Boolean(metadataDataSource)}
        dataSource={metadataDataSource}
        onClose={() => setMetadataDataSource(null)}
      />
      <ApiResourceListDrawer
        open={Boolean(apiResourceDataSource)}
        dataSource={apiResourceDataSource}
        canCreate={canCreate}
        canUpdate={canUpdate}
        canDelete={canDelete}
        canTest={canTest}
        onClose={() => setApiResourceDataSource(null)}
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
