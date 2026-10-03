import '../../../shared/components/schema-table.css';
import './data-source-metadata.css';
import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { EyeOutlined, InfoCircleOutlined, KeyOutlined, ReloadOutlined, SearchOutlined, SlidersOutlined, TableOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Checkbox, Drawer, Empty, Input, Select, Space, Spin, Table, Tabs, Tag, Tooltip, Typography } from 'antd';
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

const metadataCellText = (value: string | null | undefined) => {
  if (!value) return '—';
  return <Typography.Text className="metadata-cell-text" ellipsis={{ tooltip: value }}>{value}</Typography.Text>;
};

const tableTypeLabel = (table: DataSourceTable, tdEngine: boolean) => {
  if (tdEngine || table.type === 'SUPERTABLE') return '超级表';
  return table.type === 'VIEW' ? '视图' : '数据表';
};

export const DataSourceMetadataPanel = ({ dataSource, active }: DataSourceMetadataPanelProps) => {
  const tdEngine = dataSource.type === 'TDENGINE_WEBSOCKET' || dataSource.type === 'TDENGINE_RESTFUL';
  const [selectedNamespaceKey, setSelectedNamespaceKey] = useState<string>();
  const [keyword, setKeyword] = useState('');
  const [submittedKeyword, setSubmittedKeyword] = useState('');
  const [includeViews, setIncludeViews] = useState(false);
  const [selectedTableKey, setSelectedTableKey] = useState<string>();
  const [activeTab, setActiveTab] = useState('columns');
  const [showAdvancedColumns, setShowAdvancedColumns] = useState(false);
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
  const primaryKeyColumns = new Set(metadataQuery.data?.primaryKey?.columns ?? []);

  const columnColumns: TableProps<ColumnMetadata>['columns'] = [
    { title: '#', dataIndex: 'ordinal', width: 48, align: 'right' },
    {
      title: '字段', dataIndex: 'name', width: 180,
      render: (value: string) => (
        <Typography.Text className="metadata-cell-text metadata-field-name" ellipsis={{ tooltip: value }}>
          {value}
        </Typography.Text>
      ),
    },
    ...(tdEngine ? [{
      title: '角色',
      dataIndex: 'role',
      width: 96,
      render: (value: ColumnMetadata['role']) => value === 'TIME_KEY'
        ? <Tag color="blue">时间主列</Tag>
        : value === 'TAG' ? <Tag color="purple">TAG</Tag> : <Tag>指标列</Tag>,
    }] : []),
    {
      title: '数据类型', key: 'types', width: 220,
      render: (_: unknown, column: ColumnMetadata) => (
        <div className="schema-field-identity">
          <Tooltip title={`数据库类型：${column.nativeType}`}><span className="schema-type-label">{column.nativeType}</span></Tooltip>
          {platformTypeDefinitionLabel(column)
            ? <Typography.Text type="secondary" ellipsis={{ tooltip: platformTypeDefinitionLabel(column) }}>平台：{platformTypeDefinitionLabel(column)}</Typography.Text>
            : <Tag color="error" bordered={false}>不可映射</Tag>}
        </div>
      ),
    },
    ...(showAdvancedColumns ? [
      { title: '方言逻辑类型', dataIndex: 'logicalType', width: 120, render: (value: string) => <Tag bordered={false}>{value}</Tag> },
      {
        title: '长度/精度',
        key: 'size',
        width: 100,
        render: (_: unknown, column: ColumnMetadata) => column.precision !== null
          ? `${column.precision}${column.scale !== null ? `,${column.scale}` : ''}`
          : column.length ?? '—',
      },
    ] : []),
    {
      title: '默认值', dataIndex: 'defaultValue', width: 150,
      render: (value: string | null) => metadataCellText(value),
    },
    {
      title: '约束 / 特性',
      key: 'features',
      width: 150,
      render: (_: unknown, column: ColumnMetadata) => (
        <Space className="metadata-feature-tags" size={2} wrap>
          {primaryKeyColumns.has(column.name) && <Tag className="schema-key-tag">主键</Tag>}
          {column.autoIncrement && <Tag>自增</Tag>}
          {column.generated && <Tag>生成</Tag>}
          <span className={column.nullable ? "schema-muted" : "schema-required"}>{column.nullable ? '允许为空' : '不可为空'}</span>
        </Space>
      ),
    },
    { title: '注释', dataIndex: 'comment', render: (value: string | null) => metadataCellText(value) },
  ];

  const indexColumns: TableProps<IndexMetadata>['columns'] = [
    {
      title: '索引名称', dataIndex: 'name', width: '44%',
      render: (value: string) => <Typography.Text className="metadata-index-name" copyable={{ text: value }}>{value}</Typography.Text>,
    },
    {
      title: '类型', dataIndex: 'unique', width: 112,
      render: (unique: boolean, index) => (
        <span className="metadata-index-type">
          {metadataQuery.data?.primaryKey?.name === index.name ? '主键索引' : unique ? '唯一索引' : '普通索引'}
        </span>
      ),
    },
    {
      title: '索引字段（按顺序）', dataIndex: 'columns',
      render: (columns: string[]) => (
        <ol className="metadata-index-fields">
          {columns.map((column, position) => <li key={`${position}-${column}`}><span className="metadata-index-field-order">{position + 1}</span><span>{column}</span></li>)}
        </ol>
      ),
    },
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
          <Typography.Text strong className="metadata-detail-title" ellipsis={{ tooltip: selectedTable.identifier.table }}>
            {selectedTable.identifier.table}
          </Typography.Text>
          <Tag bordered={false} color={tdEngine ? 'cyan' : undefined}>{tableTypeLabel(selectedTable, tdEngine)}</Tag>
          {selectedTable.comment && (
            <Typography.Text type="secondary" className="metadata-detail-comment" ellipsis={{ tooltip: selectedTable.comment }}>
              {selectedTable.comment}
            </Typography.Text>
          )}
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
        tabBarExtraContent={activeTab === 'columns' ? {
          right: (
            <Tooltip title={showAdvancedColumns ? '隐藏方言逻辑类型、长度/精度' : '显示方言逻辑类型、长度/精度'}>
              <Button
                type="text"
                size="small"
                className={showAdvancedColumns ? 'metadata-advanced-columns-button-active' : undefined}
                icon={<SlidersOutlined />}
                aria-label={showAdvancedColumns ? '隐藏详细字段列' : '显示详细字段列'}
                aria-pressed={showAdvancedColumns}
                onClick={() => setShowAdvancedColumns((value) => !value)}
              >
                详细列
              </Button>
            </Tooltip>
          ),
        } : undefined}
        items={[
          {
            key: 'columns',
            label: `字段（${metadataQuery.data?.columns.length ?? 0}）`,
            children: (
              <div className="metadata-tab-content">
                <Table<ColumnMetadata>
                  className="metadata-detail-table management-table schema-definition-table"
                  size="small"
                  rowKey="name"
                  columns={columnColumns}
                  dataSource={metadataQuery.data?.columns ?? []}
                  loading={metadataQuery.isFetching}
                  pagination={false}
                  scroll={{ x: showAdvancedColumns ? 1120 : 900, y: '100%' }}
                />
              </div>
            ),
          },
          {
            key: 'indexes',
            label: `索引（${metadataQuery.data?.indexes.length ?? 0}）`,
            children: (
              <div className="metadata-tab-content metadata-index-content">
                {metadataQuery.data?.primaryKey && (
                  <div className="metadata-primary-key-summary">
                    <KeyOutlined aria-hidden />
                    <strong>主键</strong>
                    <span>{metadataQuery.data.primaryKey.name ?? '未命名'}</span>
                    <span className="metadata-primary-key-columns">{metadataQuery.data.primaryKey.columns.join('、')}</span>
                  </div>
                )}
                <Table<IndexMetadata>
                  className="metadata-detail-table management-table schema-definition-table metadata-index-table"
                  size="small"
                  rowKey="name"
                  tableLayout="fixed"
                  columns={indexColumns}
                  dataSource={metadataQuery.data?.indexes ?? []}
                  loading={metadataQuery.isFetching}
                  pagination={false}
                  scroll={{ x: 640, y: '100%' }}
                  locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="此表暂无索引" /> }}
                />
              </div>
            ),
          },
          {
            key: 'preview',
            label: (
              <Space size={4} className="metadata-preview-tab-label">
                <span>数据预览</span>
                {previewQuery.data?.truncated && (
                  <Tooltip title={`仅显示前 ${previewQuery.data.limit} 行数据`}>
                    <span className="metadata-preview-truncation-hint" tabIndex={0} aria-label="预览数据已截断">
                      <InfoCircleOutlined />
                    </span>
                  </Tooltip>
                )}
              </Space>
            ),
            children: previewQuery.isError ? (
              <Alert type="error" showIcon message={errorMessage(previewQuery.error, '预览数据失败')} />
            ) : (
              <div className="metadata-tab-content metadata-preview-content">
                <Table<PreviewRow>
                  className="metadata-detail-table management-table schema-data-table"
                  size="small"
                  rowKey="key"
                  columns={previewColumns}
                  dataSource={previewRows}
                  loading={previewQuery.isFetching}
                  pagination={false}
                  scroll={{ x: 'max-content', y: '100%' }}
                />
              </div>
            ),
          },
        ].filter((item) => !tdEngine || item.key !== 'indexes')}
      />
    </>
  ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tdEngine ? '请选择超级表' : '请选择数据表'} />;

  return (
    <div className="data-source-metadata-panel">
      {namespacesQuery.isError && (
        <Alert type="error" showIcon message={errorMessage(namespacesQuery.error, '读取库和 Schema 失败')} />
      )}
      {tablesQuery.isError && (
        <Alert type="error" showIcon message={errorMessage(tablesQuery.error, tdEngine ? '读取超级表失败' : '读取数据表失败')} />
      )}
      <div className="metadata-workspace">
        <aside className="metadata-table-browser">
          <div className="metadata-browser-toolbar">
            <div className="metadata-browser-heading">
              <Typography.Text strong>数据对象 {tablesQuery.data?.tables.length ?? 0}</Typography.Text>
              <Tooltip title="刷新数据对象">
                <Button
                  type="text"
                  size="small"
                  icon={<ReloadOutlined />}
                  aria-label="刷新数据对象"
                  loading={tablesQuery.isFetching}
                  onClick={() => void tablesQuery.refetch()}
                />
              </Tooltip>
            </div>
            <Select
              value={effectiveNamespaceKey}
              loading={namespacesQuery.isFetching}
              placeholder="选择库 / Schema"
              aria-label="选择库或 Schema"
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
            <Input
              allowClear
              value={keyword}
              placeholder={tdEngine ? '筛选超级表名' : '筛选表名'}
              aria-label={tdEngine ? '筛选超级表名' : '筛选表名'}
              className="metadata-table-search"
              onChange={(event) => setKeyword(event.target.value)}
              onPressEnter={() => setSubmittedKeyword(keyword.trim())}
              onClear={() => { setKeyword(''); setSubmittedKeyword(''); }}
              suffix={(
                <Tooltip title="搜索表名">
                  <Button type="text" icon={<SearchOutlined />} aria-label="搜索表名" onClick={() => setSubmittedKeyword(keyword.trim())} />
                </Tooltip>
              )}
            />
            {(!tdEngine || tablesQuery.data?.truncated) && (
              <div className="metadata-browser-options">
                {!tdEngine && (
                  <Checkbox checked={includeViews} onChange={(event) => setIncludeViews(event.target.checked)}>包含视图</Checkbox>
                )}
                {tablesQuery.data?.truncated && <Typography.Text type="warning">最多显示 500 个对象</Typography.Text>}
              </div>
            )}
          </div>
          <Spin spinning={tablesQuery.isFetching}>
            <Table<DataSourceTable>
              className="metadata-object-table management-table"
              size="small"
              showHeader={false}
              rowKey={(table) => identifierKey(table.identifier)}
              dataSource={tablesQuery.data?.tables ?? []}
              pagination={false}
              columns={[{
                key: 'table',
                render: (_: unknown, table: DataSourceTable) => (
                  <div className="metadata-table-item">
                    <Tooltip title={tableTypeLabel(table, tdEngine)}>
                      <span className={`metadata-table-kind${table.type === 'VIEW' ? ' metadata-table-kind-view' : ''}`}>
                        {table.type === 'VIEW' ? <EyeOutlined /> : <TableOutlined />}
                      </span>
                    </Tooltip>
                    <span className="metadata-table-item-name">
                      {table.identifier.table}
                    </span>
                  </div>
                ),
              }]}
              rowClassName={(table) => identifierKey(table.identifier) === (selectedTable ? identifierKey(selectedTable.identifier) : '')
                ? 'metadata-table-row-selected'
                : ''}
              onRow={(table) => ({
                tabIndex: 0,
                'aria-selected': identifierKey(table.identifier) === (selectedTable ? identifierKey(selectedTable.identifier) : ''),
                onClick: () => setSelectedTableKey(identifierKey(table.identifier)),
                onKeyDown: (event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault();
                    setSelectedTableKey(identifierKey(table.identifier));
                  }
                },
              })}
              scroll={{ y: '100%' }}
              locale={{ emptyText: tdEngine ? '当前条件下没有超级表' : '当前条件下没有数据表' }}
            />
          </Spin>
        </aside>
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
