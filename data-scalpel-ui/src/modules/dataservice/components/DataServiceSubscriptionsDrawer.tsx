import {
  AuditOutlined,
  DeleteOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Alert,
  Button,
  Drawer,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useApiServiceSubscriptions,
  useCreateApiServiceSubscription,
  useRevokeApiServiceSubscription,
  useReconcileApiServiceSubscriptionGateway,
  useSyncApiServiceSubscription,
} from '../hooks/useApiConsumerAccess';
import { useApiConsumers } from '../hooks/useApiConsumers';
import { gatewayProviderLabels } from '../model/apiConsumer';
import {
  apiServiceSubscriptionDesiredStateLabels,
  gatewaySubscriptionStatusLabels,
  type ApiServiceSubscription,
  type GatewaySubscriptionStatus,
} from '../model/apiConsumerAccess';
import type { DataServiceDetail, DataServiceSummary } from '../model/dataService';
import { publishedGatewayBinding } from '../model/dataServiceGateway';
import { GatewayReconciliationTag } from './GatewayReconciliationTag';

interface DataServiceSubscriptionsDrawerProps {
  open: boolean;
  dataService: DataServiceDetail | DataServiceSummary | null;
  canManage: boolean;
  onClose: () => void;
}

const statusColors: Record<GatewaySubscriptionStatus, string> = {
  GRANT_PENDING: 'processing',
  GRANTED: 'success',
  GRANT_FAILED: 'error',
  REVOKE_PENDING: 'processing',
  REVOKE_FAILED: 'error',
};

const errorMessage = (error: unknown, fallback: string) => (
  error instanceof ApiError ? error.problem?.detail ?? error.message : fallback
);

export const DataServiceSubscriptionsDrawer = ({
  open,
  dataService,
  canManage,
  onClose,
}: DataServiceSubscriptionsDrawerProps) => {
  const [selectedConsumerId, setSelectedConsumerId] = useState<string>();
  const [messageApi, messageContext] = message.useMessage();
  const request = useMemo(() => ({ page: 0, size: 500, sort: '-updatedAt' }), []);
  const consumerRequest = useMemo(() => ({ page: 0, size: 500, sort: 'name' }), []);
  const subscriptionsQuery = useApiServiceSubscriptions(
    request,
    { dataServiceId: dataService?.id },
    open && Boolean(dataService),
  );
  const consumersQuery = useApiConsumers(consumerRequest);
  const createMutation = useCreateApiServiceSubscription();
  const syncMutation = useSyncApiServiceSubscription();
  const reconcileMutation = useReconcileApiServiceSubscriptionGateway();
  const revokeMutation = useRevokeApiServiceSubscription();
  const subscriptions = subscriptionsQuery.data?.content ?? [];
  const publication = dataService ? publishedGatewayBinding(dataService) : undefined;
  const subscriptionEligible = publication?.accessMode === 'SUBSCRIPTION_REQUIRED'
    && dataService?.status === 'ENABLED'
    && Boolean(publication);
  const subscribedConsumerIds = new Set(subscriptions.map((subscription) => subscription.consumerId));
  const consumerOptions = (consumersQuery.data?.content ?? [])
    .filter((consumer) => consumer.gatewayBindings.some(
      (binding) => binding.provider === publication?.provider
        && binding.syncStatus === 'SYNCED'
        && binding.syncedRevision === consumer.revision
        && Boolean(binding.externalId),
    ) && !subscribedConsumerIds.has(consumer.id))
    .map((consumer) => ({
      value: consumer.id,
      label: `${consumer.name}（${consumer.code}）`,
    }));

  const create = async () => {
    if (!dataService || !selectedConsumerId) return;
    try {
      const response = await createMutation.mutateAsync({
        consumerId: selectedConsumerId,
        dataServiceId: dataService.id,
      });
      setSelectedConsumerId(undefined);
      const failure = response.gatewayBindings.find((binding) => binding.status === 'GRANT_FAILED');
      if (failure) messageApi.error(failure.lastError || '订阅已保存，但网关授权失败');
      else messageApi.success('消费者订阅已授权');
    } catch (error) {
      messageApi.error(errorMessage(error, '新增消费者订阅失败'));
    }
  };

  const sync = async (subscription: ApiServiceSubscription) => {
    try {
      const response = await syncMutation.mutateAsync(subscription.id);
      const failure = response.gatewayBindings.find((binding) => binding.status === 'GRANT_FAILED');
      if (failure) messageApi.error(failure.lastError || '订阅授权同步失败');
      else messageApi.success('订阅授权已同步');
    } catch (error) {
      messageApi.error(errorMessage(error, '同步订阅授权失败'));
    }
  };

  const remove = async (subscription: ApiServiceSubscription) => {
    try {
      await revokeMutation.mutateAsync(subscription.id);
      messageApi.success('消费者订阅已撤回');
    } catch (error) {
      messageApi.error(errorMessage(error, '撤回消费者订阅失败'));
    }
  };

  const reconcile = async (subscription: ApiServiceSubscription) => {
    try {
      const response = await reconcileMutation.mutateAsync(subscription.id);
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

  const columns: TableProps<ApiServiceSubscription>['columns'] = [
    { title: '消费者名称', dataIndex: 'consumerName', width: 170, ellipsis: true },
    { title: '消费者编码', dataIndex: 'consumerCode', width: 160, ellipsis: true, render: (value: string) => <code>{value}</code> },
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
                <Tag color={statusColors[binding.status]}>
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
              aria-label={`对账${subscription.consumerName}订阅的网关状态`}
              loading={reconcileMutation.isPending
                && reconcileMutation.variables === subscription.id}
              onClick={() => void reconcile(subscription)}
            />
          </Tooltip>
          {subscription.desiredState === 'GRANTED' && (
            <Tooltip title="同步授权">
              <Button
                type="text"
                size="small"
                icon={<ReloadOutlined />}
                aria-label={`同步${subscription.consumerName}订阅`}
                loading={syncMutation.isPending && syncMutation.variables === subscription.id}
                onClick={() => void sync(subscription)}
              />
            </Tooltip>
          )}
          <Popconfirm
            title="撤回消费者订阅"
            description={`确认撤回“${subscription.consumerName}”的调用权限吗？`}
            okText="撤回"
            cancelText="取消"
            onConfirm={() => remove(subscription)}
          >
            <Tooltip title="撤回">
              <Button
                type="text"
                size="small"
                danger
                icon={<DeleteOutlined />}
                aria-label={`撤回${subscription.consumerName}订阅`}
                loading={revokeMutation.isPending && revokeMutation.variables === subscription.id}
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
        title={dataService ? `${dataService.name} · 订阅消费者` : '订阅消费者'}
        open={open}
        width={780}
        onClose={onClose}
        destroyOnHidden
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          {!subscriptionEligible && (
            <Alert
              type="info"
              showIcon
              title={publication?.accessMode !== 'SUBSCRIPTION_REQUIRED'
                ? '公开访问服务不需要消费者订阅。'
                : '服务当前版本启用并发布到网关后，才可以新增消费者订阅。'}
            />
          )}
          {canManage && subscriptionEligible && (
            <Space.Compact style={{ width: '100%' }}>
              <Select
                showSearch
                allowClear
                optionFilterProp="label"
                placeholder="选择已同步的 API 消费者"
                value={selectedConsumerId}
                options={consumerOptions}
                loading={consumersQuery.isFetching}
                onChange={setSelectedConsumerId}
                style={{ flex: 1 }}
              />
              <Button
                type="primary"
                icon={<PlusOutlined />}
                disabled={!selectedConsumerId}
                loading={createMutation.isPending}
                onClick={() => void create()}
              >
                新增订阅
              </Button>
            </Space.Compact>
          )}
          <Table<ApiServiceSubscription>
            size="small"
            rowKey="id"
            columns={columns}
            dataSource={subscriptions}
            loading={subscriptionsQuery.isFetching}
            pagination={false}
            scroll={{ x: 780 }}
          />
        </Space>
      </Drawer>
    </>
  );
};
