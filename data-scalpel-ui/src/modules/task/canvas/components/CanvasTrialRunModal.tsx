import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  PlayCircleOutlined,
  StopOutlined,
} from '@ant-design/icons';
import {
  Button,
  Empty,
  Modal,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
  type TableColumnsType,
} from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../../../shared/api/http';
import { InlineFeedback } from '../../../../shared/components/ContextualFeedback';
import {
  useCancelTaskRun,
  useCanvasTrialPreview,
  useTaskRun,
  useTrialRunCanvas,
} from '../../hooks/useTasks';
import { taskRunStatusLabels, type TaskRun, type TaskRunStatus } from '../../model/task';
import { platformTypeLabel } from '../canvasSchema';
import type { CanvasNodeTrialTarget } from '../canvasNodeTrial';

interface CanvasTrialRunModalBaseProps {
  open: boolean;
  target: CanvasNodeTrialTarget;
  onClose: () => void;
  onRequestNewTrial: () => void;
}

type CanvasTrialRunModalProps = CanvasTrialRunModalBaseProps & (
  | {
    mode: 'new';
    columnNames: readonly string[];
  }
  | {
    mode: 'existing';
    runId: string;
    initialRun?: TaskRun;
  }
);

interface PreviewRow {
  key: string;
  values: Record<string, unknown>;
}

const activeStatuses = new Set<TaskRunStatus>([
  'QUEUED', 'RUNNING', 'CANCEL_REQUESTED', 'STOP_REQUESTED',
]);

const isRecord = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);

const valueText = (value: unknown): string => {
  if (value === null || value === undefined) return '—';
  if (typeof value === 'string') return value;
  if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'bigint') return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};

const parseRows = (rowsJson: readonly string[]): { rows: PreviewRow[]; error: string | null } => {
  try {
    return {
      rows: rowsJson.map((rowJson, index) => {
        const parsed: unknown = JSON.parse(rowJson);
        if (!isRecord(parsed)) throw new Error('row is not an object');
        return { key: String(index + 1), values: parsed };
      }),
      error: null,
    };
  } catch {
    return { rows: [], error: '预览行格式无法解析，请查看本次运行日志。' };
  }
};

export const CanvasTrialRunModal = (props: CanvasTrialRunModalProps) => {
  const {
    open,
    target,
    onClose,
    onRequestNewTrial,
  } = props;
  const selectedColumnNames = props.mode === 'new' ? props.columnNames : undefined;
  const columnNames = useMemo<readonly string[]>(
    () => selectedColumnNames ?? [],
    [selectedColumnNames],
  );
  const initialRun = props.mode === 'existing' ? props.initialRun : undefined;
  const selectedTableName = props.mode === 'existing'
    ? initialRun?.canvasTrialTableName ?? target.initialTableName
    : target.initialTableName;
  const selectedTable = target.outputTables.find((table) => table.name === selectedTableName)
    ?? target.outputTables[0];
  const [submittedRunId, setSubmittedRunId] = useState<string>();
  const [submitError, setSubmitError] = useState<string>();
  const autoSubmittedRef = useRef(false);
  const submitMutation = useTrialRunCanvas();
  const cancelMutation = useCancelTaskRun();
  const runId = props.mode === 'existing' ? props.runId : submittedRunId;
  const runQuery = useTaskRun(runId, open);
  const run = runQuery.data ?? initialRun;
  const active = Boolean(run && activeStatuses.has(run.status));
  const cancellationRequested = run?.status === 'CANCEL_REQUESTED' || run?.status === 'STOP_REQUESTED';
  const succeeded = run?.status === 'SUCCESS';
  const previewQuery = useCanvasTrialPreview(runId, succeeded);
  const preview = previewQuery.data?.preview;

  const submit = useCallback(async () => {
    if (props.mode !== 'new' || !selectedTable || columnNames.length === 0) return;
    setSubmittedRunId(undefined);
    setSubmitError(undefined);
    try {
      const submitted = await submitMutation.mutateAsync({
        id: target.taskId,
        request: {
          baseDefinitionVersion: target.baseDefinitionVersion,
          definition: target.definition,
          targetNodeId: target.nodeId,
          tableName: selectedTable.name,
          columnNames: [...columnNames],
        },
      });
      setSubmittedRunId(submitted.id);
    } catch (error) {
      setSubmitError(error instanceof ApiError ? error.message : '试运行提交失败');
    }
  }, [columnNames, props.mode, selectedTable, submitMutation, target]);

  useEffect(() => {
    if (!open || props.mode !== 'new' || autoSubmittedRef.current) return;
    autoSubmittedRef.current = true;
    void submit();
  }, [open, props.mode, submit]);

  const parsedPreview = useMemo(
    () => parseRows(preview?.rowsJson ?? []),
    [preview?.rowsJson],
  );
  const previewColumns: TableColumnsType<PreviewRow> = preview ? [
    {
      title: '#',
      key: 'rowNumber',
      width: 56,
      fixed: 'left',
      render: (_, row) => row.key,
    },
    ...preview.tableSchema.columns.map((column) => ({
      title: (
        <span className="canvas-trial-preview-column-title">
          <strong>{column.name}</strong>
          <small>{platformTypeLabel(column)}</small>
        </span>
      ),
      key: column.name,
      width: 190,
      render: (_: unknown, row: PreviewRow) => {
        const text = valueText(row.values[column.name]);
        const tooltip = text.length > 500 ? `${text.slice(0, 500)}…` : text;
        return (
          <Tooltip title={tooltip} mouseEnterDelay={0.5}>
            <span className="canvas-trial-preview-value">{text}</span>
          </Tooltip>
        );
      },
    })),
  ] : [];

  const renderRunning = () => (
    <div className="canvas-trial-running">
      <Spin size="large" />
      <Typography.Title level={5}>
        {run?.status === 'QUEUED' ? '试运行正在排队' : cancellationRequested ? '正在取消试运行' : '正在试运行到目标节点'}
      </Typography.Title>
      <Typography.Text type="secondary">
        将完整计算上游数据，只在目标表最终返回最多 100 行。关闭弹框不会自动终止后台运行。
      </Typography.Text>
      {run && <Tag color={cancellationRequested ? 'warning' : 'processing'}>{taskRunStatusLabels[run.status]}</Tag>}
    </div>
  );

  const renderFailure = () => (
    <div className="canvas-trial-result-state">
      <CloseCircleOutlined className="is-error" />
      <Typography.Title level={5}>试运行{run?.status ? taskRunStatusLabels[run.status] : '失败'}</Typography.Title>
      <InlineFeedback
        tone="error"
        label={run?.executionError?.message ?? run?.message ?? '未返回可展示的数据'}
        detail={run?.executionError ? `错误码：${run.executionError.code}；诊断 ID：${run.executionError.diagnosticId}` : undefined}
      />
    </div>
  );

  const renderPreview = () => (
    <div className="canvas-trial-preview">
      <div className="canvas-trial-preview-heading">
        <Space wrap size={6}>
          <CheckCircleOutlined className="is-success" />
          <Typography.Text strong>{preview?.tableSchema.name}</Typography.Text>
          <Tag color="success">{parsedPreview.rows.length} 行</Tag>
          <Tag>{preview?.tableSchema.columns.length ?? 0} 个字段</Tag>
          {preview?.truncated && <Tag color="warning">结果已截断</Tag>}
        </Space>
        <Typography.Text type="secondary">设计草稿试运行，不执行任何输出写入</Typography.Text>
      </div>
      {preview?.warnings.map((warning) => (
        <InlineFeedback key={warning} tone="warning" label={warning} />
      ))}
      {parsedPreview.error ? (
        <InlineFeedback tone="error" label="预览数据无法展示" detail={parsedPreview.error} />
      ) : (
        <Table<PreviewRow>
          className="canvas-trial-preview-table"
          size="small"
          bordered
          rowKey="key"
          tableLayout="fixed"
          pagination={false}
          columns={previewColumns}
          dataSource={parsedPreview.rows}
          scroll={{ x: Math.max(720, previewColumns.length * 190), y: 'min(460px, calc(100vh - 390px))' }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="目标表没有返回数据" /> }}
        />
      )}
    </div>
  );

  const terminalFailure = Boolean(run && !active && run.status !== 'SUCCESS');
  const body = props.mode === 'new' && submitMutation.isPending && !runId
    ? renderRunning()
    : submitError
      ? <InlineFeedback tone="error" label="试运行提交失败" detail={submitError} />
      : runQuery.error && !run
        ? <InlineFeedback tone="error" label="运行状态读取失败" detail={runQuery.error instanceof Error ? runQuery.error.message : '请稍后重试'} />
        : active || !run
          ? renderRunning()
          : terminalFailure
            ? renderFailure()
            : previewQuery.isPending
              ? <div className="canvas-trial-running"><Spin /><Typography.Text>正在读取预览数据…</Typography.Text></div>
              : previewQuery.error || !preview
                ? <InlineFeedback tone="error" label="预览数据读取失败" detail={previewQuery.error instanceof Error ? previewQuery.error.message : '成功运行未返回预览'} />
                : renderPreview();

  const requestNewTrial = () => {
    onClose();
    onRequestNewTrial();
  };

  const footer = active ? (
    <Space>
      <Button onClick={onClose}>关闭，后台继续</Button>
      <Button
        danger
        icon={<StopOutlined />}
        loading={cancelMutation.isPending || cancellationRequested}
        disabled={!run || cancellationRequested}
        onClick={() => run && void cancelMutation.mutateAsync(run.id)}
      >
        {cancellationRequested ? '正在取消' : '取消试运行'}
      </Button>
    </Space>
  ) : props.mode === 'new' && !runId && !submitError ? (
    <Button loading disabled>正在创建试运行</Button>
  ) : (
    <Space>
      <Button onClick={onClose}>关闭</Button>
      <Button
        type="primary"
        icon={<PlayCircleOutlined />}
        onClick={requestNewTrial}
      >
        重新试运行
      </Button>
    </Space>
  );

  return (
    <Modal
      open={open}
      title={`试运行数据预览 · ${target.nodeName}`}
      width={1120}
      destroyOnHidden
      className="canvas-trial-modal"
      closable
      keyboard
      mask={{ closable: false }}
      styles={{ container: { maxWidth: 'calc(100vw - 32px)' } }}
      footer={footer}
      onCancel={onClose}
    >
      <div className="canvas-trial-modal-summary">
        <Typography.Text code>{run?.canvasTrialTableName ?? selectedTable?.name}</Typography.Text>
        <Tag>{run?.canvasTrialSelectedColumnCount ?? columnNames.length} 个返回字段</Tag>
        {runId && <Typography.Text type="secondary">运行 ID：{runId}</Typography.Text>}
      </div>
      <div className="canvas-trial-modal-body">{body}</div>
    </Modal>
  );
};
