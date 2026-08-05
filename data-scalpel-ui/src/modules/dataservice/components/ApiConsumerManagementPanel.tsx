import {
  AuditOutlined,
  DeleteOutlined,
  EditOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SyncOutlined,
  UsergroupAddOutlined,
} from '@ant-design/icons';
import type { TableProps } from 'antd';
import {
  Alert,
  Button,
  Dropdown,
  Form,
  Modal,
  Space,
  Table,
  Tooltip,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { ManagementCode, ManagementDateTime, ManagementListCell, ManagementStatusIndicator, type ManagementStatusTone } from '../../../shared/components/ManagementListCells';
import { ManagementFilterActions, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';
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

const statusColors: Record<GatewayConsumerSyncStatus, ManagementStatusTone> = {
  SYNC_PENDING: 'processing',
  SYNCED: 'success',
  SYNC_FAILED: 'error',
  DELETE_PENDING: 'processing',
  DELETE_FAILED: 'error',
};

const bindingStatus = (binding: GatewayConsumerBinding) => (
  <div key={binding.id} className="management-status-group">
    <ManagementStatusIndicator
      label={`${gatewayProviderLabels[binding.provider]} · ${gatewayConsumerSyncStatusLabels[binding.syncStatus]}`}
      tone={statusColors[binding.syncStatus]}
      title={binding.lastError || undefined}
    />
    <GatewayReconciliationTag state={binding} />
  </div>
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
    { title: '消费者', dataIndex: 'name', width: 270, render: (value: string, consumer) => <ManagementListCell icon={<UsergroupAddOutlined />} iconTone="orange" primary={value} secondary={<><ManagementCode value={consumer.code} /> {consumer.description || ''}</>} /> },
    {
      title: '网关状态',
      dataIndex: 'gatewayBindings',
      width: 280,
      render: (bindings: GatewayConsumerBinding[]) => (
        bindings.length ? <div className="management-status-group">{bindings.map(bindingStatus)}</div> : <ManagementStatusIndicator label="未同步" />
      ),
    },
    {
      title: '同步信息', width: 180,
      render: (_: unknown, consumer: ApiConsumer) => {
        if (!consumer.gatewayBindings.length) return '—';
        const revision = Math.min(...consumer.gatewayBindings.map((binding) => binding.syncedRevision)) === consumer.revision
          ? `v${consumer.revision}`
          : `v${Math.min(...consumer.gatewayBindings.map((binding) => binding.syncedRevision))} / v${consumer.revision}`;
        return <ManagementListCell primary={revision} secondary={formatManagementDateTime(consumer.gatewayBindings.map((binding) => binding.lastSyncedAt).filter((value): value is string => Boolean(value)).sort().at(-1) ?? null)} />;
      },
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 160, render: (value: string) => <ManagementDateTime value={value} /> },
    {
      title: '操作',
      key: 'action',
      width: 112,
      render: (_: unknown, consumer: ApiConsumer) => (
        <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
            <Tooltip title="访问配置"><Button type="text" size="small" aria-label={`配置${consumer.name}访问权限`} icon={<SafetyCertificateOutlined />} onClick={() => setAccessConsumer(consumer)} /></Tooltip>
            {canManage && <Tooltip title="修改"><Button type="text" size="small" aria-label={`修改${consumer.name}`} icon={<EditOutlined />} onClick={() => setEditingConsumer(consumer)} /></Tooltip>}
          </div>
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  { key: 'access', label: '访问配置', icon: <SafetyCertificateOutlined /> },
                  ...(canManage ? [{ key: 'edit', label: '修改', icon: <EditOutlined /> }] : []),
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
                  if (key === 'access') setAccessConsumer(consumer);
                  if (key === 'edit') setEditingConsumer(consumer);
                  if (key === 'reconcile') void reconcileGateway(consumer);
                  if (key === 'sync') void synchronize(consumer);
                  if (key === 'delete') confirmRemove(consumer);
                },
              }}
            >
              <Tooltip title="更多操作">
              <Button
                className="management-row-actions-more"
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
        </div>
      ),
    },
  ];

  return (
    <>
      {messageContext}
      {modalContext}
      <section className="management-workbench">
        <div className="management-filter-strip">
          <Form<ApiConsumerFilters> autoComplete="off"
            form={filterForm}
            layout="inline"
            className="management-filter-form"
            onFinish={search}
          >
            <Form.Item name="keyword">
              <ManagementSearchInput allowClear placeholder="搜索消费者名称或编码" className="data-source-keyword-input" />
            </Form.Item>
          </Form>
          <ManagementFilterActions form={filterForm} appliedFilters={filters} loading={consumersQuery.isFetching} onReset={reset} />
        </div>
        <div className="management-results-surface">
          <div className="management-result-toolbar">
          <div className="management-result-title">消费者管理 <span className="management-result-count">共 {consumersQuery.data?.totalElements ?? 0} 项</span></div>
          <Space size={4} className="management-result-actions">
            <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新消费者列表" onClick={() => void consumersQuery.refetch()} /></Tooltip>
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
          scroll={{ y: '100%' }}
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
        </div>
      </section>
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
