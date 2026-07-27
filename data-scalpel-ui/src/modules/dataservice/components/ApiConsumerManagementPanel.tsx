import {
  AuditOutlined,
  DeleteOutlined,
  EditOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Alert,
  Button,
  Card,
  Dropdown,
  Form,
  Input,
  Modal,
  Space,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  useApiConsumers,
  useDeleteApiConsumer,
  useReconcileApiConsumerGateway,
  useSyncApiConsumer,
} from '../hooks/useApiConsumers';
import {
  gatewayConsumerSyncStatusLabels,
  gatewayProviderLabels,
  type ApiConsumer,
  type ApiConsumerFilters,
  type GatewayConsumerBinding,
  type GatewayConsumerSyncStatus,
} from '../model/apiConsumer';
import { buildApiConsumerSearch } from '../model/apiConsumerSearch';
import { ApiConsumerDrawer } from './ApiConsumerDrawer';
import { ApiConsumerAccessDrawer } from './ApiConsumerAccessDrawer';
import { GatewayReconciliationTag } from './GatewayReconciliationTag';

const DEFAULT_PAGE_SIZE = 20;

interface ApiConsumerManagementPanelProps {
  canManage: boolean;
  canConfigureAccess: boolean;
}

const formatDateTime = (value: string | null) => value
  ? new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: 'medium',
    hour12: false,
  }).format(new Date(value))
  : '—';

const statusColors: Record<GatewayConsumerSyncStatus, string> = {
  SYNC_PENDING: 'processing',
  SYNCED: 'success',
  SYNC_FAILED: 'error',
  DELETE_PENDING: 'processing',
  DELETE_FAILED: 'error',
};

const bindingStatus = (binding: GatewayConsumerBinding) => (
  <Space key={binding.id} size={[2, 2]} wrap>
    <Tooltip title={binding.lastError || undefined}>
      <Tag color={statusColors[binding.syncStatus]}>
        {gatewayProviderLabels[binding.provider]} · {gatewayConsumerSyncStatusLabels[binding.syncStatus]}
      </Tag>
    </Tooltip>
    <GatewayReconciliationTag state={binding} />
  </Space>
);

const errorMessage = (error: unknown, fallback: string) => (
  error instanceof ApiError ? error.problem?.detail ?? error.message : fallback
);

export const ApiConsumerManagementPanel = ({
  canManage,
  canConfigureAccess,
}: ApiConsumerManagementPanelProps) => {
  const [filterForm] = Form.useForm<ApiConsumerFilters>();
  const [filters, setFilters] = useState<ApiConsumerFilters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [editingConsumer, setEditingConsumer] = useState<ApiConsumer | null>(null);
  const [accessConsumer, setAccessConsumer] = useState<ApiConsumer | null>(null);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [messageApi, messageContext] = message.useMessage();
  const [modal, modalContext] = Modal.useModal();
  const request = useMemo(() => ({
    search: buildApiConsumerSearch(filters),
    page,
    size,
    sort: '-updatedAt,code',
  }), [filters, page, size]);
  const consumersQuery = useApiConsumers(request);
  const syncMutation = useSyncApiConsumer();
  const reconcileMutation = useReconcileApiConsumerGateway();
  const deleteMutation = useDeleteApiConsumer();

  const search = (nextFilters: ApiConsumerFilters) => {
    setFilters(nextFilters);
    setPage(0);
  };

  const reset = () => {
    filterForm.resetFields();
    search({});
  };

  const closeDrawer = () => {
    setEditingConsumer(null);
    setCreateDrawerOpen(false);
  };

  const synchronize = async (consumer: ApiConsumer) => {
    try {
      const response = await syncMutation.mutateAsync(consumer.id);
      const failure = response.gatewayBindings.find((binding) => binding.syncStatus === 'SYNC_FAILED');
      if (failure) {
        messageApi.error(failure.lastError || '网关消费者同步失败');
      } else {
        messageApi.success(`${consumer.name} 已同步`);
      }
    } catch (error) {
      messageApi.error(errorMessage(error, '同步消费者失败'));
    }
  };

  const remove = async (consumer: ApiConsumer) => {
    try {
      await deleteMutation.mutateAsync(consumer.id);
      messageApi.success('消费者已删除');
    } catch (error) {
      messageApi.error(errorMessage(error, '删除消费者失败'));
    }
  };

  const reconcileGateway = async (consumer: ApiConsumer) => {
    try {
      const response = await reconcileMutation.mutateAsync(consumer.id);
      const drifted = response.gatewayBindings.filter(
        (binding) => binding.reconciliationStatus === 'DRIFTED',
      );
      const failed = response.gatewayBindings.filter(
        (binding) => binding.reconciliationStatus === 'CHECK_FAILED',
      );
      if (drifted.length) messageApi.warning(`${consumer.name} 检测到网关状态漂移`);
      else if (failed.length) messageApi.error(`${consumer.name} 的网关状态检查失败`);
      else messageApi.success(`${consumer.name} 的网关状态一致`);
    } catch (error) {
      messageApi.error(errorMessage(error, '消费者网关状态对账失败'));
    }
  };

  const confirmRemove = (consumer: ApiConsumer) => {
    modal.confirm({
      title: '删除 API 消费者',
      content: `确认从网关和 DataScalpel 删除“${consumer.name}”吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => remove(consumer),
    });
  };

  const columns: TableProps<ApiConsumer>['columns'] = [
    { title: '名称', dataIndex: 'name', width: 180, ellipsis: true },
    {
      title: '编码',
      dataIndex: 'code',
      width: 180,
      ellipsis: true,
      render: (value: string) => <code>{value}</code>,
    },
    {
      title: '网关状态',
      dataIndex: 'gatewayBindings',
      width: 280,
      render: (bindings: GatewayConsumerBinding[]) => (
        bindings.length ? <Space size={[0, 4]} wrap>{bindings.map(bindingStatus)}</Space> : <Tag>未同步</Tag>
      ),
    },
    {
      title: '同步版本',
      key: 'syncedRevision',
      width: 100,
      render: (_: unknown, consumer: ApiConsumer) => {
        if (!consumer.gatewayBindings.length) return '—';
        return Math.min(...consumer.gatewayBindings.map((binding) => binding.syncedRevision)) === consumer.revision
          ? `v${consumer.revision}`
          : `v${Math.min(...consumer.gatewayBindings.map((binding) => binding.syncedRevision))} / v${consumer.revision}`;
      },
    },
    {
      title: '最近同步',
      key: 'lastSyncedAt',
      width: 180,
      render: (_: unknown, consumer: ApiConsumer) => formatDateTime(
        consumer.gatewayBindings
          .map((binding) => binding.lastSyncedAt)
          .filter((value): value is string => Boolean(value))
          .sort()
          .at(-1) ?? null,
      ),
    },
    { title: '说明', dataIndex: 'description', width: 220, ellipsis: true, render: (value: string | null) => value || '—' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDateTime(value) },
    {
      title: '操作',
      key: 'action',
      width: 126,
      fixed: 'right',
      render: (_: unknown, consumer: ApiConsumer) => (
        <Space size={2}>
          <Tooltip title="访问配置">
            <Button
              type="text"
              size="small"
              aria-label={`配置${consumer.name}访问权限`}
              icon={<SafetyCertificateOutlined />}
              onClick={() => setAccessConsumer(consumer)}
            />
          </Tooltip>
          {canManage && (
          <Tooltip title="修改">
            <Button
              type="text"
              size="small"
              aria-label={`修改${consumer.name}`}
              icon={<EditOutlined />}
              onClick={() => setEditingConsumer(consumer)}
            />
          </Tooltip>
          )}
          {(canManage || canConfigureAccess) && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  ...(canConfigureAccess ? [{
                    key: 'reconcile',
                    label: '对账网关状态',
                    icon: <AuditOutlined />,
                  }] : []),
                  ...(canManage ? [
                    { key: 'sync', label: '同步到网关', icon: <SyncOutlined /> },
                    { type: 'divider' as const },
                    { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true },
                  ] : []),
                ],
                onClick: ({ key }) => {
                  if (key === 'reconcile') void reconcileGateway(consumer);
                  if (key === 'sync') void synchronize(consumer);
                  if (key === 'delete') confirmRemove(consumer);
                },
              }}
            >
              <Tooltip title="更多">
              <Button
                type="text"
                size="small"
                aria-label={`更多${consumer.name}操作`}
                icon={<MoreOutlined />}
                loading={(syncMutation.isPending && syncMutation.variables === consumer.id)
                  || (reconcileMutation.isPending && reconcileMutation.variables === consumer.id)
                  || (deleteMutation.isPending && deleteMutation.variables === consumer.id)}
              />
              </Tooltip>
            </Dropdown>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      {messageContext}
      {modalContext}
      <Card className="management-card">
        <div className="management-toolbar">
          <Form<ApiConsumerFilters>
            form={filterForm}
            layout="inline"
            className="management-filter-form"
            onFinish={search}
          >
            <Form.Item name="keyword" label="名称/编码">
              <Input allowClear placeholder="按名称或编码筛选" className="data-source-keyword-input" />
            </Form.Item>
          </Form>
          <Space size={4} className="management-toolbar-actions">
            <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
            <Button onClick={reset}>重置</Button>
            <Button icon={<ReloadOutlined />} onClick={() => void consumersQuery.refetch()}>刷新</Button>
            {canManage && (
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateDrawerOpen(true)}>
                新建
              </Button>
            )}
          </Space>
        </div>
        {consumersQuery.isError && (
          <Alert
            type="error"
            showIcon
            title="消费者列表加载失败"
            description={errorMessage(consumersQuery.error, '请检查后台服务后重试')}
            action={<Button size="small" onClick={() => void consumersQuery.refetch()}>重试</Button>}
          />
        )}
        <Table<ApiConsumer>
          size="small"
          className="management-table"
          rowKey="id"
          columns={columns}
          dataSource={consumersQuery.data?.content ?? []}
          loading={consumersQuery.isFetching}
          scroll={{ x: 1380, y: '100%' }}
          pagination={{
            current: page + 1,
            pageSize: size,
            total: consumersQuery.data?.totalElements ?? 0,
            size: 'small',
            placement: ['bottomEnd'],
            hideOnSinglePage: false,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 项`,
          }}
          onChange={(pagination) => {
            setPage((pagination.current ?? 1) - 1);
            setSize(pagination.pageSize ?? DEFAULT_PAGE_SIZE);
          }}
        />
      </Card>
      <ApiConsumerDrawer
        open={createDrawerOpen || Boolean(editingConsumer)}
        consumer={editingConsumer}
        onClose={closeDrawer}
      />
      <ApiConsumerAccessDrawer
        open={Boolean(accessConsumer)}
        consumer={accessConsumer}
        canManage={canConfigureAccess}
        onClose={() => setAccessConsumer(null)}
      />
    </>
  );
};
