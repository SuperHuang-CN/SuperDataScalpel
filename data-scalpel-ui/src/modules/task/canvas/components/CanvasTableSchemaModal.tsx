import {
  CheckCircleFilled,
  EyeOutlined,
  CodeOutlined,
  ExclamationCircleOutlined,
  FieldTimeOutlined,
  FileTextOutlined,
  MinusCircleOutlined,
  PlayCircleOutlined,
  PlusCircleOutlined,
  StopOutlined,
} from '@ant-design/icons';
import {
  Button,
  Drawer,
  Empty,
  Input,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useMemo, useState } from 'react';
import { useCancelTaskRun, useLatestCanvasNodeTrialRun } from '../../hooks/useTasks';
import {
  taskRunStatusColors,
  taskRunStatusLabels,
  type TaskRun,
  type TaskRunStatus,
} from '../../model/task';
import { platformTypeLabel } from '../canvasSchema';
import type { CanvasNodeTrialTarget } from '../canvasNodeTrial';
import type { CanvasColumnSchema, CanvasTableSchema } from '../canvasTypes';
import { CanvasTrialRunModal } from './CanvasTrialRunModal';

interface SchemaTableColumnRow {
  key: string;
  column: CanvasColumnSchema;
  mapped: boolean;
}

type CanvasTrialRunSelection =
  | {
    mode: 'new';
    target: CanvasNodeTrialTarget;
    columnNames: string[];
  }
  | {
    mode: 'existing';
    target: CanvasNodeTrialTarget;
    run: TaskRun;
  };

export interface CanvasTableSchemaModalProps {
  open: boolean;
  title: string;
  tables: readonly CanvasTableSchema[];
  initialTableName?: string;
  mappedColumnNames?: readonly string[];
  mappedCount?: number;
  createTrialTarget?: (tableName: string) => CanvasNodeTrialTarget | null;
  trialRunDisabled?: boolean;
  trialRunDisabledReason?: string;
  onClose: () => void;
}

interface CanvasTableSchemaDrawerProps extends CanvasTableSchemaModalProps {
  latestTrialRun?: TaskRun;
  cancelLatestTrialPending?: boolean;
  onCancelLatestTrial?: (runId: string) => void;
}

interface CanvasTableSchemaModalWithTrialProps extends CanvasTableSchemaModalProps {
  createTrialTarget: (tableName: string) => CanvasNodeTrialTarget | null;
}

const datasetKindLabel = (table: CanvasTableSchema) => (
  table.datasetKind === 'UNBOUNDED' ? '流表' : '有界表'
);

const tableLabel = (table: CanvasTableSchema) => `${table.name} · ${table.columns.length} 个字段`;

const activeTrialStatuses = new Set<TaskRunStatus>([
  'QUEUED', 'RUNNING', 'CANCEL_REQUESTED', 'STOP_REQUESTED',
]);

const trialViewLabel = (status: TaskRunStatus) => {
  if (activeTrialStatuses.has(status)) return '查看进度';
  if (status === 'SUCCESS') return '查看结果';
  if (status === 'FAILED' || status === 'TIMED_OUT') return '查看错误';
  return '查看详情';
};

const trialElapsedLabel = (run: TaskRun) => {
  const started = new Date(run.startedAt ?? run.queuedAt).getTime();
  const ended = run.endedAt ? new Date(run.endedAt).getTime() : Date.now();
  if (!Number.isFinite(started) || !Number.isFinite(ended)) return null;
  const seconds = Math.max(Math.floor((ended - started) / 1_000), 0);
  const hours = Math.floor(seconds / 3_600);
  const minutes = Math.floor((seconds % 3_600) / 60);
  const remainingSeconds = seconds % 60;
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(remainingSeconds).padStart(2, '0')}`;
};

const CanvasTableSchemaDrawer = ({
  open,
  title,
  tables,
  initialTableName,
  mappedColumnNames = [],
  mappedCount,
  createTrialTarget,
  trialRunDisabled = false,
  trialRunDisabledReason,
  latestTrialRun,
  cancelLatestTrialPending = false,
  onCancelLatestTrial,
  onClose,
}: CanvasTableSchemaDrawerProps) => {
  const [selectedTableName, setSelectedTableName] = useState(initialTableName ?? tables[0]?.name ?? '');
  const [tableSearch, setTableSearch] = useState('');
  const [fieldSearch, setFieldSearch] = useState('');
  const [trialSelectionMode, setTrialSelectionMode] = useState(false);
  const [selectedTrialColumnNames, setSelectedTrialColumnNames] = useState<string[]>([]);
  const [trialRunSelection, setTrialRunSelection] = useState<CanvasTrialRunSelection | null>(null);

  const selectedTable = tables.find((table) => table.name === selectedTableName) ?? tables[0];
  const currentTrialTarget = selectedTable && createTrialTarget
    ? createTrialTarget(selectedTable.name)
    : null;
  const latestTrialActive = Boolean(latestTrialRun && activeTrialStatuses.has(latestTrialRun.status));
  const normalizedTableSearch = tableSearch.trim().toLocaleLowerCase();
  const normalizedFieldSearch = fieldSearch.trim().toLocaleLowerCase();
  const visibleTables = normalizedTableSearch
    ? tables.filter((table) => table.name.toLocaleLowerCase().includes(normalizedTableSearch))
    : tables;
  const mappedNames = useMemo(() => new Set(mappedColumnNames), [mappedColumnNames]);
  const rows = selectedTable?.columns
    .filter((column) => !normalizedFieldSearch || [column.name, column.comment ?? '']
      .some((value) => value.toLocaleLowerCase().includes(normalizedFieldSearch)))
    .map((column) => ({
      key: column.name,
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
    setTrialSelectionMode(false);
    setSelectedTrialColumnNames([]);
    setTrialRunSelection(null);
    onClose();
  };

  const beginTrialSelection = (preferredTableName?: string, ignoreLatestSession = false) => {
    if (!selectedTable || !createTrialTarget || trialRunDisabled
        || latestTrialActive && !ignoreLatestSession) return;
    const trialTable = tables.find((table) => table.name === preferredTableName) ?? selectedTable;
    setSelectedTableName(trialTable.name);
    setFieldSearch('');
    setSelectedTrialColumnNames(trialTable.columns.map((column) => column.name));
    setTrialSelectionMode(true);
  };

  const launchTrial = () => {
    if (!selectedTable || !createTrialTarget || selectedTrialColumnNames.length === 0) return;
    const target = createTrialTarget(selectedTable.name);
    if (!target) return;
    const selectedNames = new Set(selectedTrialColumnNames);
    const orderedColumnNames = selectedTable.columns
      .filter((column) => selectedNames.has(column.name))
      .map((column) => column.name);
    setTrialSelectionMode(false);
    setTrialRunSelection({ mode: 'new', target, columnNames: orderedColumnNames });
  };

  const openLatestTrial = () => {
    if (!latestTrialRun || !createTrialTarget || !selectedTable) return;
    const target = createTrialTarget(latestTrialRun.canvasTrialTableName ?? selectedTable.name);
    if (!target) return;
    setTrialRunSelection({ mode: 'existing', target, run: latestTrialRun });
  };

  const latestTrialHistorical = Boolean(
    latestTrialRun && currentTrialTarget
    && latestTrialRun.definitionVersion !== currentTrialTarget.baseDefinitionVersion,
  );
  const trialCreationDisabled = trialRunDisabled || latestTrialActive;
  const trialCreationDisabledReason = latestTrialActive
    ? '当前节点仍有试运行在进行，请先查看进度或取消'
    : trialRunDisabledReason;

  return (
    <>
    <Drawer
      open={open && tables.length > 0 && trialRunSelection === null}
      title={title}
      placement="right"
      size={1120}
      destroyOnHidden
      className="canvas-table-schema-drawer"
      styles={{
        body: { overflow: 'hidden' },
        wrapper: { maxWidth: 'calc(100vw - 24px)' },
      }}
      onClose={close}
      footer={(
        <Space>
          {trialSelectionMode ? (
            <>
              <Button onClick={() => {
                setTrialSelectionMode(false);
                setSelectedTrialColumnNames([]);
              }}>
                取消字段选择
              </Button>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                disabled={selectedTrialColumnNames.length === 0}
                onClick={launchTrial}
              >
                开始试运行
              </Button>
            </>
          ) : <Button onClick={close}>关闭</Button>}
          {!trialSelectionMode && createTrialTarget && selectedTable && (
            <Tooltip title={trialCreationDisabled ? trialCreationDisabledReason : '完整计算到此节点，并预览当前选中的表'}>
              <span>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  disabled={trialCreationDisabled}
                  onClick={() => beginTrialSelection()}
                >
                  试运行预览
                </Button>
              </span>
            </Tooltip>
          )}
        </Space>
      )}
    >
      <div className="canvas-table-schema-body">
      {latestTrialRun && (
        <div className="canvas-trial-session-strip">
          <div className="canvas-trial-session-summary">
            <Typography.Text strong>最近试运行</Typography.Text>
            <Tag color={taskRunStatusColors[latestTrialRun.status]}>
              {taskRunStatusLabels[latestTrialRun.status]}
              {trialElapsedLabel(latestTrialRun) ? ` · ${trialElapsedLabel(latestTrialRun)}` : ''}
            </Tag>
            <Typography.Text type="secondary" ellipsis={{ tooltip: latestTrialRun.canvasTrialTableName ?? undefined }}>
              {latestTrialRun.canvasTrialTableName ?? '未知表'}
              {' · '}
              选择 {latestTrialRun.canvasTrialSelectedColumnCount ?? '—'} 个字段
            </Typography.Text>
            {latestTrialHistorical && <Tag>历史定义 v{latestTrialRun.definitionVersion}</Tag>}
          </div>
          <Space size={6}>
            <Button size="small" icon={<EyeOutlined />} onClick={openLatestTrial}>
              {trialViewLabel(latestTrialRun.status)}
            </Button>
            {latestTrialActive ? (
              <Button
                size="small"
                danger
                icon={<StopOutlined />}
                loading={cancelLatestTrialPending}
                disabled={!onCancelLatestTrial
                  || latestTrialRun.status === 'CANCEL_REQUESTED'
                  || latestTrialRun.status === 'STOP_REQUESTED'}
                onClick={() => onCancelLatestTrial?.(latestTrialRun.id)}
              >
                {latestTrialRun.status === 'CANCEL_REQUESTED' ? '正在取消' : '取消试运行'}
              </Button>
            ) : (
              <Button
                size="small"
                icon={<PlayCircleOutlined />}
                disabled={trialRunDisabled}
                onClick={() => beginTrialSelection(latestTrialRun.canvasTrialTableName ?? undefined)}
              >
                重新试运行
              </Button>
            )}
          </Space>
        </div>
      )}
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
                  if (trialSelectionMode) {
                    setSelectedTrialColumnNames(table.columns.map((column) => column.name));
                  }
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
                  {trialSelectionMode && (
                    <Tag color="processing">已选 {selectedTrialColumnNames.length}/{selectedTable.columns.length}</Tag>
                  )}
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
                rowSelection={trialSelectionMode ? {
                  preserveSelectedRowKeys: true,
                  selectedRowKeys: selectedTrialColumnNames,
                  onChange: (keys) => setSelectedTrialColumnNames(keys.map(String)),
                } : undefined}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有匹配的字段" /> }}
              />
            </>
          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可展示的输出表" />}
        </section>
      </div>
      </div>
    </Drawer>
    {trialRunSelection?.mode === 'new' && (
      <CanvasTrialRunModal
        key={`${trialRunSelection.target.nodeId}:${trialRunSelection.target.initialTableName ?? ''}`}
        open
        mode="new"
        target={trialRunSelection.target}
        columnNames={trialRunSelection.columnNames}
        onClose={() => setTrialRunSelection(null)}
        onRequestNewTrial={() => beginTrialSelection(
          trialRunSelection.target.initialTableName,
          true,
        )}
      />
    )}
    {trialRunSelection?.mode === 'existing' && (
      <CanvasTrialRunModal
        key={trialRunSelection.run.id}
        open
        mode="existing"
        target={trialRunSelection.target}
        runId={trialRunSelection.run.id}
        initialRun={trialRunSelection.run}
        onClose={() => setTrialRunSelection(null)}
        onRequestNewTrial={() => beginTrialSelection(
          trialRunSelection.run.canvasTrialTableName ?? undefined,
          true,
        )}
      />
    )}
    </>
  );
};

const CanvasTableSchemaModalWithTrial = (props: CanvasTableSchemaModalWithTrialProps) => {
  const initialTableName = props.initialTableName ?? props.tables[0]?.name;
  const trialTarget = initialTableName ? props.createTrialTarget(initialTableName) : null;
  const latestTrialRunQuery = useLatestCanvasNodeTrialRun(
    trialTarget?.taskId,
    trialTarget?.nodeId,
    props.open && Boolean(trialTarget),
  );
  const cancelMutation = useCancelTaskRun();

  return (
    <CanvasTableSchemaDrawer
      {...props}
      latestTrialRun={latestTrialRunQuery.data?.content[0]}
      cancelLatestTrialPending={cancelMutation.isPending}
      onCancelLatestTrial={(runId) => {
        void cancelMutation.mutateAsync(runId);
      }}
    />
  );
};

export const CanvasTableSchemaModal = (props: CanvasTableSchemaModalProps) => (
  props.createTrialTarget
    ? <CanvasTableSchemaModalWithTrial {...props} createTrialTarget={props.createTrialTarget} />
    : <CanvasTableSchemaDrawer {...props} />
);
