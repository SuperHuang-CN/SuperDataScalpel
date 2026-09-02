import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { EyeOutlined, ReloadOutlined, SearchOutlined, StopOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Form, Modal, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { useCancelTaskRun, useForceTerminateTaskRun, useTaskRuns } from '../hooks/useTasks';
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
  detailRunId: string | null;
  onDetailRunChange: (runId: string | null) => void;
}

const cancellable = (run: TaskRun) => (run.taskType === 'SPARK_CANVAS'
  || run.taskType === 'SPARK_MODEL_QUALITY' || run.taskType === 'SPARK_JAR')
  && (run.status === 'QUEUED' || run.status === 'RUNNING');

const forceTerminable = (run: TaskRun) => run.taskType !== 'LOCAL_SQL'
  && (run.status === 'CANCEL_REQUESTED' || run.status === 'STOP_REQUESTED');

const qualityConclusion = (value: TaskRun['qualityConclusion']) => {
  if (!value) return '—';
  return <Tag color={value === 'PASSED' ? 'success' : 'error'}>{value === 'PASSED' ? '通过' : '不通过'}</Tag>;
};

export const TaskRunsPanel = ({
  task,
  canExecute,
  detailRunId,
  onDetailRunChange,
}: TaskRunsPanelProps) => {
  const [form] = Form.useForm<TaskRunFilters>();
  const [filters, setFilters] = useState<TaskRunFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [cancellingRunId, setCancellingRunId] = useState<string | null>(null);
  const [forceTerminatingRunId, setForceTerminatingRunId] = useState<string | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const cancelMutation = useCancelTaskRun();
  const forceTerminateMutation = useForceTerminateTaskRun();
  const request = useMemo(() => ({
    search: buildTaskRunSearch(filters),
    page,
    size,
    sort: '-queuedAt',
  }), [filters, page, size]);
  const runsQuery = useTaskRuns(task.id, request, true);

  const cancel = (run: TaskRun) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
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

  const forceTerminate = (run: TaskRun) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '强制终止任务运行',
    content: '确认立即强制终止该 Spark Application 吗？可能产生部分写入、重复数据，实时任务还可能重放当前微批。',
    okText: '强制终止',
    okButtonProps: { danger: true },
    cancelText: '返回',
    onOk: async () => {
      setForceTerminatingRunId(run.id);
      try {
        await forceTerminateMutation.mutateAsync(run.id);
        messageApi.success('已提交强制终止请求');
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '强制终止任务运行失败');
        throw error;
      } finally {
        setForceTerminatingRunId(null);
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
      title: task.type === 'SPARK_MODEL_QUALITY' ? '检查行数' : '影响行数',
      dataIndex: task.type === 'SPARK_MODEL_QUALITY' ? 'qualityCheckedRows' : 'affectedRows',
      width: 100,
      align: 'right',
      render: (value: number | null) => value === null
        ? <span aria-label={`${task.type === 'SPARK_MODEL_QUALITY' ? '检查行数' : '影响行数'}未知`}>—</span>
        : value,
    },
    ...(task.type === 'SPARK_MODEL_QUALITY' ? [{
      title: '质量结论', dataIndex: 'qualityConclusion', width: 100,
      render: qualityConclusion,
    }] : []),
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
              onClick={() => onDetailRunChange(run.id)}
            />
          </Tooltip>
          {canExecute && cancellable(run) && (
            <Tooltip title="取消运行">
              <Button
                type="text"
                danger
                icon={<StopOutlined />}
                aria-label={`取消运行 ${run.id}`}
                loading={cancellingRunId === run.id}
                onClick={() => cancel(run)}
              />
            </Tooltip>
          )}
          {canExecute && forceTerminable(run) && (
            <Tooltip title="强制终止">
              <Button
                type="text"
                danger
                icon={<StopOutlined />}
                aria-label={`强制终止运行 ${run.id}`}
                loading={forceTerminatingRunId === run.id}
                onClick={() => forceTerminate(run)}
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
      <div className="task-detail-tab-toolbar detail-table-filter-toolbar">
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
              className="detail-table-filter-select task-run-filter-select"
              options={Object.entries(taskRunStatusLabels).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="triggerType">
            <Select
              allowClear
              placeholder="全部触发方式"
              className="detail-table-filter-select task-run-filter-select"
              options={Object.entries(taskRunTriggerTypeLabels).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="executionMode">
            <Select
              allowClear
              placeholder="全部执行模式"
              className="detail-table-filter-select task-run-filter-select"
              options={Object.entries(taskRunExecutionModeLabels).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
        </Form>
        <Space size={8} className="detail-table-filter-actions">
          <Button type="primary" icon={<SearchOutlined />} onClick={() => form.submit()}>查询</Button>
          <Button type="text" onClick={() => { form.resetFields(); setFilters({}); setPage(0); }}>重置</Button>
        </Space>
      </div>
      {runsQuery.isError && (
        <Alert
          type="error"
          showIcon
          message="加载运行记录失败"
          action={<Button size="small" icon={<ReloadOutlined />} onClick={() => void runsQuery.refetch()}>重试</Button>}
        />
      )}
      <DetailTableToolbar
        title="运行记录"
        total={runsQuery.data?.totalElements ?? 0}
        current={page + 1}
        pageSize={size}
        itemUnit="条"
        onChange={(nextPage, nextSize) => { setPage(nextPage - 1); setSize(nextSize); }}
        onRefresh={() => void runsQuery.refetch()}
        refreshing={runsQuery.isFetching}
        refreshLabel="刷新运行记录"
      />
      <Table<TaskRun>
        rowKey="id"
        size="small"
        loading={runsQuery.isLoading}
        columns={columns}
        dataSource={runsQuery.data?.content ?? []}
        scroll={{ x: task.type === 'SPARK_MODEL_QUALITY' ? 1_760 : 1_660, y: 'calc(100vh - 330px)' }}
        pagination={false}
      />
      <TaskRunDetailDrawer
        open={Boolean(detailRunId)}
        runId={detailRunId}
        canExecute={canExecute}
        cancelLoading={cancellingRunId === detailRunId}
        forceTerminateLoading={forceTerminatingRunId === detailRunId}
        onClose={() => onDetailRunChange(null)}
        onCancel={cancel}
        onForceTerminate={forceTerminate}
      />
    </div>
  );
};
