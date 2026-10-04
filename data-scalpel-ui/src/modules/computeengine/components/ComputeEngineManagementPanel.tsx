import { CompactAlert as Alert, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import {
  ApiOutlined,
  DeleteOutlined,
  DisconnectOutlined,
  EyeOutlined,
  EditOutlined,
  MoreOutlined,
  PauseCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  SendOutlined,
  StopOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Dropdown, Form, Input, Modal, Select, Space, Table, Tooltip, Typography, message } from 'antd';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { ManagementAdaptiveMoreFilters, ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementCode, ManagementListCell, ManagementName, ManagementStatusIndicator, type ManagementStatusTone } from '../../../shared/components/ManagementListCells';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
import {
  useComputeEngineCommand,
  useComputeEngines,
  useDeactivateComputeEngine,
  useDetachComputeEngine,
  useDeleteComputeEngine,
  useTestComputeEngine,
} from '../hooks/useComputeEngines';
import {
  computeBackendTypeLabels,
  computeEngineHealthStateLabels,
  computeEngineRegistrationStateLabels,
  type ComputeEngine,
  type ComputeEngineFilters,
} from '../model/computeEngine';
import { buildComputeEngineSearch } from '../model/computeEngineSearch';
import { ComputeEngineDrawer } from './ComputeEngineDrawer';
import { ComputeEngineDiscoveryDrawer } from './ComputeEngineDiscoveryDrawer';
import { ComputeEngineTopics } from './ComputeEngineTopics';
import { engineLifecycle } from '../model/computeEngineLifecycle';
import { OverlayTitle } from '../../../shared/components/OverlayTitle';

interface ComputeEngineManagementPanelProps {
  canCreate: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canTest: boolean;
  canManage: boolean;
}

interface DetachComputeEngineFormValues {
  reason: string;
  confirmationName: string;
}

export const ComputeEngineManagementPanel = ({ canCreate, canUpdate, canDelete, canTest, canManage }: ComputeEngineManagementPanelProps) => {
  const [filterForm] = Form.useForm<ComputeEngineFilters>();
  const [advancedFilterForm] = Form.useForm<ComputeEngineFilters>();
  const [advancedFilterOpen, setAdvancedFilterOpen] = useState(false);
  const [advancedFilters, setAdvancedFilters] = useState<ComputeEngineFilters>({});
  const [filters, setFilters] = useState<ComputeEngineFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [drawerEngine, setDrawerEngine] = useState<ComputeEngine | null | undefined>(undefined);
  const [discoveryUrl, setDiscoveryUrl] = useState<string | null>(null);
  const [detachEngine, setDetachEngine] = useState<ComputeEngine | null>(null);
  const [detachForm] = Form.useForm<DetachComputeEngineFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const request = useMemo(() => ({
    search: buildComputeEngineSearch(filters), page, size, sort: 'dispatcherBaseUrl,expectedBackendType,name',
  }), [filters, page, size]);
  const enginesQuery = useComputeEngines(request);
  const advancedFilterCount = Number(advancedFilters.expectedBackendType !== undefined) + Number(advancedFilters.healthState !== undefined);
  const testMutation = useTestComputeEngine();
  const commandMutation = useComputeEngineCommand();
  const deactivateMutation = useDeactivateComputeEngine();
  const detachMutation = useDetachComputeEngine();
  const deleteMutation = useDeleteComputeEngine();

  const registrationTone = (state: ComputeEngine['registrationState']): ManagementStatusTone => (
    state === 'ACTIVE' ? 'success' : state === 'ERROR' ? 'error' : state === 'DRAINING' || state === 'REGISTERING' ? 'processing' : 'default'
  );
  const healthTone = (state: ComputeEngine['healthState']): ManagementStatusTone => (
    state === 'UP' ? 'success' : state === 'DOWN' ? 'error' : 'default'
  );

  const showError = (error: unknown, fallback: string) => messageApi.error(error instanceof ApiError ? error.message : fallback);

  const test = async (engine: ComputeEngine) => {
    try {
      const result = await testMutation.mutateAsync(engine.id);
      messageApi.success(`${engine.name} 连接正常：${computeBackendTypeLabels[result.backendType]}`);
    } catch (error) { showError(error, '测试计算引擎失败'); }
  };

  const register = (engine: ComputeEngine) => modalApi.confirm({
    icon: null,
    rootClassName: 'business-overlay business-modal-overlay resource-workspace-overlay',
    title: <OverlayTitle title={engine.registrationState === 'DETACHED' ? '重新注册离线解绑的计算引擎' : '注册计算引擎'} icon={<SendOutlined />} />,
    content: engine.registrationState === 'DETACHED'
      ? `请先确认原 Dispatcher 进程已经永久停止。继续后将“${engine.name}”注册到当前配置的 Dispatcher，并重新启用任务准入。`
      : `将“${engine.name}”注册到对应 Dispatcher，并启用新任务准入。`,
    okText: '注册', cancelText: '取消',
    onOk: async () => {
      try { await commandMutation.mutateAsync({ id: engine.id, command: 'register' }); messageApi.success('计算引擎已激活'); }
      catch (error) { showError(error, '注册计算引擎失败'); throw error; }
    },
  });

  const drain = (engine: ComputeEngine) => modalApi.confirm({
    icon: null,
    rootClassName: 'business-overlay business-modal-overlay resource-workspace-overlay',
    title: <OverlayTitle title={engineLifecycle.pause.label} icon={<PauseCircleOutlined />} />,
    content: `“${engine.name}”：${engineLifecycle.pause.description}`,
    okText: engineLifecycle.pause.label, cancelText: '取消',
    onOk: async () => {
      try { await commandMutation.mutateAsync({ id: engine.id, command: 'drain' }); messageApi.success('任务调度已暂停，运行中任务继续'); }
      catch (error) { showError(error, '暂停任务调度失败'); throw error; }
    },
  });

  const resume = (engine: ComputeEngine) => modalApi.confirm({
    icon: null,
    rootClassName: 'business-overlay business-modal-overlay resource-workspace-overlay',
    title: <OverlayTitle title={engineLifecycle.resume.label} icon={<ThunderboltOutlined />} />, content: `“${engine.name}”：${engineLifecycle.resume.description}`,
    okText: engineLifecycle.resume.label, cancelText: '取消',
    onOk: async () => {
      try { await commandMutation.mutateAsync({ id: engine.id, command: 'resume' }); messageApi.success('任务调度已恢复'); }
      catch (error) { showError(error, '恢复任务调度失败'); throw error; }
    },
  });

  const deactivate = (engine: ComputeEngine, force: boolean) => modalApi.confirm({
    icon: null,
    rootClassName: 'business-overlay business-modal-overlay resource-workspace-overlay',
    title: <OverlayTitle title={force ? engineLifecycle.forceStop.label : engineLifecycle.stop.label} icon={<StopOutlined />} tone="danger" />,
    content: force
      ? `“${engine.name}”：${engineLifecycle.forceStop.description} Dispatcher 必须可访问。`
      : `“${engine.name}”：${engineLifecycle.stop.description} Dispatcher 必须可访问。`,
    okText: force ? engineLifecycle.forceStop.label : engineLifecycle.stop.label, cancelText: '取消', okButtonProps: { danger: force },
    onOk: async () => {
      try { await deactivateMutation.mutateAsync({ id: engine.id, force }); messageApi.success('计算引擎已停用，配置和历史保留'); }
      catch (error) { showError(error, '停用计算引擎失败'); throw error; }
    },
  });

  const openDetach = (engine: ComputeEngine) => {
    detachForm.resetFields();
    setDetachEngine(engine);
  };

  const closeDetach = () => {
    setDetachEngine(null);
    detachForm.resetFields();
  };

  const detach = async (values: DetachComputeEngineFormValues) => {
    if (!detachEngine) return;
    try {
      await detachMutation.mutateAsync({
        id: detachEngine.id,
        request: {
          confirmationName: values.confirmationName.trim(),
          reason: values.reason.trim(),
        },
      });
      messageApi.success('计算引擎已离线解除绑定');
      closeDetach();
    } catch (error) {
      showError(error, '离线解除绑定失败');
    }
  };

  const remove = (engine: ComputeEngine) => modalApi.confirm({
    icon: null,
    rootClassName: 'business-overlay business-modal-overlay resource-workspace-overlay',
    title: <OverlayTitle title="删除计算引擎" icon={<DeleteOutlined />} tone="danger" />, content: `确认删除“${engine.name}”吗？已被任务引用时无法删除。`,
    okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: async () => {
      try { await deleteMutation.mutateAsync(engine.id); messageApi.success('计算引擎已删除'); }
      catch (error) { showError(error, '删除计算引擎失败'); throw error; }
    },
  });

  const columns: TableProps<ComputeEngine>['columns'] = [
    { title: '引擎', dataIndex: 'name', width: 220, render: (value: string, engine) => <ManagementListCell icon={<ThunderboltOutlined />} iconTone="violet" primary={<ManagementName name={value} description={engine.description}><Tooltip title={value}><Link to={`/compute-engine/${engine.id}`}>{value}</Link></Tooltip></ManagementName>} secondary={computeBackendTypeLabels[engine.expectedBackendType]} /> },
    { title: '注册 / 健康', width: 180, render: (_: unknown, engine) => <ManagementListCell primary={<ManagementStatusIndicator label={computeEngineRegistrationStateLabels[engine.registrationState]} tone={registrationTone(engine.registrationState)} />} secondary={<ManagementStatusIndicator label={computeEngineHealthStateLabels[engine.healthState]} tone={healthTone(engine.healthState)} />} /> },
    { title: 'Dispatcher / 目标', width: 290, render: (_: unknown, engine) => <ManagementListCell primary={<ManagementCode value={engine.dispatcherBaseUrl} />} secondary={<Tooltip title={engine.targetDispatcherInstanceId || engine.dispatcherInstanceId || '尚未注册实例'}><Typography.Text copyable={engine.targetKey ? { text: engine.targetKey } : false}>{engine.targetKey ? `目标 Key：${engine.targetKey}` : '旧式单目标配置'}</Typography.Text></Tooltip>} /> },
    { title: '消息通道', width: 310, render: (_: unknown, engine) => <ComputeEngineTopics engine={engine} compact /> },
    { title: '容量 / 最近检查', width: 210, render: (_: unknown, engine) => <ManagementListCell primary={`队列 ${engine.maxQueuedExecutions} · 并发 ${engine.maxConcurrentSubmissions}`} secondary={formatManagementDateTime(engine.lastCheckAt)} /> },
    {
      title: '操作', key: 'actions', width: 110,
      render: (_, engine) => {
        const deletable = !['ACTIVE', 'DRAINING', 'REGISTERING'].includes(engine.registrationState)
          && !(engine.targetKey && engine.registrationState === 'ERROR');
        const reconfigurable = ['ACTIVE', 'DRAINING'].includes(engine.registrationState);
        const hasRegisteredDispatcher = Boolean(engine.dispatcherInstanceId || engine.targetDispatcherInstanceId);
        const remotelyManageable = ['ACTIVE', 'DRAINING'].includes(engine.registrationState)
          || (engine.registrationState === 'ERROR' && hasRegisteredDispatcher);
        const editable = engine.registrationState !== 'REGISTERING'
          && canUpdate
          && (!reconfigurable || canManage);
        const items: NonNullable<MenuProps['items']> = [];
        if (canCreate && canManage && canTest) items.push({ key: 'discover', icon: <PlusOutlined />, label: '添加同实例的其他目标', onClick: () => setDiscoveryUrl(engine.dispatcherBaseUrl) });
        if (canTest) items.push({ key: 'test', icon: <ApiOutlined />, label: '测试连接', onClick: () => void test(engine) });
        items.push({ key: 'configuration', icon: editable ? <EditOutlined /> : <EyeOutlined />, label: editable ? '修改配置' : '查看配置', onClick: () => setDrawerEngine(engine) });
        if (canManage && ['CREATED', 'INACTIVE', 'DETACHED', 'ERROR'].includes(engine.registrationState)) items.push({ key: 'register', icon: <SendOutlined />, label: '注册并激活', onClick: () => register(engine) });
        if (canManage && engine.registrationState === 'ACTIVE') items.push({ key: 'drain', icon: <PauseCircleOutlined />, label: engineLifecycle.pause.label, onClick: () => drain(engine) });
        if (canManage && engine.registrationState === 'DRAINING') items.push({ key: 'resume', icon: <SendOutlined />, label: engineLifecycle.resume.label, onClick: () => resume(engine) });
        if (canManage && remotelyManageable) items.push({ key: 'deactivate', icon: <StopOutlined />, label: engineLifecycle.stop.label, onClick: () => deactivate(engine, false) });
        if (canManage && remotelyManageable) items.push({ key: 'force-deactivate', danger: true, icon: <StopOutlined />, label: engineLifecycle.forceStop.label, onClick: () => deactivate(engine, true) });
        if (canManage && hasRegisteredDispatcher && engine.healthState === 'DOWN' && ['ACTIVE', 'DRAINING', 'ERROR'].includes(engine.registrationState)) items.push({
          key: 'detach',
          danger: true,
          icon: <DisconnectOutlined />,
          label: '离线解除绑定',
          onClick: () => openDetach(engine),
        });
        if (canDelete && deletable) {
          if (items.length) items.push({ type: 'divider' });
          items.push({ key: 'delete', danger: true, icon: <DeleteOutlined />, label: '删除', onClick: () => remove(engine) });
        }
        const busy = (commandMutation.isPending && commandMutation.variables?.id === engine.id)
          || (deactivateMutation.isPending && deactivateMutation.variables?.id === engine.id);
        return <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            {canTest && <Tooltip title="测试连接"><Button type="text" size="small" aria-label={`测试${engine.name}`} icon={<ApiOutlined />} loading={testMutation.isPending && testMutation.variables === engine.id} onClick={() => void test(engine)} /></Tooltip>}
            <Tooltip title={editable ? '修改配置' : '查看配置'}><Button type="text" size="small" aria-label={`${editable ? '修改' : '查看'}${engine.name}`} icon={editable ? <EditOutlined /> : <EyeOutlined />} onClick={() => setDrawerEngine(engine)} /></Tooltip>
          </div>
          <Dropdown menu={{ items }} disabled={busy}><Tooltip title="更多操作"><Button loading={busy} className="management-row-actions-more" type="text" size="small" icon={<MoreOutlined />} aria-label={`${engine.name}的更多操作`} /></Tooltip></Dropdown>
        </div>;
      },
    },
  ];
  const applyDirect = (values: ComputeEngineFilters) => {
    const advancedValues = advancedFilterForm.getFieldsValue();
    const nextAdvancedFilters = {
      expectedBackendType: advancedValues.expectedBackendType,
      healthState: advancedValues.healthState,
    };
    setAdvancedFilters(nextAdvancedFilters);
    setFilters({
      keyword: values.keyword,
      registrationState: values.registrationState,
      ...nextAdvancedFilters,
    });
    setPage(0);
  };
  const confirmAdvanced = () => {
    const values = advancedFilterForm.getFieldsValue();
    setAdvancedFilters({
      expectedBackendType: values.expectedBackendType,
      healthState: values.healthState,
    });
    setAdvancedFilterOpen(false);
  };
  const clearAdvanced = () => advancedFilterForm.setFieldsValue({ expectedBackendType: undefined, healthState: undefined });
  const reset = () => {
    filterForm.resetFields();
    advancedFilterForm.resetFields();
    advancedFilterForm.setFieldsValue({ expectedBackendType: undefined, healthState: undefined });
    setAdvancedFilters({});
    setAdvancedFilterOpen(false);
    setFilters({});
    setPage(0);
  };

  return <>
    {messageContext}{modalContext}
    <section className="management-workbench">
      <div className="management-filter-strip">
        <Form<ComputeEngineFilters> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" onFinish={applyDirect} id="compute-engine-management-panel-filters-0">
          <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索计算引擎名称" /></Form.Item>
          <Form.Item name="registrationState"><Select allowClear placeholder="全部注册状态" style={{ width: 130 }} options={Object.entries(computeEngineRegistrationStateLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
        </Form>
          <ManagementAdaptiveMoreFilters
            count={advancedFilterCount}
            open={advancedFilterOpen}
            onOpenChange={(open) => {
              setAdvancedFilterOpen(open);
              if (open) {
                advancedFilterForm.resetFields();
                advancedFilterForm.setFieldsValue(advancedFilters);
              }
            }}
            onClear={clearAdvanced}
            onCancel={() => {
              advancedFilterForm.setFieldsValue({ expectedBackendType: advancedFilters.expectedBackendType, healthState: advancedFilters.healthState });
              setAdvancedFilterOpen(false);
            }}
            onConfirm={confirmAdvanced}
          >
            <Form<ComputeEngineFilters> form={advancedFilterForm} layout="vertical" autoComplete="off" initialValues={advancedFilters}>
              <Form.Item name="expectedBackendType" label="后端"><Select allowClear placeholder="全部后端" className="advanced-filter-select" options={Object.entries(computeBackendTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
              <Form.Item name="healthState" label="健康状态"><Select allowClear placeholder="全部健康状态" className="advanced-filter-select" options={Object.entries(computeEngineHealthStateLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
            </Form>
          </ManagementAdaptiveMoreFilters>
        <ManagementFilterActions form={filterForm} appliedFilters={filters} additionalActive={advancedFilterCount > 0} loading={enginesQuery.isFetching} onReset={reset} commands={<Space size={4} className="management-result-actions">
          <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新计算引擎列表" onClick={() => void enginesQuery.refetch()} /></Tooltip>
          {canCreate && canTest && canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setDiscoveryUrl('')}>连接 Dispatcher</Button>}
        </Space>} formId="compute-engine-management-panel-filters-0" />
      </div>
      <div className="management-results-surface">
        <div className="management-result-toolbar">
        <div className="management-result-title">计算引擎 <span className="management-result-count">共 {enginesQuery.data?.totalElements ?? 0} 项</span></div>

        </div>
        {enginesQuery.error && <InlineFeedback tone="error" label="计算引擎列表加载失败" detail={enginesQuery.error.message} action={<Button size="small" onClick={() => void enginesQuery.refetch()}>重试</Button>} />}
        <Table<ComputeEngine>
        size="small" className="management-table" rowKey="id" columns={columns}
        dataSource={enginesQuery.data?.content ?? []} loading={enginesQuery.isFetching}
        scroll={{ x: 1320, y: '100%' }}
        pagination={{ current: page + 1, pageSize: size, total: enginesQuery.data?.totalElements ?? 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 项`, position: ['bottomRight'], hideOnSinglePage: false }}
        onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? 20); }}
        />
      </div>
    </section>
    {discoveryUrl !== null && <ComputeEngineDiscoveryDrawer initialUrl={discoveryUrl} onClose={() => setDiscoveryUrl(null)} />}
    <ComputeEngineDrawer
      open={drawerEngine !== undefined}
      engine={drawerEngine ?? null}
      canUpdate={canUpdate}
      canManage={canManage}
      onClose={() => setDrawerEngine(undefined)}
    />
    <Modal
      rootClassName="business-overlay business-modal-overlay resource-workspace-overlay"
      title={<OverlayTitle title="离线解除绑定" icon={<DisconnectOutlined />} />}
      open={detachEngine !== null}
      okText="确认离线解除绑定"
      cancelText="取消"
      okButtonProps={{ danger: true }}
      confirmLoading={detachMutation.isPending}
      onCancel={closeDetach}
      onOk={() => detachForm.submit()}
      destroyOnHidden
    >
      <Alert
        type="error"
        showIcon
        message="这是 Dispatcher 不可达时的灾难恢复操作"
        description="此操作只修改 Admin 状态，不会停止 Dispatcher 或其中的任务。请确认原 Dispatcher 进程已经永久停止，否则可能出现多个 Dispatcher 同时消费任务。"
        style={{ marginBottom: 16 }}
      />
      <Form<DetachComputeEngineFormValues> autoComplete="off"
        form={detachForm}
        layout="vertical"
        onFinish={(values) => void detach(values)}
        preserve={false}
      >
        <Form.Item
          name="reason"
          label="解除绑定原因"
          rules={[{ required: true, whitespace: true, message: '请输入解除绑定原因' }, { max: 500 }]}
        >
          <Input.TextArea rows={3} maxLength={500} showCount placeholder="例如：原 Dispatcher 主机已永久下线" />
        </Form.Item>
        <Form.Item
          name="confirmationName"
          label={<>输入计算引擎名称 <strong>{detachEngine?.name}</strong> 以确认</>}
          rules={[
            { required: true, whitespace: true, message: '请输入计算引擎名称' },
            {
              validator: (_, value: string | undefined) => (
                value?.trim() === detachEngine?.name
                  ? Promise.resolve()
                  : Promise.reject(new Error('输入内容与计算引擎名称不一致'))
              ),
            },
          ]}
        >
          <Input autoComplete="off" />
        </Form.Item>
      </Form>
    </Modal>
  </>;
};
