import {
  ApiOutlined,
  AuditOutlined,
  ClearOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownOutlined,
  MoreOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  RollbackOutlined,
  StopOutlined,
  TeamOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { MenuProps, TableProps } from 'antd';
import {
  Alert,
  Button,
  Dropdown,
  Form,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { ManagementFilterActions, ManagementMoreFilters, ManagementSearchInput } from '../../../shared/components/ManagementFilters';
import { ManagementCode, ManagementListCell, ManagementStatusIndicator, type ManagementStatusTone } from '../../../shared/components/ManagementListCells';
import { DirectoryTreePanel, findDirectoryDescendantIds, useDirectoryTree, type DirectorySelection } from '../../directory';
import { useServiceEngines } from '../../serviceengine';
import { fetchDataService } from '../api/dataServiceApi';
import {
  useCleanupDataServiceDeployment,
  useDataServices,
  useDeleteDataService,
  useDisableDataService,
  useEnableDataService,
  usePublishDataService,
  useReconcileDataServiceGateway,
  useUnpublishDataService,
} from '../hooks/useDataServices';
import {
  dataServiceDeploymentStatusLabels,
  dataServiceAccessModeLabels,
  dataServiceStatusLabels,
  dataServiceTypeLabels,
  type DataServiceDeploymentStatus,
  type DataServiceFilters,
  type DataServiceStatus,
  type DataServiceSummary,
} from '../model/dataService';
import { gatewayProviderLabels } from '../model/apiConsumer';
import { buildDataServiceCurlCommand } from '../model/dataServiceCurl';
import { gatewayOperationError, publishedGatewayBinding } from '../model/dataServiceGateway';
import { parseDataServiceListRoute, serializeDataServiceListRoute } from '../model/dataServiceListRoute';
import { buildDataServiceSearch } from '../model/dataServiceSearch';
import { DataServiceSubscriptionsDrawer } from './DataServiceSubscriptionsDrawer';

const DEFAULT_PAGE_SIZE = 20;

interface DataServiceListPanelProps {
  canCreate: boolean;
  canDelete: boolean;
  canPublish: boolean;
  canViewDirectories: boolean;
  canManageDirectories: boolean;
  canViewModels: boolean;
  canViewDataSources: boolean;
  canViewEngines: boolean;
}

const formatDateTime = (value: string | null) => value ? new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium', timeStyle: 'medium', hour12: false,
}).format(new Date(value)) : '—';

const serviceStatusColors: Record<DataServiceStatus, ManagementStatusTone> = {
  DRAFT: 'default', ENABLED: 'success', DISABLED: 'warning',
};

const deploymentStatusColors: Record<DataServiceDeploymentStatus, ManagementStatusTone> = {
  PENDING: 'processing', DEPLOYED: 'success', FAILED: 'error', REMOVING: 'processing', REMOVED: 'default',
};
const statusOptions = Object.entries(dataServiceStatusLabels).map(([value, label]) => ({ value, label }));
const typeOptions = Object.entries(dataServiceTypeLabels).map(([value, label]) => ({ value, label }));

const creationLabel = (title: string, description: string, suffix?: string) => (
  <div className="data-service-create-option">
    <Space size={6}>
      <Typography.Text>{title}</Typography.Text>
      {suffix && <Tag>{suffix}</Tag>}
    </Space>
    <Typography.Text type="secondary" className="data-service-create-option-description">{description}</Typography.Text>
  </div>
);

export const DataServiceListPanel = ({
  canCreate,
  canDelete,
  canPublish,
  canViewDirectories,
  canManageDirectories,
  canViewModels,
  canViewDataSources,
  canViewEngines,
}: DataServiceListPanelProps) => {
  const navigate = useNavigate();
  const [routeSearchParams, setRouteSearchParams] = useSearchParams();
  const [initialRouteState] = useState(() => parseDataServiceListRoute(routeSearchParams));
  const [filterForm] = Form.useForm<DataServiceFilters>();
  const [advancedFilterForm] = Form.useForm<DataServiceFilters>();
  const [advancedFilterOpen, setAdvancedFilterOpen] = useState(false);
  const [advancedFilters, setAdvancedFilters] = useState<DataServiceFilters>({
    engineId: initialRouteState.filters.engineId,
  });
  const [filters, setFilters] = useState<DataServiceFilters>(initialRouteState.filters);
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(initialRouteState.directorySelection);
  const [page, setPage] = useState(initialRouteState.page);
  const [size, setSize] = useState(initialRouteState.size);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const [subscriptionService, setSubscriptionService] = useState<DataServiceSummary | null>(null);
  const directoriesQuery = useDirectoryTree('DATA_SERVICE', canViewDirectories);
  const enginesQuery = useServiceEngines({ page: 0, size: 500, sort: 'code' }, canViewEngines);
  const effectiveFilters = useMemo<DataServiceFilters>(() => {
    if (typeof directorySelection !== 'string') return filters;
    return {
      ...filters,
      directoryIds: findDirectoryDescendantIds(directoriesQuery.data ?? [], directorySelection),
      uncategorized: undefined,
    };
  }, [directorySelection, directoriesQuery.data, filters]);
  const request = useMemo(() => ({
    search: buildDataServiceSearch(effectiveFilters), page, size, sort: '-updatedAt,code',
  }), [effectiveFilters, page, size]);
  const dataServicesQuery = useDataServices(request);
  const deleteMutation = useDeleteDataService();
  const enableMutation = useEnableDataService();
  const publishMutation = usePublishDataService();
  const reconcileMutation = useReconcileDataServiceGateway();
  const unpublishMutation = useUnpublishDataService();
  const disableMutation = useDisableDataService();
  const cleanupMutation = useCleanupDataServiceDeployment();
  const engineNames = new Map((enginesQuery.data?.content ?? []).map((engine) => [engine.id, engine.name]));

  const syncRoute = (
    nextFilters: DataServiceFilters,
    nextDirectorySelection: DirectorySelection,
    nextPage: number,
    nextSize: number,
  ) => setRouteSearchParams(
    serializeDataServiceListRoute(nextFilters, nextDirectorySelection, nextPage, nextSize),
    { replace: true },
  );

  const search = (nextFilters: DataServiceFilters, nextDirectorySelection = directorySelection) => {
    const normalized = {
      ...nextFilters,
      directoryIds: undefined,
      uncategorized: nextDirectorySelection === null ? true : undefined,
    };
    setFilters(normalized);
    setPage(0);
    syncRoute(normalized, nextDirectorySelection, 0, size);
  };

  const reset = () => {
    filterForm.resetFields();
    filterForm.setFieldsValue({
      keyword: undefined,
      status: undefined,
      type: undefined,
      engineId: undefined,
    });
    advancedFilterForm.resetFields();
    setAdvancedFilters({});
    setAdvancedFilterOpen(false);
    setDirectorySelection(undefined);
    search({}, undefined);
  };

  const applyDirectFilters = (values: DataServiceFilters) => search({
    ...filters,
    keyword: values.keyword,
    status: values.status,
    type: values.type,
    engineId: advancedFilters.engineId,
  });

  const confirmAdvancedFilters = () => {
    setAdvancedFilters({ engineId: advancedFilterForm.getFieldValue('engineId') });
    setAdvancedFilterOpen(false);
  };

  const clearAdvancedFilters = () => advancedFilterForm.resetFields();

  const selectDirectory = (selection: DirectorySelection) => {
    setDirectorySelection(selection);
    search({
      ...filters,
      directoryIds: undefined,
      uncategorized: selection === null ? true : undefined,
    }, selection);
  };

  const openEditor = (path: string) => navigate(path, {
    state: {
      fromDataServiceList: true,
      initialDirectoryId: typeof directorySelection === 'string' ? directorySelection : undefined,
    },
  });

  const enable = async (dataService: DataServiceSummary) => {
    try {
      const response = await enableMutation.mutateAsync(dataService.id);
      if (response.status === 'ENABLED' && response.deploymentStatus === 'DEPLOYED') {
        messageApi.success(`${dataService.name} 已启用`);
      } else {
        messageApi.error(response.deploymentError || 'Engine 未确认启用，请查看部署状态');
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.problem?.detail ?? error.message : '启用数据服务失败');
    }
  };

  const publish = async (dataService: DataServiceSummary) => {
    try {
      const response = await publishMutation.mutateAsync(dataService.id);
      const binding = publishedGatewayBinding(response);
      if (binding) {
        messageApi.success(`${dataService.name} 已发布到 ${gatewayProviderLabels[binding.provider]}`);
      } else {
        messageApi.error(gatewayOperationError(response) || '网关未确认发布结果');
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.problem?.detail ?? error.message : '发布到网关失败');
    }
  };

  const disable = async (dataService: DataServiceSummary) => {
    try {
      const response = await disableMutation.mutateAsync(dataService.id);
      if (response.status === 'DISABLED' && response.deploymentStatus === 'REMOVED') {
        messageApi.success(`${dataService.name} 已停用`);
      } else {
        messageApi.error(gatewayOperationError(response) || response.deploymentError || '服务未确认停用结果');
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.problem?.detail ?? error.message : '停用数据服务失败');
    }
  };

  const unpublish = async (dataService: DataServiceSummary) => {
    try {
      const response = await unpublishMutation.mutateAsync(dataService.id);
      if (response.status === 'ENABLED'
          && response.deploymentStatus === 'DEPLOYED'
          && response.gatewayBindings.length === 0) {
        messageApi.success(`${dataService.name} 已取消网关发布，Service Engine 保持运行`);
      } else {
        messageApi.error(gatewayOperationError(response) || '网关未确认取消发布结果');
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError
        ? error.problem?.detail ?? error.message
        : '取消网关发布失败');
    }
  };

  const reconcileGateway = async (dataService: DataServiceSummary) => {
    try {
      const response = await reconcileMutation.mutateAsync(dataService.id);
      const drifted = response.gatewayBindings.filter(
        (binding) => binding.reconciliationStatus === 'DRIFTED',
      );
      const failed = response.gatewayBindings.filter(
        (binding) => binding.reconciliationStatus === 'CHECK_FAILED',
      );
      if (drifted.length) {
        messageApi.warning(`${dataService.name} 检测到 ${drifted.length} 个网关绑定漂移`);
      } else if (failed.length) {
        messageApi.error(`${dataService.name} 有 ${failed.length} 个网关绑定检查失败`);
      } else {
        messageApi.success(`${dataService.name} 的网关状态一致`);
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError
        ? error.problem?.detail ?? error.message
        : '网关状态对账失败');
    }
  };

  const cleanup = async (dataService: DataServiceSummary) => {
    try {
      const response = await cleanupMutation.mutateAsync(dataService.id);
      if (response.deploymentStatus === 'REMOVED') messageApi.success(`${dataService.name} 的失败部署已清理`);
      else messageApi.error(response.deploymentError || 'Engine 未确认清理结果');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.problem?.detail ?? error.message : '清理失败部署失败');
    }
  };

  const remove = async (dataService: DataServiceSummary) => {
    try {
      await deleteMutation.mutateAsync(dataService.id);
      messageApi.success('数据服务已删除');
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.problem?.detail ?? error.message : '删除数据服务失败');
    }
  };
  const confirmRemove = (dataService: DataServiceSummary) => modalApi.confirm({
    title: '删除数据服务', content: `确认删除“${dataService.name}”吗？`, okText: '删除', cancelText: '取消',
    okButtonProps: { danger: true }, onOk: () => remove(dataService),
  });

  const copyCurl = async (dataService: DataServiceSummary) => {
    const binding = publishedGatewayBinding(dataService);
    if (!binding?.gatewayUrl) {
      messageApi.error('当前版本尚未成功发布到网关');
      return;
    }
    if (!navigator.clipboard) {
      messageApi.error('当前浏览器不支持自动复制，请使用 HTTPS 或 localhost 访问');
      return;
    }
    try {
      const detail = await fetchDataService(dataService.id);
      await navigator.clipboard.writeText(buildDataServiceCurlCommand(binding.gatewayUrl, '', detail));
      messageApi.success('网关访问 cURL 已复制');
    } catch {
      messageApi.error('复制失败，请检查浏览器的剪贴板权限');
    }
  };

  const columns: TableProps<DataServiceSummary>['columns'] = [
    {
      title: '服务', dataIndex: 'name', width: 240,
      render: (value: string, service: DataServiceSummary) => (
        <ManagementListCell icon={<ApiOutlined />} iconTone="violet" primary={<Button type="link" size="small" className="data-service-name-button" onClick={() => openEditor(`/dataservice/${service.id}`)}>{value}</Button>} secondary={<><ManagementCode value={service.code} /> {service.description || ''}</>} />
      ),
    },
    { title: '类型 / 来源', width: 180, render: (_: unknown, service) => <ManagementListCell primary={dataServiceTypeLabels[service.type]} secondary={service.sourceName} /> },
    {
      title: '路由 / 访问', width: 250,
      render: (_: unknown, service) => <ManagementListCell primary={<ManagementCode value={service.routePath} />} secondary={dataServiceAccessModeLabels[service.accessMode]} />,
    },
    {
      title: '运行状态', width: 210,
      render: (_: unknown, dataService: DataServiceSummary) => <ManagementListCell
        primary={<ManagementStatusIndicator label={dataServiceStatusLabels[dataService.status]} tone={serviceStatusColors[dataService.status]} />}
        secondary={<div className="management-status-group">
          {dataService.deploymentStatus
            ? <ManagementStatusIndicator label={dataServiceDeploymentStatusLabels[dataService.deploymentStatus]} tone={deploymentStatusColors[dataService.deploymentStatus]} title={dataService.deploymentError || undefined} />
            : <ManagementStatusIndicator label="未部署" />}
          <ManagementStatusIndicator label={dataService.gatewayBindings.length ? `网关 ${dataService.gatewayBindings.length}` : '未发布网关'} tone={dataService.gatewayBindings.length ? 'success' : 'default'} />
        </div>}
      />,
    },
    { title: 'Engine / 版本', width: 190, render: (_: unknown, service) => <ManagementListCell primary={engineNames.get(service.engineId) ?? service.engineId} secondary={`v${service.revision} · ${formatDateTime(service.updatedAt)}`} /> },
    {
      title: '操作', key: 'action', width: 112,
      render: (_: unknown, dataService: DataServiceSummary) => {
        const moreItems: NonNullable<MenuProps['items']> = [
          ...(dataService.accessMode === 'SUBSCRIPTION_REQUIRED' ? [{ key: 'subscriptions', label: '订阅消费者', icon: <TeamOutlined /> }] : []),
          ...(canPublish && dataService.status !== 'ENABLED' ? [{ key: 'enable', label: dataService.deploymentStatus === 'FAILED' || dataService.deploymentStatus === 'PENDING' ? '重试启用' : '启用', icon: <PlayCircleOutlined /> }] : []),
          ...(canPublish && dataService.status === 'ENABLED' ? [{ key: 'disable', label: '停用', icon: <StopOutlined />, danger: true }] : []),
          ...(publishedGatewayBinding(dataService) ? [{ key: 'curl', label: '复制网关访问 cURL', icon: <CopyOutlined /> }] : []),
          ...(canPublish ? [{ key: 'reconcile', label: '对账网关状态', icon: <AuditOutlined /> }] : []),
          ...(canPublish && dataService.status === 'ENABLED' && dataService.deploymentStatus === 'DEPLOYED'
            ? [{ key: 'publish', label: publishedGatewayBinding(dataService) ? '重新发布到网关' : '发布到网关', icon: <UploadOutlined /> }] : []),
          ...(canPublish && dataService.status === 'ENABLED' && dataService.gatewayBindings.length > 0
            ? [{ key: 'unpublish', label: '取消发布到网关', icon: <RollbackOutlined />, danger: true }] : []),
          ...(canPublish && dataService.status !== 'ENABLED' && (dataService.deploymentStatus === 'FAILED' || dataService.deploymentStatus === 'PENDING')
            ? [{ key: 'cleanup', label: '清理失败部署', icon: <ClearOutlined /> }] : []),
          ...(canDelete && dataService.gatewayBindings.length === 0 && (!dataService.deploymentStatus || dataService.deploymentStatus === 'REMOVED')
            ? [{ type: 'divider' as const }, { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true }] : []),
        ];
        return <div className="management-row-actions">
          <div className="management-row-actions-shortcuts">
          {dataService.accessMode === 'SUBSCRIPTION_REQUIRED' && (
            <Tooltip title="订阅消费者">
              <Button
                type="text"
                size="small"
                aria-label={`管理${dataService.name}的订阅消费者`}
                icon={<TeamOutlined />}
                onClick={() => setSubscriptionService(dataService)}
              />
            </Tooltip>
          )}
          {canPublish && dataService.status !== 'ENABLED' && (
            <Tooltip title={dataService.deploymentStatus === 'FAILED' || dataService.deploymentStatus === 'PENDING' ? '重试启用' : '启用'}><Button type="text" size="small" aria-label={`启用${dataService.name}`} icon={<PlayCircleOutlined />} loading={enableMutation.isPending && enableMutation.variables === dataService.id} onClick={() => void enable(dataService)} /></Tooltip>
          )}
          {canPublish && dataService.status === 'ENABLED' && (
            <Tooltip title="停用"><Button type="text" size="small" danger aria-label={`停用${dataService.name}`} icon={<StopOutlined />} loading={disableMutation.isPending && disableMutation.variables === dataService.id} onClick={() => modalApi.confirm({ title: '停用数据服务？', content: `将先从所有网关撤回“${dataService.name}”，再从 Service Engine 移除。`, okText: '停用', cancelText: '返回', okButtonProps: { danger: true }, onOk: () => disable(dataService) })} /></Tooltip>
          )}
          </div>
          {moreItems.length > 0 && <Dropdown trigger={['click']} menu={{ items: moreItems, onClick: ({ key }) => {
            if (key === 'subscriptions') setSubscriptionService(dataService);
            if (key === 'enable') void enable(dataService);
            if (key === 'disable') modalApi.confirm({ title: '停用数据服务？', content: `将先从所有网关撤回“${dataService.name}”，再从 Service Engine 移除。`, okText: '停用', cancelText: '返回', okButtonProps: { danger: true }, onOk: () => disable(dataService) });
            if (key === 'curl') void copyCurl(dataService);
            if (key === 'reconcile') void reconcileGateway(dataService);
            if (key === 'publish') void publish(dataService);
            if (key === 'unpublish') modalApi.confirm({ title: '取消发布到网关？', content: `取消后“${dataService.name}”将无法通过网关访问，Service Engine 保持运行。`, okText: '取消发布', cancelText: '返回', okButtonProps: { danger: true }, onOk: () => unpublish(dataService) });
            if (key === 'cleanup') void cleanup(dataService);
            if (key === 'delete') confirmRemove(dataService);
          } }}><Tooltip title="更多操作"><Button className="management-row-actions-more" type="text" size="small" aria-label={`${dataService.name}的更多操作`} icon={<MoreOutlined />} /></Tooltip></Dropdown>}
        </div>;
      },
    },
  ];
  const advancedFilterCount = Number(Boolean(advancedFilters.engineId));

  const standardCreationDisabled = !canViewModels || !canViewEngines;
  const sqlCreationDisabled = standardCreationDisabled || !canViewDataSources;
  const scriptCreationDisabled = !canViewDataSources || !canViewEngines;
  const creationItems: MenuProps['items'] = [
    {
      key: 'standard',
      disabled: standardCreationDisabled,
      label: creationLabel('标准单表服务', standardCreationDisabled ? '需要模型和 Service Engine 查看权限' : '发布一个已发布模型'),
    },
    {
      key: 'sql',
      disabled: sqlCreationDisabled,
      label: creationLabel('SQL 查询服务', sqlCreationDisabled ? '需要数据源、模型和 Service Engine 查看权限' : '基于 PostgreSQL 数据源和多个模型编写只读 SQL'),
    },
    {
      key: 'script',
      disabled: scriptCreationDisabled,
      label: creationLabel(
        'Groovy 脚本服务',
        scriptCreationDisabled ? '需要数据源和 Service Engine 查看权限' : '在线编写脚本并发布复杂数据服务',
      ),
    },
  ];

  return (
    <>
      {messageContext}
      {modalContext}
      <div className={canViewDirectories ? 'directory-management-layout' : 'page-stack'}>
        {canViewDirectories && <DirectoryTreePanel scope="DATA_SERVICE" tree={directoriesQuery.data ?? []} loading={directoriesQuery.isFetching} selection={directorySelection} canManage={canManageDirectories} onSelectionChange={selectDirectory} />}
        <section className="management-workbench">
          <div className="management-filter-strip">
            <Form<DataServiceFilters> autoComplete="off" form={filterForm} initialValues={initialRouteState.filters} layout="inline" className="management-filter-form" onFinish={applyDirectFilters}>
              <Form.Item name="keyword"><ManagementSearchInput allowClear placeholder="搜索服务名称或编码" className="data-source-keyword-input" /></Form.Item>
              <Form.Item name="status"><Select allowClear placeholder="全部服务状态" options={statusOptions} className="data-source-filter-select" /></Form.Item>
              <Form.Item name="type"><Select allowClear placeholder="全部类型" options={typeOptions} className="data-source-filter-select" /></Form.Item>
              <ManagementMoreFilters
                count={advancedFilterCount}
                open={advancedFilterOpen}
                onOpenChange={(open) => {
                  setAdvancedFilterOpen(open);
                  if (open) {
                    advancedFilterForm.resetFields();
                    advancedFilterForm.setFieldsValue(advancedFilters);
                  }
                }}
                onClear={clearAdvancedFilters}
                onCancel={() => setAdvancedFilterOpen(false)}
                onConfirm={confirmAdvancedFilters}
              >
                <Form<DataServiceFilters> form={advancedFilterForm} layout="vertical" autoComplete="off"><Form.Item name="engineId" label="Service Engine"><Select allowClear showSearch optionFilterProp="label" options={(enginesQuery.data?.content ?? []).map((engine) => ({ value: engine.id, label: engine.name }))} className="advanced-filter-select" /></Form.Item></Form>
              </ManagementMoreFilters>
            </Form>
            <ManagementFilterActions form={filterForm} appliedFilters={filters} additionalActive={advancedFilterCount > 0 || directorySelection !== undefined} loading={dataServicesQuery.isFetching} onReset={reset} />
          </div>
          <div className="management-results-surface">
            <div className="management-result-toolbar">
            <div className="management-result-title">数据服务 <span className="management-result-count">共 {dataServicesQuery.data?.totalElements ?? 0} 项</span></div>
            <Space size={4} className="management-result-actions">
              <Tooltip title="刷新列表"><Button type="text" icon={<ReloadOutlined />} aria-label="刷新数据服务列表" onClick={() => void dataServicesQuery.refetch()} /></Tooltip>
              {canCreate && (
                <Dropdown
                  trigger={['click']}
                  menu={{
                    items: creationItems,
                    onClick: ({ key }) => {
                      if (key === 'standard') openEditor('/dataservice/new/standard');
                      if (key === 'sql') openEditor('/dataservice/new/sql');
                      if (key === 'script') openEditor('/dataservice/new/script');
                    },
                  }}
                >
                  <Button type="primary" icon={<PlusOutlined />}>新建服务 <DownOutlined /></Button>
                </Dropdown>
              )}
            </Space>
            </div>
            {dataServicesQuery.isError && <Alert type="error" showIcon message="数据服务列表加载失败" action={<Button onClick={() => void dataServicesQuery.refetch()}>重试</Button>} />}
            <Table<DataServiceSummary>
            size="small" className="management-table" rowKey="id" columns={columns}
            dataSource={dataServicesQuery.data?.content ?? []} loading={dataServicesQuery.isFetching}
            scroll={{ y: '100%' }}
            pagination={{ current: page + 1, pageSize: size, total: dataServicesQuery.data?.totalElements ?? 0, size: 'small', position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: (total) => `共 ${total} 项` }}
            onChange={(pagination) => {
              const nextSize = pagination.pageSize ?? DEFAULT_PAGE_SIZE;
              const nextPage = nextSize === size ? (pagination.current ?? 1) - 1 : 0;
              setPage(nextPage);
              setSize(nextSize);
              syncRoute(filters, directorySelection, nextPage, nextSize);
            }}
            />
          </div>
        </section>
      </div>
      <DataServiceSubscriptionsDrawer
        open={Boolean(subscriptionService)}
        dataService={subscriptionService}
        canManage={canPublish}
        onClose={() => setSubscriptionService(null)}
      />
    </>
  );
};
