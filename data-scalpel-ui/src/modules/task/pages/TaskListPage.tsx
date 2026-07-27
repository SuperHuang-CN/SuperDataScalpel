import {
  DeleteOutlined,
  EditOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  SendOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Card, Dropdown, Form, Input, Modal, Select, Space, Table, Tag, message } from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import {
  DirectoryTreePanel,
  findDirectoryDescendantIds,
  useDirectoryTree,
  type DirectorySelection,
  type DirectoryTreeNode,
} from '../../directory';
import { useCurrentUser } from '../../system';
import { TaskDrawer } from '../components/TaskDrawer';
import {
  useCreateTask,
  useDeleteTask,
  useRunTask,
  useTaskCommand,
  useTasks,
  useUpdateTask,
} from '../hooks/useTasks';
import {
  taskStatusColors,
  taskStatusLabels,
  taskTypeColors,
  taskTypeLabels,
  type DataTask,
  type TaskFilters,
  type TaskStatus,
  type TaskType,
} from '../model/task';
import { buildTaskSearch } from '../model/taskSearch';

const formatDateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'short', timeStyle: 'medium', hour12: false,
}).format(new Date(value));

const isCanvasTask = (task: DataTask) => (
  task.type === 'SPARK_CANVAS' || task.type === 'SPARK_STREAMING_CANVAS'
);

export const TaskListPage = () => {
  const navigate = useNavigate();
  const [filterForm] = Form.useForm<TaskFilters>();
  const [filters, setFilters] = useState<TaskFilters>({});
  const [selection, setSelection] = useState<DirectorySelection>();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [drawerTask, setDrawerTask] = useState<DataTask | null | undefined>(undefined);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const currentUser = useCurrentUser();
  const permissions = new Set(currentUser.data?.permissions ?? []);
  const canViewDirectories = permissions.has('directory.view');
  const canManageDirectories = permissions.has('directory.manage');
  const canCreate = permissions.has('task.create');
  const canUpdate = permissions.has('task.update');
  const canDelete = permissions.has('task.delete');
  const canPublish = permissions.has('task.publish');
  const canExecute = permissions.has('task.execute');
  const directoriesQuery = useDirectoryTree('TASK', canViewDirectories);
  const effectiveFilters = useMemo(() => selection === null
    ? { ...filters, directoryIds: undefined, uncategorized: true }
    : typeof selection === 'string'
      ? { ...filters, directoryIds: findDirectoryDescendantIds(directoriesQuery.data ?? [], selection), uncategorized: undefined }
      : filters, [directoriesQuery.data, filters, selection]);
  const request = useMemo(() => ({
    search: buildTaskSearch(effectiveFilters), page, size, sort: '-updatedAt,name',
  }), [effectiveFilters, page, size]);
  const tasksQuery = useTasks(request);
  const createMutation = useCreateTask();
  const updateMutation = useUpdateTask();
  const deleteMutation = useDeleteTask();
  const publishMutation = useTaskCommand('publish');
  const disableMutation = useTaskCommand('disable');
  const enableMutation = useTaskCommand('enable');
  const runMutation = useRunTask();

  const directoryNameById = useMemo(() => {
    const names = new Map<string, string>();
    const collect = (nodes: DirectoryTreeNode[]) => nodes.forEach((directory) => {
      names.set(directory.id, directory.name);
      collect(directory.children);
    });
    collect(directoriesQuery.data ?? []);
    return names;
  }, [directoriesQuery.data]);

  const runCommand = async (task: DataTask, command: 'publish' | 'disable' | 'enable') => {
    try {
      if (command === 'publish') await publishMutation.mutateAsync(task.id);
      else if (command === 'disable') await disableMutation.mutateAsync(task.id);
      else await enableMutation.mutateAsync(task.id);
      messageApi.success(command === 'publish' ? '任务已发布' : command === 'disable' ? '任务已停用' : '任务已启用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '任务状态操作失败');
    }
  };

  const triggerRun = async (task: DataTask) => {
    try {
      await runMutation.mutateAsync(task.id);
      messageApi.success(task.type === 'SPARK_CANVAS'
        ? '任务已提交，等待计算引擎调度'
        : '任务已进入执行队列');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '提交任务运行失败');
    }
  };

  const remove = (task: DataTask) => modalApi.confirm({
    title: '删除任务',
    content: `确认删除“${task.name}”吗？已有运行记录的任务不能删除。`,
    okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteMutation.mutateAsync(task.id);
        messageApi.success('任务已删除');
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '删除任务失败');
        throw error;
      }
    },
  });

  const lifecycle = (task: DataTask) => {
    if (task.status === 'DRAFT') {
      modalApi.confirm({
        title: '发布任务',
        content: isCanvasTask(task)
          ? '发布会读取当前真实数据源元数据，并通过 Task Engine 完成 Canvas 编译预检；预检不会读写业务数据。'
          : '发布会检查模型物理表、SQL 输出字段和类型，并且不会写入目标表。',
        okText: '发布', cancelText: '取消', onOk: () => runCommand(task, 'publish'),
      });
      return;
    }
    void runCommand(task, task.status === 'PUBLISHED' ? 'disable' : 'enable');
  };

  const columns: TableProps<DataTask>['columns'] = [
    {
      title: '任务名称', dataIndex: 'name', width: 190, ellipsis: true,
      render: (name: string, task) => <Button type="link" size="small" onClick={() => navigate(`/task/${task.id}`)}>{name}</Button>,
    },
    { title: '类型', dataIndex: 'type', width: 110, render: (type: TaskType) => <Tag color={taskTypeColors[type]}>{taskTypeLabels[type]}</Tag> },
    { title: '目录', dataIndex: 'directoryId', width: 140, render: (id: string | null) => id ? directoryNameById.get(id) ?? '已删除目录' : '未分类' },
    { title: '状态', dataIndex: 'status', width: 100, render: (status: TaskStatus) => <Tag color={taskStatusColors[status]}>{taskStatusLabels[status]}</Tag> },
    { title: '计算引擎', dataIndex: 'computeEngineName', width: 150, ellipsis: true, render: (name: string | null, task) => isCanvasTask(task) ? name ?? '未选择' : '—' },
    { title: '定义', width: 190, render: (_, task) => task.definitionConfigured
      ? isCanvasTask(task)
        ? `v${task.definitionVersion} · Canvas 定义`
        : `v${task.definitionVersion} · ${task.outputModelName ?? '输出模型已删除'}`
      : '尚未配置' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 170, render: formatDateTime },
    {
      title: '操作', fixed: 'right', width: 170,
      render: (_, task) => {
        const lifecycleIcon = task.status === 'DRAFT' ? <SendOutlined /> : task.status === 'PUBLISHED' ? <PauseCircleOutlined /> : <PlayCircleOutlined />;
        const lifecycleLabel = task.status === 'DRAFT' ? '发布' : task.status === 'PUBLISHED' ? '停用' : '启用';
        const items: NonNullable<MenuProps['items']> = [];
        if (canUpdate) items.push({ key: 'edit', icon: <EditOutlined />, label: '修改基本信息', onClick: () => setDrawerTask(task) });
        if (canPublish) items.push({ key: 'lifecycle', icon: lifecycleIcon, label: lifecycleLabel, onClick: () => lifecycle(task) });
        if (canExecute && task.status === 'PUBLISHED' && task.type !== 'SPARK_STREAMING_CANVAS') {
          items.push({
            key: 'run',
            icon: <PlayCircleOutlined />,
            label: '立即运行',
            onClick: () => void triggerRun(task),
          });
        }
        if (canDelete && items.length) items.push({ type: 'divider' });
        if (canDelete) items.push({ key: 'delete', danger: true, icon: <DeleteOutlined />, label: '删除', onClick: () => remove(task) });
        return <Space size={0}>
          <Button type="link" size="small" onClick={() => navigate(`/task/${task.id}?tab=definition`)}>定义</Button>
          <Dropdown menu={{ items }}>
            <Button
              type="text"
              size="small"
              icon={<MoreOutlined />}
              aria-label={`更多任务操作：${task.name}`}
            />
          </Dropdown>
        </Space>;
      },
    },
  ];

  return (
    <>
      {messageContext}
      {modalContext}
      <div className={canViewDirectories ? 'directory-management-layout' : 'page-stack'}>
        {canViewDirectories && <DirectoryTreePanel scope="TASK" tree={directoriesQuery.data ?? []} loading={directoriesQuery.isLoading} selection={selection} canManage={canManageDirectories} onSelectionChange={(next) => { setSelection(next); setPage(0); }} />}
        <Card className="management-card" title="任务管理" extra={canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setDrawerTask(null)}>新建任务</Button>}>
          <Form<TaskFilters> form={filterForm} layout="inline" initialValues={filters} onFinish={(values) => { setFilters(values); setPage(0); }} style={{ marginBottom: 16 }}>
            <Form.Item name="keyword"><Input allowClear placeholder="任务名称" /></Form.Item>
            <Form.Item name="status"><Select allowClear placeholder="全部状态" style={{ width: 130 }} options={Object.entries(taskStatusLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
            <Form.Item name="type"><Select allowClear placeholder="全部类型" style={{ width: 130 }} options={Object.entries(taskTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
            <Button type="primary" htmlType="submit">查询</Button>
            <Button onClick={() => { filterForm.resetFields(); setFilters({}); setSelection(undefined); setPage(0); }}>重置</Button>
          </Form>
          <Table<DataTask>
            rowKey="id" loading={tasksQuery.isLoading} columns={columns} dataSource={tasksQuery.data?.content ?? []} scroll={{ x: 1080 }}
            pagination={{
              current: page + 1, pageSize: size, total: tasksQuery.data?.totalElements ?? 0, showSizeChanger: true,
              onChange: (nextPage, nextSize) => { setPage(nextPage - 1); setSize(nextSize); },
            }}
          />
        </Card>
      </div>
      <TaskDrawer
        open={drawerTask !== undefined}
        task={drawerTask ?? null}
        directories={directoriesQuery.data ?? []}
        onClose={() => setDrawerTask(undefined)}
        onSubmit={async (values) => {
          try {
            if (drawerTask) {
              await updateMutation.mutateAsync({
                id: drawerTask.id,
                request: {
                  name: values.name,
                  directoryId: values.directoryId,
                  description: values.description,
                  computeEngineId: isCanvasTask(drawerTask) ? values.computeEngineId : undefined,
                },
              });
            } else {
              await createMutation.mutateAsync({
                name: values.name,
                type: values.type,
                directoryId: values.directoryId,
                description: values.description,
                computeEngineId: values.type === 'SPARK_CANVAS'
                  || values.type === 'SPARK_STREAMING_CANVAS'
                  ? values.computeEngineId
                  : undefined,
              });
            }
            messageApi.success(drawerTask ? '任务已更新' : '任务已创建');
            setDrawerTask(undefined);
          } catch (error) {
            messageApi.error(error instanceof ApiError ? error.message : '保存任务失败');
            throw error;
          }
        }}
      />
    </>
  );
};
