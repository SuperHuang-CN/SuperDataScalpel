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
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Alert, Button, Card, Dropdown, Form, Input, Modal, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
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
  computeEngineHealthStateColors,
  computeEngineHealthStateLabels,
  computeEngineRegistrationStateColors,
  computeEngineRegistrationStateLabels,
  type ComputeBackendType,
  type ComputeEngine,
  type ComputeEngineFilters,
  type ComputeEngineHealthState,
  type ComputeEngineRegistrationState,
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

const formatDateTime = (value: string | null) => value ? new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'short', timeStyle: 'medium', hour12: false,
}).format(new Date(value)) : '—';

export const ComputeEngineManagementPanel = ({ canCreate, canUpdate, canDelete, canTest, canManage }: ComputeEngineManagementPanelProps) => {
  const [filterForm] = Form.useForm<ComputeEngineFilters>();
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
  const testMutation = useTestComputeEngine();
  const commandMutation = useComputeEngineCommand();
  const deactivateMutation = useDeactivateComputeEngine();
  const detachMutation = useDetachComputeEngine();
  const deleteMutation = useDeleteComputeEngine();

  const showError = (error: unknown, fallback: string) => messageApi.error(error instanceof ApiError ? error.message : fallback);

  const test = async (engine: ComputeEngine) => {
    try {
      const result = await testMutation.mutateAsync(engine.id);
      messageApi.success(`${engine.name} 连接正常：${computeBackendTypeLabels[result.backendType]} / 协议 v${result.protocolVersion}`);
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
    { title: '名称', dataIndex: 'name', width: 180, ellipsis: true },
    { title: '计算后端', dataIndex: 'expectedBackendType', width: 145, render: (value: ComputeBackendType) => computeBackendTypeLabels[value] },
    { title: '注册状态', dataIndex: 'registrationState', width: 105, render: (value: ComputeEngineRegistrationState) => <Tag color={computeEngineRegistrationStateColors[value]}>{computeEngineRegistrationStateLabels[value]}</Tag> },
    { title: '健康', dataIndex: 'healthState', width: 90, render: (value: ComputeEngineHealthState) => <Tag color={computeEngineHealthStateColors[value]}>{computeEngineHealthStateLabels[value]}</Tag> },
    { title: 'Dispatcher 地址', dataIndex: 'dispatcherBaseUrl', width: 260, ellipsis: true },
    { title: '命令 Topic', dataIndex: 'commandTopic', width: 245, ellipsis: true },
    { title: '实例', dataIndex: 'dispatcherInstanceId', width: 160, ellipsis: true, render: (value: string | null) => value ?? '—' },
    { title: '配置版本', dataIndex: 'configRevision', width: 90, render: (value: number) => `r${value}` },
    { title: '最近检查', dataIndex: 'lastCheckAt', width: 170, render: formatDateTime },
    {
      title: '操作', key: 'actions', width: 135, fixed: 'right',
      render: (_, engine) => {
        const deletable = !['ACTIVE', 'DRAINING', 'REGISTERING'].includes(engine.registrationState);
        const reconfigurable = ['ACTIVE', 'DRAINING'].includes(engine.registrationState);
        const editable = engine.registrationState !== 'REGISTERING'
          && canUpdate
          && (!reconfigurable || canManage);
        const items: NonNullable<MenuProps['items']> = [];
        if (canManage && ['CREATED', 'INACTIVE', 'DETACHED', 'ERROR'].includes(engine.registrationState)) items.push({ key: 'register', icon: <SendOutlined />, label: '注册并激活', onClick: () => register(engine) });
        if (canManage && engine.registrationState === 'ACTIVE') items.push({ key: 'drain', icon: <PauseCircleOutlined />, label: '开始排空', onClick: () => drain(engine) });
        if (canManage && ['ACTIVE', 'DRAINING', 'ERROR'].includes(engine.registrationState)) items.push({ key: 'deactivate', icon: <StopOutlined />, label: '安全反注册', onClick: () => deactivate(engine, false) });
        if (canManage && ['ACTIVE', 'DRAINING', 'ERROR'].includes(engine.registrationState)) items.push({ key: 'force-deactivate', danger: true, icon: <StopOutlined />, label: '强制反注册并取消任务', onClick: () => deactivate(engine, true) });
        if (canManage && engine.healthState === 'DOWN' && ['ACTIVE', 'DRAINING', 'ERROR'].includes(engine.registrationState)) items.push({
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
        return <Space size={0}>
          {canTest && <Tooltip title="测试连接"><Button type="text" size="small" aria-label={`测试${engine.name}`} icon={<ApiOutlined />} loading={testMutation.isPending && testMutation.variables === engine.id} onClick={() => void test(engine)} /></Tooltip>}
          <Tooltip title={editable ? '修改配置' : '查看配置'}>
            <Button
              type="text"
              size="small"
              aria-label={`${editable ? '修改' : '查看'}${engine.name}`}
              icon={editable ? <EditOutlined /> : <EyeOutlined />}
              onClick={() => setDrawerEngine(engine)}
            />
          </Tooltip>
          {items.length > 0 && <Dropdown menu={{ items }}><Button type="text" size="small" icon={<MoreOutlined />} aria-label={`更多计算引擎操作：${engine.name}`} /></Dropdown>}
        </Space>;
      },
    },
  ];

  return <>
    {messageContext}{modalContext}
    <Card className="management-card">
      <div className="management-toolbar">
        <Form<ComputeEngineFilters> form={filterForm} layout="inline" className="management-filter-form" onFinish={(values) => { setFilters(values); setPage(0); }}>
          <Form.Item name="keyword" label="名称"><Input allowClear placeholder="计算引擎名称" /></Form.Item>
          <Form.Item name="expectedBackendType" label="后端"><Select allowClear placeholder="全部" style={{ width: 145 }} options={Object.entries(computeBackendTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item name="registrationState" label="状态"><Select allowClear placeholder="全部" style={{ width: 120 }} options={Object.entries(computeEngineRegistrationStateLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item name="healthState" label="健康"><Select allowClear placeholder="全部" style={{ width: 105 }} options={Object.entries(computeEngineHealthStateLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
        </Form>
        <Space size={4} className="management-toolbar-actions">
          <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
          <Button onClick={() => { filterForm.resetFields(); setFilters({}); setPage(0); }}>重置</Button>
          <Button icon={<ReloadOutlined />} onClick={() => void enginesQuery.refetch()}>刷新</Button>
          {canCreate && <Button type="primary" icon={<PlusOutlined />} onClick={() => setDrawerEngine(null)}>新建</Button>}
        </Space>
      </div>
      <Table<ComputeEngine>
        size="small" className="management-table" rowKey="id" columns={columns}
        dataSource={enginesQuery.data?.content ?? []} loading={enginesQuery.isFetching}
        scroll={{ x: 1500, y: '100%' }}
        pagination={{ current: page + 1, pageSize: size, total: enginesQuery.data?.totalElements ?? 0, showSizeChanger: true, showTotal: (total) => `共 ${total} 项`, position: ['bottomRight'], hideOnSinglePage: false }}
        onChange={(pagination) => { setPage((pagination.current ?? 1) - 1); setSize(pagination.pageSize ?? 20); }}
      />
    </Card>
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
      <Form<DetachComputeEngineFormValues>
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
