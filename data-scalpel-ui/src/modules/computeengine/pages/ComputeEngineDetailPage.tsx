import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import {
  ApiOutlined,
  ApartmentOutlined,
  ArrowLeftOutlined,
  DashboardOutlined,
  EditOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  ReloadOutlined,
  SendOutlined,
  SettingOutlined,
  StopOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Dropdown, Progress, Skeleton, Space, Table, Tabs, Tag, Tooltip, Typography, message, Modal } from 'antd';
import { useMemo, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { ApiError } from '../../../shared/api/http';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import { useCurrentUser } from '../../system';
import { ComputeEngineDrawer } from '../components/ComputeEngineDrawer';
import {
  useComputeEngine,
  useComputeEngineCommand,
  useComputeEngineExecutions,
  useComputeEngineRuntimeOverview,
  useDeactivateComputeEngine,
  useTestComputeEngine,
  computeEnginesKey,
} from '../hooks/useComputeEngines';
import {
  computeBackendTypeLabels,
  computeEngineHealthStateColors,
  computeEngineHealthStateLabels,
  computeEngineRegistrationStateColors,
  computeEngineRegistrationStateLabels,
  dispatcherExecutionStateColors,
  dispatcherExecutionStateLabels,
  dispatcherTaskTypeLabels,
  type ComputeEngineExecution,
  type ComputeEngineRuntimeOverview,
  type DispatcherExecutionScope,
} from '../model/computeEngine';

type DetailTab = 'overview' | 'active' | 'queue' | 'recent' | 'configuration';

const detailTabs: DetailTab[] = ['overview', 'active', 'queue', 'recent', 'configuration'];

const executionScopeByTab: Partial<Record<DetailTab, DispatcherExecutionScope>> = {
  active: 'ACTIVE',
  queue: 'QUEUED',
  recent: 'RECENT',
};

const formatDuration = (startedAt: string | null, endedAt: string | null, queuedAt: string) => {
  const started = new Date(startedAt ?? queuedAt).getTime();
  if (Number.isNaN(started)) return '—';
  const ended = endedAt ? new Date(endedAt).getTime() : Date.now();
  if (Number.isNaN(ended) || ended < started) return '—';
  const seconds = Math.floor((ended - started) / 1_000);
  if (seconds < 60) return `${seconds} 秒`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} 分 ${seconds % 60} 秒`;
  const hours = Math.floor(minutes / 60);
  return `${hours} 小时 ${minutes % 60} 分`;
};

const percentage = (used: number, limit: number | undefined) => {
  if (limit === undefined || limit <= 0) return 0;
  return Math.min(100, Math.round((used / limit) * 100));
};

const dependencyState = (state: string) => state === 'UP' ? 'success' : 'error';

const formatResourceSpec = (
  resources: { driverCores: number; driverMemoryMiB: number; executorInstances: number; executorCores: number; executorMemoryMiB: number },
  backend: 'LOCAL_DOCKER' | 'YARN' | 'KUBERNETES',
) => {
  const driver = `Driver ${resources.driverCores} Core / ${resources.driverMemoryMiB / 1024} GiB`;
  return backend === 'LOCAL_DOCKER'
    ? driver
    : `${driver}；Executor ${resources.executorInstances} 个 × ${resources.executorCores} Core / ${resources.executorMemoryMiB / 1024} GiB`;
};

const RuntimeOverviewPanel = ({
  overview,
  loading,
  error,
  onRetry,
}: {
  overview: ComputeEngineRuntimeOverview | undefined;
  loading: boolean;
  error: unknown;
  onRetry: () => void;
}) => {
  if (loading && !overview) return <Skeleton active paragraph={{ rows: 8 }} />;
  if (error && !overview) {
    return <Alert type="error" showIcon message="无法读取 Dispatcher 运行态" description={error instanceof ApiError ? error.message : '请确认 Dispatcher 可访问后重试。'} action={<Button size="small" onClick={onRetry}>重试</Button>} />;
  }
  if (!overview) return null;
  const capacity = overview.admissionCapacity;
  const usage = overview.admissionUsage;
  const resource = overview.resourceConfiguration;
  const capacityRows = [
    ['排队任务', usage.queued, capacity?.maxQueuedExecutions],
    ['提交槽位', usage.submitting, capacity?.maxConcurrentSubmissions],
    ['在途任务', usage.inFlight, capacity?.maxInFlightApplications],
  ] as const;
  const resourceItems = resource.backendType === 'LOCAL_DOCKER'
    ? [
      { key: 'image', label: '运行镜像', children: resource.image ?? '—' },
      { key: 'cpus', label: '容器 CPU 上限', children: resource.containerCpuLimit ?? '—' },
      { key: 'memory', label: '容器内存上限', children: resource.containerMemoryLimit ?? '—' },
      { key: 'heap', label: 'Runner JVM Heap', children: resource.runnerJvmHeap ?? '—' },
    ]
    : resource.backendType === 'YARN'
      ? [
        { key: 'queue', label: 'YARN 队列', children: resource.queue ?? '—' },
        { key: 'driver-memory', label: 'Driver 内存', children: resource.driverMemory ?? '—' },
        { key: 'executor-memory', label: 'Executor 内存', children: resource.executorMemory ?? '—' },
        { key: 'executor', label: 'Executor', children: `${resource.executorInstances ?? '—'} 个 · ${resource.executorCores ?? '—'} Core/个` },
      ]
      : [
        { key: 'namespace', label: 'Kubernetes Namespace', children: resource.namespace ?? '—' },
        { key: 'image', label: '运行镜像', children: resource.image ?? '—' },
        { key: 'driver-memory', label: 'Driver 内存', children: resource.driverMemory ?? '—' },
        { key: 'executor-memory', label: 'Executor 内存', children: resource.executorMemory ?? '—' },
        { key: 'executor', label: 'Executor', children: `${resource.executorInstances ?? '—'} 个 · ${resource.executorCores ?? '—'} Core/个` },
      ];

  return <div className="compute-engine-detail-panel">
    {Boolean(error) && <Alert type="warning" showIcon message="显示的是上次成功获取的运行态信息" action={<Button size="small" onClick={onRetry}>重试</Button>} />}
    <BusinessDetailSection title="任务准入与占用" description={`实时状态 · ${formatManagementDateTime(overview.collectedAt)}`} icon={<DashboardOutlined />}>
      <div className="compute-engine-capacity-list">
        {capacityRows.map(([label, used, limit]) => <div className="compute-engine-capacity-row" key={label}>
          <div><Typography.Text>{label}</Typography.Text><Typography.Text type="secondary">{limit === undefined || limit === null ? `${used} 个` : `${used} / ${limit}`}</Typography.Text></div>
          <Progress percent={percentage(used, limit)} showInfo={false} status={limit !== undefined && limit > 0 && used >= limit ? 'exception' : 'normal'} />
        </div>)}
      </div>
      <BusinessDetailDescriptions column={{ xs: 1, sm: 2, lg: 3 }} items={[
        { key: 'running', label: '运行中', children: `${usage.running} 个` },
        { key: 'submitted', label: '已提交待运行', children: `${usage.submitted} 个` },
        { key: 'cancelling', label: '取消请求中', children: `${usage.cancelRequested} 个` },
      ]} />
    </BusinessDetailSection>
    <BusinessDetailSection title="运行依赖" description={`Dispatcher ${overview.dispatcherInstanceId}`} icon={<ApartmentOutlined />}>
      <BusinessDetailDescriptions column={{ xs: 1, sm: 2, xl: 3 }} items={overview.dependencies.map((dependency) => ({
        key: dependency.name,
        label: dependency.name,
        children: <Space size={6}><Tag color={dependencyState(dependency.state)}>{dependency.state === 'UP' ? '正常' : '不可用'}</Tag>{dependency.detail && <Typography.Text type="secondary">{dependency.detail}</Typography.Text>}</Space>,
      }))} />
    </BusinessDetailSection>
    <BusinessDetailSection title="每次执行资源配置" description="配置上限，不代表实时使用量" icon={<ThunderboltOutlined />}>
      <BusinessDetailDescriptions column={{ xs: 1, sm: 2, lg: 3 }} items={resourceItems} />
    </BusinessDetailSection>
  </div>;
};

const ExecutionListPanel = ({ engineId, scope }: { engineId: string; scope: DispatcherExecutionScope }) => {
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const executionsQuery = useComputeEngineExecutions(engineId, scope, page, size);
  const columns = useMemo<TableProps<ComputeEngineExecution>['columns']>(() => [
    ...(scope === 'QUEUED' ? [{
      title: '位置', dataIndex: 'queuePosition', width: 76, align: 'right' as const,
      render: (value: number | null) => value === null ? '—' : value,
    }] : []),
    {
      title: '任务', key: 'task', width: 250,
      render: (_: unknown, execution: ComputeEngineExecution) => execution.synchronized && execution.taskRunId
        ? <Link to={`/task/${execution.taskId}?tab=runs&runId=${execution.taskRunId}`}>{execution.taskName ?? execution.taskId}</Link>
        : <Space size={6}><Typography.Text>{execution.taskName ?? execution.taskId}</Typography.Text><Tag>未同步</Tag></Space>,
    },
    {
      title: '状态', dataIndex: 'dispatcherState', width: 120,
      render: (state: ComputeEngineExecution['dispatcherState']) => <Tag color={dispatcherExecutionStateColors[state]}>{dispatcherExecutionStateLabels[state]}</Tag>,
    },
    { title: '类型', dataIndex: 'taskType', width: 135, render: (value: ComputeEngineExecution['taskType']) => dispatcherTaskTypeLabels[value] },
    { title: '排队时间', dataIndex: 'queuedAt', width: 168, render: formatManagementDateTime },
    { title: scope === 'QUEUED' ? '等待时长' : '耗时', key: 'duration', width: 110, render: (_: unknown, value: ComputeEngineExecution) => formatDuration(value.startedAt, value.endedAt, value.queuedAt) },
    ...(scope === 'RECENT' ? [{
      title: '结果', key: 'result', width: 260, ellipsis: true,
      render: (_: unknown, value: ComputeEngineExecution) => value.errorMessage ?? '—',
    }] : []),
    {
      title: '后端执行', key: 'backend', width: 210, ellipsis: true,
      render: (_: unknown, value: ComputeEngineExecution) => value.trackingUrl
        ? <a href={value.trackingUrl} target="_blank" rel="noreferrer">{value.backendExecutionId ?? '查看运行状态'}</a>
        : value.backendExecutionId ?? '—',
    },
  ], [scope]);
  const title = scope === 'ACTIVE' ? '活动任务' : scope === 'QUEUED' ? '排队任务' : '最近执行';
  return <div className="compute-engine-detail-panel compute-engine-execution-panel detail-table-panel">
    {executionsQuery.isError && <Alert type="error" showIcon message="加载执行记录失败" description={executionsQuery.error instanceof ApiError ? executionsQuery.error.message : undefined} action={<Button size="small" onClick={() => void executionsQuery.refetch()}>重试</Button>} />}
    <DetailTableToolbar
      title={title}
      total={executionsQuery.data?.totalElements ?? 0}
      current={page + 1}
      pageSize={size}
      itemUnit="条"
      onChange={(nextPage, nextSize) => { setPage(nextPage - 1); setSize(nextSize); }}
    />
    <Table<ComputeEngineExecution>
      className="management-table"
      size="small"
      rowKey="executionId"
      loading={executionsQuery.isLoading}
      columns={columns}
      dataSource={executionsQuery.data?.content ?? []}
      scroll={{ x: 1_140, y: 'calc(100% - 44px)' }}
      pagination={false}
    />
  </div>;
};

const ComputeEngineDetailContent = ({ engineId }: { engineId: string }) => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewTasks = permissions.has('task.view');
  const canUpdate = permissions.has('compute.engine.update');
  const canTest = permissions.has('compute.engine.test');
  const canManage = permissions.has('compute.engine.manage');
  const engineQuery = useComputeEngine(engineId);
  const runtimeQuery = useComputeEngineRuntimeOverview(engineId);
  const [activeTab, setActiveTab] = useState<DetailTab>('overview');
  const [editing, setEditing] = useState(false);
  const [manualRefreshing, setManualRefreshing] = useState(false);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const testMutation = useTestComputeEngine();
  const commandMutation = useComputeEngineCommand();
  const deactivateMutation = useDeactivateComputeEngine();
  const engine = engineQuery.data;

  if (engineQuery.isLoading && !engine) return <div className="business-detail-loading"><Skeleton active paragraph={{ rows: 9 }} /></div>;
  if (engineQuery.isError || !engine) return <Alert type="error" showIcon message="加载计算引擎失败" description={engineQuery.error instanceof ApiError ? engineQuery.error.message : '计算引擎不存在或当前账号无权查看。'} action={<Button onClick={() => navigate('/compute-engine')}>返回列表</Button>} />;

  const editable = engine.registrationState !== 'REGISTERING' && canUpdate
    && (!['ACTIVE', 'DRAINING'].includes(engine.registrationState) || canManage);
  const remotelyManageable = ['ACTIVE', 'DRAINING'].includes(engine.registrationState)
    || (engine.registrationState === 'ERROR' && engine.dispatcherInstanceId !== null);
  const showError = (error: unknown, fallback: string) => messageApi.error(error instanceof ApiError ? error.message : fallback);
  const refresh = async () => {
    const executionScope = executionScopeByTab[activeTab];
    setManualRefreshing(true);
    try {
      await Promise.all([
        engineQuery.refetch(),
        runtimeQuery.refetch(),
        executionScope
          ? queryClient.refetchQueries({
            queryKey: [computeEnginesKey, engine.id, 'executions', executionScope],
            type: 'active',
          })
          : Promise.resolve(),
      ]);
    } finally {
      setManualRefreshing(false);
    }
  };
  const test = async () => {
    try {
      await testMutation.mutateAsync(engine.id);
      messageApi.success('Dispatcher 连接正常');
      await Promise.all([engineQuery.refetch(), runtimeQuery.refetch()]);
    } catch (error) { showError(error, '测试计算引擎失败'); }
  };
  const register = () => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '注册计算引擎', content: `将“${engine.name}”注册到对应 Dispatcher，并启用新任务准入。`,
    okText: '注册', cancelText: '取消',
    onOk: async () => {
      try { await commandMutation.mutateAsync({ id: engine.id, command: 'register' }); messageApi.success('计算引擎已激活'); }
      catch (error) { showError(error, '注册计算引擎失败'); throw error; }
    },
  });
  const drain = () => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '排空计算引擎', content: `“${engine.name}”将停止接收新任务，但继续监管已运行任务。`,
    okText: '开始排空', cancelText: '取消',
    onOk: async () => {
      try { await commandMutation.mutateAsync({ id: engine.id, command: 'drain' }); messageApi.success('计算引擎正在排空'); }
      catch (error) { showError(error, '排空计算引擎失败'); throw error; }
    },
  });
  const deactivate = (force: boolean) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: force ? '强制反注册并取消任务' : '安全反注册计算引擎',
    content: force ? `Dispatcher 将取消“${engine.name}”中仍在排队或运行的任务，然后完成反注册。确认继续吗？` : `确认安全反注册“${engine.name}”吗？Dispatcher 必须可访问且已无活动任务。`,
    okText: force ? '强制反注册并取消任务' : '安全反注册', cancelText: '取消', okButtonProps: { danger: force },
    onOk: async () => {
      try { await deactivateMutation.mutateAsync({ id: engine.id, force }); messageApi.success('计算引擎已反注册'); }
      catch (error) { showError(error, '反注册计算引擎失败'); throw error; }
    },
  });
  const menuItems: NonNullable<MenuProps['items']> = [];
  if (canTest) menuItems.push({ key: 'test', icon: <ApiOutlined />, label: '测试连接', onClick: () => void test() });
  if (editable) menuItems.push({ key: 'edit', icon: <EditOutlined />, label: '修改配置', onClick: () => setEditing(true) });
  if (canManage && ['CREATED', 'INACTIVE', 'DETACHED', 'ERROR'].includes(engine.registrationState)) {
    menuItems.push({ key: 'register', icon: <SendOutlined />, label: '注册并激活', onClick: register });
  }
  if (canManage && engine.registrationState === 'ACTIVE') {
    menuItems.push({ key: 'drain', icon: <PauseCircleOutlined />, label: '开始排空', onClick: drain });
  }
  if (canManage && remotelyManageable) {
    menuItems.push({ key: 'deactivate', icon: <StopOutlined />, label: '安全反注册', onClick: () => deactivate(false) });
    menuItems.push({ key: 'force-deactivate', danger: true, icon: <StopOutlined />, label: '强制反注册并取消任务', onClick: () => deactivate(true) });
  }
  const tabs = [
    { key: 'overview', label: '概览', children: <RuntimeOverviewPanel overview={runtimeQuery.data} loading={runtimeQuery.isLoading} error={runtimeQuery.error} onRetry={() => void runtimeQuery.refetch()} /> },
    ...(canViewTasks ? [
      { key: 'active', label: '活动任务', children: <ExecutionListPanel engineId={engine.id} scope="ACTIVE" /> },
      { key: 'queue', label: '排队任务', children: <ExecutionListPanel engineId={engine.id} scope="QUEUED" /> },
      { key: 'recent', label: '最近执行', children: <ExecutionListPanel engineId={engine.id} scope="RECENT" /> },
    ] : []),
    {
      key: 'configuration',
      label: '配置',
      children: (
        <div className="compute-engine-detail-panel">
          <BusinessDetailSection title="基础配置" description="Dispatcher 通道、容量限制与维护时间" icon={<SettingOutlined />}>
            <BusinessDetailDescriptions column={{ xs: 1, sm: 2, lg: 3 }} items={[
              { key: 'description', label: '说明', children: engine.description ?? '—', span: 3 },
              { key: 'dispatcher', label: 'Dispatcher 地址', children: engine.dispatcherBaseUrl, span: 2 },
              { key: 'instance', label: '实例 ID', children: engine.dispatcherInstanceId ?? '—' },
              { key: 'command', label: '命令 Topic', children: engine.commandTopic, span: 2 },
              { key: 'runner', label: 'Runner 事件 Topic', children: engine.runnerEventTopic },
              { key: 'admin', label: 'Admin 事件 Topic', children: engine.adminEventTopic, span: 3 },
              { key: 'queue', label: '最大排队数', children: engine.maxQueuedExecutions },
              { key: 'submit', label: '最大并发提交数', children: engine.maxConcurrentSubmissions },
              { key: 'flight', label: '最大在途数', children: engine.maxInFlightApplications },
              { key: 'created', label: '创建时间', children: formatManagementDateTime(engine.createdAt) },
              { key: 'updated', label: '更新时间', children: formatManagementDateTime(engine.updatedAt) },
              { key: 'checked', label: '最近检查', children: formatManagementDateTime(engine.lastCheckAt) },
            ]} />
          </BusinessDetailSection>
          <BusinessDetailSection title="运行资源策略" description="单次任务不得超过最大值" icon={<ThunderboltOutlined />}>
            <BusinessDetailDescriptions column={{ xs: 1, md: 2 }} items={[
              { key: 'resource-default', label: '默认资源', children: formatResourceSpec(engine.resourcePolicy.defaults, engine.expectedBackendType) },
              { key: 'resource-maximum', label: '单次最大资源', children: formatResourceSpec(engine.resourcePolicy.maximums, engine.expectedBackendType) },
            ]} />
          </BusinessDetailSection>
        </div>
      ),
    },
  ];
  const normalizedTab = detailTabs.includes(activeTab) && (!['active', 'queue', 'recent'].includes(activeTab) || canViewTasks) ? activeTab : 'overview';

  return <div className="compute-engine-detail-page business-detail-page">
    {messageContext}{modalContext}
    <header className="compute-engine-detail-header business-detail-header">
      <div>
        <div className="compute-engine-detail-title-row">
          <Button type="text" icon={<ArrowLeftOutlined />} aria-label="返回计算引擎列表" onClick={() => navigate('/compute-engine')} />
          <span className="business-detail-resource-icon business-detail-resource-icon-purple"><ThunderboltOutlined /></span>
          <span className="compute-engine-detail-title">{engine.name}</span>
          <Tag color={computeEngineRegistrationStateColors[engine.registrationState]}>{computeEngineRegistrationStateLabels[engine.registrationState]}</Tag>
          <Tag color={computeEngineHealthStateColors[engine.healthState]}>{computeEngineHealthStateLabels[engine.healthState]}</Tag>
        </div>
        <div className="compute-engine-detail-subtitle"><span>{computeBackendTypeLabels[engine.expectedBackendType]}</span><code>{engine.dispatcherBaseUrl}</code><span>最近检查：{formatManagementDateTime(engine.lastCheckAt)}</span></div>
      </div>
      <Space className="compute-engine-detail-actions" size={8}>
        <Button type="text" icon={<ReloadOutlined />} loading={manualRefreshing} onClick={() => void refresh()}>刷新</Button>
        {canTest && <Tooltip title="测试连接"><Button icon={<ApiOutlined />} aria-label="测试计算引擎连接" loading={testMutation.isPending} onClick={() => void test()} /></Tooltip>}
        {editable && <Button type="primary" icon={<EditOutlined />} onClick={() => setEditing(true)}>编辑</Button>}
        {menuItems.length > 0 && <Dropdown menu={{ items: menuItems }} trigger={['click']}><Button icon={<MoreOutlined />} aria-label="计算引擎更多操作" /></Dropdown>}
      </Space>
    </header>
    <Tabs
      activeKey={normalizedTab}
      className="compute-engine-detail-tabs business-detail-tabs"
      destroyOnHidden
      items={tabs}
      onChange={(key) => setActiveTab(key as DetailTab)}
    />
    <ComputeEngineDrawer open={editing} engine={engine} canUpdate={canUpdate} canManage={canManage} onClose={() => setEditing(false)} />
  </div>;
};

export const ComputeEngineDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  return id ? <ComputeEngineDetailContent engineId={id} /> : <Navigate to="/compute-engine" replace />;
};
