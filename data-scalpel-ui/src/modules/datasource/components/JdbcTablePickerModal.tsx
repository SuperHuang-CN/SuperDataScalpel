import { ApartmentOutlined, DeleteOutlined, SearchOutlined, TableOutlined } from '@ant-design/icons';
import { Button, Empty, Input, Modal, Space, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useDataSourceTables } from '../hooks/useDataSources';
import type { DataSourceTable, TableIdentifier } from '../model/dataSource';
import { jdbcTableIdentifierDisplayName, jdbcTableIdentifierKey } from '../model/jdbcTableIdentifier';

const SEARCH_DELAY_MS = 300;
const CANDIDATE_LIMIT = 100;

export type JdbcTablePickerSelectionMode = 'single' | 'multiple';

interface JdbcTablePickerModalProps {
  open: boolean;
  dataSourceId: string;
  value: readonly TableIdentifier[];
  onCancel: () => void;
  onConfirm: (tables: TableIdentifier[]) => void;
  title?: string;
  selectionMode?: JdbcTablePickerSelectionMode;
  rootClassName?: string;
}

const tableColumns: TableColumnsType<DataSourceTable> = [
  {
    title: '物理表名',
    key: 'tableName',
    width: '58%',
    ellipsis: true,
    render: (_, table) => (
      <div className="resource-picker-table-name" title={jdbcTableIdentifierDisplayName(table.identifier)}>
        {table.type === 'SUPERTABLE' ? <ApartmentOutlined /> : <TableOutlined />}
        <span>{jdbcTableIdentifierDisplayName(table.identifier)}</span>
      </div>
    ),
  },
  {
    title: '说明',
    key: 'comment',
    ellipsis: true,
    render: (_, table) => (
      <Typography.Text type="secondary" ellipsis title={table.comment || '—'}>
        {table.comment || '—'}
      </Typography.Text>
    ),
  },
];

export const JdbcTablePickerModal = ({
  open,
  dataSourceId,
  value,
  onCancel,
  onConfirm,
  title = '选择 JDBC 物理表',
  selectionMode = 'single',
  rootClassName,
}: JdbcTablePickerModalProps) => {
  if (!open) return null;
  return (
    <JdbcTablePickerModalContent
      key={`${dataSourceId}:${selectionMode}:${value.map(jdbcTableIdentifierKey).join(',')}`}
      dataSourceId={dataSourceId}
      value={value}
      onCancel={onCancel}
      onConfirm={onConfirm}
      title={title}
      selectionMode={selectionMode}
      rootClassName={rootClassName}
    />
  );
};

type JdbcTablePickerModalContentProps = Omit<JdbcTablePickerModalProps, 'open'>;

const JdbcTablePickerModalContent = ({
  dataSourceId,
  value,
  onCancel,
  onConfirm,
  title,
  selectionMode,
  rootClassName,
}: JdbcTablePickerModalContentProps) => {
  const [search, setSearch] = useState('');
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<TableIdentifier[]>(() => value.map((table) => ({ ...table })));
  const searchTimerRef = useRef<number | null>(null);
  const tablesQuery = useDataSourceTables(dataSourceId || undefined, {
    keyword: keyword || undefined,
    includeViews: false,
    limit: CANDIDATE_LIMIT,
  }, Boolean(dataSourceId));
  const candidates = useMemo(() => tablesQuery.data?.tables ?? [], [tablesQuery.data?.tables]);
  const selectedKeys = useMemo(() => new Set(selected.map(jdbcTableIdentifierKey)), [selected]);
  const candidateByKey = useMemo(() => new Map(candidates.map((table) => [jdbcTableIdentifierKey(table.identifier), table])), [candidates]);

  useEffect(() => () => {
    if (searchTimerRef.current !== null) window.clearTimeout(searchTimerRef.current);
  }, []);

  const updateSearch = (next: string) => {
    setSearch(next);
    if (searchTimerRef.current !== null) window.clearTimeout(searchTimerRef.current);
    searchTimerRef.current = window.setTimeout(() => {
      setKeyword(next.trim());
      searchTimerRef.current = null;
    }, SEARCH_DELAY_MS);
  };

  const select = (identifier: TableIdentifier, checked: boolean) => {
    const key = jdbcTableIdentifierKey(identifier);
    setSelected((current) => {
      if (selectionMode === 'single') return checked ? [{ ...identifier }] : [];
      return checked
        ? current.some((table) => jdbcTableIdentifierKey(table) === key) ? current : [...current, { ...identifier }]
        : current.filter((table) => jdbcTableIdentifierKey(table) !== key);
    });
  };

  const selectVisible = () => {
    if (selectionMode === 'single') return;
    setSelected((current) => {
      const keys = new Set(current.map(jdbcTableIdentifierKey));
      return [
        ...current,
        ...candidates.flatMap((candidate): TableIdentifier[] => {
          const key = jdbcTableIdentifierKey(candidate.identifier);
          if (keys.has(key)) return [];
          keys.add(key);
          return [{ ...candidate.identifier }];
        }),
      ];
    });
  };

  return (
    <Modal
      open
      width={860}
      title={title}
      rootClassName={rootClassName}
      onCancel={onCancel}
      footer={(
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" onClick={() => onConfirm(selected)}>
            {selectionMode === 'single' ? '确定' : `确定 · ${selected.length} 张表`}
          </Button>
        </Space>
      )}
    >
      <div className="resource-picker-grid">
        <section className="resource-picker-pane">
          <div className="resource-picker-heading">
            <div>
              <strong>候选物理表</strong>
              <Typography.Text type="secondary"> 每次最多返回 {CANDIDATE_LIMIT} 项</Typography.Text>
            </div>
            {selectionMode === 'multiple' && (
              <Button type="link" size="small" disabled={candidates.length === 0} onClick={selectVisible}>
                选择当前结果
              </Button>
            )}
          </div>
          <Input
            autoFocus
            allowClear
            autoComplete="off"
            name="jdbc-table-picker-search"
            value={search}
            prefix={<SearchOutlined />}
            placeholder="输入物理表名搜索"
            onChange={(event) => updateSearch(event.target.value)}
          />
          <div className="resource-picker-candidates">
            <Table<DataSourceTable>
              size="small"
              columns={tableColumns}
              dataSource={candidates}
              rowKey={(table) => jdbcTableIdentifierKey(table.identifier)}
              pagination={false}
              loading={tablesQuery.isFetching}
              scroll={{ y: 'calc(clamp(360px, 58vh, 520px) - 192px)' }}
              locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tablesQuery.isError ? '读取物理表失败' : '没有匹配的物理表'} /> }}
              rowSelection={{
                type: selectionMode === 'single' ? 'radio' : 'checkbox',
                columnWidth: 40,
                preserveSelectedRowKeys: true,
                selectedRowKeys: [...selectedKeys],
                getCheckboxProps: (table) => ({ 'aria-label': `选择物理表 ${jdbcTableIdentifierDisplayName(table.identifier)}` }),
                onSelect: (table, checked) => select(table.identifier, checked),
                ...(selectionMode === 'multiple' ? {
                  onSelectAll: (checked: boolean, _tables: DataSourceTable[], changedTables: DataSourceTable[]) => {
                    if (!checked) {
                      const changedKeys = new Set(changedTables.map((table) => jdbcTableIdentifierKey(table.identifier)));
                      setSelected((current) => current.filter((table) => !changedKeys.has(jdbcTableIdentifierKey(table))));
                      return;
                    }
                    setSelected((current) => {
                      const keys = new Set(current.map(jdbcTableIdentifierKey));
                      return [
                        ...current,
                        ...changedTables.flatMap((table): TableIdentifier[] => {
                          const key = jdbcTableIdentifierKey(table.identifier);
                          if (keys.has(key)) return [];
                          keys.add(key);
                          return [{ ...table.identifier }];
                        }),
                      ];
                    });
                  },
                } : {}),
              }}
              onRow={(table) => ({
                onClick: (event) => {
                  const target = event.target as HTMLElement;
                  if (target.closest('.ant-checkbox-wrapper')) return;
                  const key = jdbcTableIdentifierKey(table.identifier);
                  select(table.identifier, !selectedKeys.has(key));
                },
              })}
            />
          </div>
          <Typography.Text type={tablesQuery.data?.truncated ? 'warning' : 'secondary'} className="resource-picker-tip">
            {tablesQuery.isError ? '物理表读取失败，请关闭后重试。' : tablesQuery.data?.truncated
              ? `匹配结果超过 ${CANDIDATE_LIMIT} 项，请继续输入表名缩小范围。`
              : '已选表不会因搜索条件变化而丢失。'}
          </Typography.Text>
        </section>

        <section className="resource-picker-pane is-selected">
          <div className="resource-picker-heading">
            <div><strong>已选物理表</strong> <Tag color="blue">{selected.length}</Tag></div>
            <Button type="link" size="small" danger disabled={selected.length === 0} onClick={() => setSelected([])}>
              清空
            </Button>
          </div>
          <div className="resource-picker-selected-list">
            {selected.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未选择物理表" />
            ) : selected.map((identifier) => {
              const key = jdbcTableIdentifierKey(identifier);
              const source = candidateByKey.get(key);
              return (
                <div className="resource-picker-selected-row" key={key}>
                  {source?.type === 'SUPERTABLE' ? <ApartmentOutlined /> : <TableOutlined />}
                  <Typography.Text ellipsis title={jdbcTableIdentifierDisplayName(identifier)}>{jdbcTableIdentifierDisplayName(identifier)}</Typography.Text>
                  <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label="移除物理表" onClick={() => select(identifier, false)} />
                </div>
              );
            })}
          </div>
        </section>
      </div>
    </Modal>
  );
};
