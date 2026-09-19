import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  DownloadOutlined,
  EyeOutlined,
  ReloadOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { Button, Card, Collapse, Descriptions, Drawer, Empty, Space, Spin, Table, Tag, Tooltip, Typography, message } from 'antd';
import type { TableProps } from 'antd';
import { downloadBlob } from '../../../shared/browser/downloadBlob';
import { ApiError } from '../../../shared/api/http';
import {
  modelQualityRuleSeverityLabels,
  modelQualityRuleTypeLabels,
} from '../../model';
import {
  useDownloadQualityFailureSamples,
  useDownloadTaskRunArtifact,
  useTaskRun,
  useTaskRunArtifacts,
  useTaskRunLineage,
  useTaskRunResultArtifact,
} from '../hooks/useTasks';
import {
  executionErrorCategoryLabels,
  executionFailurePhaseLabels,
  taskRunExecutionModeLabels,
  taskRunStatusColors,
  taskRunStatusLabels,
  taskRunTriggerTypeLabels,
  taskTypeLabels,
  type TaskRun,
  type TaskRunArtifactKind,
  type TaskRunArtifactMetadata,
} from '../model/task';
import type {
  OutputWriteExecutionResult,
  QualityRuleExecutionResult,
  QualitySkippedRuleResult,
} from '../model/taskExecutionResult';
import { QualityFailureSampleDrawer } from './QualityFailureSampleDrawer';
import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
const WorkflowRunPanel = lazy(() => import('../workflow/WorkflowRunPanel'));
import { UserJobObservabilityPanel } from './UserJobObservabilityPanel';
import { TaskRunArtifactPreview } from './TaskRunArtifactPreview';
import { TaskRunLogViewer } from './TaskRunLogViewer';
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
  forceTerminateLoading: boolean;
  onClose: () => void;
  onCancel: (run: TaskRun) => void;
  onForceTerminate: (run: TaskRun) => void;
}

const idValue = (value: string | null) => (
  value ? <Typography.Text code copyable={{ text: value }}>{value}</Typography.Text> : '—'
);

const formatBytes = (value: number): string => {
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(2)} MiB`;
  if (value >= 1024) return `${(value / 1024).toFixed(2)} KiB`;
  return `${value} B`;
};

const lineageCoverageLabels = {
  MODEL_ONLY: '资产级',
  FIELD_PARTIAL: '字段部分',
  FIELD_COMPLETE: '字段完整',
} as const;

const qualityConclusion = (value: 'PASSED' | 'FAILED' | null) => {
  if (!value) return '—';
  return <Tag color={value === 'PASSED' ? 'success' : 'error'}>{value === 'PASSED' ? '通过' : '不通过'}</Tag>;
};

const qualityMetric = (metric: QualityRuleExecutionResult['metric']) => {
  if (metric.kind === 'VIOLATION') {
    const tolerance = metric.toleranceMetric === 'COUNT'
      ? `${metric.toleranceValue} 行`
      : `${metric.toleranceValue}%`;
    return `异常 ${metric.violationCount} 行（${metric.violationPercent}%），允许 ${tolerance}`;
  }
  if (metric.kind === 'ROW_COUNT') return `实际 ${metric.actualRows} 行，至少 ${metric.minimumRows} 行`;
  return metric.maximumValue
    ? `最大时间 ${new Date(metric.maximumValue).toLocaleString('zh-CN')}，延迟 ${metric.actualDelayMinutes ?? '—'} 分钟，允许 ${metric.maximumDelayMinutes} 分钟`
    : `没有有效时间值，允许延迟 ${metric.maximumDelayMinutes} 分钟`;
};

const qualitySampleStatus = (rule: QualityRuleExecutionResult) => {
  if (!rule.sample) return '—';
  if (rule.sample.status === 'AVAILABLE') {
    return `${rule.sample.sampledRows} 条${rule.sample.truncated ? '（截断）' : ''}`;
  }
  if (rule.sample.status === 'NOT_FAILED') return '规则通过';
  if (rule.sample.status === 'NOT_APPLICABLE') return '不适用';
  return '未保存';
};

const baseQualityRuleColumns: TableProps<QualityRuleExecutionResult>['columns'] = [
  {
    title: '规则', dataIndex: 'ruleName', width: 180,
    render: (name: string, rule) => (
      <Space orientation="vertical" size={0}>
        <Typography.Text>{name}</Typography.Text>
        <Typography.Text type="secondary">{modelQualityRuleTypeLabels[rule.ruleType]}</Typography.Text>
      </Space>
    ),
  },
  {
    title: '级别', dataIndex: 'severity', width: 80,
    render: (severity: QualityRuleExecutionResult['severity']) => modelQualityRuleSeverityLabels[severity],
  },
  {
    title: '结论', dataIndex: 'state', width: 88,
    render: (state: QualityRuleExecutionResult['state']) => (
      <Tag color={state === 'PASSED' ? 'success' : 'error'}>{state === 'PASSED' ? '通过' : '不通过'}</Tag>
    ),
  },
  { title: '指标', dataIndex: 'metric', ellipsis: true, render: qualityMetric },
  { title: '失败样本', width: 110, render: (_, rule) => qualitySampleStatus(rule) },
  { title: '耗时', dataIndex: 'durationMs', width: 90, align: 'right', render: (value: number) => `${value} ms` },
];

const outputWriteState = (state: OutputWriteExecutionResult['state']) => {
  const labels: Record<OutputWriteExecutionResult['state'], string> = {
    PENDING: '等待', RUNNING: '执行中', SUCCESS: '成功', FAILED: '失败', SKIPPED: '未执行',
  };
  const colors: Record<OutputWriteExecutionResult['state'], string> = {
    PENDING: 'default', RUNNING: 'processing', SUCCESS: 'success', FAILED: 'error', SKIPPED: 'warning',
  };
  return <Tag color={colors[state]}>{labels[state]}</Tag>;
};

const outputWriteColumns: TableProps<OutputWriteExecutionResult>['columns'] = [
  {
    title: '来源表', dataIndex: 'sourceTableName', width: 190, ellipsis: true,
    render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
  },
  { title: '目标', dataIndex: 'targetDisplayName', ellipsis: true },
  { title: '状态', dataIndex: 'state', width: 90, render: outputWriteState },
  {
    title: '影响行数', dataIndex: 'affectedRows', width: 110, align: 'right',
    render: (value: number | null) => value ?? '—',
  },
  { title: '错误码', dataIndex: 'errorCode', width: 180, ellipsis: true, render: (value: string | null) => value ?? '—' },
];

export const TaskRunDetailDrawer = ({
  open,
  runId,
  canExecute,
  cancelLoading,
  forceTerminateLoading,
  onClose,
  onCancel,
  onForceTerminate,
}: TaskRunDetailDrawerProps) => {
  const [messageApi, messageContext] = message.useMessage();
  const [sampleRule, setSampleRule] = useState<QualityRuleExecutionResult | null>(null);
  const [previewSelection, setPreviewSelection] = useState<{
    runId: string;
    kind: TaskRunArtifactKind;
  } | null>(null);
  const detailContentRef = useRef<HTMLDivElement>(null);
  const detailScrollTopRef = useRef(0);
  const runQuery = useTaskRun(runId ?? undefined, open);
  const resultDownload = useDownloadTaskRunArtifact();
  const logDownload = useDownloadTaskRunArtifact();
  const sampleDownload = useDownloadQualityFailureSamples();
  const run = runQuery.data;
  const parentQuery = useTaskRun(run?.parentRunId ?? undefined, open && Boolean(run?.parentRunId));
  const previewKind = previewSelection?.runId === runId ? previewSelection.kind : null;
  const supportsRunArtifacts = run?.taskType === 'SPARK_CANVAS'
    || run?.taskType === 'SPARK_STREAMING_CANVAS'
    || run?.taskType === 'SPARK_MODEL_QUALITY'
    || run?.taskType === 'SPARK_JAR'
    || run?.taskType === 'SPARK_STREAMING_JAR';
  const artifactsQuery = useTaskRunArtifacts(run?.id, open && supportsRunArtifacts);
  const lineageQuery = useTaskRunLineage(
    run?.id,
    open && run?.taskType === 'SPARK_JAR',
  );
  const resultArtifactQuery = useTaskRunResultArtifact(
    run?.id,
    open && (run?.taskType === 'SPARK_CANVAS' || run?.taskType === 'SPARK_MODEL_QUALITY'
      || run?.taskType === 'SPARK_JAR')
      && (run.status === 'SUCCESS' || run.status === 'FAILED'
        || run.status === 'TIMED_OUT' || run.status === 'CANCELLED')
      && artifactsQuery.data?.result.previewAvailable === true,
  );
  const snapshotResults = resultArtifactQuery.data?.nodeResults.filter(
    (node) => node.metrics?.kind === 'SNAPSHOT_SYNC',
  ) ?? [];
  const outputResults = resultArtifactQuery.data?.nodeResults.filter(
    (node) => node.metrics?.kind === 'OUTPUT_WRITES',
  ) ?? [];

  useEffect(() => {
    detailScrollTopRef.current = 0;
  }, [runId]);

  useEffect(() => {
    if (previewKind !== null || detailScrollTopRef.current === 0) return;
    const frame = requestAnimationFrame(() => {
      detailContentRef.current?.closest<HTMLElement>('.ant-drawer-body')
        ?.scrollTo({ top: detailScrollTopRef.current });
    });
    return () => cancelAnimationFrame(frame);
  }, [previewKind]);

  const openPreview = (kind: TaskRunArtifactKind) => {
    detailScrollTopRef.current = detailContentRef.current
      ?.closest<HTMLElement>('.ant-drawer-body')?.scrollTop ?? 0;
    if (runId) setPreviewSelection({ runId, kind });
  };

  const download = async (kind: TaskRunArtifactKind) => {
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

  const artifactAction = (artifact: TaskRunArtifactMetadata) => {
    const label = artifact.kind === 'result' ? '执行结果' : '控制台日志';
    const mutation = artifact.kind === 'result' ? resultDownload : logDownload;
    if (artifact.availability === 'NOT_GENERATED') {
      return (
        <div key={artifact.kind} className="task-run-artifact-row">
          <Typography.Text strong>{label}</Typography.Text>
          <Typography.Text type="secondary">尚未生成</Typography.Text>
          {artifact.kind === 'log' && (
            <Button size="small" type="text" icon={<EyeOutlined />} onClick={() => openPreview('log')}>查看运行日志</Button>
          )}
        </div>
      );
    }
    if (artifact.availability === 'SIZE_UNAVAILABLE') {
      return (
        <div key={artifact.kind} className="task-run-artifact-row">
          <Typography.Text strong>{label}</Typography.Text>
          <Typography.Text type="secondary">大小获取失败</Typography.Text>
          <Button size="small" type="text" icon={<ReloadOutlined />} aria-label={`重新获取${label}大小`}
            onClick={() => void artifactsQuery.refetch()} />
          {artifact.kind === 'log' && (
            <Button size="small" type="text" icon={<EyeOutlined />} onClick={() => openPreview('log')}>查看运行日志</Button>
          )}
          <Button size="small" icon={<DownloadOutlined />} loading={mutation.isPending}
            onClick={() => void download(artifact.kind)}>下载</Button>
        </div>
      );
    }
    return (
      <div key={artifact.kind} className="task-run-artifact-row">
        <Typography.Text strong>{label}</Typography.Text>
        <Typography.Text type="secondary">{artifact.sizeBytes == null ? '大小未知' : formatBytes(artifact.sizeBytes)}</Typography.Text>
        {artifact.previewAvailable || artifact.kind === 'log' ? (
          <Button size="small" type="text" icon={<EyeOutlined />} onClick={() => openPreview(artifact.kind)}>预览</Button>
        ) : (
          <Typography.Text type="secondary">文件较大，仅支持下载</Typography.Text>
        )}
        <Button size="small" icon={<DownloadOutlined />} loading={mutation.isPending}
          onClick={() => void download(artifact.kind)}>下载</Button>
      </div>
    );
  };

  const unavailableStructuredResult = () => {
    const result = artifactsQuery.data?.result;
    const tooLarge = result?.availability === 'AVAILABLE' && !result.previewAvailable;
    return (
      <Alert
        showIcon
        type="warning"
        message={tooLarge ? '执行结果文件较大，未自动加载' : '暂时无法读取结构化执行结果'}
        description={tooLarge
          ? `执行结果为 ${result.sizeBytes == null ? '未知大小' : formatBytes(result.sizeBytes)}，请按需下载查看。`
          : '为避免在后台读取未知大小的结果文件，请通过上方“执行结果”下载查看。'}
        action={(
          <Button size="small" icon={<DownloadOutlined />} onClick={() => void download('result')}>
            下载结果
          </Button>
        )}
      />
    );
  };

  const shouldSkipStructuredResultPreview = artifactsQuery.isError
    || (artifactsQuery.data?.result.availability === 'AVAILABLE'
      && !artifactsQuery.data.result.previewAvailable);

  const trackingUrl = safeTrackingUrl(run?.trackingUrl ?? null);
  const cancellable = (run?.taskType === 'WORKFLOW' || run?.taskType === 'LOCAL_SQL' || run?.taskType === 'SPARK_CANVAS' || run?.taskType === 'SPARK_MODEL_QUALITY'
    || run?.taskType === 'SPARK_JAR')
    && (run.status === 'QUEUED' || run.status === 'RUNNING');
  const forceTerminable = run?.taskType !== 'LOCAL_SQL' && run?.taskType !== 'WORKFLOW'
    && (run?.status === 'CANCEL_REQUESTED' || run?.status === 'STOP_REQUESTED');
  const downloadSamples = async (rule: QualityRuleExecutionResult) => {
    if (!run) return;
    try {
      const blob = await sampleDownload.mutateAsync({ runId: run.id, ruleId: rule.ruleId });
      const safeName = rule.ruleName.replace(/[\r\n\t/\\";]/g, '_').trim().slice(0, 80);
      downloadBlob(blob, `${safeName || rule.ruleId}-失败样本.parquet`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '下载失败样本失败');
    }
  };
  const qualityRuleColumns: TableProps<QualityRuleExecutionResult>['columns'] = [
    ...baseQualityRuleColumns,
    {
      title: '操作', width: 90, fixed: 'right',
      render: (_, rule) => rule.sample?.status === 'AVAILABLE' ? (
        <Space size={0}>
          <Tooltip title={`查看${rule.ruleName}失败样本`}>
            <Button
              type="text"
              icon={<EyeOutlined />}
              aria-label={`查看${rule.ruleName}失败样本`}
              onClick={() => setSampleRule(rule)}
            />
          </Tooltip>
          <Tooltip title={`下载${rule.ruleName}失败样本 Parquet`}>
            <Button
              type="text"
              icon={<DownloadOutlined />}
              aria-label={`下载${rule.ruleName}失败样本 Parquet`}
              loading={sampleDownload.isPending && sampleDownload.variables?.ruleId === rule.ruleId}
              onClick={() => void downloadSamples(rule)}
            />
          </Tooltip>
        </Space>
      ) : '—',
    },
  ];

  return (
    <Drawer
      rootClassName="business-overlay business-drawer-overlay"
      className={previewKind ? 'task-run-detail-drawer task-run-detail-drawer-preview' : 'task-run-detail-drawer'}
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
            onClick={() => {
              void runQuery.refetch();
              if (supportsRunArtifacts) void artifactsQuery.refetch();
            }}
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
      {run && previewKind && artifactsQuery.data ? (
        previewKind === 'log' ? (
          <TaskRunLogViewer
            key={run.id}
            runId={run.id}
            visible={open && previewKind === 'log'}
            endedAt={run.endedAt}
            onBack={() => setPreviewSelection(null)}
            onShowResult={artifactsQuery.data.result.previewAvailable
              ? () => setPreviewSelection({ runId: run.id, kind: 'result' }) : undefined}
          />
        ) : (
          <TaskRunArtifactPreview
            runId={run.id}
            artifact={artifactsQuery.data.result}
            artifacts={artifactsQuery.data}
            onBack={() => setPreviewSelection(null)}
            onSelect={(kind) => setPreviewSelection({ runId: run.id, kind })}
            onDownload={(kind) => void download(kind)}
            downloadLoading={resultDownload.isPending}
          />
        )
      ) : run && (
        <div ref={detailContentRef} className="task-run-detail-content">
          <Space wrap>
            <Tag color={taskRunStatusColors[run.status]}>{taskRunStatusLabels[run.status]}</Tag>
            {canExecute && cancellable && (
              <Button
                danger
                icon={<StopOutlined />}
                aria-label="取消运行"
                loading={cancelLoading}
                onClick={() => onCancel(run)}
              >
                取消运行
              </Button>
            )}
            {canExecute && forceTerminable && (
              <Button
                danger
                icon={<StopOutlined />}
                aria-label="强制终止运行"
                loading={forceTerminateLoading}
                onClick={() => onForceTerminate(run)}
              >
                强制终止
              </Button>
            )}
          </Space>

          {supportsRunArtifacts && (
            <div className="task-run-artifact-actions" aria-label="运行制品">
              {artifactsQuery.isPending ? <Typography.Text type="secondary">正在获取制品大小…</Typography.Text>
                : artifactsQuery.isError ? (
                  <Space size="small">
                    <Typography.Text type="secondary">制品大小获取失败</Typography.Text>
                    <Button size="small" type="text" icon={<ReloadOutlined />} onClick={() => void artifactsQuery.refetch()}>重试</Button>
                  </Space>
                ) : artifactsQuery.data ? (
                  <>{artifactAction(artifactsQuery.data.log)}{artifactAction(artifactsQuery.data.result)}</>
                ) : null}
            </div>
          )}

          {run.parentRunId && parentQuery.data && <Link to={`/task/${parentQuery.data.taskId}?tab=runs&runId=${run.parentRunId}`}>返回父工作流运行</Link>}
          {run.taskType === 'WORKFLOW' && <Suspense fallback={<Spin />}><WorkflowRunPanel runId={run.id} /></Suspense>}
          <Descriptions size="small" bordered column={2} title="运行信息">
            <Descriptions.Item label="运行 ID" span={2}>{idValue(run.id)}</Descriptions.Item>
            <Descriptions.Item label="任务类型">{taskTypeLabels[run.taskType]}</Descriptions.Item>
            <Descriptions.Item label="定义版本">v{run.definitionVersion}</Descriptions.Item>
            <Descriptions.Item label="触发方式">{taskRunTriggerTypeLabels[run.triggerType]}</Descriptions.Item>
            <Descriptions.Item label="执行模式">{taskRunExecutionModeLabels[run.executionMode]}</Descriptions.Item>
            <Descriptions.Item label={run.taskType === 'SPARK_MODEL_QUALITY'
              ? '检查行数'
              : run.taskType === 'SPARK_CANVAS' && run.status !== 'SUCCESS'
                ? '已提交影响行数' : '影响行数'}>
              {run.taskType === 'SPARK_MODEL_QUALITY'
                ? run.qualityCheckedRows ?? '—'
                : run.affectedRows ?? <span aria-label="影响行数未知">—</span>}
            </Descriptions.Item>
            <Descriptions.Item label="耗时">{formatTaskRunDuration(run)}</Descriptions.Item>
            {(run.taskType === 'SPARK_JAR' || run.taskType === 'SPARK_STREAMING_JAR') && (
              <>
                <Descriptions.Item label="用户 JAR">{run.userJarFileName ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="JAR 大小">
                  {run.userJarSizeBytes == null ? '—' : formatBytes(run.userJarSizeBytes)}
                </Descriptions.Item>
                <Descriptions.Item label="JAR SHA-256" span={2}>
                  {run.userJarSha256
                    ? <Typography.Text code copyable ellipsis>{run.userJarSha256}</Typography.Text>
                    : '—'}
                </Descriptions.Item>
                <Descriptions.Item label="运行资源" span={2}>
                  {run.executionResources
                    ? `${run.executionResources.driverCores} Core / ${run.executionResources.driverMemoryMiB / 1024} GiB`
                    + (run.taskType === 'SPARK_STREAMING_JAR' || run.executionResources.executorInstances > 1
                      ? `；${run.executionResources.executorInstances} 个执行器 × ${run.executionResources.executorCores} Core / ${run.executionResources.executorMemoryMiB / 1024} GiB`
                      : '')
                    : '—'}
                </Descriptions.Item>
              </>
            )}
            <Descriptions.Item label="排队时间">{formatTaskRunDateTime(run.queuedAt)}</Descriptions.Item>
            <Descriptions.Item label="计划触发时间">{formatTaskRunDateTime(run.scheduledFireAt)}</Descriptions.Item>
            <Descriptions.Item label="开始时间">{formatTaskRunDateTime(run.startedAt)}</Descriptions.Item>
            <Descriptions.Item label="结束时间">{formatTaskRunDateTime(run.endedAt)}</Descriptions.Item>
            <Descriptions.Item label="执行期限">{formatTaskRunDateTime(run.deadlineAt)}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{formatTaskRunDateTime(run.updatedAt)}</Descriptions.Item>
          </Descriptions>

          {(run.taskType === 'SPARK_JAR' || run.taskType === 'SPARK_STREAMING_JAR') && (
            <UserJobObservabilityPanel observability={run.userJobObservability} attemptScoped />
          )}

          {run.taskType === 'SPARK_JAR' && (
            <Card size="small" title="运行血缘" loading={lineageQuery.isPending}>
              {lineageQuery.isError ? (
                <Alert
                  showIcon
                  type="warning"
                  message="暂时无法读取运行血缘状态"
                  action={<Button size="small" onClick={() => void lineageQuery.refetch()}>重试</Button>}
                />
              ) : lineageQuery.data ? (
                <Space orientation="vertical" size="small" style={{ width: '100%' }}>
                  <Space wrap>
                    <Tag color={lineageQuery.data.status === 'SUCCEEDED'
                      ? 'success'
                      : lineageQuery.data.status === 'FAILED' ? 'error'
                        : lineageQuery.data.status === 'STALE' ? 'warning' : 'processing'}>
                      {{
                        PENDING: '等待运行结果', NOT_AVAILABLE: '不可用', QUEUED: '等待摄取',
                        RUNNING: '正在摄取', SUCCEEDED: '已完成', FAILED: '摄取失败', STALE: '定义已变化',
                      }[lineageQuery.data.status]}
                    </Tag>
                    <Typography.Text>
                      覆盖度：{lineageQuery.data.coverage
                        ? lineageCoverageLabels[lineageQuery.data.coverage]
                        : '—'}
                    </Typography.Text>
                    <Typography.Text>
                      Flow：{lineageQuery.data.flowCount ?? '—'}
                    </Typography.Text>
                    <Typography.Text>
                      正式快照：{lineageQuery.data.publishedSnapshot ? '已合并' : '未合并'}
                    </Typography.Text>
                  </Space>
                  {lineageQuery.data.errorDetail && (
                    <Alert
                      showIcon
                      type={lineageQuery.data.status === 'FAILED' ? 'error' : 'warning'}
                      message={lineageQuery.data.errorCode ?? '运行血缘提示'}
                      description={lineageQuery.data.errorDetail}
                    />
                  )}
                  {lineageQuery.data.warnings.map((warning) => (
                    <Alert
                      key={`${warning.code}-${warning.flowKey ?? ''}`}
                      showIcon
                      type="warning"
                      message={warning.code}
                      description={warning.message}
                    />
                  ))}
                </Space>
              ) : <Typography.Text type="secondary">暂无运行血缘状态。</Typography.Text>}
            </Card>
          )}

          {(run.taskType === 'SPARK_CANVAS'
            || run.taskType === 'SPARK_STREAMING_CANVAS'
            || run.taskType === 'SPARK_STREAMING_JAR'
            || run.taskType === 'SPARK_MODEL_QUALITY'
            || run.taskType === 'SPARK_JAR') && (
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

          {run.taskType === 'SPARK_CANVAS'
            && (run.status === 'SUCCESS' || run.status === 'FAILED'
              || run.status === 'TIMED_OUT' || run.status === 'CANCELLED') && (
            <Card size="small" title="输出写入结果" loading={artifactsQuery.isPending || resultArtifactQuery.isPending}>
              {shouldSkipStructuredResultPreview ? unavailableStructuredResult() : resultArtifactQuery.isError ? (
                <Alert
                  showIcon
                  type="warning"
                  message="暂时无法读取逐写入结果"
                  description="仍可通过上方“执行结果”下载完整结果制品。"
                  action={<Button size="small" onClick={() => void resultArtifactQuery.refetch()}>重试</Button>}
                />
              ) : outputResults.length === 0 ? (
                <Typography.Text type="secondary">本次运行没有逐写入结构化指标。</Typography.Text>
              ) : (
                <Collapse
                  size="small"
                  items={outputResults.map((node) => {
                    const metrics = node.metrics?.kind === 'OUTPUT_WRITES' ? node.metrics : null;
                    const writes = metrics?.writes ?? [];
                    const successCount = writes.filter((write) => write.state === 'SUCCESS').length;
                    const failedCount = writes.filter((write) => write.state === 'FAILED').length;
                    const skippedCount = writes.filter((write) => write.state === 'SKIPPED').length;
                    return {
                      key: node.nodeId,
                      label: (
                        <Space wrap>
                          <Typography.Text>{node.nodeName}</Typography.Text>
                          <Typography.Text type="secondary">{node.nodeType}</Typography.Text>
                          <Tag color="success">成功 {successCount}</Tag>
                          <Tag color={failedCount > 0 ? 'error' : 'default'}>失败 {failedCount}</Tag>
                          <Tag color={skippedCount > 0 ? 'warning' : 'default'}>未执行 {skippedCount}</Tag>
                          <Typography.Text type="secondary">
                            已提交 {node.rowsWritten ?? '—'} 行
                          </Typography.Text>
                        </Space>
                      ),
                      children: (
                        <Table<OutputWriteExecutionResult>
                          size="small"
                          pagination={false}
                          rowKey="writeId"
                          columns={outputWriteColumns}
                          dataSource={writes}
                          scroll={{ x: 780 }}
                        />
                      ),
                    };
                  })}
                />
              )}
            </Card>
          )}

          {run.taskType === 'SPARK_CANVAS' && run.status === 'SUCCESS' && (
            <Card size="small" title="快照同步结果" loading={artifactsQuery.isPending || resultArtifactQuery.isPending}>
              {shouldSkipStructuredResultPreview ? unavailableStructuredResult() : resultArtifactQuery.isError ? (
                <Alert
                  showIcon
                  type="warning"
                  message="暂时无法读取结构化执行指标"
                  description="仍可通过上方“执行结果”下载完整结果制品。"
                  action={<Button size="small" onClick={() => void resultArtifactQuery.refetch()}>重试</Button>}
                />
              ) : snapshotResults.length === 0 ? (
                <Typography.Text type="secondary">本次运行没有 Snapshot Sync 节点指标。</Typography.Text>
              ) : (
                <Space orientation="vertical" size={10} className="task-run-snapshot-results">
                  {snapshotResults.map((node) => {
                    const metrics = node.metrics?.kind === 'SNAPSHOT_SYNC' ? node.metrics : null;
                    if (!metrics) return null;
                    return (
                      <Descriptions
                        key={node.nodeId}
                        size="small"
                        bordered
                        column={4}
                        title={`${node.nodeName} · ${node.nodeType}`}
                      >
                        <Descriptions.Item label="来源行数">{metrics.sourceRows}</Descriptions.Item>
                        <Descriptions.Item label="目标行数">{metrics.targetRows}</Descriptions.Item>
                        <Descriptions.Item label="新增"><Tag color="success">{metrics.insertedRows}</Tag></Descriptions.Item>
                        <Descriptions.Item label="更新"><Tag color="processing">{metrics.updatedRows}</Tag></Descriptions.Item>
                        <Descriptions.Item label="删除"><Tag color={metrics.deletedRows > 0 ? 'error' : 'default'}>{metrics.deletedRows}</Tag></Descriptions.Item>
                        <Descriptions.Item label="未变化">{metrics.unchangedRows}</Descriptions.Item>
                        <Descriptions.Item label="保留目标独有行">{metrics.retainedTargetOnlyRows}</Descriptions.Item>
                        <Descriptions.Item label="实际写入">{node.rowsWritten ?? 0}</Descriptions.Item>
                      </Descriptions>
                    );
                  })}
                </Space>
              )}
            </Card>
          )}

          {run.taskType === 'SPARK_MODEL_QUALITY' && run.qualityConclusion && (
            <Card size="small" title="质量检查汇总">
              <Descriptions size="small" bordered column={3}>
                <Descriptions.Item label="质量结论">{qualityConclusion(run.qualityConclusion)}</Descriptions.Item>
                <Descriptions.Item label="检查行数">{run.qualityCheckedRows ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="规则总数">{run.qualityTotalRules ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="通过"><Tag color="success">{run.qualityPassedRules ?? 0}</Tag></Descriptions.Item>
                <Descriptions.Item label="不通过"><Tag color="error">{run.qualityFailedRules ?? 0}</Tag></Descriptions.Item>
                <Descriptions.Item label="跳过"><Tag color="warning">{run.qualitySkippedRules ?? 0}</Tag></Descriptions.Item>
              </Descriptions>
            </Card>
          )}

          {run.taskType === 'SPARK_MODEL_QUALITY' && run.status === 'SUCCESS' && (
            <Card size="small" title="规则检查结果" loading={artifactsQuery.isPending || resultArtifactQuery.isPending}>
              {shouldSkipStructuredResultPreview ? unavailableStructuredResult() : resultArtifactQuery.isError ? (
                <Alert
                  showIcon
                  type="warning"
                  message="暂时无法读取结构化质检结果"
                  description="仍可通过上方“执行结果”下载完整结果制品。"
                  action={<Button size="small" onClick={() => void resultArtifactQuery.refetch()}>重试</Button>}
                />
              ) : resultArtifactQuery.data?.qualityResult ? (
                <Space orientation="vertical" size={16} className="task-run-quality-results">
                  <Table<QualityRuleExecutionResult>
                    size="small"
                    rowKey="ruleId"
                    pagination={false}
                    columns={qualityRuleColumns}
                    dataSource={resultArtifactQuery.data.qualityResult.ruleResults}
                  />
                  {resultArtifactQuery.data.qualityResult.skippedRuleResults.length > 0 && (
                    <Table
                      size="small"
                      rowKey="id"
                      pagination={false}
                      title={() => '跳过规则'}
                      dataSource={resultArtifactQuery.data.qualityResult.skippedRuleResults}
                      columns={[
                        { title: '规则', dataIndex: 'name', width: 180 },
                        {
                          title: '类型', dataIndex: 'type', width: 120,
                          render: (type: QualitySkippedRuleResult['type']) => modelQualityRuleTypeLabels[type],
                        },
                        { title: '原因', dataIndex: 'reason' },
                        { title: '原因码', dataIndex: 'reasonCode', width: 180, render: (code) => <Typography.Text code>{code}</Typography.Text> },
                      ]}
                    />
                  )}
                </Space>
              ) : (
                <Empty description="没有可展示的质检结果" />
              )}
            </Card>
          )}

          {run.taskType === 'SPARK_MODEL_QUALITY'
            && run.status !== 'SUCCESS'
            && run.executionError
            && (
              <Alert
                type="error"
                showIcon
                message="质检执行发生技术错误"
                description={resultArtifactQuery.data?.qualityResult?.technicalFailure
                  ? `失败规则：${resultArtifactQuery.data.qualityResult.technicalFailure.ruleName}（${modelQualityRuleTypeLabels[resultArtifactQuery.data.qualityResult.technicalFailure.ruleType]}），诊断 ID：${resultArtifactQuery.data.qualityResult.technicalFailure.diagnosticId}`
                  : '本次运行没有生成通过/不通过指标；请结合下方诊断 ID、执行结果和控制台日志定位问题。'}
              />
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
      <QualityFailureSampleDrawer
        open={Boolean(sampleRule)}
        runId={run?.id ?? null}
        ruleId={sampleRule?.ruleId ?? null}
        ruleName={sampleRule?.ruleName ?? null}
        onClose={() => setSampleRule(null)}
      />
    </Drawer>
  );
};
