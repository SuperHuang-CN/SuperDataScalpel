import {
  DeleteOutlined,
  KeyOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { api, post } from '../api';
import type { ApiKey, Consumer, PageResponse, Service, Subscription } from '../model';

interface ConsumerForm {
  code: string;
  name: string;
  enabled: boolean;
  description?: string;
  source?: string;
  externalId?: string;
}

export const ConsumersPage = () => {
  const queryClient = useQueryClient();
  const [selectedConsumerId, setSelectedConsumerId] = useState<string>();
  const [consumerDrawerOpen, setConsumerDrawerOpen] = useState(false);
  const [keyDrawerOpen, setKeyDrawerOpen] = useState(false);
  const [subscriptionDrawerOpen, setSubscriptionDrawerOpen] = useState(false);
  const [secret, setSecret] = useState<string>();
  const [consumerForm] = Form.useForm<ConsumerForm>();
  const [keyForm] = Form.useForm<{ name: string }>();
  const [subscriptionForm] = Form.useForm<{ serviceId: string }>();

  const consumersQuery = useQuery({
    queryKey: ['consumers'],
    queryFn: () => api<PageResponse<Consumer>>('/consumers?page=0&size=200'),
  });
  const servicesQuery = useQuery({
    queryKey: ['services'],
    queryFn: () => api<PageResponse<Service>>('/services?page=0&size=200'),
  });
  const keysQuery = useQuery({
    queryKey: ['api-keys', selectedConsumerId],
    queryFn: () => api<ApiKey[]>(`/consumers/${selectedConsumerId}/api-keys`),
    enabled: !!selectedConsumerId,
  });
  const subscriptionsQuery = useQuery({
    queryKey: ['subscriptions', selectedConsumerId],
    queryFn: () => api<PageResponse<Subscription>>(`/subscriptions?page=0&size=200&consumerId=${selectedConsumerId}`),
    enabled: !!selectedConsumerId,
  });
  const selectedConsumer = useMemo(
    () => consumersQuery.data?.content.find((item) => item.id === selectedConsumerId),
    [selectedConsumerId, consumersQuery.data],
  );
  const services = servicesQuery.data?.content ?? [];

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['consumers'] });
    queryClient.invalidateQueries({ queryKey: ['api-keys'] });
    queryClient.invalidateQueries({ queryKey: ['subscriptions'] });
    queryClient.invalidateQueries({ queryKey: ['runtime'] });
  };
  const command = useMutation({
    mutationFn: ({ path }: { path: string }) => post(path),
    onSuccess: () => { message.success('操作成功'); refresh(); },
    onError: (error: Error) => message.error(error.message),
  });
  const createConsumer = useMutation({
    mutationFn: (values: ConsumerForm) => post<Consumer>('/consumers', values),
    onSuccess: (saved) => {
      message.success('Consumer 已创建');
      setConsumerDrawerOpen(false);
      setSelectedConsumerId(saved.id);
      refresh();
    },
    onError: (error: Error) => message.error(error.message),
  });
  const createKey = useMutation({
    mutationFn: (values: { name: string }) => post<ApiKey>(`/consumers/${selectedConsumerId}/api-keys`, { ...values, source: 'MANUAL' }),
    onSuccess: (created) => {
      setKeyDrawerOpen(false);
      setSecret(created.secret);
      refresh();
    },
    onError: (error: Error) => message.error(error.message),
  });
  const rotateKey = useMutation({
    mutationFn: (key: ApiKey) => post<ApiKey>(`/consumers/${key.consumerId}/api-keys/${key.id}/actions/rotate`),
    onSuccess: (rotated) => {
      setSecret(rotated.secret);
      refresh();
    },
    onError: (error: Error) => message.error(error.message),
  });
  const grant = useMutation({
    mutationFn: ({ serviceId }: { serviceId: string }) => post<Subscription>('/subscriptions', {
      consumerId: selectedConsumerId,
      serviceId,
      source: 'MANUAL',
    }),
    onSuccess: () => {
      message.success('订阅已授权');
      setSubscriptionDrawerOpen(false);
      refresh();
    },
    onError: (error: Error) => message.error(error.message),
  });

  return (
    <div className="split-panel">
      <section className="panel">
        <div className="toolbar">
          <Space><strong>Consumer</strong><Tag>{consumersQuery.data?.totalElements ?? 0}</Tag></Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => {
            consumerForm.setFieldsValue({ enabled: true, source: 'MANUAL' } as ConsumerForm);
            setConsumerDrawerOpen(true);
          }}>新建 Consumer</Button>
        </div>
        <Table
          rowKey="id"
          size="small"
          loading={consumersQuery.isLoading}
          dataSource={consumersQuery.data?.content ?? []}
          rowSelection={{
            type: 'radio',
            selectedRowKeys: selectedConsumerId ? [selectedConsumerId] : [],
            onChange: (keys) => setSelectedConsumerId(keys[0] as string),
          }}
          pagination={{ pageSize: 20, showSizeChanger: false }}
          scroll={{ y: 'calc(100vh - 210px)' }}
          columns={[
            { title: 'Code', dataIndex: 'code', width: 170, ellipsis: true },
            { title: '名称', dataIndex: 'name', ellipsis: true },
            { title: '状态', dataIndex: 'enabled', width: 80, render: (enabled: boolean) => <Tag color={enabled ? 'success' : 'default'}>{enabled ? '启用' : '停用'}</Tag> },
            {
              title: '操作', width: 100,
              render: (_: unknown, record: Consumer) => (
                <Space size={2}>
                  <Tooltip title={record.enabled ? '停用' : '启用'}>
                    <Button type="text" icon={record.enabled ? <PauseCircleOutlined /> : <PlayCircleOutlined />} onClick={() => command.mutate({ path: `/consumers/${record.id}/actions/${record.enabled ? 'disable' : 'enable'}` })} />
                  </Tooltip>
                  <Popconfirm title={`删除 Consumer ${record.name}？`} description="需要先停用；其 Key 和订阅会同时删除。" onConfirm={() => command.mutate({ path: `/consumers/${record.id}/actions/delete` })}>
                    <Tooltip title="删除"><Button danger type="text" icon={<DeleteOutlined />} /></Tooltip>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </section>

      <section className="panel">
        <div className="toolbar">
          <Space><strong>访问配置</strong><span>{selectedConsumer?.name ?? '请选择 Consumer'}</span></Space>
        </div>
        <Tabs
          items={[
            {
              key: 'keys',
              label: 'API Key',
              children: (
                <>
                  <div className="toolbar"><span>明文只在创建或轮换后显示一次</span><Button type="primary" icon={<KeyOutlined />} disabled={!selectedConsumerId} onClick={() => { keyForm.resetFields(); setKeyDrawerOpen(true); }}>创建 Key</Button></div>
                  <Table
                    rowKey="id"
                    size="small"
                    loading={keysQuery.isLoading}
                    dataSource={keysQuery.data ?? []}
                    pagination={false}
                    columns={[
                      { title: '名称', dataIndex: 'name' },
                      { title: '凭据提示', render: (_: unknown, key: ApiKey) => <Typography.Text code>{key.prefix}…{key.lastFour}</Typography.Text> },
                      { title: '状态', dataIndex: 'status', width: 90, render: (status: string) => <Tag color={status === 'ACTIVE' ? 'success' : 'default'}>{status}</Tag> },
                      {
                        title: '操作', width: 130,
                        render: (_: unknown, key: ApiKey) => (
                          <Space size={2}>
                            <Popconfirm title={`轮换 ${key.name}？`} description="旧 Key 将在配置传播后失效。" onConfirm={() => rotateKey.mutate(key)}>
                              <Tooltip title="轮换"><Button type="text" icon={<ReloadOutlined />} /></Tooltip>
                            </Popconfirm>
                            {key.status === 'ACTIVE' && <Popconfirm title={`撤销 ${key.name}？`} onConfirm={() => command.mutate({ path: `/consumers/${key.consumerId}/api-keys/${key.id}/actions/revoke` })}>
                              <Tooltip title="撤销"><Button danger type="text" icon={<PauseCircleOutlined />} /></Tooltip>
                            </Popconfirm>}
                            <Popconfirm title={`永久删除 ${key.name}？`} onConfirm={() => command.mutate({ path: `/consumers/${key.consumerId}/api-keys/${key.id}/actions/delete` })}>
                              <Tooltip title="删除"><Button danger type="text" icon={<DeleteOutlined />} /></Tooltip>
                            </Popconfirm>
                          </Space>
                        ),
                      },
                    ]}
                  />
                </>
              ),
            },
            {
              key: 'subscriptions',
              label: '服务订阅',
              children: (
                <>
                  <div className="toolbar"><span>授权受保护服务</span><Button type="primary" icon={<PlusOutlined />} disabled={!selectedConsumerId} onClick={() => { subscriptionForm.resetFields(); setSubscriptionDrawerOpen(true); }}>授权订阅</Button></div>
                  <Table
                    rowKey="id"
                    size="small"
                    loading={subscriptionsQuery.isLoading}
                    dataSource={subscriptionsQuery.data?.content ?? []}
                    pagination={false}
                    columns={[
                      { title: '服务', dataIndex: 'serviceId', render: (id: string) => services.find((item) => item.id === id)?.name ?? id },
                      { title: '状态', dataIndex: 'status', width: 100, render: (status: string) => <Tag color={status === 'ACTIVE' ? 'success' : 'default'}>{status}</Tag> },
                      {
                        title: '操作', width: 80,
                        render: (_: unknown, subscription: Subscription) => subscription.status === 'ACTIVE' ? (
                          <Popconfirm title="撤回此服务订阅？" onConfirm={() => command.mutate({ path: `/subscriptions/${subscription.id}/actions/revoke` })}>
                            <Button danger type="text">撤回</Button>
                          </Popconfirm>
                        ) : '—',
                      },
                    ]}
                  />
                </>
              ),
            },
          ]}
        />
      </section>

      <Drawer title="新建 Consumer" width={520} open={consumerDrawerOpen} onClose={() => setConsumerDrawerOpen(false)} destroyOnHidden extra={<Button type="primary" loading={createConsumer.isPending} onClick={() => consumerForm.submit()}>保存</Button>}>
        <Form form={consumerForm} layout="vertical" autoComplete="off" onFinish={(values) => createConsumer.mutate(values)}>
          <Form.Item label="Code" name="code" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="名称" name="name" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item label="说明" name="description"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item label="来源" name="source"><Input /></Form.Item>
          <Form.Item label="外部引用" name="externalId"><Input /></Form.Item>
          <Form.Item label="启用" name="enabled" valuePropName="checked"><Switch /></Form.Item>
        </Form>
      </Drawer>
      <Drawer title="创建 API Key" width={440} open={keyDrawerOpen} onClose={() => setKeyDrawerOpen(false)} destroyOnHidden extra={<Button type="primary" loading={createKey.isPending} onClick={() => keyForm.submit()}>创建</Button>}>
        <Form form={keyForm} layout="vertical" autoComplete="off" onFinish={(values) => createKey.mutate(values)}>
          <Form.Item label="Key 名称" name="name" rules={[{ required: true }]}><Input name="gateway-api-key-name" autoComplete="off" /></Form.Item>
        </Form>
      </Drawer>
      <Drawer title="授权服务订阅" width={480} open={subscriptionDrawerOpen} onClose={() => setSubscriptionDrawerOpen(false)} destroyOnHidden extra={<Button type="primary" loading={grant.isPending} onClick={() => subscriptionForm.submit()}>授权</Button>}>
        <Form form={subscriptionForm} layout="vertical" onFinish={(values) => grant.mutate(values)}>
          <Form.Item label="服务" name="serviceId" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={services.filter((service) => service.accessMode === 'SUBSCRIPTION_REQUIRED').map((service) => ({ value: service.id, label: `${service.name} (${service.code})` }))} />
          </Form.Item>
        </Form>
      </Drawer>
      <Modal open={!!secret} title="请立即保存 API Key" okText="我已保存" cancelButtonProps={{ style: { display: 'none' } }} closable={false} maskClosable={false} onOk={() => setSecret(undefined)}>
        <Typography.Paragraph type="warning">该明文关闭后无法再次查看。</Typography.Paragraph>
        <Typography.Text className="secret-value" copyable>{secret}</Typography.Text>
      </Modal>
    </div>
  );
};
