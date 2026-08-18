import {
  AuditOutlined,
  DeleteOutlined,
  KeyOutlined,
  PlusOutlined,
  ReloadOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Alert,
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useApiConsumerCredentials,
  useApiServiceSubscriptions,
  useCreateApiConsumerCredential,
  useCreateApiServiceSubscription,
  useDeleteApiConsumerCredential,
  useRevokeApiServiceSubscription,
  useReconcileApiConsumerCredentialGateway,
  useReconcileApiServiceSubscriptionGateway,
  useRotateApiConsumerCredential,
  useSyncApiServiceSubscription,
} from '../hooks/useApiConsumerAccess';
import { useDataServices } from '../hooks/useDataServices';
import type { ApiConsumer } from '../model/apiConsumer';
import { gatewayProviderLabels } from '../model/apiConsumer';
import {
  apiServiceSubscriptionDesiredStateLabels,
  gatewayCredentialStatusLabels,
  gatewaySubscriptionStatusLabels,
  type ApiConsumerCredential,
  type ApiConsumerCredentialSecretResponse,
  type ApiServiceSubscription,
  type GatewayCredentialStatus,
  type GatewaySubscriptionStatus,
} from '../model/apiConsumerAccess';
import { publishedGatewayBinding } from '../model/dataServiceGateway';
import { GatewayReconciliationTag } from './GatewayReconciliationTag';

interface ApiConsumerAccessDrawerProps {
  open: boolean;
  consumer: ApiConsumer | null;
  canManage: boolean;
  onClose: () => void;
}

interface CredentialFormValues {
  name: string;
}

const credentialStatusColors: Record<GatewayCredentialStatus, string> = {
  SYNC_PENDING: 'processing',
  ACTIVE: 'success',
  SYNC_FAILED: 'error',
  DELETE_PENDING: 'processing',
  DELETE_FAILED: 'error',
};

const subscriptionStatusColors: Record<GatewaySubscriptionStatus, string> = {
  GRANT_PENDING: 'processing',
  GRANTED: 'success',
  GRANT_FAILED: 'error',
  REVOKE_PENDING: 'processing',
  REVOKE_FAILED: 'error',
};

const errorMessage = (error: unknown, fallback: string) => (
  error instanceof ApiError ? error.problem?.detail ?? error.message : fallback
);

const failedCredentialMessage = (response: ApiConsumerCredentialSecretResponse) => (
  response.credential.gatewayBindings.find((binding) => binding.status === 'SYNC_FAILED')?.lastError
  ?? '网关未确认 API Key 同步'
);

export const ApiConsumerAccessDrawer = ({
  open,
  consumer,
  canManage,
  onClose,
}: ApiConsumerAccessDrawerProps) => {
  const [credentialForm] = Form.useForm<CredentialFormValues>();
  const [createCredentialOpen, setCreateCredentialOpen] = useState(false);
  const [oneTimeSecret, setOneTimeSecret] = useState<string | null>(null);
  const [selectedServiceId, setSelectedServiceId] = useState<string>();
  const [messageApi, messageContext] = message.useMessage();
  const consumerId = consumer?.id;
  const subscriptionsRequest = useMemo(() => ({ page: 0, size: 500, sort: '-updatedAt' }), []);
  const protectedServicesRequest = useMemo(() => ({
    page: 0,
    size: 500,
    sort: 'name',
    search: 'accessMode:"SUBSCRIPTION_REQUIRED" AND status:"ENABLED"',
  }), []);
  const credentialsQuery = useApiConsumerCredentials(consumerId, open);
  const subscriptionsQuery = useApiServiceSubscriptions(
    subscriptionsRequest,
    { consumerId },
    open && Boolean(consumerId),
  );
  const servicesQuery = useDataServices(protectedServicesRequest);
  const createCredentialMutation = useCreateApiConsumerCredential();
  const rotateCredentialMutation = useRotateApiConsumerCredential();
  const reconcileCredentialMutation = useReconcileApiConsumerCredentialGateway();
  const deleteCredentialMutation = useDeleteApiConsumerCredential();
  const createSubscriptionMutation = useCreateApiServiceSubscription();
  const syncSubscriptionMutation = useSyncApiServiceSubscription();
  const reconcileSubscriptionMutation = useReconcileApiServiceSubscriptionGateway();
  const revokeSubscriptionMutation = useRevokeApiServiceSubscription();

  const subscriptions = subscriptionsQuery.data?.content ?? [];
  const subscribedServiceIds = new Set(subscriptions.map((subscription) => subscription.dataServiceId));
  const serviceOptions = (servicesQuery.data?.content ?? [])
    .filter((service) => {
      const publication = publishedGatewayBinding(service);
      const consumerSynchronized = consumer?.gatewayBindings.some(
        (binding) => binding.provider === publication?.provider
          && binding.syncStatus === 'SYNCED'
          && binding.syncedRevision === consumer.revision
          && Boolean(binding.externalId),
      );
      return publication && consumerSynchronized && !subscribedServiceIds.has(service.id);
    })
    .map((service) => ({
      value: service.id,
      label: `${service.name}（${service.code}）`,
    }));

  const showSecret = (response: ApiConsumerCredentialSecretResponse, success: string) => {
    if (response.secret) {
      setOneTimeSecret(response.secret);
      messageApi.success(success);
      return;
    }
    messageApi.error(failedCredentialMessage(response));
  };

  const createCredential = async (values: CredentialFormValues) => {
    if (!consumerId) return;
    try {
      const response = await createCredentialMutation.mutateAsync({
        consumerId,
        request: { name: values.name.trim() },
      });
      credentialForm.resetFields();
      setCreateCredentialOpen(false);
      showSecret(response, 'API Key 已创建');
    } catch (error) {
      messageApi.error(errorMessage(error, '创建 API Key 失败'));
    }
  };

  const rotateCredential = async (credential: ApiConsumerCredential) => {
    if (!consumerId) return;
    try {
      const response = await rotateCredentialMutation.mutateAsync({
        consumerId,
        credentialId: credential.id,
      });
      showSecret(response, 'API Key 已轮换');
    } catch (error) {
      messageApi.error(errorMessage(error, '轮换 API Key 失败'));
    }
  };

  const deleteCredential = async (credential: ApiConsumerCredential) => {
    if (!consumerId) return;
    try {
      await deleteCredentialMutation.mutateAsync({ consumerId, credentialId: credential.id });
      messageApi.success('API Key 已删除');
    } catch (error) {
      messageApi.error(errorMessage(error, '删除 API Key 失败'));
    }
  };

  const reconcileCredential = async (credential: ApiConsumerCredential) => {
    if (!consumerId) return;
    try {
      const response = await reconcileCredentialMutation.mutateAsync({
        consumerId,
        credentialId: credential.id,
      });
      const drift = response.gatewayBindings.find(
        (binding) => binding.reconciliationStatus === 'DRIFTED',
      );
      const failure = response.gatewayBindings.find(
        (binding) => binding.reconciliationStatus === 'CHECK_FAILED',
      );
      if (drift) messageApi.warning(drift.reconciliationMessage || 'API Key 检测到网关状态漂移');
      else if (failure) messageApi.error(failure.reconciliationMessage || 'API Key 网关状态检查失败');
      else messageApi.success('API Key 网关状态一致');
    } catch (error) {
      messageApi.error(errorMessage(error, 'API Key 网关状态对账失败'));
    }
  };

  const createSubscription = async () => {
    if (!consumerId || !selectedServiceId) return;
    try {
      const response = await createSubscriptionMutation.mutateAsync({
        consumerId,
        dataServiceId: selectedServiceId,
      });
      setSelectedServiceId(undefined);
      const failure = response.gatewayBindings.find((binding) => binding.status === 'GRANT_FAILED');
      if (failure) messageApi.error(failure.lastError || '订阅已保存，但网关授权失败');
      else messageApi.success('服务订阅已授权');
    } catch (error) {
      messageApi.error(errorMessage(error, '创建服务订阅失败'));
    }
  };

  const syncSubscription = async (subscription: ApiServiceSubscription) => {
    try {
      const response = await syncSubscriptionMutation.mutateAsync(subscription.id);
      const failure = response.gatewayBindings.find((binding) => binding.status === 'GRANT_FAILED');
      if (failure) messageApi.error(failure.lastError || '订阅授权同步失败');
      else messageApi.success('订阅授权已同步');
    } catch (error) {
      messageApi.error(errorMessage(error, '同步订阅授权失败'));
    }
  };

  const revokeSubscription = async (subscription: ApiServiceSubscription) => {
    try {
      await revokeSubscriptionMutation.mutateAsync(subscription.id);
      messageApi.success('服务订阅已撤回');
    } catch (error) {
      messageApi.error(errorMessage(error, '撤回服务订阅失败'));
    }
  };

  const reconcileSubscription = async (subscription: ApiServiceSubscription) => {
    try {
      const response = await reconcileSubscriptionMutation.mutateAsync(subscription.id);
      const drift = response.gatewayBindings.find(
        (binding) => binding.reconciliationStatus === 'DRIFTED',
      );
      const failure = response.gatewayBindings.find(
        (binding) => binding.reconciliationStatus === 'CHECK_FAILED',
      );
      if (drift) messageApi.warning(drift.reconciliationMessage || '订阅检测到网关状态漂移');
      else if (failure) messageApi.error(failure.reconciliationMessage || '订阅网关状态检查失败');
      else messageApi.success('订阅网关状态一致');
    } catch (error) {
      messageApi.error(errorMessage(error, '订阅网关状态对账失败'));
    }
  };

  const credentialColumns: TableProps<ApiConsumerCredential>['columns'] = [
    { title: '用途名称', dataIndex: 'name', width: 150, ellipsis: true },
    { title: '密钥提示', dataIndex: 'secretHint', width: 135, render: (value: string) => <code>{value}</code> },
    { title: '版本', dataIndex: 'revision', width: 70, render: (value: number) => `v${value}` },
    {
      title: '网关状态',
      dataIndex: 'gatewayBindings',
      width: 280,
      render: (bindings: ApiConsumerCredential['gatewayBindings']) => bindings.length ? (
        <Space size={[0, 4]} wrap>
          {bindings.map((binding) => (
            <Space key={binding.id} size={[2, 2]} wrap>
              <Tooltip title={binding.lastError || undefined}>
                <Tag color={credentialStatusColors[binding.status]}>
                  {gatewayProviderLabels[binding.provider]} · {gatewayCredentialStatusLabels[binding.status]}
                </Tag>
              </Tooltip>
              <GatewayReconciliationTag state={binding} />
            </Space>
          ))}
        </Space>
      ) : <Tag>未同步</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 122,
      fixed: 'right',
      render: (_: unknown, credential: ApiConsumerCredential) => canManage ? (
        <Space size={2}>
          <Tooltip title="立即对账网关状态">
            <Button
              type="text"
              size="small"
              icon={<AuditOutlined />}
              aria-label={`对账${credential.name}的网关状态`}
              loading={reconcileCredentialMutation.isPending
                && reconcileCredentialMutation.variables?.credentialId === credential.id}
              onClick={() => void reconcileCredential(credential)}
            />
          </Tooltip>
          <Popconfirm
            title="轮换 API Key"
            description="旧密钥会立即失效，请先确认调用方可以同步更新。"
            okText="轮换"
            cancelText="取消"
            onConfirm={() => rotateCredential(credential)}
          >
            <Tooltip title={credential.gatewayBindings.some((binding) => (
              binding.reconciliationReason === 'REMOTE_MISSING'
                || binding.reconciliationReason === 'SECRET_MISMATCH'
            )) ? '轮换并重新同步（无法恢复原密钥）' : '轮换'}>
              <Button
                type="text"
                size="small"
                icon={<SyncOutlined />}
                aria-label={`轮换${credential.name}`}
                loading={rotateCredentialMutation.isPending
                  && rotateCredentialMutation.variables?.credentialId === credential.id}
              />
            </Tooltip>
          </Popconfirm>
          <Popconfirm
            title="删除 API Key"
            description={`确认删除“${credential.name}”吗？`}
            okText="删除"
            cancelText="取消"
            onConfirm={() => deleteCredential(credential)}
          >
            <Tooltip title="删除">
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                aria-label={`删除${credential.name}`}
                loading={deleteCredentialMutation.isPending
                  && deleteCredentialMutation.variables?.credentialId === credential.id}
              />
            </Tooltip>
          </Popconfirm>
        </Space>
      ) : '—',
    },
  ];

  const subscriptionColumns: TableProps<ApiServiceSubscription>['columns'] = [
    { title: '服务名称', dataIndex: 'dataServiceName', width: 160, ellipsis: true },
    { title: '服务编码', dataIndex: 'dataServiceCode', width: 150, ellipsis: true, render: (value: string) => <code>{value}</code> },
    {
      title: '业务状态',
      dataIndex: 'desiredState',
      width: 100,
      render: (value: ApiServiceSubscription['desiredState']) => (
        <Tag color={value === 'GRANTED' ? 'success' : 'warning'}>
          {apiServiceSubscriptionDesiredStateLabels[value]}
        </Tag>
      ),
    },
    {
      title: '网关授权',
      dataIndex: 'gatewayBindings',
      width: 280,
      render: (bindings: ApiServiceSubscription['gatewayBindings']) => bindings.length ? (
        <Space size={[0, 4]} wrap>
          {bindings.map((binding) => (
            <Space key={binding.id} size={[2, 2]} wrap>
              <Tooltip title={binding.lastError || undefined}>
                <Tag color={subscriptionStatusColors[binding.status]}>
                  {gatewayProviderLabels[binding.provider]} · {gatewaySubscriptionStatusLabels[binding.status]}
                </Tag>
              </Tooltip>
              <GatewayReconciliationTag state={binding} />
            </Space>
          ))}
        </Space>
      ) : <Tag>未授权</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 122,
      fixed: 'right',
      render: (_: unknown, subscription: ApiServiceSubscription) => canManage ? (
        <Space size={2}>
          <Tooltip title="立即对账网关状态">
            <Button
              type="text"
              size="small"
              icon={<AuditOutlined />}
              aria-label={`对账${subscription.dataServiceName}订阅的网关状态`}
              loading={reconcileSubscriptionMutation.isPending
                && reconcileSubscriptionMutation.variables === subscription.id}
              onClick={() => void reconcileSubscription(subscription)}
            />
          </Tooltip>
          {subscription.desiredState === 'GRANTED' && (
            <Tooltip title="同步授权">
              <Button
                type="text"
                size="small"
                icon={<ReloadOutlined />}
                aria-label={`同步${subscription.dataServiceName}订阅`}
                loading={syncSubscriptionMutation.isPending
                  && syncSubscriptionMutation.variables === subscription.id}
                onClick={() => void syncSubscription(subscription)}
              />
            </Tooltip>
          )}
          <Popconfirm
            title="撤回服务订阅"
            description={`确认撤回“${subscription.dataServiceName}”的调用权限吗？`}
            okText="撤回"
            cancelText="取消"
            onConfirm={() => revokeSubscription(subscription)}
          >
            <Tooltip title="撤回">
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                aria-label={`撤回${subscription.dataServiceName}订阅`}
                loading={revokeSubscriptionMutation.isPending
                  && revokeSubscriptionMutation.variables === subscription.id}
              />
            </Tooltip>
          </Popconfirm>
        </Space>
      ) : '—',
    },
  ];

  return (
    <>
      {messageContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        title={consumer ? `${consumer.name} · 访问配置` : '访问配置'}
        open={open}
        width={860}
        onClose={onClose}
        destroyOnHidden
      >
        <Tabs
          items={[
            {
              key: 'credentials',
              label: 'API Key',
              children: (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <Alert
                    type="info"
                    showIcon
                    title="API Key 只负责识别消费者，所有有效密钥共享同一组服务订阅。"
                  />
                  <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                    {canManage && (
                      <Button
                        type="primary"
                        icon={<KeyOutlined />}
                        onClick={() => setCreateCredentialOpen(true)}
                      >
                        新建 API Key
                      </Button>
                    )}
                  </div>
                  <Table<ApiConsumerCredential>
                    size="small"
                    rowKey="id"
                    columns={credentialColumns}
                    dataSource={credentialsQuery.data ?? []}
                    loading={credentialsQuery.isFetching}
                    pagination={false}
                    scroll={{ x: 700 }}
                  />
                </Space>
              ),
            },
            {
              key: 'subscriptions',
              label: '服务订阅',
              children: (
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  {canManage && (
                    <Space.Compact style={{ width: '100%' }}>
                      <Select
                        showSearch
                        allowClear
                        optionFilterProp="label"
                        placeholder="选择已发布的订阅访问服务"
                        value={selectedServiceId}
                        options={serviceOptions}
                        loading={servicesQuery.isFetching}
                        onChange={setSelectedServiceId}
                        style={{ flex: 1 }}
                      />
                      <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        disabled={!selectedServiceId}
                        loading={createSubscriptionMutation.isPending}
                        onClick={() => void createSubscription()}
                      >
                        新增订阅
                      </Button>
                    </Space.Compact>
                  )}
                  <Table<ApiServiceSubscription>
                    size="small"
                    rowKey="id"
                    columns={subscriptionColumns}
                    dataSource={subscriptions}
                    loading={subscriptionsQuery.isFetching}
                    pagination={false}
                    scroll={{ x: 820 }}
                  />
                </Space>
              ),
            },
          ]}
        />
      </Drawer>

      <Modal
        rootClassName="business-overlay business-modal-overlay"
        title="新建 API Key"
        open={createCredentialOpen}
        okText="创建"
        cancelText="取消"
        confirmLoading={createCredentialMutation.isPending}
        onOk={() => credentialForm.submit()}
        onCancel={() => {
          credentialForm.resetFields();
          setCreateCredentialOpen(false);
        }}
        destroyOnHidden
      >
        <Form<CredentialFormValues> autoComplete="off"
          form={credentialForm}
          layout="vertical"
          onFinish={(values) => void createCredential(values)}
        >
          <Form.Item
            name="name"
            label="用途名称"
            rules={[
              { required: true, whitespace: true, message: '请输入用途名称' },
              { max: 100, message: '用途名称不能超过 100 个字符' },
            ]}
          >
            <Input autoFocus placeholder="如：生产应用、灰度环境" maxLength={100} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        rootClassName="business-overlay business-modal-overlay"
        title="请立即保存 API Key"
        open={Boolean(oneTimeSecret)}
        footer={<Button type="primary" onClick={() => setOneTimeSecret(null)}>我已保存</Button>}
        closable={false}
        maskClosable={false}
        destroyOnHidden
      >
        <Alert
          type="warning"
          showIcon
          title="该密钥只显示一次，关闭后无法再次查看。"
          style={{ marginBottom: 12 }}
        />
        <Typography.Paragraph copyable={{ text: oneTimeSecret ?? '' }}>
          <Typography.Text code>{oneTimeSecret}</Typography.Text>
        </Typography.Paragraph>
      </Modal>
    </>
  );
};
