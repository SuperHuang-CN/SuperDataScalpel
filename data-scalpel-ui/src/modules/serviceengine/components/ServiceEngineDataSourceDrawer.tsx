import { DeleteOutlined, PlusOutlined, ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Alert, Button, Drawer, Form, Popconfirm, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import { useEffect } from 'react';
import { ApiError } from '../../../shared/api/http';
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

interface ServiceEngineDataSourceDrawerProps {
  open: boolean;
  engine: ServiceEngine | null;
  canUpdate: boolean;
  canTest: boolean;
  canViewDataSources: boolean;
  onClose: () => void;
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

const formatDateTime = (value: string | null) => value ? new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium', timeStyle: 'medium', hour12: false,
}).format(new Date(value)) : '—';

export const ServiceEngineDataSourceDrawer = ({
  open,
  engine,
  canUpdate,
  canTest,
  canViewDataSources,
  onClose,
}: ServiceEngineDataSourceDrawerProps) => {
  const [form] = Form.useForm<RegistrationFormValues>();
  const [messageApi, messageContext] = message.useMessage();
  const registrationsQuery = useServiceEngineDataSourceRegistrations(
    { search: engine ? `engineId:"${engine.id}"` : undefined, page: 0, size: 500, sort: '-updatedAt' },
    open && Boolean(engine),
  );
  const dataSourcesQuery = useDataSources({ search: 'enabled:"true"', page: 0, size: 500, sort: 'code' });
  const createMutation = useCreateServiceEngineDataSourceRegistration();
  const syncMutation = useSyncServiceEngineDataSourceRegistration();
  const testMutation = useTestServiceEngineDataSourceRegistration();
  const deleteMutation = useDeleteServiceEngineDataSourceRegistration();

  useEffect(() => {
    if (open) form.resetFields();
  }, [form, open]);

  const availableDataSources = (dataSourcesQuery.data?.content ?? []).filter((dataSource) => (
    dataSource.connectionKind === 'JDBC' && dataSource.purposes.includes('STORAGE')
  ));
  const registeredDataSourceIds = new Set((registrationsQuery.data?.content ?? []).map((registration) => registration.dataSourceId));

  const register = async (values: RegistrationFormValues) => {
    if (!engine) return;
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
      if (response.status === 'READY') {
        messageApi.success('数据源已同步到 Engine');
      } else {
        messageApi.error(response.lastError || '数据源同步失败');
      }
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

  const columns: TableProps<ServiceEngineDataSourceRegistration>['columns'] = [
    { title: '数据源', key: 'dataSource', width: 230, render: (_: unknown, item) => `${item.dataSourceName}（${item.dataSourceCode}）` },
    { title: '数据库', dataIndex: 'databaseType', width: 116, render: (value: string | null) => value ?? '—' },
    { title: '状态', dataIndex: 'status', width: 104, render: (value: ServiceEngineDataSourceRegistrationStatus) => <Tag color={statusColors[value]}>{statusLabels[value]}</Tag> },
    { title: '版本', dataIndex: 'revision', width: 72, align: 'right' },
    { title: '最近同步', dataIndex: 'synchronizedAt', width: 180, render: formatDateTime },
    { title: '错误信息', dataIndex: 'lastError', width: 230, ellipsis: true, render: (value: string | null) => value ?? '—' },
    {
      title: '操作', key: 'actions', width: 118, fixed: 'right',
      render: (_: unknown, registration) => (
        <Space size={2}>
          {canUpdate && <Tooltip title="同步"><Button type="text" size="small" aria-label={`同步${registration.dataSourceName}`} icon={<SyncOutlined />} loading={syncMutation.isPending && syncMutation.variables === registration.id} onClick={() => void sync(registration)} /></Tooltip>}
          {canTest && <Tooltip title="测试连接"><Button type="text" size="small" aria-label={`测试${registration.dataSourceName}`} icon={<ReloadOutlined />} loading={testMutation.isPending && testMutation.variables === registration.id} onClick={() => void test(registration)} /></Tooltip>}
          {canUpdate && <Popconfirm title="解除数据源注册" description={`确认解除“${registration.dataSourceName}”吗？已发布服务使用时不能解除。`} okText="解除" cancelText="取消" onConfirm={() => remove(registration)}><Tooltip title="解除注册"><Button type="text" size="small" danger aria-label={`解除${registration.dataSourceName}注册`} icon={<DeleteOutlined />} /></Tooltip></Popconfirm>}
        </Space>
      ),
    },
  ];

  return (
    <>
      {messageContext}
      <Drawer
        title={engine ? `${engine.name} · 数据源` : 'Engine 数据源'}
        open={open}
        size={960}
        onClose={onClose}
        destroyOnHidden
      >
        {!canViewDataSources && <Alert type="warning" showIcon message="没有数据源查看权限，不能新增 Engine 数据源注册。" />}
        {!engine?.enabled && <Alert className="file-dataset-form-alert" type="warning" showIcon message="当前 Engine 已停用，不能新增或同步数据源。" />}
        <Form<RegistrationFormValues> form={form} layout="inline" onFinish={(values) => void register(values)} className="management-filter-form">
          <Form.Item name="dataSourceId" label="注册数据源" rules={[{ required: true, message: '请选择 JDBC 数据存储' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              loading={dataSourcesQuery.isFetching}
              disabled={!canUpdate || !canViewDataSources || !engine?.enabled}
              placeholder="选择已启用的 JDBC 数据存储"
              style={{ minWidth: 330 }}
              options={availableDataSources.map((dataSource) => ({
                value: dataSource.id,
                label: `${dataSource.name}（${dataSource.code} / ${dataSource.type}）`,
                disabled: registeredDataSourceIds.has(dataSource.id),
              }))}
            />
          </Form.Item>
          {canUpdate && <Button type="primary" icon={<PlusOutlined />} loading={createMutation.isPending} disabled={!canViewDataSources || !engine?.enabled} onClick={() => form.submit()}>注册并同步</Button>}
        </Form>
        <Table<ServiceEngineDataSourceRegistration>
          className="management-table"
          size="small"
          rowKey="id"
          columns={columns}
          dataSource={registrationsQuery.data?.content ?? []}
          loading={registrationsQuery.isFetching}
          scroll={{ x: 1180 }}
          pagination={false}
          style={{ marginTop: 20 }}
        />
      </Drawer>
    </>
  );
};
