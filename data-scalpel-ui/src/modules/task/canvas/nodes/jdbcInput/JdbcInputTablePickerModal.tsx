import { ApartmentOutlined, DeleteOutlined, SearchOutlined, TableOutlined } from '@ant-design/icons';
import { Button, Empty, Input, List, Modal, Popconfirm, Space, Table, Tag, Typography } from 'antd';
import type { TableColumnsType } from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useDataSourceTables, type DataSourceTable } from '../../../../datasource';
import type { JdbcInputTableSelection } from '../../canvasTypes';

const SEARCH_DELAY_MS = 300;
const CANDIDATE_LIMIT = 100;

interface JdbcInputTablePickerModalProps {
  open: boolean;
  dataSourceId: string;
  value: readonly JdbcInputTableSelection[];
  onCancel: () => void;
  onConfirm: (tables: JdbcInputTableSelection[]) => void;
}

const tableName = (table: DataSourceTable) => table.identifier.table;

const candidateColumns: TableColumnsType<DataSourceTable> = [
  {
    title: '物理表名',
    key: 'tableName',
    width: '58%',
    ellipsis: true,
    render: (_, table) => {
      const name = tableName(table);
      const isSupertable = table.type === 'SUPERTABLE';
      return (
        <div className="canvas-jdbc-input-picker-table-name" title={name}>
          <span
            className={`canvas-jdbc-input-picker-table-type-icon ${isSupertable ? 'is-supertable' : ''}`}
            role="img"
            aria-label={isSupertable ? '超级表' : '普通表'}
            title={isSupertable ? '超级表' : '普通表'}
          >
            {isSupertable ? <ApartmentOutlined /> : <TableOutlined />}
          </span>
          <span>{name}</span>
        </div>
      );
    },
  },
  {
    title: '说明',
    key: 'comment',
    ellipsis: true,
    render: (_, table) => (
      <Typography.Text
        className="canvas-jdbc-input-picker-table-comment"
        type="secondary"
        ellipsis
        title={table.comment || '—'}
      >
        {table.comment || '—'}
      </Typography.Text>
    ),
  },
];

export const JdbcInputTablePickerModal = ({
  open,
  dataSourceId,
  value,
  onCancel,
  onConfirm,
}: JdbcInputTablePickerModalProps) => {
  if (!open) return null;
  return (
    <JdbcInputTablePickerModalContent
      key={`${dataSourceId}:${JSON.stringify(value)}`}
      dataSourceId={dataSourceId}
      value={value}
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
};

interface JdbcInputTablePickerModalContentProps {
  dataSourceId: string;
  value: readonly JdbcInputTableSelection[];
  onCancel: () => void;
  onConfirm: (tables: JdbcInputTableSelection[]) => void;
}

const JdbcInputTablePickerModalContent = ({
  dataSourceId,
  value,
  onCancel,
  onConfirm,
}: JdbcInputTablePickerModalContentProps) => {
  const [search, setSearch] = useState('');
  const [keyword, setKeyword] = useState('');
  const [draftTables, setDraftTables] = useState<JdbcInputTableSelection[]>(() => value.map((table) => ({
    ...table,
    readOptions: table.readOptions.map((option) => ({ ...option })),
  })));
  const searchTimerRef = useRef<number | null>(null);

  useEffect(() => () => {
    if (searchTimerRef.current !== null) window.clearTimeout(searchTimerRef.current);
  }, []);

  const tablesQuery = useDataSourceTables(dataSourceId || undefined, {
    keyword: keyword || undefined,
    includeViews: false,
    limit: CANDIDATE_LIMIT,
  }, Boolean(dataSourceId));
  const candidates = tablesQuery.data?.tables ?? [];
  const selectedNames = useMemo(
    () => new Set(draftTables.map((table) => table.tableName)),
    [draftTables],
  );
  const hasConfiguredReadOptions = draftTables.some((table) => table.readOptions.length > 0);

  const updateSearch = (next: string) => {
    setSearch(next);
    if (searchTimerRef.current !== null) window.clearTimeout(searchTimerRef.current);
    searchTimerRef.current = window.setTimeout(() => {
      setKeyword(next.trim());
      searchTimerRef.current = null;
    }, SEARCH_DELAY_MS);
  };

  const toggleTable = (name: string, checked: boolean) => {
    setDraftTables((current) => checked
      ? current.some((table) => table.tableName === name)
        ? current
        : [...current, { tableName: name, readOptions: [] }]
      : current.filter((table) => table.tableName !== name));
  };

  const requestToggleTable = (name: string, checked: boolean) => {
    const selected = draftTables.find((table) => table.tableName === name);
    if (checked || !selected || selected.readOptions.length === 0) {
      toggleTable(name, checked);
      return;
    }
    Modal.confirm({
      title: '取消选择物理表？',
      content: `同时删除 ${selected.readOptions.length} 项读取参数。`,
      okText: '取消选择',
      cancelText: '保留',
      okButtonProps: { danger: true },
      onOk: () => toggleTable(name, false),
    });
  };

  const selectVisible = () => {
    setDraftTables((current) => {
      const names = new Set(current.map((table) => table.tableName));
      return [
        ...current,
        ...candidates.flatMap((table): JdbcInputTableSelection[] => {
          const name = tableName(table);
          if (names.has(name)) return [];
          names.add(name);
          return [{ tableName: name, readOptions: [] }];
        }),
      ];
    });
  };

  const toggleTables = (tables: readonly DataSourceTable[], checked: boolean) => {
    const changedNames = new Set(tables.map(tableName));
    setDraftTables((current) => {
      if (!checked) return current.filter((table) => !changedNames.has(table.tableName));
      const existingNames = new Set(current.map((table) => table.tableName));
      return [
        ...current,
        ...tables.flatMap((table): JdbcInputTableSelection[] => {
          const name = tableName(table);
          if (existingNames.has(name)) return [];
          existingNames.add(name);
          return [{ tableName: name, readOptions: [] }];
        }),
      ];
    });
  };

  const requestToggleTables = (tables: readonly DataSourceTable[], checked: boolean) => {
    if (checked) {
      toggleTables(tables, true);
      return;
    }
    const configuredCount = tables.reduce((count, table) => {
      const selected = draftTables.find((candidate) => candidate.tableName === tableName(table));
      return count + (selected && selected.readOptions.length > 0 ? 1 : 0);
    }, 0);
    if (configuredCount === 0) {
      toggleTables(tables, false);
      return;
    }
    Modal.confirm({
      title: '取消选择物理表？',
      content: `其中 ${configuredCount} 张表带有读取参数，取消选择会同时删除这些配置。`,
      okText: '取消选择',
      cancelText: '保留',
      okButtonProps: { danger: true },
      onOk: () => toggleTables(tables, false),
    });
  };

  return (
    <Modal
      open
      width={860}
      className="canvas-jdbc-input-table-picker"
      title="选择 JDBC 物理表"
      onCancel={onCancel}
      footer={(
        <Space>
          <Button onClick={onCancel}>取消</Button>
          <Button type="primary" onClick={() => onConfirm(draftTables)}>
            确定 · {draftTables.length} 张表
          </Button>
        </Space>
      )}
    >
      <div className="canvas-jdbc-input-picker-grid">
        <section className="canvas-jdbc-input-picker-pane">
          <div className="canvas-jdbc-input-picker-heading">
            <div>
              <strong>候选物理表</strong>
              <Typography.Text type="secondary"> 每次最多返回 {CANDIDATE_LIMIT} 项</Typography.Text>
            </div>
            <Button
              type="link"
              size="small"
              disabled={candidates.length === 0}
              onClick={selectVisible}
            >
              选择当前结果
            </Button>
          </div>
          <Input
            autoFocus
            allowClear
            autoComplete="off"
            name="jdbc-input-table-search"
            value={search}
            prefix={<SearchOutlined />}
            placeholder="输入物理表名搜索"
            onChange={(event) => updateSearch(event.target.value)}
          />
          <div className="canvas-jdbc-input-picker-candidate-table">
            <Table<DataSourceTable>
              size="small"
              columns={candidateColumns}
              dataSource={candidates}
              rowKey={tableName}
              pagination={false}
              loading={tablesQuery.isFetching}
              scroll={{ y: 'calc(clamp(360px, 58vh, 520px) - 192px)' }}
              locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tablesQuery.isError ? '读取物理表失败' : '没有匹配的物理表'} /> }}
              rowSelection={{
                columnWidth: 40,
                preserveSelectedRowKeys: true,
                selectedRowKeys: [...selectedNames],
                getCheckboxProps: (table) => ({ 'aria-label': `选择物理表 ${tableName(table)}` }),
                onSelect: (table, checked) => requestToggleTable(tableName(table), checked),
                onSelectAll: (checked, _selectedRows, changedRows) => requestToggleTables(changedRows, checked),
              }}
              onRow={(table) => ({
                onClick: (event) => {
                  const target = event.target as HTMLElement;
                  if (target.closest('.ant-checkbox-wrapper')) return;
                  const name = tableName(table);
                  requestToggleTable(name, !selectedNames.has(name));
                },
              })}
            />
          </div>
          <Typography.Text type={tablesQuery.data?.truncated ? 'warning' : 'secondary'} className="canvas-jdbc-input-picker-tip">
            {tablesQuery.isError ? (
              <Space size={4}>
                <span>物理表读取失败。</span>
                <Button type="link" size="small" onClick={() => void tablesQuery.refetch()}>重试</Button>
              </Space>
            ) : tablesQuery.data?.truncated
              ? `匹配结果超过 ${CANDIDATE_LIMIT} 项，请继续输入表名缩小范围；已选项不会因搜索而丢失。`
              : '候选列表只读取表摘要；字段信息仅为已选表加载。'}
          </Typography.Text>
        </section>

        <section className="canvas-jdbc-input-picker-pane is-selected">
          <div className="canvas-jdbc-input-picker-heading">
            <div><strong>已选物理表</strong> <Tag color="blue">{draftTables.length}</Tag></div>
            {hasConfiguredReadOptions ? (
              <Popconfirm
                title="清空已选物理表？"
                description="同时删除这些表的读取参数配置。"
                okText="清空"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                onConfirm={() => setDraftTables([])}
              >
                <Button type="link" size="small" danger disabled={draftTables.length === 0}>清空</Button>
              </Popconfirm>
            ) : (
              <Button
                type="link"
                size="small"
                danger
                disabled={draftTables.length === 0}
                onClick={() => setDraftTables([])}
              >
                清空
              </Button>
            )}
          </div>
          <div className="canvas-jdbc-input-picker-list is-selected">
            <List<JdbcInputTableSelection>
              size="small"
              dataSource={draftTables}
              locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未选择物理表" /> }}
              renderItem={(table, index) => (
                <List.Item
                  className="canvas-jdbc-input-picker-selected-item"
                  actions={[
                    table.readOptions.length > 0 ? (
                      <Popconfirm
                        key="remove"
                        title="移除物理表？"
                        description={`同时删除 ${table.readOptions.length} 项读取参数。`}
                        okText="移除"
                        cancelText="取消"
                        okButtonProps={{ danger: true }}
                        onConfirm={() => toggleTable(table.tableName, false)}
                      >
                        <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`移除物理表 ${table.tableName}`} />
                      </Popconfirm>
                    ) : (
                      <Button
                        key="remove"
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        aria-label={`移除物理表 ${table.tableName}`}
                        onClick={() => toggleTable(table.tableName, false)}
                      />
                    ),
                  ]}
                >
                  <span className="canvas-jdbc-input-picker-index">{index + 1}</span>
                  <Typography.Text ellipsis title={table.tableName}>{table.tableName}</Typography.Text>
                </List.Item>
              )}
            />
          </div>
        </section>
      </div>
    </Modal>
  );
};
