import { taskPageHref } from '../model/taskViews';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { DeploymentUnitOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Descriptions, Empty, Space, Spin, Table, Tag, Typography } from 'antd';
import { ApiError } from '../../../shared/api/http';
import { useLocation, useNavigate } from 'react-router-dom';
import { CanvasNodeType, type CanvasNodeDefinition } from '../canvas/canvasTypes';
import { useCanvasTaskDefinition, useTaskStreamingStatus } from '../hooks/useTasks';
import {
  streamingDeploymentStateColors,
  streamingDeploymentStateLabels,
  streamingQueryStateColors,
  streamingQueryStateLabels,
  streamingSinkTypeLabels,
  type DataTask,
  type TaskStreamingQuery,
} from '../model/task';
import { formatTaskRunDateTime, safeTrackingUrl } from '../model/taskRunPresentation';
import { UserJobObservabilityPanel } from './UserJobObservabilityPanel';

interface TaskStreamingRuntimePanelProps {
  task: DataTask;
}

const numberValue = (value: number | null, digits = 2) => (
  value === null ? '—' : value.toLocaleString('zh-CN', { maximumFractionDigits: digits })
);

const unboundedInput = (nodes: CanvasNodeDefinition[]) => nodes.find((node) => (
  node.type === CanvasNodeType.KafkaInput
  || node.type === CanvasNodeType.TdEngineTmqInput
  || node.type === CanvasNodeType.JdbcIncrementalInput
));

const inputLabel = (node: CanvasNodeDefinition | undefined) => {
  if (!node) return { name: '—', kind: '—', interval: null as number | null };
  if (node.type === CanvasNodeType.JdbcIncrementalInput) {
    return { name: node.name, kind: 'JDBC 增量输入', interval: node.configuration.triggerIntervalSeconds ?? 60 };
  }
  if (node.type === CanvasNodeType.TdEngineTmqInput) {
    return { name: node.name, kind: 'TDengine TMQ', interval: node.configuration.triggerIntervalSeconds ?? 10 };
  }
  if (node.type === CanvasNodeType.KafkaInput) {
    return { name: node.name, kind: 'Kafka', interval: node.configuration.triggerIntervalSeconds ?? 10 };
  }
  return { name: '—', kind: '—', interval: null };
};

const durationValue = (value: number | null) => {
  if (value === null) return '—';
  if (value < 1_000) return `${value.toLocaleString('zh-CN')} ms`;
  return `${(value / 1_000).toLocaleString('zh-CN', { maximumFractionDigits: 1 })} 秒`;
};

export const TaskStreamingRuntimePanel = ({ task }: TaskStreamingRuntimePanelProps) => {
  const navigate = useNavigate();
  const location = useLocation();
  const streamingJar = task.type === 'SPARK_STREAMING_JAR';
  const statusQuery = useTaskStreamingStatus(task.id);
  const definitionQuery = useCanvasTaskDefinition(task.id, !streamingJar);
  const deployment = statusQuery.data?.deployment ?? null;
  const source = streamingJar
    ? { name: '用户代码控制 Trigger', kind: 'Streaming SDK', interval: null as number | null }
    : inputLabel(unboundedInput(definitionQuery.data?.definition?.nodes ?? []));
  const trackingUrl = safeTrackingUrl(deployment?.trackingUrl ?? null);
  const columns: TableProps<TaskStreamingQuery>['columns'] = [
    {
      title: streamingJar ? '逻辑查询' : '输出节点',
      dataIndex: 'outputNodeName',
      width: 180,
      ellipsis: true,
    },
    {
      title: 'Sink',
      dataIndex: 'sinkType',
      width: 80,
      render: (value: TaskStreamingQuery['sinkType']) => streamingSinkTypeLabels[value],
    },
    {
      title: '状态',
      dataIndex: 'state',
      width: 100,
      render: (value: TaskStreamingQuery['state']) => (
        <Tag color={streamingQueryStateColors[value]}>{streamingQueryStateLabels[value]}</Tag>
      ),
    },
    {
      title: 'Batch ID',
      dataIndex: 'latestBatchId',
      width: 100,
      align: 'right',
      render: (value: number | null) => numberValue(value, 0),
    },
    {
      title: '输入行数',
      dataIndex: 'latestInputRows',
      width: 100,
      align: 'right',
      render: (value: number | null) => numberValue(value, 0),
    },
    {
      title: '输入速率',
      dataIndex: 'inputRowsPerSecond',
      width: 125,
      align: 'right',
      render: (value: number | null) => `${numberValue(value)} 行/秒`,
    },
    {
      title: '处理速率',
      dataIndex: 'processedRowsPerSecond',
      width: 125,
      align: 'right',
      render: (value: number | null) => `${numberValue(value)} 行/秒`,
    },
    {
      title: '批次耗时',
      dataIndex: 'batchDurationMillis',
      width: 110,
      align: 'right',
      render: (value: number | null) => value === null ? '—' : `${numberValue(value, 0)} ms`,
    },
    {
      title: '最后进度',
      dataIndex: 'lastProgressAt',
      width: 175,
      render: formatTaskRunDateTime,
    },
    {
      title: 'Checkpoint',
      dataIndex: 'checkpointKey',
      width: 260,
      ellipsis: true,
      render: (value: string) => <Typography.Text copyable={{ text: value }}>{value}</Typography.Text>,
    },
    {
      title: '错误',
      dataIndex: 'lastError',
      width: 240,
      ellipsis: true,
      render: (value: string | null) => value ?? '—',
    },
  ];

  if (statusQuery.isPending) {
    return <div className="task-detail-tab-panel"><Spin tip="正在加载实时运行状态…" /></div>;
  }

  if (statusQuery.isError) return (
    <div className="task-detail-tab-panel">
      <InlineFeedback
        tone="error"
        label="实时运行状态加载失败"
        detail={statusQuery.error instanceof ApiError ? statusQuery.error.message : '请稍后重试。'}
        action={<Button size="small" onClick={() => void statusQuery.refetch()}>重试</Button>}
      />
    </div>
  );

  return (
    <div className="task-detail-tab-panel task-streaming-runtime-panel">
      <div className="task-detail-tab-toolbar">
        <Space size={8} wrap>
          <Typography.Text strong>实时运行</Typography.Text>
          <ContextHelp
            ariaLabel="实时任务运行说明"
            presentation="popover"
            content={streamingJar
              ? '实时任务持续消费数据，不使用 Cron 定时计划。用户代码控制 Trigger、Output Mode 和处理逻辑；平台注册并监控全部查询，各查询使用独立 Checkpoint，任一查询失败会停止整个 Application。'
              : '实时任务持续消费数据，不使用 Cron 定时计划。Kafka 与 JDBC Sink 均按至少一次处理，故障恢复可能产生重复；多个输出使用独立 Checkpoint，不提供跨 Sink 事务。'}
          />
          <Tag color="processing">{source.kind}</Tag>
          {(statusQuery.data?.tmqConsumerGroupCleanupPendingCount ?? 0) > 0
            ? <Tag color="warning">TMQ Group 待清理 {statusQuery.data?.tmqConsumerGroupCleanupPendingCount}</Tag>
            : null}
          {(statusQuery.data?.tmqConsumerGroupCleanupFailedCount ?? 0) > 0
            ? <>
              <Tag color="error">TMQ Group 清理失败 {statusQuery.data?.tmqConsumerGroupCleanupFailedCount}</Tag>
              <ContextHelp
                ariaLabel="TMQ Consumer Group 清理失败说明"
                content="旧 Consumer Group 的后台清理会自动退避重试，不影响当前实时任务启动、停止或运行。"
              />
            </>
            : null}
          <Typography.Text>{source.name}</Typography.Text>
          {!streamingJar && <Typography.Text type="secondary">
            {source.interval === null ? '间隔 —' : `每 ${source.interval} 秒触发`}
          </Typography.Text>}
        </Space>
        <Space>
          <Button
            icon={<EditOutlined />}
            onClick={() => navigate(taskPageHref(`/task/${task.id}/definition`, location.search, task.type))}
          >
            {streamingJar ? '编辑 JAR 定义' : '编辑输入节点'}
          </Button>
          <Button
            icon={<ReloadOutlined />}
            loading={statusQuery.isFetching || definitionQuery.isFetching}
            onClick={() => {
              void statusQuery.refetch();
              if (!streamingJar) void definitionQuery.refetch();
            }}
          >
            刷新
          </Button>
        </Space>
      </div>
      {!deployment ? (
        <Empty description="当前定义尚未启动过，发布后可从页面顶部启动实时任务。" />
      ) : (
        <Space orientation="vertical" size={12} className="task-streaming-runtime-content">
          <BusinessDetailSection title="实时部署状态" description="当前 Deployment、Checkpoint 与处理进度" icon={<DeploymentUnitOutlined />}>
          <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 4 }}>
            <Descriptions.Item label="部署状态">
              {deployment.lastError ? (
                <InlineFeedback
                  tone="error"
                  label={streamingDeploymentStateLabels[deployment.actualState]}
                  detail={deployment.lastError}
                  ariaLabel="实时部署失败详情"
                />
              ) : (
                <Tag color={streamingDeploymentStateColors[deployment.actualState]}>
                  {streamingDeploymentStateLabels[deployment.actualState]}
                </Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="定义版本">v{deployment.definitionVersion}</Descriptions.Item>
            <Descriptions.Item label="当前 Attempt">{deployment.attempt ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="最后进度">
              {formatTaskRunDateTime(deployment.lastProgressAt)}
            </Descriptions.Item>
            <Descriptions.Item label="Checkpoint 世代">G{deployment.checkpointGeneration}</Descriptions.Item>
            <Descriptions.Item label="启动方式">
              {deployment.checkpointStartMode === 'CONTINUE' ? '继续最近 Checkpoint' : '全新启动'}
            </Descriptions.Item>
            <Descriptions.Item label="来源 Deployment" span={2}>
              {deployment.checkpointSourceDeploymentId
                ? <Typography.Text code copyable={{ text: deployment.checkpointSourceDeploymentId }}>{deployment.checkpointSourceDeploymentId}</Typography.Text>
                : '—'}
            </Descriptions.Item>
            {!streamingJar && deployment.sourceKind === 'TDENGINE_TMQ' && <>
              <Descriptions.Item label="最近批次行数">
                {numberValue(deployment.rowCount, 0)}
              </Descriptions.Item>
              <Descriptions.Item label="VGroup 数量">
                {numberValue(deployment.vGroupCount, 0)}
              </Descriptions.Item>
              <Descriptions.Item label="批次 Offset 跨度">
                {numberValue(deployment.batchOffsetSpan, 0)}
              </Descriptions.Item>
              <Descriptions.Item label="批次处理耗时">
                {durationValue(deployment.pollDurationMillis)}
              </Descriptions.Item>
              <Descriptions.Item label="最近轮询">
                {formatTaskRunDateTime(deployment.pollTime)}
              </Descriptions.Item>
            </>}
            {!streamingJar && deployment.sourceKind !== 'TDENGINE_TMQ' && <>
              <Descriptions.Item label="最近读取窗口" span={2}>
                {deployment.windowEnd
                  ? `${deployment.windowStart ? formatTaskRunDateTime(deployment.windowStart) : '无下界'} ～ ${formatTaskRunDateTime(deployment.windowEnd)}`
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="窗口读取行数">{numberValue(deployment.rowCount, 0)}</Descriptions.Item>
              <Descriptions.Item label="窗口处理耗时">{durationValue(deployment.pollDurationMillis)}</Descriptions.Item>
              <Descriptions.Item label="已提交时间">{formatTaskRunDateTime(deployment.windowEnd)}</Descriptions.Item>
              <Descriptions.Item label="近似游标延迟">{durationValue(deployment.cursorLagMillis)}</Descriptions.Item>
              <Descriptions.Item label="最近轮询">{formatTaskRunDateTime(deployment.pollTime)}</Descriptions.Item>
            </>}
            {!streamingJar && <>
              <Descriptions.Item label="来源签名" span={2}>
                {deployment.sourceSignature
                  ? <Typography.Text copyable={{ text: deployment.sourceSignature }} code>{deployment.sourceSignature.slice(0, 16)}…</Typography.Text>
                  : '—'}
              </Descriptions.Item>
            </>}
            <Descriptions.Item label="Application ID" span={2}>
              {deployment.applicationId ?? '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Tracking URL" span={2}>
              {trackingUrl
                ? (
                  <Typography.Link href={trackingUrl} target="_blank" rel="noopener noreferrer">
                    打开 Spark 跟踪页面
                  </Typography.Link>
                )
                : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="Deployment ID" span={2}>
              <Typography.Text code copyable={{ text: deployment.id }}>{deployment.id}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="TaskRun ID" span={2}>
              {deployment.currentRunId
                ? (
                  <Typography.Text code copyable={{ text: deployment.currentRunId }}>
                    {deployment.currentRunId}
                  </Typography.Text>
                )
                : '—'}
            </Descriptions.Item>
          </BusinessDetailDescriptions>
          </BusinessDetailSection>
          {streamingJar && (
            <UserJobObservabilityPanel
              observability={deployment.userJobObservability}
              attemptScoped
            />
          )}
          <Table<TaskStreamingQuery>
            rowKey="id"
            size="small"
            pagination={false}
            columns={columns}
            dataSource={deployment.queries}
            scroll={{ x: 1_590 }}
          />
        </Space>
      )}
    </div>
  );
};
