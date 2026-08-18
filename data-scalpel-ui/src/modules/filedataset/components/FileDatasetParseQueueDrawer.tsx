import { ReloadOutlined } from '@ant-design/icons';
import type { DescriptionsProps, TableProps } from 'antd';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Form,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import { useMemo, useState } from 'react';
import {
  useFileDatasetParseJobs,
  useFileDatasetParseQueueSummary,
} from '../hooks/useFileDatasets';
import {
  buildFileDatasetParseJobSearch,
  fileDatasetParseJobLoadModeLabels,
  fileDatasetParseJobStatusLabels,
  fileDatasetParseJobStatusOptions,
  fileDatasetParseJobTypeLabels,
  type FileDatasetParseJob,
  type FileDatasetParseJobFilters,
  type FileDatasetParseJobStatus,
} from '../model/fileDatasetParseJob';

interface FileDatasetParseQueueDrawerProps {
  open: boolean;
  onClose: () => void;
}

const DEFAULT_PAGE_SIZE = 20;

const statusColors: Record<FileDatasetParseJobStatus, string> = {
  QUEUED: 'blue',
  RUNNING: 'processing',
  SUCCEEDED: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
};

const formatDateTime = (value: string | null): string => value
  ? new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium', timeStyle: 'medium', hour12: false,
  }).format(new Date(value))
  : '—';

const shortId = (id: string): string => id.slice(0, 8);

const resourceName = (name: string | null, id: string): React.ReactNode => (
  <Tooltip title={name ? id : `源资源已经删除 · ${id}`}>
    <Typography.Text copyable={{ text: id }}>{name ?? shortId(id)}</Typography.Text>
  </Tooltip>
);

export const FileDatasetParseQueueDrawer = ({ open, onClose }: FileDatasetParseQueueDrawerProps) => {
  const [form] = Form.useForm<FileDatasetParseJobFilters>();
  const [filters, setFilters] = useState<FileDatasetParseJobFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const request = useMemo(() => ({
    search: buildFileDatasetParseJobSearch(filters),
    page,
    size,
    sort: '-queuedAt',
  }), [filters, page, size]);
  const summaryQuery = useFileDatasetParseQueueSummary(open);
  const jobsQuery = useFileDatasetParseJobs(request, open);
  const summary = summaryQuery.data;

  const search = (values: FileDatasetParseJobFilters) => {
    setFilters(values);
    setPage(0);
  };

  const reset = () => {
    form.resetFields();
    search({});
  };

  const summaryItems: DescriptionsProps['items'] = summary ? [
    {
      key: 'enabled',
      label: '队列开关',
      children: <Tag color={summary.queueEnabled ? 'success' : 'warning'}>{summary.queueEnabled ? '已开启' : '已关闭'}</Tag>,
    },
    { key: 'concurrency', label: '单实例并发', children: summary.configuredWorkerConcurrency },
    { key: 'queued', label: '排队总数', children: summary.queuedCount },
    { key: 'runnable', label: '可领取', children: summary.runnableQueuedCount },
    { key: 'retry', label: '退避等待', children: summary.retryWaitingCount },
    { key: 'running', label: '运行中', children: summary.runningCount },
    { key: 'failed', label: '失败历史', children: summary.failedCount },
    { key: 'retention', label: '历史保留', children: `${summary.historyRetentionDays} 天` },
    { key: 'succeeded', label: '成功历史', children: summary.succeededCount },
    { key: 'cancelled', label: '取消历史', children: summary.cancelledCount },
    { key: 'oldestQueued', label: '最早排队', children: formatDateTime(summary.oldestQueuedAt) },
    { key: 'oldestRunning', label: '最早运行', children: formatDateTime(summary.oldestRunningAt) },
  ] : [];

  const columns: TableProps<FileDatasetParseJob>['columns'] = [
    {
      title: '任务', dataIndex: 'id', width: 105,
      render: (id: string) => (
        <Typography.Text copyable={{ text: id }} code>{shortId(id)}</Typography.Text>
      ),
    },
    {
      title: '类型', dataIndex: 'type', width: 90,
      render: (type: FileDatasetParseJob['type']) => <Tag>{fileDatasetParseJobTypeLabels[type]}</Tag>,
    },
    {
      title: '数据集 / 表', width: 230,
      render: (_value: unknown, job: FileDatasetParseJob) => (
        <Space direction="vertical" size={0}>
          {resourceName(job.fileDatasetName, job.fileDatasetId)}
          <Typography.Text type="secondary">
            {job.fileDatasetTableId
              ? resourceName(job.tableName, job.fileDatasetTableId)
              : '准备归档文件'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '源文件', width: 180, ellipsis: true,
      render: (_value: unknown, job: FileDatasetParseJob) => resourceName(job.sourceFileName, job.sourceFileId),
    },
    {
      title: '装载 / 来源', width: 190,
      render: (_value: unknown, job: FileDatasetParseJob) => job.loadMode ? (
        <Space direction="vertical" size={0}>
          <Tag>{fileDatasetParseJobLoadModeLabels[job.loadMode]}</Tag>
          <Typography.Text type="secondary" ellipsis={{ tooltip: job.sourceName ?? undefined }}>
            {job.sourceName ?? '—'}
          </Typography.Text>
          {job.targetSourceId && (
            <Tooltip title={job.targetSourceId}>
              <Typography.Text type="secondary">目标 {shortId(job.targetSourceId)}</Typography.Text>
            </Tooltip>
          )}
        </Space>
      ) : '—',
    },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: (status: FileDatasetParseJobStatus) => (
        <Tag color={statusColors[status]}>{fileDatasetParseJobStatusLabels[status]}</Tag>
      ),
    },
    {
      title: '尝试', width: 75, align: 'right',
      render: (_value: unknown, job: FileDatasetParseJob) => `${job.attemptCount} / ${job.maxAttempts}`,
    },
    { title: '入队时间', dataIndex: 'queuedAt', width: 175, render: formatDateTime },
    {
      title: '开始 / 可执行', width: 175,
      render: (_value: unknown, job: FileDatasetParseJob) => formatDateTime(
        job.status === 'QUEUED' ? job.availableAt : job.startedAt,
      ),
    },
    {
      title: '完成 / 租约', width: 175,
      render: (_value: unknown, job: FileDatasetParseJob) => formatDateTime(
        job.status === 'RUNNING' ? job.leaseExpiresAt : job.completedAt,
      ),
    },
    {
      title: 'Worker / 心跳', width: 210,
      render: (_value: unknown, job: FileDatasetParseJob) => job.leaseOwner ? (
        <Space direction="vertical" size={0}>
          <Typography.Text ellipsis={{ tooltip: job.leaseOwner }}>{job.leaseOwner}</Typography.Text>
          <Typography.Text type="secondary">{formatDateTime(job.lastHeartbeatAt)}</Typography.Text>
        </Space>
      ) : '—',
    },
    {
      title: '错误摘要', dataIndex: 'errorMessage', width: 260,
      render: (value: string | null) => value
        ? <Typography.Text ellipsis={{ tooltip: value }}>{value}</Typography.Text>
        : '—',
    },
  ];

  return (
    <Drawer
      rootClassName="business-overlay business-drawer-overlay"
      title="文件解析队列"
      open={open}
      size={1180}
      onClose={onClose}
      destroyOnHidden
      extra={(
        <Button
          icon={<ReloadOutlined />}
          loading={summaryQuery.isFetching || jobsQuery.isFetching}
          onClick={() => void Promise.all([summaryQuery.refetch(), jobsQuery.refetch()])}
        >
          刷新
        </Button>
      )}
    >
      {summaryQuery.isError && (
        <Alert
          type="error"
          showIcon
          className="file-dataset-form-alert"
          message="解析队列摘要加载失败"
          action={<Button size="small" onClick={() => void summaryQuery.refetch()}>重试</Button>}
        />
      )}
      {summary && (
        <>
          <Alert
            type={summary.queueEnabled ? 'info' : 'warning'}
            showIcon
            className="file-dataset-form-alert"
            message={summary.queueEnabled ? '统一解析队列已开启' : '统一解析队列已关闭'}
            description={summary.queueEnabled
              ? '摘要和任务列表每 5 秒自动刷新；关闭抽屉后停止。'
              : 'Worker 将停止领取新任务，但已排队任务不会丢失，正在运行的任务继续完成。'}
          />
          <Descriptions size="small" bordered column={4} items={summaryItems} className="file-dataset-form-alert" />
        </>
      )}
      <div className="management-toolbar">
        <Form<FileDatasetParseJobFilters> autoComplete="off" form={form} layout="inline" onFinish={search}>
          <Form.Item name="status" label="任务状态">
            <Select allowClear placeholder="全部" options={fileDatasetParseJobStatusOptions} style={{ width: 140 }} />
          </Form.Item>
        </Form>
        <Space size={4}>
          <Button type="primary" onClick={() => form.submit()}>查询</Button>
          <Button onClick={reset}>重置</Button>
        </Space>
      </div>
      {jobsQuery.isError && (
        <Alert
          type="error"
          showIcon
          className="file-dataset-form-alert"
          message="解析任务列表加载失败"
          action={<Button size="small" onClick={() => void jobsQuery.refetch()}>重试</Button>}
        />
      )}
      <Table<FileDatasetParseJob>
        size="small"
        rowKey="id"
        columns={columns}
        dataSource={jobsQuery.data?.content ?? []}
        loading={jobsQuery.isFetching}
        scroll={{ x: 1690, y: 460 }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: jobsQuery.data?.totalElements ?? 0,
          size: 'small',
          position: ['bottomRight'],
          hideOnSinglePage: false,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 项`,
        }}
        onChange={(pagination) => {
          setPage((pagination.current ?? 1) - 1);
          setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE);
        }}
      />
    </Drawer>
  );
};
