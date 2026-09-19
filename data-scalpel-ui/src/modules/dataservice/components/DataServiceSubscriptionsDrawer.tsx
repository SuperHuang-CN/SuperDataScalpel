import {
  AuditOutlined,
  DeleteOutlined,
  EllipsisOutlined,
  LinkOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import { Button, Drawer, Dropdown, Modal, Select, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ContextHelp, InlineFeedback } from '../../../shared/components/ContextualFeedback';
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
  const [operationError, setOperationError] = useState<string | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
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
    setOperationError(null);
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
      const failure = errorMessage(error, '新增消费者订阅失败');
      setOperationError(failure);
      messageApi.error(failure);
    }
  };

  const sync = async (subscription: ApiServiceSubscription) => {
    setOperationError(null);
    try {
      const response = await syncMutation.mutateAsync(subscription.id);
      const failure = response.gatewayBindings.find((binding) => binding.status === 'GRANT_FAILED');
      if (failure) messageApi.error(failure.lastError || '订阅授权同步失败');
      else messageApi.success('订阅授权已同步');
    } catch (error) {
      const failure = errorMessage(error, '同步订阅授权失败');
      setOperationError(failure);
      messageApi.error(failure);
    }
  };

  const remove = async (subscription: ApiServiceSubscription) => {
    setOperationError(null);
    try {
      await revokeMutation.mutateAsync(subscription.id);
      messageApi.success('消费者订阅已撤回');
    } catch (error) {
      const failure = errorMessage(error, '撤回消费者订阅失败');
      setOperationError(failure);
      messageApi.error(failure);
    }
  };

  const reconcile = async (subscription: ApiServiceSubscription) => {
    setOperationError(null);
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
      const failureMessage = errorMessage(error, '订阅网关状态对账失败');
      setOperationError(failureMessage);
      messageApi.error(failureMessage);
    }
  };

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
      if (key === 'reconcile') void reconcile(subscription);
      if (key === 'sync') void sync(subscription);
      if (key === 'revoke') {
        modalApi.confirm({
          rootClassName: 'business-overlay business-modal-overlay',
          title: '撤回消费者订阅',
          content: `确认撤回“${subscription.consumerName}”对当前服务的调用权限吗？`,
          okText: '撤回',
          okButtonProps: { danger: true },
          cancelText: '取消',
          onOk: () => remove(subscription),
        });
      }
    },
  });

  const columns: TableProps<ApiServiceSubscription>['columns'] = [
    {
      title: '消费者',
      key: 'consumer',
      width: 230,
      render: (_value, row) => (
        <span className="data-service-subscription-identity">
          <strong title={row.consumerName}>{row.consumerName}</strong>
          <code title={row.consumerCode}>{row.consumerCode}</code>
        </span>
      ),
    },
    {
      title: '订阅与网关状态',
      key: 'status',
      render: (_value, row) => (
        <span className="data-service-subscription-statuses">
          <Tag color={row.desiredState === 'GRANTED' ? 'success' : 'warning'}>
            {apiServiceSubscriptionDesiredStateLabels[row.desiredState]}
          </Tag>
          {row.gatewayBindings.length ? row.gatewayBindings.map((binding) => (
            <span key={binding.id}>
              <Tooltip title={binding.lastError || undefined}>
                <Tag color={statusColors[binding.status]}>
                  {gatewayProviderLabels[binding.provider]} · {gatewaySubscriptionStatusLabels[binding.status]}
                </Tag>
              </Tooltip>
              <GatewayReconciliationTag state={binding} />
            </span>
          )) : <Tag>未授权</Tag>}
        </span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 58,
      align: 'center',
      render: (_: unknown, subscription: ApiServiceSubscription) => canManage ? (
        <Dropdown menu={subscriptionMenu(subscription)} trigger={['click']}>
          <Tooltip title="管理订阅">
            <span>
              <Button
                type="text"
                size="small"
                icon={<EllipsisOutlined />}
                aria-label={`管理${subscription.consumerName}订阅`}
                loading={(reconcileMutation.isPending && reconcileMutation.variables === subscription.id)
                  || (syncMutation.isPending && syncMutation.variables === subscription.id)
                  || (revokeMutation.isPending && revokeMutation.variables === subscription.id)}
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
        className="data-service-subscriptions-drawer"
        title={(
          <div className="data-service-subscriptions-title">
            <span className="data-service-subscriptions-title-icon" aria-hidden="true"><LinkOutlined /></span>
            <span className="data-service-subscriptions-title-copy">
              <span>管理服务订阅</span>
              <Typography.Text type="secondary">{dataService?.name ?? '数据服务'} · 管理消费者调用授权与网关同步</Typography.Text>
            </span>
          </div>
        )}
        extra={<Tag className="data-service-subscriptions-header-tag">{subscriptions.length} 个消费者</Tag>}
        open={open}
        width={780}
        onClose={() => { setSelectedConsumerId(undefined); setOperationError(null); onClose(); }}
        destroyOnHidden
      >
        <div className="data-service-subscriptions-workspace">
          <div className="data-service-subscriptions-toolbar">
            <span className="data-service-subscriptions-toolbar-copy">
              <span>
                <strong>已授权消费者</strong>
                <ContextHelp ariaLabel="查看服务订阅说明" content="只有已启用、以订阅访问方式发布且已同步到同一网关的消费者才能新增订阅。" presentation="popover" placement="bottomLeft" />
              </span>
              <Typography.Text type="secondary">授权变更会同步到服务当前发布的 API 网关</Typography.Text>
            </span>
          {!subscriptionEligible && (
            <InlineFeedback
              tone="info"
              label={publication?.accessMode !== 'SUBSCRIPTION_REQUIRED'
                ? '公开访问服务不需要消费者订阅。'
                : '服务当前版本启用并发布到网关后，才可以新增消费者订阅。'}
            />
          )}
          {canManage && subscriptionEligible && (
            <Space.Compact className="data-service-subscriptions-create">
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
          </div>
          {operationError && <InlineFeedback className="data-service-subscriptions-error" tone="error" label="订阅操作失败" detail={operationError} />}
          <Table<ApiServiceSubscription>
            size="small"
            className="management-table management-table-comfortable data-service-subscriptions-table"
            rowKey="id"
            columns={columns}
            dataSource={subscriptions}
            loading={subscriptionsQuery.isFetching}
            pagination={false}
          />
        </div>
      </Drawer>
    </>
  );
};
