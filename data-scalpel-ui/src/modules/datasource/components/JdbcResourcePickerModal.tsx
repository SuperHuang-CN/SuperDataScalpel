import { SearchOutlined, TableOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Modal, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useDataSource, useDataSourceTables, useDataSources } from '../hooks/useDataSources';
import { buildDataSourceSearch } from '../model/dataSourceSearch';
import type { DataSource, DataSourcePurpose, DataSourceTable, TableIdentifier } from '../model/dataSource';
import { jdbcTableIdentifierDisplayName, jdbcTableIdentifierKey } from '../model/jdbcTableIdentifier';

const SEARCH_DELAY_MS = 300;
const DATA_SOURCE_PAGE_SIZE = 50;
const TABLE_PAGE_SIZE = 100;

export interface JdbcResourceSelection {
  dataSourceId: string;
  dataSourceName: string;
  dataSourceCode: string;
  table: TableIdentifier | null;
}

interface JdbcResourcePickerModalProps {
  open: boolean;
  value: JdbcResourceSelection | null;
  purposes: readonly Extract<DataSourcePurpose, 'SOURCE' | 'DISTRIBUTION'>[];
  requireTable: boolean;
  onCancel: () => void;
  onConfirm: (selection: JdbcResourceSelection) => void;
  title?: string;
  rootClassName?: string;
}

const dataSourceContext = (dataSource: DataSource): string => {
  return dataSource.type;
};

const selectableDataSource = (dataSource: DataSource, purposes: readonly DataSourcePurpose[]): boolean => (
  dataSource.enabled
  && dataSource.connectionKind === 'JDBC'
  && purposes.every((purpose) => dataSource.purposes.includes(purpose))
);

const uniqueDataSources = (dataSources: DataSource[]): DataSource[] => (
  [...new Map(dataSources.map((dataSource) => [dataSource.id, dataSource])).values()]
);

const tableColumns: TableColumnsType<DataSourceTable> = [
  {
    title: '物理表名',
    key: 'tableName',
    width: '62%',
    ellipsis: true,
    render: (_, table) => (
      <div className="resource-picker-table-name" title={jdbcTableIdentifierDisplayName(table.identifier)}>
        <TableOutlined />
        <span>{jdbcTableIdentifierDisplayName(table.identifier)}</span>
      </div>
    ),
  },
  {
    title: '说明',
    key: 'comment',
    ellipsis: true,
    render: (_, table) => <Typography.Text type="secondary" ellipsis title={table.comment || '—'}>{table.comment || '—'}</Typography.Text>,
  },
];

export const JdbcResourcePickerModal = ({
  open,
  value,
  purposes,
  requireTable,
  onCancel,
  onConfirm,
  title = '选择 JDBC 资源',
  rootClassName,
}: JdbcResourcePickerModalProps) => {
  if (!open) return null;
  return (
    <JdbcResourcePickerModalContent
      key={`${purposes.join(',')}:${requireTable}:${value?.dataSourceId ?? ''}:${value?.table ? jdbcTableIdentifierKey(value.table) : ''}`}
      value={value}
      purposes={purposes}
      requireTable={requireTable}
      onCancel={onCancel}
      onConfirm={onConfirm}
      title={title}
      rootClassName={rootClassName}
    />
  );
};

type JdbcResourcePickerModalContentProps = Omit<JdbcResourcePickerModalProps, 'open'>;

const JdbcResourcePickerModalContent = ({
  value,
  purposes,
  requireTable,
  onCancel,
  onConfirm,
  title,
  rootClassName,
}: JdbcResourcePickerModalContentProps) => {
  const [sourceOpen, setSourceOpen] = useState(false);
  const [sourceKeyword, setSourceKeyword] = useState('');
  const [tableSearch, setTableSearch] = useState('');
  const [tableKeyword, setTableKeyword] = useState('');
  const [dataSourceId, setDataSourceId] = useState(value?.dataSourceId ?? '');
  const [table, setTable] = useState<TableIdentifier | null>(() => value?.table ? { ...value.table } : null);
  const sourceTimerRef = useRef<number | null>(null);
  const tableTimerRef = useRef<number | null>(null);
  const selectedSourceQuery = useDataSource(dataSourceId || undefined, Boolean(dataSourceId));
  const sourceRequest = useMemo(() => ({
    search: buildDataSourceSearch({ keyword: sourceKeyword, enabled: true }),
    page: 0,
    size: DATA_SOURCE_PAGE_SIZE,
    sort: 'name',
  }), [sourceKeyword]);
  const sourcesQuery = useDataSources(sourceRequest, sourceOpen);
  const sources = useMemo(() => uniqueDataSources([
    ...(selectedSourceQuery.data ? [selectedSourceQuery.data] : []),
    ...(sourcesQuery.data?.content ?? []),
  ].filter((source) => selectableDataSource(source, purposes))), [purposes, selectedSourceQuery.data, sourcesQuery.data?.content]);
  const sourceById = useMemo(() => new Map(sources.map((source) => [source.id, source])), [sources]);
  const tablesQuery = useDataSourceTables(dataSourceId || undefined, {
    keyword: tableKeyword || undefined,
    includeViews: false,
    limit: TABLE_PAGE_SIZE,
  }, requireTable && Boolean(dataSourceId));
  const tables = tablesQuery.data?.tables ?? [];
  const selectedSource = sourceById.get(dataSourceId) ?? selectedSourceQuery.data;

  useEffect(() => () => {
    if (sourceTimerRef.current !== null) window.clearTimeout(sourceTimerRef.current);
    if (tableTimerRef.current !== null) window.clearTimeout(tableTimerRef.current);
  }, []);

  const updateSourceSearch = (next: string) => {
    if (sourceTimerRef.current !== null) window.clearTimeout(sourceTimerRef.current);
    sourceTimerRef.current = window.setTimeout(() => {
      setSourceKeyword(next.trim());
      sourceTimerRef.current = null;
    }, SEARCH_DELAY_MS);
  };

  const updateTableSearch = (next: string) => {
    setTableSearch(next);
    if (tableTimerRef.current !== null) window.clearTimeout(tableTimerRef.current);
    tableTimerRef.current = window.setTimeout(() => {
      setTableKeyword(next.trim());
      tableTimerRef.current = null;
    }, SEARCH_DELAY_MS);
  };

  const confirm = () => {
    if (!selectedSource) return;
    onConfirm({
      dataSourceId: selectedSource.id,
      dataSourceName: selectedSource.name,
      dataSourceCode: selectedSource.code,
      table,
    });
  };

  const confirmDisabled = !selectedSource || (requireTable && !table);

  return (
    <Modal
      open
      width={760}
      title={title}
      rootClassName={rootClassName}
      onCancel={onCancel}
      footer={(
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" disabled={confirmDisabled} onClick={confirm}>确定</Button>
        </Space>
      )}
    >
      <div className="jdbc-resource-picker">
        <section className="jdbc-resource-picker-source">
          <Typography.Text strong>数据源</Typography.Text>
          <Select<string>
            showSearch
            virtual
            value={dataSourceId || undefined}
            open={sourceOpen}
            placeholder="按名称或编码搜索 JDBC 数据源"
            filterOption={false}
            loading={sourcesQuery.isFetching || selectedSourceQuery.isFetching}
            popupMatchSelectWidth={520}
            options={sources.map((source) => ({ value: source.id, label: `${source.name} · ${source.code}` }))}
            optionRender={(option) => {
              const source = sourceById.get(String(option.value));
              if (!source) return option.label;
              return (
                <div className="resource-picker-identity">
                  <Typography.Text ellipsis>{source.name}</Typography.Text>
                  <Typography.Text type="secondary" ellipsis>{dataSourceContext(source)} · {source.code}</Typography.Text>
                </div>
              );
            }}
            notFoundContent={sourcesQuery.isFetching
              ? <Spin size="small" />
              : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可用的 JDBC 数据源" />}
            onOpenChange={setSourceOpen}
            onSearch={updateSourceSearch}
            onChange={(nextId) => {
              setDataSourceId(nextId);
              setTable(null);
              setTableSearch('');
              setTableKeyword('');
            }}
          />
        </section>

        {requireTable && (
          <section className="jdbc-resource-picker-table">
            <div className="resource-picker-heading">
              <div>
                <strong>数据表</strong>
                <Typography.Text type="secondary"> 选择一张用于本地开发的物理表</Typography.Text>
              </div>
              {table && <Tag color="blue">已选择</Tag>}
            </div>
            <Input
              allowClear
              autoComplete="off"
              name="jdbc-resource-picker-table-search"
              value={tableSearch}
              disabled={!dataSourceId}
              prefix={<SearchOutlined />}
              placeholder="输入物理表名搜索"
              onChange={(event) => updateTableSearch(event.target.value)}
            />
            <div className="jdbc-resource-picker-table-list">
              <Table<DataSourceTable>
                size="small"
                columns={tableColumns}
                dataSource={tables}
                rowKey={(candidate) => jdbcTableIdentifierKey(candidate.identifier)}
                pagination={false}
                loading={tablesQuery.isFetching}
                scroll={{ y: 'calc(clamp(260px, 42vh, 380px) - 112px)' }}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={!dataSourceId ? '请先选择 JDBC 数据源' : tablesQuery.isError ? '读取物理表失败' : '没有匹配的数据表'} /> }}
                rowSelection={{
                  type: 'radio',
                  columnWidth: 40,
                  selectedRowKeys: table ? [jdbcTableIdentifierKey(table)] : [],
                  getCheckboxProps: (candidate) => ({ 'aria-label': `选择物理表 ${jdbcTableIdentifierDisplayName(candidate.identifier)}` }),
                  onSelect: (candidate, checked) => setTable(checked ? { ...candidate.identifier } : null),
                }}
                onRow={(candidate) => ({
                  onClick: (event) => {
                    const target = event.target as HTMLElement;
                    if (target.closest('.ant-checkbox-wrapper')) return;
                    const key = jdbcTableIdentifierKey(candidate.identifier);
                    setTable((current) => current && jdbcTableIdentifierKey(current) === key ? null : { ...candidate.identifier });
                  },
                })}
              />
            </div>
            <Typography.Text type={tablesQuery.data?.truncated ? 'warning' : 'secondary'} className="resource-picker-tip">
              {tablesQuery.data?.truncated
                ? `匹配结果超过 ${TABLE_PAGE_SIZE} 项，请继续输入表名缩小范围。`
                : '仅展示当前数据源范围内的物理表。'}
            </Typography.Text>
          </section>
        )}
      </div>
    </Modal>
  );
};
