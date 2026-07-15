import type { TableProps } from 'antd';
import { Alert, Table, Tag } from 'antd';
import { useMemo } from 'react';
import { useTaskRuns } from '../hooks/useTasks';
import { taskRunStatusColors, taskRunStatusLabels, type TaskRun } from '../model/task';

interface TaskRunsPanelProps {
  taskId: string;
}

const dateTime = (value: string | null) => value ? new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'short', timeStyle: 'medium', hour12: false,
}).format(new Date(value)) : '—';

export const TaskRunsPanel = ({ taskId }: TaskRunsPanelProps) => {
  const request = useMemo(() => ({ page: 0, size: 20, sort: '-queuedAt' }), []);
  const runsQuery = useTaskRuns(taskId, request, true);
  const columns: TableProps<TaskRun>['columns'] = [
    { title: '状态', dataIndex: 'status', width: 100, render: (status: TaskRun['status']) => <Tag color={taskRunStatusColors[status]}>{taskRunStatusLabels[status]}</Tag> },
    { title: '定义版本', dataIndex: 'definitionVersion', width: 100, align: 'right' },
    { title: '排队时间', dataIndex: 'queuedAt', width: 175, render: dateTime },
    { title: '开始/结束', width: 310, render: (_, run) => `${dateTime(run.startedAt)} / ${dateTime(run.endedAt)}` },
    { title: '影响行数', dataIndex: 'affectedRows', width: 100, align: 'right', render: (value: number | null) => value ?? '—' },
    { title: '结果', dataIndex: 'message', ellipsis: true, render: (value: string | null, run) => value ?? run.errorDetail ?? '—' },
  ];
  return (
    <>
      {runsQuery.isError && <Alert type="error" showIcon message="加载运行记录失败" style={{ marginBottom: 12 }} />}
      <Table<TaskRun>
        rowKey="id"
        size="small"
        loading={runsQuery.isLoading}
        columns={columns}
        dataSource={runsQuery.data?.content ?? []}
        pagination={false}
      />
    </>
  );
};
