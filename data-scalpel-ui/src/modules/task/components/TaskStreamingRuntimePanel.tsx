import { EditOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Alert,
  Button,
  Descriptions,
  Empty,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import { ApiError } from '../../../shared/api/http';
import { useNavigate } from 'react-router-dom';
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

  if (statusQuery.isError) {
    return (
      <div className="task-detail-tab-panel">
        <Alert
          type="error"
          showIcon
          message="实时运行状态加载失败"
          description={statusQuery.error instanceof ApiError ? statusQuery.error.message : '请稍后重试。'}
          action={<Button size="small" onClick={() => void statusQuery.refetch()}>重试</Button>}
        />
      </div>
    );
  }

  return (
    <div className="task-detail-tab-panel task-streaming-runtime-panel">
      <div className="task-detail-tab-toolbar">
        <Space size={8} wrap>
          <Typography.Text strong>实时运行</Typography.Text>
          <Tag color="processing">{source.kind}</Tag>
          <Typography.Text>{source.name}</Typography.Text>
          {!streamingJar && <Typography.Text type="secondary">
            {source.interval === null ? '间隔 —' : `每 ${source.interval} 秒触发`}
          </Typography.Text>}
        </Space>
        <Space>
          <Button
            icon={<EditOutlined />}
            onClick={() => navigate(`/task/${task.id}/definition`)}
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
      <Alert
        type="info"
        showIcon
        message="实时任务持续消费数据，不使用 Cron 定时计划"
        description={streamingJar
          ? '用户代码控制 Trigger、Output Mode 和处理逻辑；平台注册并监控全部查询，各查询使用独立 Checkpoint，任一查询失败会停止整个 Application。'
          : 'Kafka 与 JDBC Sink 均按至少一次处理，故障恢复可能产生重复；多个输出使用独立 Checkpoint，不提供跨 Sink 事务。'}
      />
      {!deployment ? (
        <Empty description="当前定义尚未启动过，发布后可从页面顶部启动实时任务。" />
      ) : (
        <Space orientation="vertical" size={12} className="task-streaming-runtime-content">
          {deployment.lastError && (
            <Alert
              type="error"
              showIcon
              message="实时部署失败"
              description={deployment.lastError}
            />
          )}
          <Descriptions size="small" bordered column={4}>
            <Descriptions.Item label="部署状态">
              <Tag color={streamingDeploymentStateColors[deployment.actualState]}>
                {streamingDeploymentStateLabels[deployment.actualState]}
              </Tag>
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
            {!streamingJar && <>
              <Descriptions.Item label="最近读取窗口" span={2}>
                {deployment.windowEnd
                  ? `${deployment.windowStart ? formatTaskRunDateTime(deployment.windowStart) : '无下界'} ～ ${formatTaskRunDateTime(deployment.windowEnd)}`
                  : '—'}
              </Descriptions.Item>
              <Descriptions.Item label="窗口读取行数">
                {numberValue(deployment.rowCount, 0)}
              </Descriptions.Item>
              <Descriptions.Item label="窗口处理耗时">
                {durationValue(deployment.pollDurationMillis)}
              </Descriptions.Item>
              <Descriptions.Item label="已提交时间">
                {formatTaskRunDateTime(deployment.windowEnd)}
              </Descriptions.Item>
              <Descriptions.Item label="近似游标延迟">
                {durationValue(deployment.cursorLagMillis)}
              </Descriptions.Item>
              <Descriptions.Item label="最近轮询">
                {formatTaskRunDateTime(deployment.pollTime)}
              </Descriptions.Item>
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
          </Descriptions>
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
