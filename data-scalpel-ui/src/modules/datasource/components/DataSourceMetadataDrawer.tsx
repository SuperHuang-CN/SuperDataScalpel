import { ReloadOutlined, TableOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Alert,
  Button,
  Checkbox,
  Drawer,
  Empty,
  Input,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useDataSourceNamespaces,
  useDataSourceTables,
  useTableMetadata,
  useTablePreview,
} from '../hooks/useDataSources';
import type {
  ColumnMetadata,
  DataSource,
  DataSourceTable,
  IndexMetadata,
  TableIdentifier,
} from '../model/dataSource';
import { defaultNamespaceKey, namespaceKey } from '../model/metadataSelection';

interface DataSourceMetadataDrawerProps {
  dataSource: DataSource | null;
  open: boolean;
  onClose: () => void;
}

interface DataSourceMetadataPanelProps {
  dataSource: DataSource;
  active: boolean;
}

interface PreviewRow {
  key: number;
  cells: unknown[];
}

const errorMessage = (error: unknown, fallback: string) => (
  error instanceof ApiError ? error.message : fallback
);

const identifierKey = (identifier: TableIdentifier) => JSON.stringify([
  identifier.catalog ?? '',
  identifier.schema ?? '',
  identifier.table,
]);

const displayCell = (value: unknown) => {
  if (value === null || value === undefined) {
    return <Typography.Text type="secondary">NULL</Typography.Text>;
  }
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
};

const platformTypeDefinitionLabel = (column: ColumnMetadata) => {
  const definition = column.platformTypeDefinition;
  if (!definition) {
    return null;
  }
  if (definition.type === 'STRING' && definition.length !== null) {
    return `STRING(${definition.length})`;
  }
  if (definition.type === 'DECIMAL') {
    return `DECIMAL(${definition.precision ?? '?'},${definition.scale ?? '?'})`;
  }
  if (definition.type === 'GEOMETRY') {
    const geometry = definition.geometry;
    return geometry
      ? `${geometry.kind}(${geometry.crs.authority}:${geometry.crs.code},${geometry.dimension})`
      : 'GEOMETRY(?)';
  }
  return definition.type;
};

export const DataSourceMetadataPanel = ({ dataSource, active }: DataSourceMetadataPanelProps) => {
  const tdEngine = dataSource.type === 'TDENGINE_WEBSOCKET' || dataSource.type === 'TDENGINE_RESTFUL';
  const [selectedNamespaceKey, setSelectedNamespaceKey] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [submittedKeyword, setSubmittedKeyword] = useState('');
  const [includeViews, setIncludeViews] = useState(false);
  const [selectedTableKey, setSelectedTableKey] = useState<string>();
  const [activeTab, setActiveTab] = useState('columns');
  const namespacesQuery = useDataSourceNamespaces(dataSource.id, active);
  const effectiveNamespaceKey = selectedNamespaceKey ?? defaultNamespaceKey(namespacesQuery.data ?? []);
  const selectedNamespace = namespacesQuery.data?.find((namespace) => namespaceKey(namespace) === effectiveNamespaceKey);
  const tableQuery = useMemo(() => ({
    catalog: selectedNamespace?.catalog ?? undefined,
    schema: selectedNamespace?.schema ?? undefined,
    keyword: submittedKeyword || undefined,
    includeViews: tdEngine ? false : includeViews,
  }), [includeViews, selectedNamespace?.catalog, selectedNamespace?.schema, submittedKeyword, tdEngine]);
  const tablesQuery = useDataSourceTables(dataSource.id, tableQuery, active && Boolean(selectedNamespace));
  const selectedTable = tablesQuery.data?.tables.find((table) => identifierKey(table.identifier) === selectedTableKey)
    ?? tablesQuery.data?.tables[0];
  const metadataQuery = useTableMetadata(dataSource.id, selectedTable?.identifier, active);
  const previewQuery = useTablePreview(dataSource.id, selectedTable?.identifier, active && activeTab === 'preview');

  const columnColumns: TableProps<ColumnMetadata>['columns'] = [
    { title: '#', dataIndex: 'ordinal', width: 48 },
    { title: '字段', dataIndex: 'name', width: 180, ellipsis: true, render: (value: string) => <code>{value}</code> },
    ...(tdEngine ? [{
      title: '角色',
      dataIndex: 'role',
      width: 96,
      render: (value: ColumnMetadata['role']) => value === 'TIME_KEY'
        ? <Tag color="blue">时间主列</Tag>
        : value === 'TAG' ? <Tag color="purple">TAG</Tag> : <Tag>指标列</Tag>,
    }] : []),
    { title: '数据库类型', dataIndex: 'nativeType', width: 140, ellipsis: true },
    {
      title: '平台类型',
      key: 'platformTypeDefinition',
      width: 210,
      ellipsis: true,
      render: (_: unknown, column: ColumnMetadata) => {
        const label = platformTypeDefinitionLabel(column);
        return label ? <Tag color="blue">{label}</Tag> : <Tag color="error">不可映射</Tag>;
      },
    },
    { title: '方言逻辑类型', dataIndex: 'logicalType', width: 120, render: (value: string) => <Tag>{value}</Tag> },
    {
      title: '长度/精度',
      key: 'size',
      width: 100,
      render: (_: unknown, column: ColumnMetadata) => column.precision !== null
        ? `${column.precision}${column.scale !== null ? `,${column.scale}` : ''}`
        : column.length ?? '—',
    },
    { title: '可空', dataIndex: 'nullable', width: 64, render: (value: boolean) => value ? '是' : '否' },
    { title: '默认值', dataIndex: 'defaultValue', width: 150, ellipsis: true, render: (value: string | null) => value ?? '—' },
    {
      title: '特性',
      key: 'features',
      width: 120,
      render: (_: unknown, column: ColumnMetadata) => (
        <Space size={2}>
          {column.autoIncrement && <Tag color="blue">自增</Tag>}
          {column.generated && <Tag color="purple">生成</Tag>}
          {!column.autoIncrement && !column.generated && '—'}
        </Space>
      ),
    },
    { title: '注释', dataIndex: 'comment', ellipsis: true, render: (value: string | null) => value ?? '—' },
  ];

  const indexColumns: TableProps<IndexMetadata>['columns'] = [
    { title: '索引名称', dataIndex: 'name', width: 220, render: (value: string) => <code>{value}</code> },
    { title: '唯一', dataIndex: 'unique', width: 80, render: (value: boolean) => value ? <Tag color="green">是</Tag> : '否' },
    { title: '字段', dataIndex: 'columns', render: (columns: string[]) => columns.map((column) => <Tag key={column}>{column}</Tag>) },
  ];

  const previewRows: PreviewRow[] = (previewQuery.data?.rows ?? []).map((cells, index) => ({ key: index, cells }));
  const previewColumns: TableProps<PreviewRow>['columns'] = (previewQuery.data?.columns ?? []).map((column, index) => ({
    title: (
      <div>
        <div>{column.name}</div>
        <Typography.Text type="secondary" className="metadata-column-type">{column.nativeType}</Typography.Text>
      </div>
    ),
    dataIndex: ['cells', index],
    width: 180,
    ellipsis: true,
    render: displayCell,
  }));

  const detailContent = selectedTable ? (
    <>
      <div className="metadata-detail-header">
        <Space size={6}>
          <TableOutlined />
          <Typography.Text strong>{selectedTable.identifier.table}</Typography.Text>
          <Tag color={tdEngine ? 'cyan' : undefined}>{tdEngine ? '超级表' : selectedTable.type}</Tag>
          {selectedTable.comment && <Typography.Text type="secondary">{selectedTable.comment}</Typography.Text>}
        </Space>
      </div>
      {metadataQuery.isError && (
        <Alert type="error" showIcon message={errorMessage(metadataQuery.error, '读取表元数据失败')} />
      )}
      {tdEngine && (
        <Alert
          type="info"
          showIcon
          banner
          message="仅管理超级表定义；数据预览会读取超级表下的汇总数据，但不会枚举或管理子表。"
        />
      )}
      <Tabs
        size="small"
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'columns',
            label: `字段（${metadataQuery.data?.columns.length ?? 0}）`,
            children: (
              <Table<ColumnMetadata>
                size="small"
                rowKey="name"
                columns={columnColumns}
                dataSource={metadataQuery.data?.columns ?? []}
                loading={metadataQuery.isFetching}
                pagination={false}
                scroll={{ x: 1380, y: 'calc(100vh - 245px)' }}
              />
            ),
          },
          {
            key: 'indexes',
            label: `索引（${metadataQuery.data?.indexes.length ?? 0}）`,
            children: (
              <Space direction="vertical" size={8} className="metadata-index-content">
                {metadataQuery.data?.primaryKey && (
                  <Alert
                    type="info"
                    showIcon
                    message={`主键：${metadataQuery.data.primaryKey.name ?? '未命名'}`}
                    description={metadataQuery.data.primaryKey.columns.join('、')}
                  />
                )}
                <Table<IndexMetadata>
                  size="small"
                  rowKey="name"
                  columns={indexColumns}
                  dataSource={metadataQuery.data?.indexes ?? []}
                  loading={metadataQuery.isFetching}
                  pagination={false}
                  scroll={{ y: 'calc(100vh - 315px)' }}
                />
              </Space>
            ),
          },
          {
            key: 'preview',
            label: '数据预览',
            children: previewQuery.isError ? (
              <Alert type="error" showIcon message={errorMessage(previewQuery.error, '预览数据失败')} />
            ) : (
              <>
                {previewQuery.data?.truncated && <Alert type="info" banner message="仅显示前 50 行数据" />}
                <Table<PreviewRow>
                  size="small"
                  rowKey="key"
                  columns={previewColumns}
                  dataSource={previewRows}
                  loading={previewQuery.isFetching}
                  pagination={false}
                  scroll={{ x: 'max-content', y: 'calc(100vh - 260px)' }}
                />
              </>
            ),
          },
        ].filter((item) => !tdEngine || item.key !== 'indexes')}
      />
    </>
  ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tdEngine ? '请选择超级表' : '请选择数据表'} />;

  return (
    <div className="data-source-metadata-panel">
      <div className="metadata-toolbar">
        <Select
          value={effectiveNamespaceKey}
          loading={namespacesQuery.isFetching}
          placeholder="选择库 / Schema"
          className="metadata-namespace-select"
          options={(namespacesQuery.data ?? []).map((namespace) => ({
            value: namespaceKey(namespace),
            label: namespace.displayName,
          }))}
          onChange={(value) => {
            setSelectedNamespaceKey(value);
            setSelectedTableKey(undefined);
          }}
        />
        <Input.Search
          allowClear
          value={keyword}
          placeholder={tdEngine ? '筛选超级表名' : '筛选表名'}
          className="metadata-table-search"
          onChange={(event) => setKeyword(event.target.value)}
          onSearch={(value) => setSubmittedKeyword(value.trim())}
        />
        {!tdEngine && (
          <Checkbox checked={includeViews} onChange={(event) => setIncludeViews(event.target.checked)}>包含视图</Checkbox>
        )}
        <Button icon={<ReloadOutlined />} onClick={() => void tablesQuery.refetch()}>刷新</Button>
        {tablesQuery.data?.truncated && <Typography.Text type="warning">结果已截断为 500 个对象</Typography.Text>}
      </div>
      {namespacesQuery.isError && (
        <Alert type="error" showIcon message={errorMessage(namespacesQuery.error, '读取库和 Schema 失败')} />
      )}
      {tablesQuery.isError && (
        <Alert type="error" showIcon message={errorMessage(tablesQuery.error, tdEngine ? '读取超级表失败' : '读取数据表失败')} />
      )}
      <div className="metadata-workspace">
        <div className="metadata-table-browser">
          <Spin spinning={tablesQuery.isFetching}>
            <Table<DataSourceTable>
              size="small"
              showHeader={false}
              rowKey={(table) => identifierKey(table.identifier)}
              dataSource={tablesQuery.data?.tables ?? []}
              pagination={false}
              columns={[{
                key: 'table',
                render: (_: unknown, table: DataSourceTable) => (
                  <div className="metadata-table-item">
                    <Typography.Text ellipsis>{table.identifier.table}</Typography.Text>
                    <Tag bordered={false} color={table.type === 'SUPERTABLE' ? 'cyan' : undefined}>
                      {table.type === 'SUPERTABLE' ? '超级表' : table.type === 'TABLE' ? '表' : '视图'}
                    </Tag>
                  </div>
                ),
              }]}
              rowClassName={(table) => identifierKey(table.identifier) === (selectedTable ? identifierKey(selectedTable.identifier) : '')
                ? 'metadata-table-row-selected'
                : ''}
              onRow={(table) => ({ onClick: () => setSelectedTableKey(identifierKey(table.identifier)) })}
              scroll={{ y: 'calc(100vh - 175px)' }}
            />
          </Spin>
        </div>
        <div className="metadata-detail">{detailContent}</div>
      </div>
    </div>
  );
};

export const DataSourceMetadataDrawer = ({ dataSource, open, onClose }: DataSourceMetadataDrawerProps) => (
  <Drawer
    rootClassName="business-overlay business-drawer-overlay"
    title={dataSource
      ? `${dataSource.name} · ${dataSource.type.startsWith('TDENGINE_') ? '超级表结构' : '表结构'}`
      : '表结构'}
    open={open}
    width="86vw"
    className="data-source-metadata-drawer"
    onClose={onClose}
    destroyOnHidden
  >
    {dataSource && <DataSourceMetadataPanel key={dataSource.id} dataSource={dataSource} active={open} />}
  </Drawer>
);
