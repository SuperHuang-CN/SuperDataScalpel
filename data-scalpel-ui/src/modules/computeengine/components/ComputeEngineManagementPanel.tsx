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
import { Alert, Button, Dropdown, Form, Input, Modal, Select, Space, Table, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ManagementFilterActions, ManagementMoreFilters, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementCode, ManagementListCell, ManagementStatusIndicator, type ManagementStatusTone } from '../../../shared/components/ManagementListCells';
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
  const [detachEngine, setDetachEngine] = useState<ComputeEngine | null>(null);
  const [detachForm] = Form.useForm<DetachComputeEngineFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const request = useMemo(() => ({
    search: buildComputeEngineSearch(filters), page, size, sort: '-updatedAt,name',
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
    title: engine.registrationState === 'DETACHED' ? '重新注册离线解绑的计算引擎' : '注册计算引擎',
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
    title: '排空计算引擎',
    content: `“${engine.name}”将停止接收新任务，但继续监管已运行任务。`,
    okText: '开始排空', cancelText: '取消',
    onOk: async () => {
      try { await commandMutation.mutateAsync({ id: engine.id, command: 'drain' }); messageApi.success('计算引擎正在排空'); }
      catch (error) { showError(error, '排空计算引擎失败'); throw error; }
    },
  });

  const deactivate = (engine: ComputeEngine, force: boolean) => modalApi.confirm({
    title: force ? '强制反注册并取消任务' : '安全反注册计算引擎',
    content: force
      ? `Dispatcher 将取消“${engine.name}”中仍在排队或运行的任务，然后完成反注册。此操作需要 Dispatcher 可访问，确认继续吗？`
      : `确认安全反注册“${engine.name}”吗？Dispatcher 必须可访问且已无活动任务。`,
    okText: force ? '强制反注册并取消任务' : '安全反注册', cancelText: '取消', okButtonProps: { danger: force },
    onOk: async () => {
      try { await deactivateMutation.mutateAsync({ id: engine.id, force }); messageApi.success('计算引擎已反注册'); }
      catch (error) { showError(error, '反注册计算引擎失败'); throw error; }
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
    title: '删除计算引擎', content: `确认删除“${engine.name}”吗？已被任务引用时无法删除。`,
    okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: async () => {
      try { await deleteMutation.mutateAsync(engine.id); messageApi.success('计算引擎已删除'); }
      catch (error) { showError(error, '删除计算引擎失败'); throw error; }
    },
  });

  const columns: TableProps<ComputeEngine>['columns'] = [
    { title: '引擎', dataIndex: 'name', width: 220, render: (value: string, engine) => <ManagementListCell icon={<ThunderboltOutlined />} iconTone="violet" primary={value} secondary={computeBackendTypeLabels[engine.expectedBackendType]} /> },
    { title: '注册 / 健康', width: 180, render: (_: unknown, engine) => <ManagementListCell primary={<ManagementStatusIndicator label={computeEngineRegistrationStateLabels[engine.registrationState]} tone={registrationTone(engine.registrationState)} />} secondary={<ManagementStatusIndicator label={computeEngineHealthStateLabels[engine.healthState]} tone={healthTone(engine.healthState)} />} /> },
    { title: 'Dispatcher', width: 290, render: (_: unknown, engine) => <ManagementListCell primary={<ManagementCode value={engine.dispatcherBaseUrl} />} secondary={engine.dispatcherInstanceId || '尚未注册实例'} /> },
    { title: '消息通道', width: 310, render: (_: unknown, engine) => <ManagementListCell primary={<ManagementCode value={engine.commandTopic} />} secondary={`Runner：${engine.runnerEventTopic} · Admin：${engine.adminEventTopic}`} /> },
    { title: '容量 / 最近检查', width: 210, render: (_: unknown, engine) => <ManagementListCell primary={`队列 ${engine.maxQueuedExecutions} · 并发 ${engine.maxConcurrentSubmissions}`} secondary={formatManagementDateTime(engine.lastCheckAt)} /> },
    {
      title: '操作', key: 'actions', width: 110,
      render: (_, engine) => {
        const deletable = !['ACTIVE', 'DRAINING', 'REGISTERING'].includes(engine.registrationState);
        const reconfigurable = ['ACTIVE', 'DRAINING'].includes(engine.registrationState);
        const hasRegisteredDispatcher = engine.dispatcherInstanceId !== null;
        const remotelyManageable = ['ACTIVE', 'DRAINING'].includes(engine.registrationState)
          || (engine.registrationState === 'ERROR' && hasRegisteredDispatcher);
        const editable = engine.registrationState !== 'REGISTERING'
          && canUpdate
          && (!reconfigurable || canManage);
        const items: NonNullable<MenuProps['items']> = [];
        if (canTest) items.push({ key: 'test', icon: <ApiOutlined />, label: '测试连接', onClick: () => void test(engine) });
        items.push({ key: 'configuration', icon: editable ? <EditOutlined /> : <EyeOutlined />, label: editable ? '修改配置' : '查看配置', onClick: () => setDrawerEngine(engine) });
        if (canManage && ['CREATED', 'INACTIVE', 'DETACHED', 'ERROR'].includes(engine.registrationState)) items.push({ key: 'register', icon: <SendOutlined />, label: '注册并激活', onClick: () => register(engine) });
        if (canManage && engine.registrationState === 'ACTIVE') items.push({ key: 'drain', icon: <PauseCircleOutlined />, label: '开始排空', onClick: () => drain(engine) });
        if (canManage && remotelyManageable) items.push({ key: 'deactivate', icon: <StopOutlined />, label: '安全反注册', onClick: () => deactivate(engine, false) });
        if (canManage && remotelyManageable) items.push({ key: 'force-deactivate', danger: true, icon: <StopOutlined />, label: '强制反注册并取消任务', onClick: () => deactivate(engine, true) });
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
        return <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            {canTest && <Tooltip title="测试连接"><Button type="text" size="small" aria-label={`测试${engine.name}`} icon={<ApiOutlined />} loading={testMutation.isPending && testMutation.variables === engine.id} onClick={() => void test(engine)} /></Tooltip>}
            <Tooltip title={editable ? '修改配置' : '查看配置'}><Button type="text" size="small" aria-label={`${editable ? '修改' : '查看'}${engine.name}`} icon={editable ? <EditOutlined /> : <EyeOutlined />} onClick={() => setDrawerEngine(engine)} /></Tooltip>
          </div>
          <Dropdown menu={{ items }}><Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" size="small" icon={<MoreOutlined />} aria-label={`${engine.name}的更多操作`} /></Tooltip></Dropdown>
        </div>;
      },
    },
  ];
  const applyDirect = (values: ComputeEngineFilters) => {
    setFilters({
      keyword: values.keyword,
      registrationState: values.registrationState,
      expectedBackendType: advancedFilters.expectedBackendType,
      healthState: advancedFilters.healthState,
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
  const clearAdvanced = () => advancedFilterForm.resetFields();
  const reset = () => {
    filterForm.resetFields();
    advancedFilterForm.resetFields();
    setAdvancedFilters({});
    setAdvancedFilterOpen(false);
    setFilters({});
    setPage(0);
  };

  return <>
    {messageContext}{modalContext}
    <section className="management-workbench">
      <div className="management-filter-strip">
        <Form<ComputeEngineFilters> autoComplete="off" form={filterForm} layout="inline" className="management-filter-form" onFinish={applyDirect}>
          <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索计算引擎名称" /></Form.Item>
          <Form.Item name="registrationState"><Select allowClear placeholder="全部注册状态" style={{ width: 130 }} options={Object.entries(computeEngineRegistrationStateLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <ManagementMoreFilters
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
            onCancel={() => setAdvancedFilterOpen(false)}
            onConfirm={confirmAdvanced}
          >
            <Form<ComputeEngineFilters> form={advancedFilterForm} layout="vertical" autoComplete="off">
              <Form.Item name="expectedBackendType" label="后端"><Select allowClear placeholder="全部" className="advanced-filter-select" options={Object.entries(computeBackendTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
              <Form.Item name="healthState" label="健康状态"><Select allowClear placeholder="全部" className="advanced-filter-select" options={Object.entries(computeEngineHealthStateLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
            </Form>
          </ManagementMoreFilters>
        </Form>
        <ManagementFilterActions form={filterForm} appliedFilters={filters} additionalActive={advancedFilterCount > 0} loading={enginesQuery.isFetching} onReset={reset} />
      </div>
      <div className="management-results-surface">
        <div className="management-result-toolbar">
        <div className="management-result-title">计算引擎 <span className="management-result-count">共 {enginesQuery.data?.totalElements ?? 0} 项</span></div>
        <Space size={4} className="management-result-actions">
          <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新计算引擎列表" onClick={() => void enginesQuery.refetch()} /></Tooltip>
          {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setDrawerEngine(null)}>新建</Button>}
        </Space>
        </div>
        <Table<ComputeEngine>
        size="small" className="management-table" rowKey="id" columns={columns}
        dataSource={enginesQuery.data?.content ?? []} loading={enginesQuery.isFetching}
        scroll={{ y: '100%' }}
        pagination={{ current: page + 1, pageSize: size, total: enginesQuery.data?.totalElements ?? 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 项`, position: ['bottomRight'], hideOnSinglePage: false }}
        onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? 20); }}
        />
      </div>
    </section>
    <ComputeEngineDrawer
      open={drawerEngine !== undefined}
      engine={drawerEngine ?? null}
      canUpdate={canUpdate}
      canManage={canManage}
      onClose={() => setDrawerEngine(undefined)}
    />
    <Modal
      title="离线解除绑定"
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
