import {
  AuditOutlined,
  DeleteOutlined,
  EllipsisOutlined,
  KeyOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SyncOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Drawer, Dropdown, Form, Input, Modal, Select, Space, Table, Tabs, Tag, Tooltip, Typography, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
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
  const [operationError, setOperationError] = useState<string | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const consumerId = consumer?.id;
  const subscriptionsRequest = useMemo(() => ({ page: 0, size: 500, sort: '-updatedAt' }), []);
  const protectedServicesRequest = useMemo(() => ({
    page: 0,
    size: 500,
    sort: 'name',
    search: 'status:"ENABLED"',
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
      return publication?.accessMode === 'SUBSCRIPTION_REQUIRED'
        && consumerSynchronized && !subscribedServiceIds.has(service.id);
    })
    .map((service) => ({
      value: service.id,
      label: `${service.name}（${service.code}）`,
    }));

  const showSecret = (response: ApiConsumerCredentialSecretResponse, success: string) => {
    if (response.secret) {
      setOperationError(null);
      setOneTimeSecret(response.secret);
      messageApi.success(success);
      return;
    }
    const failure = failedCredentialMessage(response);
    setOperationError(failure);
    messageApi.error(failure);
  };

  const createCredential = async (values: CredentialFormValues) => {
    if (!consumerId) return;
    setOperationError(null);
    try {
      const response = await createCredentialMutation.mutateAsync({
        consumerId,
        request: { name: values.name.trim() },
      });
      credentialForm.resetFields();
      setCreateCredentialOpen(false);
      showSecret(response, 'API Key 已创建');
    } catch (error) {
      const failure = errorMessage(error, '创建 API Key 失败');
      setOperationError(failure);
      messageApi.error(failure);
    }
  };

  const rotateCredential = async (credential: ApiConsumerCredential) => {
    if (!consumerId) return;
    setOperationError(null);
    try {
      const response = await rotateCredentialMutation.mutateAsync({
        consumerId,
        credentialId: credential.id,
      });
      showSecret(response, 'API Key 已轮换');
    } catch (error) {
      const failure = errorMessage(error, '轮换 API Key 失败');
      setOperationError(failure);
      messageApi.error(failure);
    }
  };

  const deleteCredential = async (credential: ApiConsumerCredential) => {
    if (!consumerId) return;
    setOperationError(null);
    try {
      await deleteCredentialMutation.mutateAsync({ consumerId, credentialId: credential.id });
      messageApi.success('API Key 已删除');
    } catch (error) {
      const failure = errorMessage(error, '删除 API Key 失败');
      setOperationError(failure);
      messageApi.error(failure);
    }
  };

  const reconcileCredential = async (credential: ApiConsumerCredential) => {
    if (!consumerId) return;
    setOperationError(null);
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
      const failureMessage = errorMessage(error, 'API Key 网关状态对账失败');
      setOperationError(failureMessage);
      messageApi.error(failureMessage);
    }
  };

  const createSubscription = async () => {
    if (!consumerId || !selectedServiceId) return;
    setOperationError(null);
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
      const failureMessage = errorMessage(error, '创建服务订阅失败');
      setOperationError(failureMessage);
      messageApi.error(failureMessage);
    }
  };

  const syncSubscription = async (subscription: ApiServiceSubscription) => {
    setOperationError(null);
    try {
      const response = await syncSubscriptionMutation.mutateAsync(subscription.id);
      const failure = response.gatewayBindings.find((binding) => binding.status === 'GRANT_FAILED');
      if (failure) messageApi.error(failure.lastError || '订阅授权同步失败');
      else messageApi.success('订阅授权已同步');
    } catch (error) {
      const failureMessage = errorMessage(error, '同步订阅授权失败');
      setOperationError(failureMessage);
      messageApi.error(failureMessage);
    }
  };

  const revokeSubscription = async (subscription: ApiServiceSubscription) => {
    setOperationError(null);
    try {
      await revokeSubscriptionMutation.mutateAsync(subscription.id);
      messageApi.success('服务订阅已撤回');
    } catch (error) {
      const failureMessage = errorMessage(error, '撤回服务订阅失败');
      setOperationError(failureMessage);
      messageApi.error(failureMessage);
    }
  };

  const reconcileSubscription = async (subscription: ApiServiceSubscription) => {
    setOperationError(null);
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
      const failureMessage = errorMessage(error, '订阅网关状态对账失败');
      setOperationError(failureMessage);
      messageApi.error(failureMessage);
    }
  };

  const credentialMenu = (credential: ApiConsumerCredential): MenuProps => ({
    items: [
      { key: 'reconcile', icon: <AuditOutlined />, label: '对账网关状态' },
      { key: 'rotate', icon: <SyncOutlined />, label: '轮换 API Key' },
      { type: 'divider' },
      { key: 'delete', icon: <DeleteOutlined />, label: '删除 API Key', danger: true },
    ],
    onClick: ({ key }) => {
      if (key === 'reconcile') void reconcileCredential(credential);
      if (key === 'rotate') {
        modalApi.confirm({
          rootClassName: 'business-overlay business-modal-overlay',
          title: `轮换 API Key“${credential.name}”？`,
          content: '旧密钥会立即失效，请先确认调用方可以同步更新。新密钥同样只显示一次。',
          okText: '轮换',
          cancelText: '取消',
          onOk: () => rotateCredential(credential),
        });
      }
      if (key === 'delete') {
        modalApi.confirm({
          rootClassName: 'business-overlay business-modal-overlay',
          title: `删除 API Key“${credential.name}”？`,
          content: '删除后使用该密钥的调用方将立即无法通过身份识别。',
          okText: '删除',
          okButtonProps: { danger: true },
          cancelText: '取消',
          onOk: () => deleteCredential(credential),
        });
      }
    },
  });

  const subscriptionMenu = (subscription: ApiServiceSubscription): MenuProps => ({
    items: [
      { key: 'reconcile', icon: <AuditOutlined />, label: '对账网关状态' },
      ...(subscription.desiredState === 'GRANTED'
        ? [{ key: 'sync', icon: <ReloadOutlined />, label: '同步授权' }]
        : []),
      { type: 'divider' },
      { key: 'revoke', icon: <DeleteOutlined />, label: '撤回订阅', danger: true },
    ],
    onClick: ({ key }) => {
      if (key === 'reconcile') void reconcileSubscription(subscription);
      if (key === 'sync') void syncSubscription(subscription);
      if (key === 'revoke') {
        modalApi.confirm({
          rootClassName: 'business-overlay business-modal-overlay',
          title: `撤回服务订阅“${subscription.dataServiceName}”？`,
          content: `确认撤回当前消费者对“${subscription.dataServiceName}”的调用权限吗？`,
          okText: '撤回',
          okButtonProps: { danger: true },
          cancelText: '取消',
          onOk: () => revokeSubscription(subscription),
        });
      }
    },
  });

  const credentialColumns: TableProps<ApiConsumerCredential>['columns'] = [
    {
      title: 'API Key',
      key: 'identity',
      width: 220,
      render: (_value, row) => (
        <span className="api-consumer-access-identity">
          <strong title={row.name}>{row.name}</strong>
          <code title={row.secretHint}>{row.secretHint}</code>
        </span>
      ),
    },
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
      width: 58,
      align: 'center',
      render: (_: unknown, credential: ApiConsumerCredential) => canManage ? (
        <Dropdown menu={credentialMenu(credential)} trigger={['click']}>
          <Tooltip title="管理 API Key">
            <span>
              <Button
                type="text"
                size="small"
                icon={<EllipsisOutlined />}
                aria-label={`管理 API Key ${credential.name}`}
                loading={(reconcileCredentialMutation.isPending
                  && reconcileCredentialMutation.variables?.credentialId === credential.id)
                  || (rotateCredentialMutation.isPending
                    && rotateCredentialMutation.variables?.credentialId === credential.id)
                  || (deleteCredentialMutation.isPending
                    && deleteCredentialMutation.variables?.credentialId === credential.id)}
              />
            </span>
          </Tooltip>
        </Dropdown>
      ) : '—',
    },
  ];

  const subscriptionColumns: TableProps<ApiServiceSubscription>['columns'] = [
    {
      title: '数据服务',
      key: 'service',
      width: 220,
      render: (_value, row) => (
        <span className="api-consumer-access-identity">
          <strong title={row.dataServiceName}>{row.dataServiceName}</strong>
          <code title={row.dataServiceCode}>{row.dataServiceCode}</code>
        </span>
      ),
    },
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
      width: 58,
      align: 'center',
      render: (_: unknown, subscription: ApiServiceSubscription) => canManage ? (
        <Dropdown menu={subscriptionMenu(subscription)} trigger={['click']}>
          <Tooltip title="管理服务订阅">
            <span>
              <Button
                type="text"
                size="small"
                icon={<EllipsisOutlined />}
                aria-label={`管理${subscription.dataServiceName}订阅`}
                loading={(reconcileSubscriptionMutation.isPending
                  && reconcileSubscriptionMutation.variables === subscription.id)
                  || (syncSubscriptionMutation.isPending
                    && syncSubscriptionMutation.variables === subscription.id)
                  || (revokeSubscriptionMutation.isPending
                    && revokeSubscriptionMutation.variables === subscription.id)}
              />
            </span>
          </Tooltip>
        </Dropdown>
      ) : '—',
    },
  ];

  return (
    <>
      {messageContext}{modalContext}
      <Drawer
        rootClassName="business-overlay business-drawer-overlay"
        className="api-consumer-access-drawer"
        title={(
          <div className="api-consumer-access-title">
            <span className="api-consumer-access-title-icon" aria-hidden="true"><UsergroupAddOutlined /></span>
            <span className="api-consumer-access-title-copy">
              <span>消费者访问配置</span>
              <Typography.Text type="secondary">{consumer?.name ?? 'API 消费者'} · 管理身份密钥与服务订阅关系</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="api-consumer-access-header-tag">{consumer ? `v${consumer.revision}` : '—'}</Tag>}
        open={open}
        width={860}
        onClose={() => {
          setSelectedServiceId(undefined);
          setOperationError(null);
          onClose();
        }}
        destroyOnHidden
      >
        <Tabs
          className="api-consumer-access-tabs"
          items={[
            {
              key: 'credentials',
              label: `API Key · ${credentialsQuery.data?.length ?? 0}`,
              children: (
                <div className="api-consumer-access-tab-workspace">
                  <div className="api-consumer-access-toolbar">
                    <span className="api-consumer-access-toolbar-copy">
                      <span>
                        <strong>消费者身份密钥</strong>
                        <ContextHelp ariaLabel="查看 API Key 与服务订阅关系" content="API Key 只负责识别消费者；同一消费者的所有有效密钥共享同一组服务订阅。轮换或删除单个密钥不会改变订阅关系。" presentation="popover" placement="bottomLeft" />
                      </span>
                      <Typography.Text type="secondary">密钥原文只显示一次，网关状态与平台记录需保持一致</Typography.Text>
                    </span>
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
                  {operationError && <InlineFeedback className="api-consumer-access-error" tone="error" label="访问配置操作失败" detail={operationError} />}
                  {credentialsQuery.error && <InlineFeedback className="api-consumer-access-error" tone="error" label="API Key 加载失败" detail={credentialsQuery.error.message} action={<Button size="small" type="link" onClick={() => void credentialsQuery.refetch()}>重试</Button>} />}
                  <Table<ApiConsumerCredential>
                    size="small"
                    className="management-table management-table-comfortable api-consumer-access-table"
                    rowKey="id"
                    columns={credentialColumns}
                    dataSource={credentialsQuery.data ?? []}
                    loading={credentialsQuery.isFetching}
                    pagination={false}
                  />
                </div>
              ),
            },
            {
              key: 'subscriptions',
              label: `服务订阅 · ${subscriptions.length}`,
              children: (
                <div className="api-consumer-access-tab-workspace">
                  <div className="api-consumer-access-toolbar">
                    <span className="api-consumer-access-toolbar-copy">
                      <span>
                        <strong>可调用数据服务</strong>
                        <ContextHelp ariaLabel="查看消费者订阅条件" content="仅显示已使用订阅访问模式发布，且消费者已同步到同一 API 网关的服务。" presentation="popover" placement="bottomLeft" />
                      </span>
                      <Typography.Text type="secondary">订阅授权会同步到服务当前发布的 API 网关</Typography.Text>
                    </span>
                  {canManage && (
                    <Space.Compact className="api-consumer-access-subscription-create">
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
                  </div>
                  {operationError && <InlineFeedback className="api-consumer-access-error" tone="error" label="访问配置操作失败" detail={operationError} />}
                  {subscriptionsQuery.error && <InlineFeedback className="api-consumer-access-error" tone="error" label="服务订阅加载失败" detail={subscriptionsQuery.error.message} action={<Button size="small" type="link" onClick={() => void subscriptionsQuery.refetch()}>重试</Button>} />}
                  <Table<ApiServiceSubscription>
                    size="small"
                    className="management-table management-table-comfortable api-consumer-access-table"
                    rowKey="id"
                    columns={subscriptionColumns}
                    dataSource={subscriptions}
                    loading={subscriptionsQuery.isFetching}
                    pagination={false}
                  />
                </div>
              ),
            },
          ]}
        />
      </Drawer>

      <Modal
        rootClassName="business-overlay business-modal-overlay api-consumer-key-modal"
        title={(
          <div className="api-consumer-key-modal-title">
            <span className="api-consumer-key-modal-title-icon" aria-hidden="true"><KeyOutlined /></span>
            <span className="api-consumer-key-modal-title-copy">
              <span>新建 API Key</span>
              <Typography.Text type="secondary">为当前消费者创建一组新的身份凭据</Typography.Text>
            </span>
          </div>
        )}
        open={createCredentialOpen}
        closable={!createCredentialMutation.isPending}
        maskClosable={!createCredentialMutation.isPending}
        onCancel={() => {
          if (createCredentialMutation.isPending) return;
          credentialForm.resetFields();
          setOperationError(null);
          setCreateCredentialOpen(false);
        }}
        footer={(
          <div className="api-consumer-key-modal-footer">
            {operationError ? (
              <InlineFeedback tone="error" label="API Key 创建失败" detail={operationError} />
            ) : (
              <InlineFeedback tone="info" label={consumer ? `归属 ${consumer.name} · ${consumer.code}` : '等待选择消费者'} />
            )}
            <Space>
              <Button disabled={createCredentialMutation.isPending} onClick={() => { credentialForm.resetFields(); setOperationError(null); setCreateCredentialOpen(false); }}>取消</Button>
              <Button type="primary" loading={createCredentialMutation.isPending} onClick={() => credentialForm.submit()}>创建密钥</Button>
            </Space>
          </div>
        )}
        destroyOnHidden
      >
        <Form<CredentialFormValues> autoComplete="off"
          form={credentialForm}
          layout="vertical"
          className="api-consumer-key-form"
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
            <Input name="api-consumer-key-purpose-name" autoComplete="off" autoFocus placeholder="如：生产应用、灰度环境" maxLength={100} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        rootClassName="business-overlay business-modal-overlay api-consumer-secret-modal"
        title={(
          <div className="api-consumer-key-modal-title">
            <span className="api-consumer-key-modal-title-icon" aria-hidden="true"><SafetyCertificateOutlined /></span>
            <span className="api-consumer-key-modal-title-copy">
              <span>立即保存 API Key</span>
              <Typography.Text type="secondary">密钥原文不会再次出现在平台界面中</Typography.Text>
            </span>
          </div>
        )}
        open={Boolean(oneTimeSecret)}
        footer={(
          <div className="api-consumer-secret-footer">
            <InlineFeedback tone="warning" label="关闭前请确认密钥已经安全保存" />
            <Button type="primary" onClick={() => setOneTimeSecret(null)}>我已安全保存</Button>
          </div>
        )}
        closable={false}
        maskClosable={false}
        destroyOnHidden
      >
        <div className="api-consumer-secret-risk" role="alert">
          <SafetyCertificateOutlined aria-hidden="true" />
          <span>
            <strong>该密钥只显示一次</strong>
            <Typography.Text type="secondary">关闭后无法再次查看，只能通过轮换生成新的密钥。</Typography.Text>
          </span>
        </div>
        <Typography.Paragraph className="api-consumer-secret-value" copyable={{ text: oneTimeSecret ?? '' }}>
          <code>{oneTimeSecret}</code>
        </Typography.Paragraph>
      </Modal>
    </>
  );
};
