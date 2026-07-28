import {
  AuditOutlined,
  ClearOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownOutlined,
  FilterOutlined,
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
  Badge,
  Button,
  Card,
  Dropdown,
  Form,
  Input,
  Popconfirm,
  Popover,
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
  gatewayServicePublicationStatusLabels,
  type DataServiceDeploymentStatus,
  type DataServiceAccessMode,
  type DataServiceFilters,
  type DataServiceStatus,
  type DataServiceSummary,
  type DataServiceType,
  type GatewayServicePublicationStatus,
} from '../model/dataService';
import { gatewayProviderLabels } from '../model/apiConsumer';
import { buildDataServiceCurlCommand } from '../model/dataServiceCurl';
import { gatewayOperationError, publishedGatewayBinding } from '../model/dataServiceGateway';
import { parseDataServiceListRoute, serializeDataServiceListRoute } from '../model/dataServiceListRoute';
import { buildDataServiceSearch } from '../model/dataServiceSearch';
import { DataServiceSubscriptionsDrawer } from './DataServiceSubscriptionsDrawer';
import { GatewayReconciliationTag } from './GatewayReconciliationTag';

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

const serviceStatusColors: Record<DataServiceStatus, string> = {
  DRAFT: 'default', ENABLED: 'success', DISABLED: 'warning',
};

const deploymentStatusColors: Record<DataServiceDeploymentStatus, string> = {
  PENDING: 'processing', DEPLOYED: 'success', FAILED: 'error', REMOVING: 'processing', REMOVED: 'default',
};
const gatewayStatusColors: Record<GatewayServicePublicationStatus, string> = {
  PUBLISHING: 'processing',
  PUBLISHED: 'success',
  PUBLISH_FAILED: 'error',
  REMOVING: 'processing',
  REMOVE_FAILED: 'error',
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
  const selectedEngineId = Form.useWatch('engineId', filterForm);
  const [filters, setFilters] = useState<DataServiceFilters>(initialRouteState.filters);
  const [directorySelection, setDirectorySelection] = useState<DirectorySelection>(initialRouteState.directorySelection);
  const [page, setPage] = useState(initialRouteState.page);
  const [size, setSize] = useState(initialRouteState.size);
  const [messageApi, messageContext] = message.useMessage();
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
    setDirectorySelection(undefined);
    search({}, undefined);
  };

  const selectDirectory = (selection: DirectorySelection) => {
    setDirectorySelection(selection);
    search({
      ...filters,
      directoryIds: undefined,
      uncategorized: selection === null ? true : undefined,
    }, selection);
  };

  const openEditor = (path: string) => navigate(path, { state: { fromDataServiceList: true } });

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
      title: '名称', dataIndex: 'name', width: 170, ellipsis: true,
      render: (value: string, service: DataServiceSummary) => (
        <Button type="link" size="small" className="data-service-name-button" onClick={() => openEditor(`/dataservice/${service.id}`)}>{value}</Button>
      ),
    },
    { title: '编码', dataIndex: 'code', width: 165, ellipsis: true, render: (value: string) => <code>{value}</code> },
    { title: '类型', dataIndex: 'type', width: 105, render: (value: DataServiceType) => <Tag>{dataServiceTypeLabels[value]}</Tag> },
    { title: '数据来源', dataIndex: 'sourceName', width: 180, ellipsis: true },
    { title: 'Engine', dataIndex: 'engineId', width: 160, ellipsis: true, render: (value: string) => engineNames.get(value) ?? value },
    { title: '公开路由', dataIndex: 'routePath', width: 235, ellipsis: true, render: (value: string) => <code>{value}</code> },
    {
      title: '访问模式',
      dataIndex: 'accessMode',
      width: 105,
      render: (value: DataServiceAccessMode) => (
        <Tag color={value === 'SUBSCRIPTION_REQUIRED' ? 'purple' : 'default'}>
          {dataServiceAccessModeLabels[value]}
        </Tag>
      ),
    },
    {
      title: '服务状态', dataIndex: 'status', width: 100,
      render: (value: DataServiceStatus) => <Tag color={serviceStatusColors[value]}>{dataServiceStatusLabels[value]}</Tag>,
    },
    {
      title: '部署状态', key: 'deploymentStatus', width: 118,
      render: (_: unknown, dataService: DataServiceSummary) => dataService.deploymentStatus ? (
        <Tooltip title={dataService.deploymentError || undefined}>
          <Tag color={deploymentStatusColors[dataService.deploymentStatus]}>
            {dataServiceDeploymentStatusLabels[dataService.deploymentStatus]}
          </Tag>
        </Tooltip>
      ) : '—',
    },
    {
      title: '网关发布', key: 'gatewayBindings', width: 245,
      render: (_: unknown, dataService: DataServiceSummary) => dataService.gatewayBindings.length > 0 ? (
        <Space size={[2, 2]} wrap>
          {dataService.gatewayBindings.map((binding) => (
            <Space key={binding.id} size={[2, 2]} wrap>
              <Tooltip title={binding.lastError || binding.gatewayUrl || undefined}>
                <Tag color={gatewayStatusColors[binding.publicationStatus]}>
                  {gatewayProviderLabels[binding.provider]} · {gatewayServicePublicationStatusLabels[binding.publicationStatus]}
                </Tag>
              </Tooltip>
              <GatewayReconciliationTag state={binding} />
            </Space>
          ))}
        </Space>
      ) : '—',
    },
    { title: '版本', dataIndex: 'revision', width: 74, align: 'right' },
    { title: '更新时间', dataIndex: 'updatedAt', width: 180, render: (value: string) => formatDateTime(value) },
    {
      title: '操作', key: 'action', width: 142, fixed: 'right',
      render: (_: unknown, dataService: DataServiceSummary) => (
        <Space size={2}>
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
          {publishedGatewayBinding(dataService) && (
            <Tooltip title="复制网关访问 cURL"><Button type="text" size="small" aria-label={`复制${dataService.name}的网关访问 cURL`} icon={<CopyOutlined />} onClick={() => void copyCurl(dataService)} /></Tooltip>
          )}
          {canPublish && dataService.gatewayBindings.length > 0 && (
            <Tooltip title="立即对账网关状态">
              <Button
                type="text"
                size="small"
                aria-label={`对账${dataService.name}的网关状态`}
                icon={<AuditOutlined />}
                loading={reconcileMutation.isPending
                  && reconcileMutation.variables === dataService.id}
                onClick={() => void reconcileGateway(dataService)}
              />
            </Tooltip>
          )}
          {canPublish && dataService.status === 'ENABLED' && dataService.deploymentStatus === 'DEPLOYED' && (
            <Tooltip title={publishedGatewayBinding(dataService) ? '重新发布到网关' : '发布到网关'}><Button type="text" size="small" aria-label={`发布${dataService.name}到网关`} icon={<UploadOutlined />} loading={publishMutation.isPending && publishMutation.variables === dataService.id} onClick={() => void publish(dataService)} /></Tooltip>
          )}
          {canPublish && dataService.status === 'ENABLED' && dataService.gatewayBindings.length > 0 && (
            <Popconfirm
              title="取消发布到网关？"
              description={`取消后“${dataService.name}”将无法通过网关访问，Service Engine 保持运行。`}
              okText="取消发布"
              cancelText="返回"
              okButtonProps={{ danger: true }}
              onConfirm={() => void unpublish(dataService)}
            >
              <Tooltip title="取消发布">
                <Button
                  type="text"
                  size="small"
                  danger
                  aria-label={`取消发布${dataService.name}`}
                  icon={<RollbackOutlined />}
                  loading={unpublishMutation.isPending
                    && unpublishMutation.variables === dataService.id}
                />
              </Tooltip>
            </Popconfirm>
          )}
          {canPublish && dataService.status !== 'ENABLED' && (
            <Tooltip title={dataService.deploymentStatus === 'FAILED' || dataService.deploymentStatus === 'PENDING' ? '重试启用' : '启用'}><Button type="text" size="small" aria-label={`启用${dataService.name}`} icon={<PlayCircleOutlined />} loading={enableMutation.isPending && enableMutation.variables === dataService.id} onClick={() => void enable(dataService)} /></Tooltip>
          )}
          {canPublish && dataService.status !== 'ENABLED' && (dataService.deploymentStatus === 'FAILED' || dataService.deploymentStatus === 'PENDING') && (
            <Tooltip title="清理失败部署"><Button type="text" size="small" aria-label={`清理${dataService.name}的失败部署`} icon={<ClearOutlined />} loading={cleanupMutation.isPending && cleanupMutation.variables === dataService.id} onClick={() => void cleanup(dataService)} /></Tooltip>
          )}
          {canPublish && dataService.status === 'ENABLED' && (
            <Popconfirm
              title="停用数据服务？"
              description={`将先从所有网关撤回“${dataService.name}”，再从 Service Engine 移除。`}
              okText="停用"
              cancelText="返回"
              okButtonProps={{ danger: true }}
              onConfirm={() => void disable(dataService)}
            >
              <Tooltip title="停用">
                <Button
                  type="text"
                  size="small"
                  danger
                  aria-label={`停用${dataService.name}`}
                  icon={<StopOutlined />}
                  loading={disableMutation.isPending
                    && disableMutation.variables === dataService.id}
                />
              </Tooltip>
            </Popconfirm>
          )}
          {canDelete && dataService.gatewayBindings.length === 0 && (!dataService.deploymentStatus || dataService.deploymentStatus === 'REMOVED') && (
            <Popconfirm title="删除数据服务" description={`确认删除“${dataService.name}”吗？`} okText="删除" cancelText="取消" onConfirm={() => remove(dataService)}><Tooltip title="删除"><Button type="text" size="small" danger aria-label={`删除${dataService.name}`} icon={<DeleteOutlined />} /></Tooltip></Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const standardCreationDisabled = !canViewModels || !canViewEngines;
  const sqlCreationDisabled = standardCreationDisabled || !canViewDataSources;
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
    { key: 'script', disabled: true, label: creationLabel('脚本服务', '通过脚本编排自定义服务逻辑', '规划中') },
  ];

  return (
    <>
      {messageContext}
      <div className={canViewDirectories ? 'directory-management-layout' : 'page-stack'}>
        {canViewDirectories && <DirectoryTreePanel scope="DATA_SERVICE" tree={directoriesQuery.data ?? []} loading={directoriesQuery.isFetching} selection={directorySelection} canManage={canManageDirectories} onSelectionChange={selectDirectory} />}
        <Card className="management-card">
          <div className="management-toolbar">
            <Form<DataServiceFilters> form={filterForm} initialValues={initialRouteState.filters} layout="inline" className="management-filter-form" onFinish={search}>
              <Form.Item name="keyword" label="名称/编码"><Input allowClear placeholder="按名称或编码筛选" className="data-source-keyword-input" /></Form.Item>
              <Form.Item name="status" label="服务状态"><Select allowClear placeholder="全部" options={statusOptions} className="data-source-filter-select" /></Form.Item>
              <Form.Item name="type" label="类型"><Select allowClear placeholder="全部" options={typeOptions} className="data-source-filter-select" /></Form.Item>
              <Popover
                trigger="click"
                placement="bottomLeft"
                content={(
                  <div className="advanced-filter-popover">
                    <div className="advanced-filter-title">更多筛选</div>
                    <Form.Item name="engineId" label="Service Engine"><Select allowClear showSearch optionFilterProp="label" options={(enginesQuery.data?.content ?? []).map((engine) => ({ value: engine.id, label: engine.name }))} className="advanced-filter-select" /></Form.Item>
                    <div className="advanced-filter-actions"><Button type="link" size="small" htmlType="button" onClick={() => filterForm.setFieldsValue({ engineId: undefined })}>清空更多条件</Button></div>
                  </div>
                )}
              >
                <Badge count={Number(Boolean(selectedEngineId))} size="small" offset={[-2, 2]}><Button icon={<FilterOutlined />}>更多</Button></Badge>
              </Popover>
            </Form>
            <Space size={4} className="management-toolbar-actions">
              <Button type="primary" onClick={() => filterForm.submit()}>查询</Button>
              <Button onClick={reset}>重置</Button>
              <Button icon={<ReloadOutlined />} onClick={() => void dataServicesQuery.refetch()}>刷新</Button>
              {canCreate && (
                <Dropdown
                  trigger={['click']}
                  menu={{
                    items: creationItems,
                    onClick: ({ key }) => {
                      if (key === 'standard') openEditor('/dataservice/new/standard');
                      if (key === 'sql') openEditor('/dataservice/new/sql');
                    },
                  }}
                >
                  <Button type="primary" icon={<PlusOutlined />}>新建服务 <DownOutlined /></Button>
                </Dropdown>
              )}
            </Space>
          </div>
          {dataServicesQuery.isError && <Alert type="error" showIcon message="数据服务列表加载失败" action={<Button onClick={() => void dataServicesQuery.refetch()}>重试</Button>} style={{ marginBottom: 12 }} />}
          <Table<DataServiceSummary>
            size="small" className="management-table" rowKey="id" columns={columns}
            dataSource={dataServicesQuery.data?.content ?? []} loading={dataServicesQuery.isFetching}
            scroll={{ x: 1880, y: '100%' }}
            pagination={{ current: page + 1, pageSize: size, total: dataServicesQuery.data?.totalElements ?? 0, size: 'small', position: ['bottomRight'], hideOnSinglePage: false, showSizeChanger: true, showTotal: (total) => `共 ${total} 项` }}
            onChange={(pagination) => {
              const nextSize = pagination.pageSize ?? DEFAULT_PAGE_SIZE;
              const nextPage = nextSize === size ? (pagination.current ?? 1) - 1 : 0;
              setPage(nextPage);
              setSize(nextSize);
              syncRoute(filters, directorySelection, nextPage, nextSize);
            }}
          />
        </Card>
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
