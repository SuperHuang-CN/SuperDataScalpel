import {
  ApartmentOutlined,
  DownOutlined,
  HistoryOutlined,
  ConsoleSqlOutlined,
  CodeSandboxOutlined,
  DeleteOutlined,
  EditOutlined,
  FileZipOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ProfileOutlined,
  ReloadOutlined,
  SendOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Dropdown, Form, Modal, Select, Table, Tooltip, message } from 'antd';
import { useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { ManagementDateTime, ManagementListCell, ManagementStatusIndicator } from '../../../shared/components/ManagementListCells';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { getTaskView, taskPageHref, taskViews, type TaskListView } from '../model/taskViews';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import {
  DirectoryTreePanel,
  findDirectoryDescendantIds,
  useDirectoryTree,
  type DirectorySelection,
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
  taskStatusLabels,
  taskTypeLabels,
  type DataTask,
  type TaskFilters,
  type TaskType,
} from '../model/task';
import { buildTaskSearch } from '../model/taskSearch';

const taskTypeVisuals: Record<TaskType, {
  icon: ReactNode;
  tone: 'cyan' | 'violet' | 'orange';
}> = {
  WORKFLOW: { icon: <ApartmentOutlined />, tone: 'violet' },
  LOCAL_SQL: { icon: <ConsoleSqlOutlined />, tone: 'cyan' },
  SPARK_CANVAS: { icon: <ApartmentOutlined />, tone: 'violet' },
  SPARK_STREAMING_CANVAS: { icon: <ThunderboltOutlined />, tone: 'orange' },
  SPARK_MODEL_QUALITY: { icon: <ProfileOutlined />, tone: 'cyan' },
  SPARK_JAR: { icon: <FileZipOutlined />, tone: 'violet' },
  SPARK_STREAMING_JAR: { icon: <CodeSandboxOutlined />, tone: 'orange' },
};

const isCanvasTask = (task: DataTask) => (
  task.type === 'SPARK_CANVAS' || task.type === 'SPARK_STREAMING_CANVAS'
);

const requiresComputeEngine = (type: TaskType) => type !== 'LOCAL_SQL' && type !== 'WORKFLOW';

const isStreamingTask = (task: DataTask) => (
  task.type === 'SPARK_STREAMING_CANVAS' || task.type === 'SPARK_STREAMING_JAR'
);

const definitionLabel = (task: DataTask) => task.type === 'WORKFLOW' ? '依赖定义' : task.type === 'SPARK_MODEL_QUALITY' ? '质检定义' : '任务定义';

const definitionTab = (task: DataTask) => task.type === 'SPARK_MODEL_QUALITY' ? 'quality' : 'definition';

export const TaskListPage = ({ view = 'all' }: { view?: TaskListView }) => {
  const viewConfig = getTaskView(view);
  const detailHref = (task: DataTask, tab?: string) => taskPageHref(`/task/${task.id}`, `?taskView=${view}`, task.type, tab ? { tab } : {});
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
    search: buildTaskSearch(effectiveFilters, view === 'all' ? undefined : viewConfig.types), page, size, sort: '-updatedAt,name',
  }), [effectiveFilters, page, size, view, viewConfig.types]);
  const tasksQuery = useTasks(request);
  const createMutation = useCreateTask();
  const updateMutation = useUpdateTask();
  const deleteMutation = useDeleteTask();
  const publishMutation = useTaskCommand('publish');
  const disableMutation = useTaskCommand('disable');
  const enableMutation = useTaskCommand('enable');
  const runMutation = useRunTask();


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
      messageApi.success(task.type === 'SPARK_CANVAS' || task.type === 'SPARK_MODEL_QUALITY' || task.type === 'SPARK_JAR'
        ? '任务已提交，等待计算引擎调度'
        : '任务已进入执行队列');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '提交任务运行失败');
    }
  };

  const remove = (task: DataTask) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
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
        rootClassName: 'business-overlay business-modal-overlay',
        title: '发布任务',
        content: task.type === 'WORKFLOW' ? '发布会校验依赖图和引用任务的发布状态。' : task.type === 'SPARK_MODEL_QUALITY'
          ? '发布会校验目标模型、计算引擎和当前可执行规则，不会读取模型物理表。'
          : task.type === 'SPARK_JAR' || task.type === 'SPARK_STREAMING_JAR'
            ? '发布会校验当前 JAR、资源绑定和计算引擎，不会加载用户类或读写业务数据。'
          : isCanvasTask(task)
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
      title: '任务', dataIndex: 'name', width: 310,
      render: (name: string, task) => {
        const typeLabel = taskTypeLabels[task.type];
        const visual = taskTypeVisuals[task.type];
        return (
          <ManagementListCell
            icon={<Tooltip title={typeLabel}>{visual.icon}</Tooltip>}
            iconLabel={`任务类型：${typeLabel}`}
            iconTone={visual.tone}
            primary={<Button type="link" size="small" onClick={() => navigate(detailHref(task))}>{name}</Button>}
            secondary={task.description || '—'}
          />
        );
      },
    },
    { title: '发布状态', width: 120, render: (_: unknown, task) => <ManagementStatusIndicator label={taskStatusLabels[task.status]} tone={task.status === 'PUBLISHED' ? 'success' : task.status === 'DISABLED' ? 'default' : 'processing'} /> },
    {
      title: '定义摘要', width: 300,
      render: (_: unknown, task) => (
        <ManagementListCell
          primary={task.type === 'WORKFLOW' ? '依赖工作流' : task.type === 'LOCAL_SQL' ? '本地 SQL' : `${taskTypeLabels[task.type]} · ${task.computeEngineName ?? '未选择计算引擎'}`}
          secondary={!task.definitionConfigured
            ? '尚未配置'
            : task.type === 'WORKFLOW'
              ? `v${task.definitionVersion} · 依赖定义`
            : task.type === 'SPARK_MODEL_QUALITY'
              ? `v${task.definitionVersion} · ${task.qualityTargetModelName ?? '目标模型已删除'}`
              : task.type === 'SPARK_JAR' || task.type === 'SPARK_STREAMING_JAR'
                ? `v${task.definitionVersion} · 用户作业 JAR`
              : isCanvasTask(task)
                ? `v${task.definitionVersion} · Canvas 定义`
                : `v${task.definitionVersion} · ${task.outputModelName ?? '输出模型已删除'}`}
        />
      ),
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 160, render: (value: string) => <ManagementDateTime value={value} /> },
    {
      title: '操作', width: 112,
      render: (_, task) => {
        const lifecycleIcon = task.status === 'DRAFT' ? <SendOutlined /> : task.status === 'PUBLISHED' ? <PauseCircleOutlined /> : <PlayCircleOutlined />;
        const lifecycleLabel = task.status === 'DRAFT' ? '发布' : task.status === 'PUBLISHED' ? '停用' : '启用';
        const items: NonNullable<MenuProps['items']> = [{
          key: 'definition',
          label: definitionLabel(task),
          onClick: () => navigate(detailHref(task, definitionTab(task))),
        }, {
          key: 'runtime',
          label: isStreamingTask(task) ? '实时运行' : '运行记录',
          icon: isStreamingTask(task) ? <ThunderboltOutlined /> : <HistoryOutlined />,
          onClick: () => navigate(detailHref(task, isStreamingTask(task) ? 'streaming' : 'runs')),
        }];
        if (!isStreamingTask(task)) items.push({ key: 'schedules', label: '定时计划', onClick: () => navigate(detailHref(task, 'schedules')) });
        if (canUpdate) items.push({ key: 'edit', icon: <EditOutlined />, label: '修改基本信息', onClick: () => setDrawerTask(task) });
        if (canPublish) items.push({ key: 'lifecycle', icon: lifecycleIcon, label: lifecycleLabel, onClick: () => lifecycle(task) });
        if (canExecute && task.status === 'PUBLISHED' && !isStreamingTask(task)) {
          items.push({
            key: 'run',
            icon: <PlayCircleOutlined />,
            label: '立即运行',
            onClick: () => void triggerRun(task),
          });
        }
        if (canDelete && items.length) items.push({ type: 'divider' });
        if (canDelete) items.push({ key: 'delete', danger: true, icon: <DeleteOutlined />, label: '删除', onClick: () => remove(task) });
        return <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            <Tooltip title={definitionLabel(task)}>
              <Button
                type="text"
                icon={<ProfileOutlined />}
                aria-label={`查看${task.name}的${definitionLabel(task)}`}
                onClick={() => navigate(detailHref(task, definitionTab(task)))}
              />
            </Tooltip>
            <Tooltip title={isStreamingTask(task) ? '实时运行' : '运行记录'}>
              <Button type="text" icon={isStreamingTask(task) ? <ThunderboltOutlined /> : <HistoryOutlined />} aria-label={`查看${task.name}的${isStreamingTask(task) ? '实时运行' : '运行记录'}`} onClick={() => navigate(detailHref(task, isStreamingTask(task) ? 'streaming' : 'runs'))} />
            </Tooltip>
          </div>
          <Dropdown menu={{ items }}><Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" icon={<MoreOutlined />} aria-label={`${task.name}的更多操作`} /></Tooltip></Dropdown>
        </div>;
      },
    },
  ];

  return (
    <>
      {messageContext}
      {modalContext}
      <div className={canViewDirectories ? 'directory-management-layout' : 'page-stack'}>
        {canViewDirectories && <DirectoryTreePanel showResourceCounts={view === 'all'} scope="TASK" tree={directoriesQuery.data ?? []} loading={directoriesQuery.isLoading} selection={selection} canManage={canManageDirectories} onSelectionChange={(next) => { setSelection(next); setPage(0); }} />}
        <section className="management-workbench">
          <div className="management-filter-strip">
              <Form<TaskFilters> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" initialValues={filters} onFinish={(values) => { setFilters(values); setPage(0); }}>
                <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索任务名称" /></Form.Item>
                <Form.Item name="status"><Select allowClear placeholder="全部状态" style={{ width: 130 }} options={Object.entries(taskStatusLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
                {viewConfig.types.length > 1 && <Form.Item name="type"><Select allowClear aria-label={view === 'all' ? '任务类型' : '执行方式'} placeholder={view === 'all' ? '全部类型' : '执行方式'} style={{ width: 170 }} options={viewConfig.types.map(value => ({ value, label: taskTypeLabels[value] }))} /></Form.Item>}
              </Form>
              <ManagementFilterActions form={filterForm} appliedFilters={filters} additionalActive={selection !== undefined} loading={tasksQuery.isFetching} onReset={() => { filterForm.resetFields(); setFilters({}); setSelection(undefined); setPage(0); }} />
          </div>
          <div className="management-results-surface">
            <div className="management-result-toolbar"><span className="management-result-title"><Dropdown menu={{ selectedKeys: [view], items: taskViews.map(item => ({ key: item.id, label: item.label, onClick: () => navigate(item.path) })) }}><Button type="text" aria-label="切换任务视图">{viewConfig.label} <DownOutlined /></Button></Dropdown> <span className="management-result-count">共 {tasksQuery.data?.totalElements ?? 0} 项</span></span><div className="management-result-actions"><Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新任务列表" onClick={() => void tasksQuery.refetch()} /></Tooltip>{canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setDrawerTask(null); }}>新建{view === 'all' ? '任务' : viewConfig.label}</Button>}</div></div>
            {tasksQuery.isError && <InlineFeedback tone="error" label={tasksQuery.error instanceof ApiError ? tasksQuery.error.message : '任务列表加载失败'} action={<Button size="small" onClick={() => void tasksQuery.refetch()}>重试</Button>} />}
            <Table<DataTask>
            size="small" className="management-table" rowKey="id" loading={tasksQuery.isLoading} columns={columns} dataSource={tasksQuery.data?.content ?? []} scroll={{ y: '100%' }}
            pagination={{
              current: page + 1, pageSize: size, total: tasksQuery.data?.totalElements ?? 0, showSizeChanger: true, hideOnSinglePage: false, showTotal: (total) => `共 ${total} 项`,
              onChange: (nextPage, nextSize) => { setPage(nextPage - 1); setSize(nextSize); },
            }}
            />
          </div>
        </section>
      </div>
      <TaskDrawer
        view={view}
        open={drawerTask !== undefined}
        task={drawerTask ?? null}
        initialDirectoryId={typeof selection === 'string' ? selection : undefined}
        directories={directoriesQuery.data ?? []}
        onClose={() => { setDrawerTask(undefined); }}
        onSubmit={async (values) => {
          try {
            if (drawerTask) {
              await updateMutation.mutateAsync({
                id: drawerTask.id,
                request: {
                  name: values.name,
                  directoryId: values.directoryId,
                  description: values.description,
                  computeEngineId: requiresComputeEngine(drawerTask.type) ? values.computeEngineId : undefined,
                },
              });
            } else {
              await createMutation.mutateAsync({
                name: values.name,
                type: values.type,
                directoryId: values.directoryId,
                description: values.description,
                computeEngineId: requiresComputeEngine(values.type) ? values.computeEngineId : undefined,
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
