import { ReloadOutlined } from '@ant-design/icons';
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
import { useTaskStreamingStatus } from '../hooks/useTasks';
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

interface TaskStreamingRuntimePanelProps {
  task: DataTask;
}

const numberValue = (value: number | null, digits = 2) => (
  value === null ? '—' : value.toLocaleString('zh-CN', { maximumFractionDigits: digits })
);

export const TaskStreamingRuntimePanel = ({ task }: TaskStreamingRuntimePanelProps) => {
  const statusQuery = useTaskStreamingStatus(task.id);
  const deployment = statusQuery.data?.deployment ?? null;
  const trackingUrl = safeTrackingUrl(deployment?.trackingUrl ?? null);
  const columns: TableProps<TaskStreamingQuery>['columns'] = [
    {
      title: '输出节点',
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
        <Typography.Text strong>实时运行</Typography.Text>
        <Button
          icon={<ReloadOutlined />}
          loading={statusQuery.isFetching}
          onClick={() => void statusQuery.refetch()}
        >
          刷新
        </Button>
      </div>
      <Alert
        type="info"
        showIcon
        message="实时任务持续消费数据，不使用 Cron 定时计划"
        description="Kafka 与 JDBC Sink 均按至少一次处理，故障恢复可能产生重复；多个输出使用独立 Checkpoint，不提供跨 Sink 事务。"
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
