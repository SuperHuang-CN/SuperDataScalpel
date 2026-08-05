import { EyeOutlined, ReloadOutlined, SearchOutlined, StopOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Form, Modal, Select, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useCancelTaskRun, useTaskRuns } from '../hooks/useTasks';
import {
  taskRunExecutionModeLabels,
  taskRunStatusColors,
  taskRunStatusLabels,
  taskRunTriggerTypeLabels,
  type DataTask,
  type TaskRun,
} from '../model/task';
import { buildTaskRunSearch, type TaskRunFilters } from '../model/taskRunSearch';
import { formatTaskRunDateTime, formatTaskRunDuration } from '../model/taskRunPresentation';
import { TaskRunDetailDrawer } from './TaskRunDetailDrawer';

interface TaskRunsPanelProps {
  task: DataTask;
  canExecute: boolean;
}

const cancellable = (run: TaskRun) => run.taskType === 'SPARK_CANVAS'
  && (run.status === 'QUEUED' || run.status === 'RUNNING' || run.status === 'CANCEL_REQUESTED');

export const TaskRunsPanel = ({ task, canExecute }: TaskRunsPanelProps) => {
  const [form] = Form.useForm<TaskRunFilters>();
  const [filters, setFilters] = useState<TaskRunFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [detailRunId, setDetailRunId] = useState<string | null>(null);
  const [cancellingRunId, setCancellingRunId] = useState<string | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const cancelMutation = useCancelTaskRun();
  const request = useMemo(() => ({
    search: buildTaskRunSearch(filters),
    page,
    size,
    sort: '-queuedAt',
  }), [filters, page, size]);
  const runsQuery = useTaskRuns(task.id, request, true);

  const cancel = (run: TaskRun) => modalApi.confirm({
    title: '取消任务运行',
    content: `确认取消运行“${run.id}”吗？已开始的 Spark 应用将被停止。`,
    okText: '取消运行',
    okButtonProps: { danger: true },
    cancelText: '返回',
    onOk: async () => {
      setCancellingRunId(run.id);
      try {
        await cancelMutation.mutateAsync(run.id);
        messageApi.success('已提交取消请求');
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '取消任务运行失败');
        throw error;
      } finally {
        setCancellingRunId(null);
      }
    },
  });

  const columns: TableProps<TaskRun>['columns'] = [
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (status: TaskRun['status']) => (
        <Tag color={taskRunStatusColors[status]}>{taskRunStatusLabels[status]}</Tag>
      ),
    },
    {
      title: '触发方式', dataIndex: 'triggerType', width: 90,
      render: (value: TaskRun['triggerType']) => taskRunTriggerTypeLabels[value],
    },
    {
      title: '执行模式', dataIndex: 'executionMode', width: 100,
      render: (value: TaskRun['executionMode']) => taskRunExecutionModeLabels[value],
    },
    { title: '定义版本', dataIndex: 'definitionVersion', width: 100, align: 'right' },
    { title: '计划触发时间', dataIndex: 'scheduledFireAt', width: 175, render: formatTaskRunDateTime },
    { title: '排队时间', dataIndex: 'queuedAt', width: 175, render: formatTaskRunDateTime },
    { title: '开始时间', dataIndex: 'startedAt', width: 175, render: formatTaskRunDateTime },
    { title: '结束时间', dataIndex: 'endedAt', width: 175, render: formatTaskRunDateTime },
    { title: '耗时', key: 'duration', width: 105, render: (_, run) => formatTaskRunDuration(run) },
    {
      title: '影响行数', dataIndex: 'affectedRows', width: 100, align: 'right',
      render: (value: number | null) => value === null
        ? <span aria-label="影响行数未知">—</span>
        : value,
    },
    {
      title: '结果', dataIndex: 'message', width: 260, ellipsis: true,
      render: (value: string | null, run) => value ?? run.errorDetail ?? '—',
    },
    {
      title: '操作', key: 'actions', width: 86, fixed: 'right',
      render: (_, run) => (
        <Space size={0}>
          <Tooltip title="查看运行详情">
            <Button
              type="text"
              icon={<EyeOutlined />}
              aria-label={`查看运行 ${run.id}`}
              onClick={() => setDetailRunId(run.id)}
            />
          </Tooltip>
          {canExecute && cancellable(run) && (
            <Tooltip title={run.status === 'CANCEL_REQUESTED' ? '正在取消' : '取消运行'}>
              <Button
                type="text"
                danger
                icon={<StopOutlined />}
                aria-label={`取消运行 ${run.id}`}
                loading={cancellingRunId === run.id}
                disabled={run.status === 'CANCEL_REQUESTED'}
                onClick={() => cancel(run)}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className="task-detail-tab-panel task-runs-panel">
      {messageContext}
      {modalContext}
      <div className="task-detail-tab-toolbar">
        <Typography.Text strong>运行记录</Typography.Text>
        <Form<TaskRunFilters> autoComplete="off"
          form={form}
          layout="inline"
          initialValues={filters}
          onFinish={(values) => { setFilters(values); setPage(0); }}
        >
          <Form.Item name="status">
            <Select
              allowClear
              placeholder="全部状态"
              className="task-run-filter-select"
              options={Object.entries(taskRunStatusLabels).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="triggerType">
            <Select
              allowClear
              placeholder="全部触发方式"
              className="task-run-filter-select"
              options={Object.entries(taskRunTriggerTypeLabels).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="executionMode">
            <Select
              allowClear
              placeholder="全部执行模式"
              className="task-run-filter-select"
              options={Object.entries(taskRunExecutionModeLabels).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Space size={8}>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查询</Button>
            <Button onClick={() => { form.resetFields(); setFilters({}); setPage(0); }}>重置</Button>
            <Button
              icon={<ReloadOutlined />}
              loading={runsQuery.isFetching}
              onClick={() => void runsQuery.refetch()}
            >
              刷新
            </Button>
          </Space>
        </Form>
      </div>
      {runsQuery.isError && (
        <Alert
          type="error"
          showIcon
          message="加载运行记录失败"
          action={<Button size="small" icon={<ReloadOutlined />} onClick={() => void runsQuery.refetch()}>重试</Button>}
        />
      )}
      <Table<TaskRun>
        rowKey="id"
        size="small"
        loading={runsQuery.isLoading}
        columns={columns}
        dataSource={runsQuery.data?.content ?? []}
        scroll={{ x: 1_660, y: 'calc(100vh - 330px)' }}
        pagination={{
          current: page + 1,
          pageSize: size,
          total: runsQuery.data?.totalElements ?? 0,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
          onChange: (nextPage, nextSize) => {
            setPage(nextPage - 1);
            setSize(nextSize);
          },
        }}
      />
      <TaskRunDetailDrawer
        open={Boolean(detailRunId)}
        runId={detailRunId}
        canExecute={canExecute}
        cancelLoading={cancellingRunId === detailRunId}
        onClose={() => setDetailRunId(null)}
        onCancel={cancel}
      />
    </div>
  );
};
