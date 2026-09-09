import { CompactAlert as Alert, ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { DashboardOutlined, DeleteOutlined, MoreOutlined, PlusOutlined, ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Dropdown, Form, Modal, Progress, Select, Space, Switch, Table, Tag, Tooltip, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ManagementDateTime, ManagementListCell } from '../../../shared/components/ManagementListCells';
import { DetailTableToolbar } from '../../../shared/components/DetailTableToolbar';
import { useDataSources } from '../../datasource';
import {
  useCreateServiceEngineDataSourceRegistration,
  useDeleteServiceEngineDataSourceRegistration,
  useServiceEngineDataSourceRegistrations,
  useSyncServiceEngineDataSourceRegistration,
  useTestServiceEngineDataSourceRegistration,
} from '../hooks/useServiceEngines';
import type {
  ServiceEngine,
  ServiceEngineDataSourceRegistration,
  ServiceEngineDataSourceRegistrationStatus,
} from '../model/serviceEngine';
import { useDataSourcePoolSummaries } from '../hooks/useServiceEngineMonitoring';
import { monitorNumber, monitorTime, poolUnavailableLabels } from '../model/serviceEngineMonitoring';
import { ServiceEnginePoolMonitorDrawer } from './ServiceEnginePoolMonitorDrawer';

interface ServiceEngineDataSourcePanelProps {
  engine: ServiceEngine;
  canUpdate: boolean;
  canTest: boolean;
  canViewDataSources: boolean;
}

interface RegistrationFormValues {
  dataSourceId: string;
}

const statusColors: Record<ServiceEngineDataSourceRegistrationStatus, string> = {
  PENDING: 'processing',
  READY: 'success',
  OUTDATED: 'warning',
  FAILED: 'error',
};

const statusLabels: Record<ServiceEngineDataSourceRegistrationStatus, string> = {
  PENDING: '同步中',
  READY: '已就绪',
  OUTDATED: '待同步',
  FAILED: '失败',
};

export const ServiceEngineDataSourcePanel = ({
  engine,
  canUpdate,
  canTest,
  canViewDataSources,
}: ServiceEngineDataSourcePanelProps) => {
  const [form] = Form.useForm<RegistrationFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const [modal, modalContext] = Modal.useModal();
  const [monitoringRegistration, setMonitoringRegistration] = useState<ServiceEngineDataSourceRegistration | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const supportsMonitoring = engine.type !== 'GEOSERVER';
  const registrationsQuery = useServiceEngineDataSourceRegistrations(
    { search: `engineId:"${engine.id}"`, page: 0, size: 500, sort: '-updatedAt' },
  );
  const dataSourcesQuery = useDataSources(
    { search: 'enabled:"true"', page: 0, size: 500, sort: 'code' },
    canViewDataSources,
  );
  const createMutation = useCreateServiceEngineDataSourceRegistration();
  const syncMutation = useSyncServiceEngineDataSourceRegistration();
  const testMutation = useTestServiceEngineDataSourceRegistration();
  const deleteMutation = useDeleteServiceEngineDataSourceRegistration();
  const poolsQuery = useDataSourcePoolSummaries(engine.id, supportsMonitoring, autoRefresh && !monitoringRegistration);
  const poolsById = new Map(poolsQuery.data?.dataSources.map((entry) => [entry.dataSourceId, entry]));
  const registeredDataSourceIds = useMemo(
    () => new Set((registrationsQuery.data?.content ?? []).map((registration) => registration.dataSourceId)),
    [registrationsQuery.data?.content],
  );
  const availableDataSources = (dataSourcesQuery.data?.content ?? []).filter((dataSource) => (
    dataSource.connectionKind === 'JDBC'
      && dataSource.purposes.includes('STORAGE')
      && (engine.type !== 'GEOSERVER' || dataSource.type === 'POSTGRESQL')
  ));

  const register = async (values: RegistrationFormValues) => {
    try {
      const response = await createMutation.mutateAsync({ engineId: engine.id, dataSourceId: values.dataSourceId });
      if (response.status === 'READY') {
        messageApi.success('数据源已注册并同步到 Engine');
        form.resetFields();
      } else {
        messageApi.error(response.lastError || '数据源注册失败，请查看状态后重试');
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '注册数据源失败');
    }
  };

  const sync = async (registration: ServiceEngineDataSourceRegistration) => {
    try {
      const response = await syncMutation.mutateAsync(registration.id);
      if (response.status === 'READY') messageApi.success('数据源已同步到 Engine');
      else messageApi.error(response.lastError || '数据源同步失败');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '同步数据源失败');
    }
  };

  const test = async (registration: ServiceEngineDataSourceRegistration) => {
    try {
      const result = await testMutation.mutateAsync(registration.id);
      messageApi.success(`${registration.dataSourceName} 连接成功（${result.databaseType}）`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '测试数据源失败');
    }
  };

  const remove = async (registration: ServiceEngineDataSourceRegistration) => {
    try {
      await deleteMutation.mutateAsync(registration.id);
      messageApi.success('已解除数据源注册');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '解除注册失败');
    }
  };

  const confirmRemove = (registration: ServiceEngineDataSourceRegistration) => modal.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '解除数据源注册',
    content: `确认解除“${registration.dataSourceName}”吗？已发布服务使用时不能解除。`,
    okText: '解除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: () => remove(registration),
  });

  const refresh = () => {
    void registrationsQuery.refetch();
    if (supportsMonitoring) void poolsQuery.refetch();
  };

  const poolCell = (registration: ServiceEngineDataSourceRegistration) => {
    const entry = poolsById.get(registration.dataSourceId);
    if (poolsQuery.isError) return <span className="jdbc-pool-cell-hint">监控不可用</span>;
    if (!entry) return <span className="jdbc-pool-cell-hint">{poolsQuery.isFetching ? '读取中…' : '暂无监控快照'}</span>;
    if (entry.status !== 'AVAILABLE' || !entry.pool) {
      return <Tooltip title={entry.status === 'NOT_LOADED' ? '检查数据源同步状态或 API Studio 的加载错误' : '请确认 API Studio 已启用 JDBC 监控'}>
        <span className="jdbc-pool-cell-hint">{entry.status === 'AVAILABLE' ? '暂无监控快照' : poolUnavailableLabels[entry.status]}</span>
      </Tooltip>;
    }
    const pool = entry.pool;
    const risk = (pool.waiting ?? 0) + (pool.longRunningQueryCount ?? 0) + (pool.longHeldConnectionCount ?? 0) > 0;
    const hint = `空闲 ${monitorNumber(pool.idle)} · 等待 ${monitorNumber(pool.waiting)}`
      + (risk ? ` · 长 SQL ${monitorNumber(pool.longRunningQueryCount)} / 长占用 ${monitorNumber(pool.longHeldConnectionCount)}` : '');
    return <div className="jdbc-pool-cell">
      <div className="jdbc-pool-cell-head">
        <Button type="link" size="small" aria-label={`查看${registration.dataSourceName}的 JDBC 监控`} onClick={() => setMonitoringRegistration(registration)}>
          占用 {monitorNumber(pool.active)} / {monitorNumber(pool.maximum)}
        </Button>
        {pool.utilizationPercent != null && <Progress percent={Math.min(100, Math.max(0, pool.utilizationPercent))} showInfo={false} size="small" status={risk ? 'exception' : 'normal'} />}
      </div>
      <Tooltip title={hint}><span className={`jdbc-pool-cell-hint jdbc-monitor-truncate${risk ? ' jdbc-pool-risk' : ''}`}>{hint}</span></Tooltip>
    </div>;
  };

  const columns: TableProps<ServiceEngineDataSourceRegistration>['columns'] = [
    {
      title: '数据源',
      width: 230,
      render: (_: unknown, registration) => (
        <ManagementListCell primary={registration.dataSourceName} secondary={registration.dataSourceCode} />
      ),
    },
    { title: '数据库', dataIndex: 'databaseType', width: 100, render: (value: string | null) => value ?? '—' },
    {
      title: '同步状态 / 错误',
      width: 190,
      render: (_: unknown, registration) => (
        <ManagementListCell
          primary={<Tag color={statusColors[registration.status]}>{statusLabels[registration.status]}</Tag>}
          secondary={registration.lastError ? <Tooltip title={registration.lastError}><span>{registration.lastError}</span></Tooltip> : '—'}
        />
      ),
    },
    ...(supportsMonitoring ? [{ title: 'JDBC 连接池', key: 'pool', width: 245, render: (_: unknown, registration: ServiceEngineDataSourceRegistration) => poolCell(registration) }] : []),
    { title: '最近同步', dataIndex: 'synchronizedAt', width: 155, render: (value: string | null) => <ManagementDateTime value={value} /> },
    {
      title: '操作',
      key: 'actions',
      width: 112,
      render: (_: unknown, registration) => (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            {supportsMonitoring && <Tooltip title="JDBC 监控"><Button type="text" size="small" aria-label={`监控${registration.dataSourceName}`} icon={<DashboardOutlined />} onClick={() => setMonitoringRegistration(registration)} /></Tooltip>}
            {canUpdate && <Tooltip title="同步"><Button type="text" size="small" aria-label={`同步${registration.dataSourceName}`} icon={<SyncOutlined />} loading={syncMutation.isPending && syncMutation.variables === registration.id} disabled={!engine.enabled} onClick={() => void sync(registration)} /></Tooltip>}
          </div>
          {(supportsMonitoring || canUpdate || canTest) && <Dropdown trigger={['click']} menu={{ items: [
            ...(supportsMonitoring ? [{ key: 'monitor', label: 'JDBC 监控', icon: <DashboardOutlined /> }] : []),
            ...(canUpdate ? [{ key: 'sync', label: '同步', icon: <SyncOutlined />, disabled: !engine.enabled || (syncMutation.isPending && syncMutation.variables === registration.id) }] : []),
            ...(canTest ? [{ key: 'test', label: '测试连接', icon: <ReloadOutlined />, disabled: testMutation.isPending && testMutation.variables === registration.id }] : []),
            ...(canUpdate ? [{ key: 'delete', label: '解除注册', icon: <DeleteOutlined />, danger: true, disabled: deleteMutation.isPending && deleteMutation.variables === registration.id }] : []),
          ], onClick: ({ key }) => {
            if (key === 'monitor') setMonitoringRegistration(registration);
            if (key === 'sync') void sync(registration);
            if (key === 'test') void test(registration);
            if (key === 'delete') confirmRemove(registration);
          } }}>
            <Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" size="small" aria-label={`${registration.dataSourceName}的更多操作`} icon={<MoreOutlined />} loading={(testMutation.isPending && testMutation.variables === registration.id) || (deleteMutation.isPending && deleteMutation.variables === registration.id)} /></Tooltip>
          </Dropdown>}
        </div>
      ),
    },
  ];

  return (
    <section className="service-engine-tab-panel service-engine-data-source-panel">
      {messageContext}
      {modalContext}
      {monitoringRegistration && supportsMonitoring && <ServiceEnginePoolMonitorDrawer key={monitoringRegistration.id} registration={monitoringRegistration} onClose={() => setMonitoringRegistration(null)} />}
      {!canViewDataSources && <Alert type="warning" showIcon message="没有数据源查看权限，不能新增 Engine 数据源注册。" />}
      {!engine.enabled && <Alert type="warning" showIcon message="当前 Engine 已停用，不能新增或同步数据源。" />}
      <Form<RegistrationFormValues> autoComplete="off" form={form} layout="inline" onFinish={(values) => void register(values)} className="management-filter-form service-engine-data-source-create-form">
        <Form.Item name="dataSourceId" label="注册数据源" rules={[{ required: true, message: '请选择 JDBC 数据存储' }]}>
          <Select
            showSearch
            optionFilterProp="label"
            loading={dataSourcesQuery.isFetching}
            disabled={!canUpdate || !canViewDataSources || !engine.enabled}
            placeholder={engine.type === 'GEOSERVER' ? '选择 PostgreSQL/PostGIS 数据存储' : '选择已启用的 JDBC 数据存储'}
            className="service-engine-data-source-select"
            options={availableDataSources.map((dataSource) => ({
              value: dataSource.id,
              label: `${dataSource.name}（${dataSource.code} / ${dataSource.type}）`,
              disabled: registeredDataSourceIds.has(dataSource.id),
            }))}
          />
        </Form.Item>
        {canUpdate && <Button type="primary" icon={<PlusOutlined />} loading={createMutation.isPending} disabled={!canViewDataSources || !engine.enabled} onClick={() => form.submit()}>注册并同步</Button>}
      </Form>
      <div className="management-results-surface service-engine-tab-results">
        <DetailTableToolbar title="已注册数据源" total={registrationsQuery.data?.totalElements ?? 0} showPagination={false}
          onRefresh={refresh} refreshing={registrationsQuery.isFetching || (supportsMonitoring && poolsQuery.isFetching)}
          extra={supportsMonitoring && <Space wrap size={8}>
            <Tooltip title={`监控采样：${monitorTime(poolsQuery.data?.capturedAt)}`}><span className="jdbc-pool-cell-hint" tabIndex={0}>JDBC 监控</span></Tooltip>
            <ContextHelp ariaLabel="连接池监控说明" content="指标来自 API Studio 当前运行时连接池，与数据源同步状态独立。点击占用数量或更多菜单查看 SQL 和连接详情。自动刷新仅在当前页面可见时运行。" />
            <span className="jdbc-pool-cell-hint">每 5 秒刷新</span><Switch size="small" checked={autoRefresh} onChange={setAutoRefresh} aria-label="自动刷新数据源连接池摘要" />
          </Space>} />
        {registrationsQuery.isError && <InlineFeedback className="jdbc-monitor-feedback" tone="error" label="数据源注册列表加载失败" detail={registrationsQuery.error.message} action={<Button type="link" size="small" onClick={() => void registrationsQuery.refetch()}>重试</Button>} />}
        {dataSourcesQuery.isError && canViewDataSources && <InlineFeedback className="jdbc-monitor-feedback" tone="error" label="候选数据源加载失败" detail={dataSourcesQuery.error.message} action={<Button type="link" size="small" onClick={() => void dataSourcesQuery.refetch()}>重试</Button>} />}
        {supportsMonitoring && poolsQuery.isError && <InlineFeedback className="jdbc-monitor-feedback" tone="warning" label="JDBC 监控不可用，注册管理不受影响" detail={poolsQuery.error instanceof ApiError ? poolsQuery.error.message : '读取监控失败，请检查网络或引擎状态'} action={<Button type="link" size="small" onClick={() => void poolsQuery.refetch()}>重试</Button>} />}
        {(registrationsQuery.data?.totalElements ?? 0) > 500 && <InlineFeedback className="jdbc-monitor-feedback" tone="warning" label="仅展示最近 500 个注册数据源" />}
        <Table<ServiceEngineDataSourceRegistration>
          className="management-table"
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={registrationsQuery.data?.content ?? []}
          loading={registrationsQuery.isFetching}
          scroll={{ x: supportsMonitoring ? 1030 : 790, y: '100%' }}
          pagination={false}
        />
      </div>
    </section>
  );
};
