import {
  DownloadOutlined,
  FileTextOutlined,
  ReloadOutlined,
  StopOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { ApiError } from '../../../shared/api/http';
import { useDownloadTaskRunArtifact, useTaskRun } from '../hooks/useTasks';
import {
  executionErrorCategoryLabels,
  executionFailurePhaseLabels,
  taskRunExecutionModeLabels,
  taskRunStatusColors,
  taskRunStatusLabels,
  taskRunTriggerTypeLabels,
  taskTypeLabels,
  type TaskRun,
} from '../model/task';
import {
  formatTaskRunDateTime,
  formatTaskRunDuration,
  safeTrackingUrl,
} from '../model/taskRunPresentation';

interface TaskRunDetailDrawerProps {
  open: boolean;
  runId: string | null;
  canExecute: boolean;
  cancelLoading: boolean;
  onClose: () => void;
  onCancel: (run: TaskRun) => void;
}

const idValue = (value: string | null) => (
  value ? <Typography.Text code copyable={{ text: value }}>{value}</Typography.Text> : '—'
);

export const TaskRunDetailDrawer = ({
  open,
  runId,
  canExecute,
  cancelLoading,
  onClose,
  onCancel,
}: TaskRunDetailDrawerProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const runQuery = useTaskRun(runId ?? undefined, open);
  const resultDownload = useDownloadTaskRunArtifact();
  const logDownload = useDownloadTaskRunArtifact();
  const run = runQuery.data;

  const download = async (kind: 'result' | 'log') => {
    if (!run) return;
    const mutation = kind === 'result' ? resultDownload : logDownload;
    try {
      const blob = await mutation.mutateAsync({ runId: run.id, kind });
      downloadBlob(blob, `task-run-${run.id}-${kind === 'result' ? 'result.json' : 'console.log'}`);
    } catch (error) {
      messageApi.error(error instanceof ApiError
        ? error.message
        : kind === 'result' ? '下载执行结果失败' : '下载执行日志失败');
    }
  };

  const trackingUrl = safeTrackingUrl(run?.trackingUrl ?? null);
  const cancellable = run?.taskType === 'SPARK_CANVAS'
    && (run.status === 'QUEUED' || run.status === 'RUNNING' || run.status === 'CANCEL_REQUESTED');

  return (
    <Drawer
      open={open}
      size="large"
      destroyOnHidden
      title={run ? `运行详情：${run.id}` : '运行详情'}
      onClose={onClose}
      extra={(
        <Tooltip title="刷新运行详情">
          <Button
            icon={<ReloadOutlined />}
            aria-label="刷新运行详情"
            loading={runQuery.isFetching}
            disabled={!runId}
            onClick={() => void runQuery.refetch()}
          />
        </Tooltip>
      )}
    >
      {messageContext}
      {runQuery.isPending && <div className="task-run-detail-loading"><Spin tip="正在加载运行详情…" /></div>}
      {runQuery.isError && (
        <Alert
          type="error"
          showIcon
          message="运行详情加载失败"
          description={runQuery.error instanceof ApiError ? runQuery.error.message : '请稍后重试。'}
          action={<Button size="small" onClick={() => void runQuery.refetch()}>重试</Button>}
        />
      )}
      {!runQuery.isPending && !runQuery.isError && !run && <Empty description="没有可展示的运行记录" />}
      {run && (
        <div className="task-run-detail-content">
          <Space wrap>
            <Tag color={taskRunStatusColors[run.status]}>{taskRunStatusLabels[run.status]}</Tag>
            {run.taskType === 'SPARK_CANVAS' && (
              <>
                <Button
                  icon={<DownloadOutlined />}
                  aria-label="下载执行结果"
                  loading={resultDownload.isPending}
                  onClick={() => void download('result')}
                >
                  执行结果
                </Button>
                <Button
                  icon={<FileTextOutlined />}
                  aria-label="下载控制台日志"
                  loading={logDownload.isPending}
                  onClick={() => void download('log')}
                >
                  控制台日志
                </Button>
              </>
            )}
            {canExecute && cancellable && (
              <Button
                danger
                icon={<StopOutlined />}
                aria-label={run.status === 'CANCEL_REQUESTED' ? '正在取消运行' : '取消运行'}
                loading={cancelLoading}
                disabled={run.status === 'CANCEL_REQUESTED'}
                onClick={() => onCancel(run)}
              >
                {run.status === 'CANCEL_REQUESTED' ? '正在取消' : '取消运行'}
              </Button>
            )}
          </Space>

          <Descriptions size="small" bordered column={2} title="运行信息">
            <Descriptions.Item label="运行 ID" span={2}>{idValue(run.id)}</Descriptions.Item>
            <Descriptions.Item label="任务类型">{taskTypeLabels[run.taskType]}</Descriptions.Item>
            <Descriptions.Item label="定义版本">v{run.definitionVersion}</Descriptions.Item>
            <Descriptions.Item label="触发方式">{taskRunTriggerTypeLabels[run.triggerType]}</Descriptions.Item>
            <Descriptions.Item label="执行模式">{taskRunExecutionModeLabels[run.executionMode]}</Descriptions.Item>
            <Descriptions.Item label="影响行数">
              {run.affectedRows === null
                ? <span aria-label="影响行数未知">—</span>
                : run.affectedRows}
            </Descriptions.Item>
            <Descriptions.Item label="耗时">{formatTaskRunDuration(run)}</Descriptions.Item>
            <Descriptions.Item label="排队时间">{formatTaskRunDateTime(run.queuedAt)}</Descriptions.Item>
            <Descriptions.Item label="计划触发时间">{formatTaskRunDateTime(run.scheduledFireAt)}</Descriptions.Item>
            <Descriptions.Item label="开始时间">{formatTaskRunDateTime(run.startedAt)}</Descriptions.Item>
            <Descriptions.Item label="结束时间">{formatTaskRunDateTime(run.endedAt)}</Descriptions.Item>
            <Descriptions.Item label="执行期限">{formatTaskRunDateTime(run.deadlineAt)}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{formatTaskRunDateTime(run.updatedAt)}</Descriptions.Item>
          </Descriptions>

          {(run.taskType === 'SPARK_CANVAS' || run.taskType === 'SPARK_STREAMING_CANVAS') && (
            <Descriptions size="small" bordered column={2} title="Spark 执行路由">
              {run.streamingDeploymentId && (
                <Descriptions.Item label="实时部署 ID" span={2}>
                  {idValue(run.streamingDeploymentId)}
                </Descriptions.Item>
              )}
              <Descriptions.Item label="计算引擎 ID" span={2}>{idValue(run.computeEngineId)}</Descriptions.Item>
              <Descriptions.Item label="外部执行 ID" span={2}>{idValue(run.externalExecutionId)}</Descriptions.Item>
              <Descriptions.Item label="执行 Attempt">{run.attempt ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="Backend Application ID">
                {run.backendApplicationId ?? '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Tracking URL" span={2}>
                {trackingUrl
                  ? <Typography.Link href={trackingUrl} target="_blank" rel="noopener noreferrer">打开 Spark 跟踪页面</Typography.Link>
                  : '—'}
              </Descriptions.Item>
            </Descriptions>
          )}

          {run.executionError && (
            <Alert
              type={run.status === 'CANCELLED' ? 'warning' : 'error'}
              showIcon
              message={(
                <Space wrap>
                  <span>{run.executionError.message}</span>
                  <Typography.Text code copyable={{ text: run.executionError.code }}>
                    {run.executionError.code}
                  </Typography.Text>
                </Space>
              )}
              description={(
                <Descriptions size="small" column={2} bordered>
                  <Descriptions.Item label="执行阶段">
                    {executionFailurePhaseLabels[run.executionError.phase]}
                  </Descriptions.Item>
                  <Descriptions.Item label="错误类别">
                    {executionErrorCategoryLabels[run.executionError.category]}
                  </Descriptions.Item>
                  <Descriptions.Item label="建议重试">
                    <Tag color={run.executionError.retryable ? 'warning' : 'default'}>
                      {run.executionError.retryable ? '是' : '否'}
                    </Tag>
                  </Descriptions.Item>
                  {run.executionError.sqlState && (
                    <Descriptions.Item label="SQLState">
                      <Typography.Text code>{run.executionError.sqlState}</Typography.Text>
                    </Descriptions.Item>
                  )}
                  {run.executionError.nodeId && (
                    <>
                      <Descriptions.Item label="失败节点名称">
                        {run.executionError.nodeName ?? '—'}
                      </Descriptions.Item>
                      <Descriptions.Item label="失败节点类型">
                        {run.executionError.nodeType ?? '—'}
                      </Descriptions.Item>
                      <Descriptions.Item label="失败节点 ID" span={2}>
                        {idValue(run.executionError.nodeId)}
                      </Descriptions.Item>
                    </>
                  )}
                  <Descriptions.Item label="诊断 ID" span={2}>
                    {idValue(run.executionError.diagnosticId)}
                  </Descriptions.Item>
                </Descriptions>
              )}
            />
          )}
          {!run.executionError && (run.message || run.errorDetail) && (
            <Alert
              type={run.status === 'FAILED' || run.status === 'TIMED_OUT' ? 'error' : 'info'}
              showIcon
              message={run.message ?? '运行信息'}
              description={run.errorDetail ? <Typography.Text copyable>{run.errorDetail}</Typography.Text> : undefined}
            />
          )}
        </div>
      )}
    </Drawer>
  );
};
