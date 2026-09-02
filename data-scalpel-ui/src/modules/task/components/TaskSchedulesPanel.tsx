import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  DeleteOutlined,
  EditOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Popconfirm, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useCreateTaskSchedule,
  useDeleteTaskSchedule,
  useTaskScheduleCommand,
  useTaskSchedules,
  useUpdateTaskSchedule,
} from '../hooks/useTasks';
import {
  taskMisfirePolicyLabels,
  taskOverlapPolicyLabels,
  taskScheduleStatusLabels,
  type DataTask,
  type TaskSchedule,
  type TaskScheduleRequest,
} from '../model/task';
import { TaskScheduleDrawer } from './TaskScheduleDrawer';

interface TaskSchedulesPanelProps {
  task: DataTask;
  canUpdate: boolean;
  canPublish: boolean;
  canDelete: boolean;
}

const dateTime = (value: string | null) => value ? new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'short', timeStyle: 'medium', hour12: false,
}).format(new Date(value)) : '—';

export const TaskSchedulesPanel = ({ task, canUpdate, canPublish, canDelete }: TaskSchedulesPanelProps) => {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingSchedule, setEditingSchedule] = useState<TaskSchedule | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const schedulesQuery = useTaskSchedules(task.id);
  const createMutation = useCreateTaskSchedule(task.id);
  const updateMutation = useUpdateTaskSchedule(task.id);
  const commandMutation = useTaskScheduleCommand(task.id);
  const deleteMutation = useDeleteTaskSchedule(task.id);

  const closeDrawer = () => {
    setDrawerOpen(false);
    setEditingSchedule(null);
  };

  const save = async (request: TaskScheduleRequest) => {
    try {
      if (editingSchedule) {
        await updateMutation.mutateAsync({ scheduleId: editingSchedule.id, request });
        messageApi.success('定时计划已更新');
      } else {
        await createMutation.mutateAsync(request);
        messageApi.success('定时计划已创建，默认处于停用状态');
      }
      closeDrawer();
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存定时计划失败');
    }
  };

  const toggle = async (schedule: TaskSchedule) => {
    const command = schedule.status === 'ENABLED' ? 'disable' : 'enable';
    try {
      await commandMutation.mutateAsync({ scheduleId: schedule.id, command });
      messageApi.success(command === 'enable' ? '定时计划已启用' : '定时计划已停用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '更新定时计划状态失败');
    }
  };

  const remove = async (schedule: TaskSchedule) => {
    try {
      await deleteMutation.mutateAsync(schedule.id);
      messageApi.success('定时计划已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '删除定时计划失败');
    }
  };

  const columns: TableProps<TaskSchedule>['columns'] = [
    { title: '名称', dataIndex: 'name', width: 150, ellipsis: true },
    { title: 'Cron', dataIndex: 'cronExpression', width: 160, render: (value: string) => <code>{value}</code> },
    { title: '时区', dataIndex: 'zoneId', width: 135 },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: (status: TaskSchedule['status']) => (
        <Tag color={status === 'ENABLED' ? 'success' : 'default'}>{taskScheduleStatusLabels[status]}</Tag>
      ),
    },
    {
      title: 'Misfire', dataIndex: 'misfirePolicy', width: 135,
      render: (value: TaskSchedule['misfirePolicy']) => taskMisfirePolicyLabels[value],
    },
    {
      title: '重叠策略', dataIndex: 'overlapPolicy', width: 105,
      render: (value: TaskSchedule['overlapPolicy']) => taskOverlapPolicyLabels[value],
    },
    { title: '下一次触发', dataIndex: 'nextFireAt', width: 175, render: dateTime },
    {
      title: '操作', key: 'actions', width: 128, fixed: 'right',
      render: (_, schedule) => {
        const commandPending = commandMutation.isPending && commandMutation.variables?.scheduleId === schedule.id;
        const deletePending = deleteMutation.isPending && deleteMutation.variables === schedule.id;
        const cannotEnable = schedule.status === 'DISABLED' && task.status !== 'PUBLISHED';
        return (
          <Space size={2}>
            {canUpdate && (
              <Tooltip title="编辑计划">
                <Button
                  type="text"
                  icon={<EditOutlined />}
                  aria-label={`编辑计划 ${schedule.name}`}
                  onClick={() => { setEditingSchedule(schedule); setDrawerOpen(true); }}
                />
              </Tooltip>
            )}
            {canPublish && (
              <Tooltip title={cannotEnable ? '请先发布或重新启用任务' : schedule.status === 'ENABLED' ? '停用计划' : '启用计划'}>
                <Button
                  type="text"
                  icon={schedule.status === 'ENABLED' ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                  aria-label={`${schedule.status === 'ENABLED' ? '停用' : '启用'}计划 ${schedule.name}`}
                  disabled={cannotEnable}
                  loading={commandPending}
                  onClick={() => void toggle(schedule)}
                />
              </Tooltip>
            )}
            {canDelete && (
              <Popconfirm
                title={`删除计划“${schedule.name}”？`}
                description="删除后 Quartz 中对应的触发器也会被清理。"
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true, loading: deletePending }}
                onConfirm={() => remove(schedule)}
              >
                <Tooltip title="删除计划">
                  <Button
                    type="text"
                    danger
                    icon={<DeleteOutlined />}
                    aria-label={`删除计划 ${schedule.name}`}
                    loading={deletePending}
                  />
                </Tooltip>
              </Popconfirm>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div className="task-detail-tab-panel task-schedules-panel">
      {messageContext}
      <div className="task-detail-tab-toolbar">
        <Typography.Text strong>定时计划</Typography.Text>
        {canUpdate && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingSchedule(null); setDrawerOpen(true); }}>
            新建计划
          </Button>
        )}
      </div>
      {task.status !== 'PUBLISHED' && (
        <Alert
          type="info"
          showIcon
          message="当前任务未发布"
          description="可以创建和编辑计划，但需要先发布或重新启用任务才能启用计划。"
          style={{ marginBottom: 12 }}
        />
      )}
      {schedulesQuery.isError && (
        <Alert
          type="error"
          showIcon
          message="加载定时计划失败"
          action={<Button size="small" icon={<ReloadOutlined />} onClick={() => void schedulesQuery.refetch()}>重试</Button>}
          style={{ marginBottom: 12 }}
        />
      )}
      <Table<TaskSchedule>
        rowKey="id"
        size="small"
        loading={schedulesQuery.isLoading}
        columns={columns}
        dataSource={schedulesQuery.data ?? []}
        pagination={false}
        scroll={{ x: 1_180, y: 'calc(100vh - 300px)' }}
      />
      <TaskScheduleDrawer
        open={drawerOpen}
        schedule={editingSchedule}
        submitting={createMutation.isPending || updateMutation.isPending}
        onClose={closeDrawer}
        onSubmit={save}
      />
    </div>
  );
};
