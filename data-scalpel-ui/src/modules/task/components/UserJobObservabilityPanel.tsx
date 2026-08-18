import type { TableProps } from 'antd';
import { Descriptions, Table, Tag, Typography } from 'antd';
import type { UserJobMetricSnapshot, UserJobObservability } from '../model/task';
import { formatTaskRunDateTime } from '../model/taskRunPresentation';

interface UserJobObservabilityPanelProps {
  observability: UserJobObservability | null | undefined;
  attemptScoped?: boolean;
}

const duration = (value: number | null) => {
  if (value === null) return '—';
  if (value < 1_000) return `${value.toLocaleString('zh-CN')} ms`;
  return `${(value / 1_000).toLocaleString('zh-CN', { maximumFractionDigits: 2 })} 秒`;
};

const metricValue = (metric: UserJobMetricSnapshot) => {
  if (metric.kind === 'COUNTER') return metric.counterValue?.toLocaleString('zh-CN') ?? '—';
  if (metric.kind === 'GAUGE') {
    return metric.gaugeValue?.toLocaleString('zh-CN', { maximumFractionDigits: 6 }) ?? '—';
  }
  return `次数 ${metric.count?.toLocaleString('zh-CN') ?? '—'} · 最近 ${duration(metric.lastDurationMillis)} · 累计 ${duration(metric.totalDurationMillis)} · 最大 ${duration(metric.maxDurationMillis)}`;
};

const columns: TableProps<UserJobMetricSnapshot>['columns'] = [
  {
    title: '指标',
    dataIndex: 'name',
    width: 300,
    ellipsis: true,
    render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
  },
  {
    title: '类型',
    dataIndex: 'kind',
    width: 90,
    render: (value: UserJobMetricSnapshot['kind']) => (
      <Tag>{value === 'COUNTER' ? 'Counter' : value === 'GAUGE' ? 'Gauge' : 'Timer'}</Tag>
    ),
  },
  {
    title: '当前值',
    render: (_, metric) => metricValue(metric),
  },
];

export const UserJobObservabilityPanel = ({
  observability,
  attemptScoped = false,
}: UserJobObservabilityPanelProps) => {
  if (!observability || (!observability.status && observability.metrics.length === 0)) return null;
  return (
    <div className="task-user-observability-panel">
      <Descriptions
        size="small"
        bordered
        column={2}
        title={attemptScoped ? '用户作业观测（当前 Attempt）' : '用户作业观测'}
      >
        <Descriptions.Item label="当前阶段">
          {observability.status ? <Tag color="processing">{observability.status.phase}</Tag> : '—'}
        </Descriptions.Item>
        <Descriptions.Item label="阶段更新时间">
          {formatTaskRunDateTime(observability.status?.updatedAt ?? null)}
        </Descriptions.Item>
        <Descriptions.Item label="状态说明" span={2}>
          {observability.status?.message ?? '—'}
        </Descriptions.Item>
      </Descriptions>
      {observability.metrics.length > 0 && (
        <Table<UserJobMetricSnapshot>
          rowKey="name"
          size="small"
          pagination={false}
          columns={columns}
          dataSource={observability.metrics}
          scroll={{ x: 720 }}
        />
      )}
    </div>
  );
};
