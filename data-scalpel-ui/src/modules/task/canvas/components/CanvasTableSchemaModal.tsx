import {
  CheckCircleFilled,
  CodeOutlined,
  ExclamationCircleOutlined,
  FieldTimeOutlined,
  FileTextOutlined,
  MinusCircleOutlined,
  PlusCircleOutlined,
} from '@ant-design/icons';
import {
  Button,
  Empty,
  Input,
  Modal,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useMemo, useState } from 'react';
import { platformTypeLabel } from '../canvasSchema';
import type { CanvasColumnSchema, CanvasTableSchema } from '../canvasTypes';

interface SchemaTableColumnRow {
  key: string;
  column: CanvasColumnSchema;
  mapped: boolean;
}

export interface CanvasTableSchemaModalProps {
  open: boolean;
  title: string;
  tables: readonly CanvasTableSchema[];
  initialTableName?: string;
  mappedColumnNames?: readonly string[];
  mappedCount?: number;
  onClose: () => void;
}

const datasetKindLabel = (table: CanvasTableSchema) => (
  table.datasetKind === 'UNBOUNDED' ? '流表' : '有界表'
);

const tableLabel = (table: CanvasTableSchema) => `${table.name} · ${table.columns.length} 个字段`;

export const CanvasTableSchemaModal = ({
  open,
  title,
  tables,
  initialTableName,
  mappedColumnNames = [],
  mappedCount,
  onClose,
}: CanvasTableSchemaModalProps) => {
  const [selectedTableName, setSelectedTableName] = useState(initialTableName ?? tables[0]?.name ?? '');
  const [tableSearch, setTableSearch] = useState('');
  const [fieldSearch, setFieldSearch] = useState('');

  const selectedTable = tables.find((table) => table.name === selectedTableName) ?? tables[0];
  const normalizedTableSearch = tableSearch.trim().toLocaleLowerCase();
  const normalizedFieldSearch = fieldSearch.trim().toLocaleLowerCase();
  const visibleTables = normalizedTableSearch
    ? tables.filter((table) => table.name.toLocaleLowerCase().includes(normalizedTableSearch))
    : tables;
  const mappedNames = useMemo(() => new Set(mappedColumnNames), [mappedColumnNames]);
  const rows = selectedTable?.columns
    .filter((column) => !normalizedFieldSearch || [column.name, column.comment ?? '']
      .some((value) => value.toLocaleLowerCase().includes(normalizedFieldSearch)))
    .map((column, index) => ({
      key: `${column.name}-${index}`,
      column,
      mapped: mappedNames.has(column.name),
    })) ?? [];

  const columns: TableColumnsType<SchemaTableColumnRow> = [
    {
      title: '字段编码',
      key: 'name',
      width: 190,
      render: (_, row) => (
        <Typography.Text className="canvas-table-schema-code" ellipsis={{ tooltip: row.column.name }}>
          {row.column.name}
        </Typography.Text>
      ),
    },
    {
      title: '描述',
      key: 'comment',
      width: 250,
      render: (_, row) => {
        const comment = row.column.comment?.trim();
        return comment ? (
          <Typography.Text type="secondary" ellipsis={{ tooltip: comment }}>
            {comment}
          </Typography.Text>
        ) : '—';
      },
    },
    {
      title: '平台类型',
      key: 'type',
      width: 206,
      render: (_, row) => (
        <Typography.Text className="canvas-table-schema-type" ellipsis={{ tooltip: platformTypeLabel(row.column) }}>
          {platformTypeLabel(row.column)}
        </Typography.Text>
      ),
    },
    {
      title: '约束',
      key: 'constraints',
      width: mappedCount === undefined ? 132 : 178,
      render: (_, row) => (
        <span className="canvas-table-schema-constraints">
          {!row.column.nullable && <Tooltip title="非空"><ExclamationCircleOutlined className="is-required" /></Tooltip>}
          {row.column.defaultValue !== null && <Tooltip title="存在默认值"><FileTextOutlined /></Tooltip>}
          {row.column.autoIncrement && <Tooltip title="自增"><PlusCircleOutlined /></Tooltip>}
          {row.column.generated && <Tooltip title="生成字段"><CodeOutlined /></Tooltip>}
          {selectedTable?.eventTimeColumn === row.column.name && <Tooltip title="事件时间字段"><FieldTimeOutlined className="is-event-time" /></Tooltip>}
          {mappedCount !== undefined && (
            row.mapped
              ? <Tooltip title="已参与当前写入映射"><CheckCircleFilled className="is-mapped" /></Tooltip>
              : <Tooltip title="未参与当前写入映射"><MinusCircleOutlined className="is-unmapped" /></Tooltip>
          )}
        </span>
      ),
    },
  ];
  const close = () => {
    setTableSearch('');
    setFieldSearch('');
    onClose();
  };

  return (
    <Modal
      open={open && tables.length > 0}
      title={title}
      width={860}
      destroyOnHidden
      className="canvas-table-schema-modal"
      styles={{
        body: { overflow: 'hidden' },
        content: { maxWidth: 'calc(100vw - 32px)' },
      }}
      onCancel={close}
      footer={<Button type="primary" onClick={close}>关闭</Button>}
    >
      <div className="canvas-table-schema-layout">
        <aside className="canvas-table-schema-table-nav">
          <div className="canvas-table-schema-nav-heading">
            <Typography.Text strong>表</Typography.Text>
            <Typography.Text type="secondary">{tables.length} 张</Typography.Text>
          </div>
          <Input
            allowClear
            autoComplete="off"
            name="canvas-schema-table-search"
            size="small"
            placeholder="搜索表编码"
            value={tableSearch}
            onChange={(event) => setTableSearch(event.target.value)}
          />
          <div className="canvas-table-schema-table-list">
            {visibleTables.map((table) => (
              <button
                className={`canvas-table-schema-table-item${selectedTable?.name === table.name ? ' is-active' : ''}`}
                type="button"
                key={table.name}
                onClick={() => {
                  setSelectedTableName(table.name);
                  setFieldSearch('');
                }}
              >
                <Typography.Text ellipsis={{ tooltip: table.name }}>{table.name}</Typography.Text>
                <span>
                  <Tag color={table.datasetKind === 'UNBOUNDED' ? 'gold' : 'blue'}>{datasetKindLabel(table)}</Tag>
                  {table.columns.length} 字段
                </span>
              </button>
            ))}
            {visibleTables.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有匹配的表" />}
          </div>
        </aside>
        <section className="canvas-table-schema-detail">
          {selectedTable ? (
            <>
              <div className="canvas-table-schema-detail-heading">
                <div>
                  <Typography.Text className="canvas-table-schema-title-code" ellipsis={{ tooltip: selectedTable.name }}>
                    {selectedTable.name}
                  </Typography.Text>
                  <Typography.Text type="secondary">{tableLabel(selectedTable)}</Typography.Text>
                </div>
                <span className="canvas-table-schema-detail-tags">
                  <Tag color={selectedTable.datasetKind === 'UNBOUNDED' ? 'gold' : 'blue'}>{datasetKindLabel(selectedTable)}</Tag>
                  {mappedCount !== undefined && <Tag color="green">映射 {mappedCount}/{selectedTable.columns.length}</Tag>}
                </span>
              </div>
              {selectedTable.datasetKind === 'UNBOUNDED' && (
                <div className="canvas-table-schema-stream-meta">
                  <span>事件时间：<Typography.Text code>{selectedTable.eventTimeColumn ?? '—'}</Typography.Text></span>
                  <span>Watermark：<Typography.Text code>{selectedTable.watermarkDelay ?? '—'}</Typography.Text></span>
                </div>
              )}
              <Input
                allowClear
                autoComplete="off"
                name="canvas-schema-field-search"
                placeholder="按字段编码或描述搜索"
                value={fieldSearch}
                onChange={(event) => setFieldSearch(event.target.value)}
              />
              <Table<SchemaTableColumnRow>
                className="canvas-table-schema-field-table"
                size="small"
                rowKey="key"
                pagination={false}
                tableLayout="fixed"
                scroll={{ y: 'min(430px, calc(100vh - 420px))' }}
                dataSource={rows}
                columns={columns}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有匹配的字段" /> }}
              />
            </>
          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可展示的输出表" />}
        </section>
      </div>
    </Modal>
  );
};
