import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Dropdown,
  Modal,
  Result,
  Skeleton,
  Space,
  Tabs,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { useCallback, useMemo, useState } from 'react';
import {
  useBlocker,
  useNavigate,
  useParams,
  useSearchParams,
  type BlockerFunction,
} from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useDirectoryTree, type DirectoryTreeNode } from '../../directory';
import { useCurrentUser } from '../../system';
import { CanvasTaskDefinitionPanel } from '../components/CanvasTaskDefinitionPanel';
import { LocalSqlTaskDefinitionPanel } from '../components/LocalSqlTaskDefinitionPanel';
import { TaskBasicPanel } from '../components/TaskBasicPanel';
import { TaskModelsPanel } from '../components/TaskModelsPanel';
import { TaskDrawer, type TaskDrawerValues } from '../components/TaskDrawer';
import { TaskRunsPanel } from '../components/TaskRunsPanel';
import { TaskSchedulesPanel } from '../components/TaskSchedulesPanel';
import { TaskStreamingRuntimePanel } from '../components/TaskStreamingRuntimePanel';
import {
  useDeleteTask,
  useRunTask,
  useTask,
  useTaskCommand,
  useTaskStreamingCommand,
  useTaskStreamingStatus,
  useUpdateTask,
} from '../hooks/useTasks';
import {
  taskStatusColors,
  taskStatusLabels,
  taskTypeColors,
  taskTypeLabels,
  type DataTask,
} from '../model/task';
import { normalizeTaskDetailTab, type TaskDetailTabKey } from '../model/taskDetail';

const lifecycleLabel = (task: DataTask) => {
  if (task.status === 'DRAFT') return '发布';
  if (task.status === 'PUBLISHED') return '停用';
  return '启用';
};

const lifecycleIcon = (task: DataTask) => {
  if (task.status === 'DRAFT') return <SendOutlined />;
  if (task.status === 'PUBLISHED') return <PauseCircleOutlined />;
  return <PlayCircleOutlined />;
};

export const TaskDetailPage = () => {
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [editing, setEditing] = useState(false);
  const [definitionDirty, setDefinitionDirty] = useState(false);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const taskQuery = useTask(taskId);
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewDirectories = permissions.has('directory.view');
  const canUpdate = permissions.has('task.update');
  const canDelete = permissions.has('task.delete');
  const canPublish = permissions.has('task.publish');
  const canExecute = permissions.has('task.execute');
  const canViewModels = permissions.has('model.view');
  const directoriesQuery = useDirectoryTree('TASK', canViewDirectories);
  const updateMutation = useUpdateTask();
  const deleteMutation = useDeleteTask();
  const publishMutation = useTaskCommand('publish');
  const disableMutation = useTaskCommand('disable');
  const enableMutation = useTaskCommand('enable');
  const runMutation = useRunTask();
  const startStreamingMutation = useTaskStreamingCommand('start');
  const stopStreamingMutation = useTaskStreamingCommand('stop');
  const activeTab = normalizeTaskDetailTab(searchParams.get('tab'));
  const definitionLeaveBlocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => definitionDirty && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [definitionDirty],
  ));
  const task = taskQuery.data;
  const streamingStatusQuery = useTaskStreamingStatus(
    taskId,
    task?.type === 'SPARK_STREAMING_CANVAS',
  );
  const streamingState = streamingStatusQuery.data?.deployment?.actualState;
  const streamingActive = streamingState === 'STARTING'
    || streamingState === 'RUNNING'
    || streamingState === 'STOPPING';

  const directoryNameById = useMemo(() => {
    const names = new Map<string, string>();
    const collect = (nodes: DirectoryTreeNode[]) => nodes.forEach((node) => {
      names.set(node.id, node.name);
      collect(node.children);
    });
    collect(directoriesQuery.data ?? []);
    return names;
  }, [directoriesQuery.data]);

  const handleDefinitionDirtyChange = useCallback((dirty: boolean) => {
    setDefinitionDirty(dirty);
  }, []);

  const executeCommand = async (target: DataTask, command: 'publish' | 'disable' | 'enable') => {
    try {
      const mutation = command === 'publish'
        ? publishMutation
        : command === 'disable' ? disableMutation : enableMutation;
      await mutation.mutateAsync(target.id);
      messageApi.success(command === 'publish'
        ? '任务已发布'
        : command === 'disable' ? '任务已停用' : '任务已启用');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '任务状态操作失败');
      throw error;
    }
  };

  const transition = (target: DataTask) => {
    if (definitionDirty && target.status !== 'PUBLISHED') {
      messageApi.warning('请先保存或放弃当前任务定义修改');
      return;
    }
    if (target.status === 'DRAFT') {
      modalApi.confirm({
        title: '发布任务',
        content: target.type === 'SPARK_CANVAS' || target.type === 'SPARK_STREAMING_CANVAS'
          ? '发布会读取当前数据源元数据并完成 Canvas 编译预检，预检不会读写业务数据。'
          : '发布会检查模型物理表、SQL 输出字段和类型，并且不会写入目标表。',
        okText: '发布',
        cancelText: '取消',
        onOk: () => executeCommand(target, 'publish'),
      });
      return;
    }
    if (target.type === 'SPARK_STREAMING_CANVAS'
      && target.status === 'PUBLISHED'
      && streamingActive) {
      messageApi.warning('请先正常停止实时任务，再停用任务');
      return;
    }
    void executeCommand(target, target.status === 'PUBLISHED' ? 'disable' : 'enable');
  };

  const executeStreamingCommand = async (
    target: DataTask,
    command: 'start' | 'stop',
  ) => {
    try {
      const mutation = command === 'start' ? startStreamingMutation : stopStreamingMutation;
      await mutation.mutateAsync(target.id);
      messageApi.success(command === 'start' ? '实时任务启动请求已提交' : '实时任务停止请求已提交');
      setSearchParams({ tab: 'streaming' }, { replace: true });
    } catch (error) {
      messageApi.error(error instanceof ApiError
        ? error.message
        : command === 'start' ? '启动实时任务失败' : '停止实时任务失败');
    }
  };

  const startStreaming = (target: DataTask) => {
    if (!target.definitionConfigured) {
      messageApi.warning('任务定义缺失，无法启动');
      return;
    }
    if (definitionDirty) {
      messageApi.warning('请先保存或放弃当前任务定义修改');
      return;
    }
    modalApi.confirm({
      title: streamingState === 'FAILED' || streamingState === 'STOPPED'
        ? '恢复 Spark 实时任务'
        : '启动 Spark 实时任务',
      content: '任务将持续消费 Kafka 数据。Kafka 与 JDBC Sink 均按至少一次处理，多输出使用独立 Checkpoint。',
      okText: '确认启动',
      cancelText: '取消',
      onOk: () => executeStreamingCommand(target, 'start'),
    });
  };

  const stopStreaming = (target: DataTask) => modalApi.confirm({
    title: '正常停止 Spark 实时任务',
    content: '系统会通知 Runner 停止全部 StreamingQuery 并保留 Checkpoint，后续可从原位置恢复。',
    okText: '停止',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: () => executeStreamingCommand(target, 'stop'),
  });

  const submitRun = async (target: DataTask) => {
    try {
      await runMutation.mutateAsync(target.id);
      messageApi.success(target.type === 'SPARK_CANVAS'
        ? '任务已提交，等待计算引擎调度'
        : '任务已进入执行队列');
      setSearchParams({ tab: 'runs' }, { replace: true });
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '提交任务运行失败');
    }
  };

  const run = (target: DataTask) => {
    if (!target.definitionConfigured) {
      messageApi.warning('任务定义缺失，无法运行，请先停用后重新配置');
      return;
    }
    if (definitionDirty) {
      messageApi.warning('请先保存或放弃当前任务定义修改');
      return;
    }
    if (target.type === 'SPARK_CANVAS') {
      modalApi.confirm({
        title: '运行 Spark Canvas 任务',
        content: '本次运行会真实访问输入数据源并写入 JDBC_OUTPUT 目标表。APPEND 会追加数据，OVERWRITE 会清空目标表后写入；多个输出之间不提供跨表事务回滚。',
        okText: '确认运行',
        cancelText: '取消',
        onOk: () => submitRun(target),
      });
      return;
    }
    void submitRun(target);
  };

  const remove = (target: DataTask) => modalApi.confirm({
    title: '删除任务',
    content: `确认删除“${target.name}”吗？已有运行记录的任务不能删除。`,
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteMutation.mutateAsync(target.id);
        messageApi.success('任务已删除');
        navigate('/task', { replace: true });
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '删除任务失败');
        throw error;
      }
    },
  });

  const updateBasicInfo = async (values: TaskDrawerValues) => {
    if (!task) return;
    try {
      await updateMutation.mutateAsync({
        id: task.id,
        request: {
          name: values.name,
          directoryId: values.directoryId,
          description: values.description,
          computeEngineId: task.type === 'SPARK_CANVAS' || task.type === 'SPARK_STREAMING_CANVAS'
            ? values.computeEngineId
            : undefined,
        },
      });
      messageApi.success('任务基本信息已更新');
      setEditing(false);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '保存任务失败');
      throw error;
    }
  };

  if (!taskId) {
    return <Result status="404" title="任务地址无效" extra={<Button type="primary" onClick={() => navigate('/task')}>返回任务列表</Button>} />;
  }

  if (taskQuery.isPending) {
    return <div className="task-detail-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }

  if (!task || taskQuery.error) {
    return (
      <Result
        status="error"
        title="任务详情加载失败"
        subTitle={taskQuery.error instanceof ApiError ? taskQuery.error.message : '请确认任务是否存在。'}
        extra={(
          <Space>
            <Button onClick={() => navigate('/task')}>返回列表</Button>
            <Button type="primary" onClick={() => void taskQuery.refetch()}>重试</Button>
          </Space>
        )}
      />
    );
  }

  const commandLoading = publishMutation.isPending || disableMutation.isPending || enableMutation.isPending;
  const directoryName = task.directoryId ? directoryNameById.get(task.directoryId) : undefined;
  const subtitleResource = task.type === 'LOCAL_SQL'
    ? '本地 JDBC 执行'
    : task.computeEngineName ?? (task.computeEngineId ? '计算引擎已删除' : '未绑定计算引擎');
  const lifecycleBlockedByDirty = definitionDirty && task.status !== 'PUBLISHED';
  const lifecycleBlockedByStreaming = task.type === 'SPARK_STREAMING_CANVAS'
    && task.status === 'PUBLISHED'
    && streamingActive;
  const runDisabledReason = !task.definitionConfigured
    ? '任务定义缺失，无法运行，请先停用后重新配置'
    : definitionDirty ? '请先保存或放弃当前任务定义修改' : undefined;
  const tabItems = [
    {
      key: 'basic',
      label: '基本信息',
      children: <TaskBasicPanel task={task} directoryName={directoryName} />,
    },
    {
      key: 'definition',
      label: '任务定义',
      children: task.type === 'SPARK_CANVAS' || task.type === 'SPARK_STREAMING_CANVAS'
        ? (
          <CanvasTaskDefinitionPanel
            task={task}
            canUpdate={canUpdate}
            onDirtyChange={handleDefinitionDirtyChange}
            protectNavigation={false}
          />
        )
        : (
          <LocalSqlTaskDefinitionPanel
            task={task}
            canUpdate={canUpdate}
            canValidate={canPublish}
            onDirtyChange={handleDefinitionDirtyChange}
            protectNavigation={false}
          />
        ),
    },
    {
      key: 'models',
      label: '关联模型',
      children: <TaskModelsPanel taskId={task.id} canViewModels={canViewModels} />,
    },
    ...(task.type === 'SPARK_STREAMING_CANVAS' ? [{
      key: 'streaming',
      label: '实时运行',
      children: <TaskStreamingRuntimePanel task={task} />,
    }] : [{
      key: 'schedules',
      label: '定时计划',
      children: (
        <TaskSchedulesPanel
          task={task}
          canUpdate={canUpdate}
          canPublish={canPublish}
          canDelete={canDelete}
        />
      ),
    }]),
    {
      key: 'runs',
      label: '运行记录',
      children: <TaskRunsPanel task={task} canExecute={canExecute} />,
    },
  ];

  return (
    <div className="task-detail-page">
      {messageContext}
      {modalContext}
      <div className="task-detail-header">
        <div className="task-detail-identity">
          <div className="task-detail-title-row">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/task')}>返回列表</Button>
            <span className="task-detail-title">{task.name}</span>
            <Tag color={taskTypeColors[task.type]}>{taskTypeLabels[task.type]}</Tag>
            <Tag color={taskStatusColors[task.status]}>{taskStatusLabels[task.status]}</Tag>
            <Tag>{task.definitionConfigured ? `定义 v${task.definitionVersion}` : '定义未配置'}</Tag>
            {definitionDirty && <Tag color="processing">定义有未保存修改</Tag>}
          </div>
          <div className="task-detail-subtitle">
            <span>{directoryName ?? (task.directoryId ? '目录已删除' : '未分类')}</span>
            <span>·</span>
            <span>{subtitleResource}</span>
          </div>
        </div>
        <Space size={4}>
          <Tooltip title="刷新任务">
            <Button
              icon={<ReloadOutlined />}
              aria-label="刷新任务详情"
              loading={taskQuery.isFetching}
              onClick={() => void taskQuery.refetch()}
            />
          </Tooltip>
          {canUpdate && <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>修改</Button>}
          {canPublish && (
            <Tooltip title={lifecycleBlockedByDirty
              ? '请先保存或放弃当前任务定义修改'
              : lifecycleBlockedByStreaming ? '请先正常停止实时任务' : undefined}>
              <Button
                type={task.status === 'DRAFT' ? 'primary' : 'default'}
                icon={lifecycleIcon(task)}
                loading={commandLoading}
                disabled={lifecycleBlockedByDirty || lifecycleBlockedByStreaming}
                onClick={() => transition(task)}
              >
                {lifecycleLabel(task)}
              </Button>
            </Tooltip>
          )}
          {canExecute && task.status === 'PUBLISHED' && task.type !== 'SPARK_STREAMING_CANVAS' && (
            <Tooltip title={runDisabledReason}>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                loading={runMutation.isPending}
                disabled={Boolean(runDisabledReason)}
                onClick={() => void run(task)}
              >
                立即运行
              </Button>
            </Tooltip>
          )}
          {canExecute && task.status === 'PUBLISHED' && task.type === 'SPARK_STREAMING_CANVAS' && (
            streamingActive ? (
              <Tooltip title={streamingState === 'STOPPING' ? '正在停止' : undefined}>
                <Button
                  danger
                  icon={<StopOutlined />}
                  loading={stopStreamingMutation.isPending}
                  disabled={streamingState === 'STOPPING'}
                  onClick={() => stopStreaming(task)}
                >
                  {streamingState === 'STOPPING' ? '停止中' : '停止'}
                </Button>
              </Tooltip>
            ) : (
              <Tooltip title={runDisabledReason}>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  loading={startStreamingMutation.isPending}
                  disabled={Boolean(runDisabledReason)}
                  onClick={() => startStreaming(task)}
                >
                  {streamingState === 'FAILED' || streamingState === 'STOPPED' ? '恢复运行' : '启动'}
                </Button>
              </Tooltip>
            )
          )}
          {canDelete && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: [{ key: 'delete', label: '删除任务', danger: true, icon: <DeleteOutlined /> }],
                onClick: ({ key }) => key === 'delete' && remove(task),
              }}
            >
              <Button icon={<MoreOutlined />} aria-label="任务更多操作" />
            </Dropdown>
          )}
        </Space>
      </div>
      {!task.definitionConfigured && task.status === 'PUBLISHED' && (
        <Alert
          type="error"
          showIcon
          message="已发布任务的定义缺失"
          description="当前任务无法运行。请先停用任务，再重新配置任务定义；没有运行记录时也可以在停用后删除任务。"
        />
      )}
      <Tabs
        activeKey={activeTab}
        className={`task-detail-tabs task-detail-tabs-${activeTab}`}
        destroyOnHidden
        items={tabItems}
        onChange={(key) => setSearchParams({ tab: key as TaskDetailTabKey }, { replace: true })}
      />
      <Modal
        open={definitionLeaveBlocker.state === 'blocked'}
        title="放弃未保存的任务定义修改？"
        okText="放弃修改"
        okButtonProps={{ danger: true }}
        cancelText="继续编辑"
        closable={false}
        mask={{ closable: false }}
        onOk={() => {
          setDefinitionDirty(false);
          definitionLeaveBlocker.proceed?.();
        }}
        onCancel={() => definitionLeaveBlocker.reset?.()}
      >
        离开任务定义后，当前尚未保存的修改将丢失。
      </Modal>
      <TaskDrawer
        open={editing}
        task={task}
        directories={directoriesQuery.data ?? []}
        onClose={() => setEditing(false)}
        onSubmit={updateBasicInfo}
      />
    </div>
  );
};
