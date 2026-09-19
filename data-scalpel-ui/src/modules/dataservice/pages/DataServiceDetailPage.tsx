import { writeClipboardText } from '../../../shared/browser/writeClipboardText';
import {
  ArrowLeftOutlined,
  AuditOutlined,
  ClearOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  MoreOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  RollbackOutlined,
  StopOutlined,
  TeamOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { Button, Dropdown, Modal, Result, Skeleton, Space, Tabs, Tag, Tooltip, message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useBlocker, useLocation, useNavigate, useParams, useSearchParams, type BlockerFunction } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useDataSource } from '../../datasource';
import { useDirectoryTree, type DirectoryTreeNode } from '../../directory';
import { useServiceEngines } from '../../serviceengine';
import { useCurrentUser } from '../../system';
import { DataServiceBasicPanel } from '../components/DataServiceBasicPanel';
import { DataServiceBasicDrawer } from '../components/DataServiceBasicDrawer';
import { DataServiceDefinitionPanel } from '../components/DataServiceDefinitionPanel';
import { DataServiceLineagePanel } from '../components/DataServiceLineagePanel';
import { DataServiceRelatedModelsPanel } from '../components/DataServiceRelatedModelsPanel';
import { DataServiceRuntimePanel } from '../components/DataServiceRuntimePanel';
import { DataServiceSpatialPreviewPanel } from '../components/DataServiceSpatialPreviewPanel';
import { DataServiceSubscriptionsDrawer } from '../components/DataServiceSubscriptionsDrawer';
import { PublishDataServiceModal } from '../components/PublishDataServiceModal';
import { DataServiceTypeIcon } from '../components/DataServiceTypeIcon';
import {
  useCleanupDataServiceDeployment,
  useDataService,
  useDeleteDataService,
  useDisableDataService,
  useEnableDataService,
  usePublishDataService,
  useReconcileDataServiceGateway,
  useUnpublishDataService,
} from '../hooks/useDataServices';
import { useDataServiceRelatedModels } from '../hooks/useDataServiceRelatedModels';
import { gatewayProviderLabels } from '../model/apiConsumer';
import {
  dataServiceDeploymentStatusLabels,
  dataServiceStatusLabels,
  dataServiceTypeLabels,
  gatewayServicePublicationStatusLabels,
  type DataServiceDeploymentStatus,
  type DataServiceDetail,
  type DataServiceStatus,
  type PublishDataServiceRequest,
  type GatewayServicePublicationStatus,
} from '../model/dataService';
import { buildDataServiceCurlCommand } from '../model/dataServiceCurl';
import { normalizeDataServiceDetailTab, type DataServiceDetailTabKey } from '../model/dataServiceDetail';
import { gatewayOperationError, publishedGatewayBinding } from '../model/dataServiceGateway';
import './dataServiceDetail.css';

interface DataServiceDetailLocationState {
  fromDataServiceList?: boolean;
  returnTo?: string;
  returnLabel?: string;
}

const serviceStatusColors: Record<DataServiceStatus, string> = {
  DRAFT: 'default',
  ENABLED: 'success',
  DISABLED: 'warning',
};

const deploymentStatusColors: Record<DataServiceDeploymentStatus, string> = {
  PENDING: 'processing',
  DEPLOYED: 'success',
  FAILED: 'error',
  REMOVING: 'processing',
  REMOVED: 'default',
};

const gatewayStatusColors: Record<GatewayServicePublicationStatus, string> = {
  PUBLISHING: 'processing',
  PUBLISHED: 'success',
  PUBLISH_FAILED: 'error',
  REMOVING: 'processing',
  REMOVE_FAILED: 'error',
};

const problemMessage = (error: unknown, fallback: string) => (
  error instanceof ApiError ? error.problem?.detail ?? error.message : fallback
);

const editableDataService = (dataService: DataServiceDetail) => (
  dataService.status !== 'ENABLED'
  && (!dataService.deploymentStatus || dataService.deploymentStatus === 'REMOVED')
);

export const DataServiceDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const [subscriptionsOpen, setSubscriptionsOpen] = useState(false);
  const [basicEditorOpen, setBasicEditorOpen] = useState(false);
  const [publishTarget, setPublishTarget] = useState<DataServiceDetail | null>(null);
  const [spatialStyleDirty, setSpatialStyleDirty] = useState(false);
  const allowStyleNavigationRef = useRef(false);
  const styleBlockerPromptOpenRef = useRef(false);
  const detailQuery = useDataService(id, Boolean(id));
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canUpdate = permissions.has('service.update');
  const canDelete = permissions.has('service.delete');
  const canPublish = permissions.has('service.publish');
  const canViewDirectories = permissions.has('directory.view');
  const canViewModels = permissions.has('model.view');
  const canViewTasks = permissions.has('task.view');
  const permissionsLoaded = Boolean(currentUserQuery.data);
  const canViewDataSources = permissions.has('datasource.view');
  const canViewEngines = permissions.has('service.engine.view');
  const dataService = detailQuery.data;
  const relatedModels = useDataServiceRelatedModels(dataService, canViewModels);
  const sourceDataSourceId = dataService?.sqlDefinition?.dataSourceId
    ?? dataService?.scriptDefinition?.dataSourceId;
  const dataSourceQuery = useDataSource(sourceDataSourceId, canViewDataSources && Boolean(sourceDataSourceId));
  const directoriesQuery = useDirectoryTree('DATA_SERVICE', canViewDirectories);
  const enginesQuery = useServiceEngines({ page: 0, size: 500, sort: 'code' }, canViewEngines);
  const enableMutation = useEnableDataService();
  const disableMutation = useDisableDataService();
  const publishMutation = usePublishDataService();
  const unpublishMutation = useUnpublishDataService();
  const reconcileMutation = useReconcileDataServiceGateway();
  const cleanupMutation = useCleanupDataServiceDeployment();
  const deleteMutation = useDeleteDataService();
  const requestedTab = normalizeDataServiceDetailTab(searchParams.get('tab'));
  const lineageUnavailable = requestedTab === 'lineage' && (!canViewModels || !canViewTasks);
  const cartographyUnavailable = requestedTab === 'cartography'
    && Boolean(dataService)
    && dataService?.type !== 'SPATIAL_SERVICE';
  const activeTab = cartographyUnavailable || requestedTab === 'lineage' && (!permissionsLoaded || lineageUnavailable)
    ? 'basic'
    : requestedTab;
  const styleBlocker = useBlocker(useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) => !allowStyleNavigationRef.current && spatialStyleDirty && (
      currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search
    ),
    [spatialStyleDirty],
  ));

  useEffect(() => {
    if (cartographyUnavailable || permissionsLoaded && lineageUnavailable) {
      setSearchParams({ tab: 'basic' }, { replace: true });
    }
  }, [cartographyUnavailable, lineageUnavailable, permissionsLoaded, setSearchParams]);

  useEffect(() => {
    if (styleBlocker.state !== 'blocked' || styleBlockerPromptOpenRef.current) return;
    styleBlockerPromptOpenRef.current = true;
    modalApi.confirm({
      title: '放弃未保存的样式修改？',
      content: '当前在线配图还没有保存，离开页面后修改会丢失。',
      okText: '放弃并离开',
      okButtonProps: { danger: true },
      cancelText: '继续编辑',
      onOk: () => {
        allowStyleNavigationRef.current = true;
        setSpatialStyleDirty(false);
        styleBlocker.proceed();
        styleBlockerPromptOpenRef.current = false;
      },
      onCancel: () => {
        styleBlocker.reset();
        styleBlockerPromptOpenRef.current = false;
      },
    });
  }, [modalApi, styleBlocker]);

  const directoryNameById = useMemo(() => {
    const names = new Map<string, string>();
    const collect = (nodes: DirectoryTreeNode[]) => nodes.forEach((node) => {
      names.set(node.id, node.name);
      collect(node.children);
    });
    collect(directoriesQuery.data ?? []);
    return names;
  }, [directoriesQuery.data]);
  const engineNames = useMemo(
    () => new Map((enginesQuery.data?.content ?? []).map((engine) => [engine.id, engine.name])),
    [enginesQuery.data?.content],
  );
  const enginesById = useMemo(
    () => new Map((enginesQuery.data?.content ?? []).map((engine) => [engine.id, engine])),
    [enginesQuery.data?.content],
  );
  const sourceName = dataSourceQuery.data?.name ?? relatedModels[0]?.storageDataSourceName;

  const backToList = () => {
    if (spatialStyleDirty) {
      modalApi.confirm({
        title: '放弃未保存的样式修改？',
        content: '当前在线配图还没有保存，离开页面后修改会丢失。',
        okText: '放弃并离开',
        okButtonProps: { danger: true },
        cancelText: '继续编辑',
        onOk: () => {
          allowStyleNavigationRef.current = true;
          setSpatialStyleDirty(false);
          navigateBackToList();
        },
      });
      return;
    }
    navigateBackToList();
  };

  const navigateBackToList = () => {
    const state = location.state as DataServiceDetailLocationState | null;
    if (state?.returnTo) {
      navigate(state.returnTo);
      return;
    }
    if (state?.fromDataServiceList) navigate(-1);
    else navigate('/dataservice');
  };

  const refreshDetail = () => void detailQuery.refetch();

  const enable = async (target: DataServiceDetail) => {
    try {
      const response = await enableMutation.mutateAsync(target.id);
      if (response.status === 'ENABLED' && response.deploymentStatus === 'DEPLOYED') {
        messageApi.success(target.status === 'DISABLED' ? '数据服务已重新启用' : '数据服务已启用');
      } else {
        messageApi.error(response.deploymentError || (target.type === 'SPATIAL_SERVICE'
          ? 'GeoServer 未确认图层发布，请查看运行与发布'
          : 'Engine 未确认启用，请查看运行与发布'));
      }
    } catch (error) {
      messageApi.error(problemMessage(error, '启用数据服务失败'));
    }
  };

  const disable = (target: DataServiceDetail) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '停用数据服务？',
    content: target.type === 'SPATIAL_SERVICE'
      ? `将从 GeoServer 删除“${target.name}”对应的 Layer 和 FeatureType，保留共享 DataStore 与 Workspace。`
      : `将先从所有网关撤回“${target.name}”，再从 Service Engine 移除；停用成功后才能修改定义。`,
    okText: '停用',
    cancelText: '返回',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        const response = await disableMutation.mutateAsync(target.id);
        if (response.status === 'DISABLED' && response.deploymentStatus === 'REMOVED') {
          messageApi.success('数据服务已停用');
        } else {
          messageApi.error(gatewayOperationError(response) || response.deploymentError || '服务未确认停用结果');
        }
      } catch (error) {
        messageApi.error(problemMessage(error, '停用数据服务失败'));
        throw error;
      }
    },
  });

  const publish = async (target: DataServiceDetail, request: PublishDataServiceRequest) => {
    try {
      const response = await publishMutation.mutateAsync({ id: target.id, request });
      const binding = publishedGatewayBinding(response);
      if (binding) {
        setPublishTarget(null);
        messageApi.success(`数据服务已发布到 ${gatewayProviderLabels[binding.provider]}`);
      }
      else messageApi.error(gatewayOperationError(response) || '网关未确认发布结果');
    } catch (error) {
      messageApi.error(problemMessage(error, '发布到网关失败'));
    }
  };

  const unpublish = (target: DataServiceDetail) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '取消发布到网关？',
    content: `取消后“${target.name}”将无法通过网关访问，Service Engine 保持运行。`,
    okText: '取消发布',
    cancelText: '返回',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        const response = await unpublishMutation.mutateAsync(target.id);
        if (response.status === 'ENABLED'
            && response.deploymentStatus === 'DEPLOYED'
            && response.gatewayBindings.length === 0) {
          messageApi.success('已取消网关发布，Service Engine 保持运行');
        } else {
          messageApi.error(gatewayOperationError(response) || '网关未确认取消发布结果');
        }
      } catch (error) {
        messageApi.error(problemMessage(error, '取消网关发布失败'));
        throw error;
      }
    },
  });

  const reconcile = async (target: DataServiceDetail) => {
    try {
      const response = await reconcileMutation.mutateAsync(target.id);
      const drifted = response.gatewayBindings.filter((binding) => binding.reconciliationStatus === 'DRIFTED');
      const failed = response.gatewayBindings.filter((binding) => binding.reconciliationStatus === 'CHECK_FAILED');
      if (drifted.length) messageApi.warning(`检测到 ${drifted.length} 个网关绑定漂移`);
      else if (failed.length) messageApi.error(`有 ${failed.length} 个网关绑定检查失败`);
      else messageApi.success('网关状态一致');
    } catch (error) {
      messageApi.error(problemMessage(error, '网关状态对账失败'));
    }
  };

  const cleanup = async (target: DataServiceDetail) => {
    try {
      const response = await cleanupMutation.mutateAsync(target.id);
      if (response.deploymentStatus === 'REMOVED') messageApi.success('失败或不确定的部署已清理');
      else messageApi.error(response.deploymentError || 'Engine 未确认清理结果');
    } catch (error) {
      messageApi.error(problemMessage(error, '清理失败部署失败'));
    }
  };

  const copyCurl = async (target: DataServiceDetail) => {
    const binding = publishedGatewayBinding(target);
    if (!binding?.gatewayUrl) {
      messageApi.error('当前 Revision 尚未成功发布到网关');
      return;
    }
    try {
      await writeClipboardText(buildDataServiceCurlCommand(
        binding.gatewayUrl,
        '',
        { ...target, accessMode: binding.accessMode },
      ));
      messageApi.success('网关访问 cURL 已复制');
    } catch {
      messageApi.error('复制失败，请检查浏览器的剪贴板权限');
    }
  };

  const remove = (target: DataServiceDetail) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '删除数据服务',
    content: `确认删除“${target.name}”吗？`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteMutation.mutateAsync(target.id);
        messageApi.success('数据服务已删除');
        backToList();
      } catch (error) {
        messageApi.error(problemMessage(error, '删除数据服务失败'));
        throw error;
      }
    },
  });

  if (!id) {
    return <Result status="404" title="数据服务地址无效" extra={<Button type="primary" onClick={() => navigate('/dataservice')}>返回数据服务列表</Button>} />;
  }
  if (detailQuery.isPending) {
    return <div className="data-service-detail-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }
  if (!dataService || detailQuery.error) {
    const notFound = detailQuery.error instanceof ApiError && detailQuery.error.status === 404;
    return (
      <Result
        status={notFound ? '404' : 'error'}
        title={notFound ? '数据服务不存在' : '数据服务详情加载失败'}
        subTitle={detailQuery.error ? problemMessage(detailQuery.error, '请稍后重试') : '请确认数据服务是否存在。'}
        extra={<Space><Button onClick={backToList}>返回列表</Button><Button type="primary" onClick={refreshDetail}>重试</Button></Space>}
      />
    );
  }

  const editable = editableDataService(dataService);
  const publishedBinding = publishedGatewayBinding(dataService);
  const commandLoading = enableMutation.isPending || disableMutation.isPending;
  const gatewayCommandLoading = publishMutation.isPending || unpublishMutation.isPending;
  const canRemove = canDelete
    && dataService.gatewayBindings.length === 0
    && (!dataService.deploymentStatus || dataService.deploymentStatus === 'REMOVED');
  const moreItems: NonNullable<MenuProps['items']> = [
    ...(dataService.type !== 'SPATIAL_SERVICE' && publishedBinding?.accessMode === 'SUBSCRIPTION_REQUIRED'
      ? [{ key: 'subscriptions', label: '订阅消费者', icon: <TeamOutlined /> }] : []),
    ...(dataService.type !== 'SPATIAL_SERVICE' && publishedBinding ? [{ key: 'curl', label: '复制网关访问 cURL', icon: <CopyOutlined /> }] : []),
    ...(dataService.type !== 'SPATIAL_SERVICE' && canPublish && dataService.gatewayBindings.length > 0
      ? [{ key: 'reconcile', label: '对账网关状态', icon: <AuditOutlined /> }] : []),
    ...(dataService.type !== 'SPATIAL_SERVICE' && canPublish && publishedBinding
      ? [{ key: 'republish', label: '重新发布到网关', icon: <UploadOutlined /> }] : []),
    ...(canPublish && dataService.status !== 'ENABLED'
        && (dataService.deploymentStatus === 'FAILED' || dataService.deploymentStatus === 'PENDING')
      ? [{ key: 'cleanup', label: '清理失败部署', icon: <ClearOutlined /> }] : []),
    ...(canRemove
      ? [{ type: 'divider' as const }, { key: 'delete', label: '删除数据服务', icon: <DeleteOutlined />, danger: true }] : []),
  ];
  const tabItems = [
    {
      key: 'basic',
      label: '基本信息',
      children: (
        <DataServiceBasicPanel
          dataService={dataService}
          directoryName={dataService.directoryId ? directoryNameById.get(dataService.directoryId) : undefined}
          engineName={engineNames.get(dataService.engineId)}
          sourceName={sourceName ?? undefined}
        />
      ),
    },
    {
      key: 'definition',
      label: dataService.definitionConfigured ? `服务定义 v${dataService.definitionVersion}` : '服务定义 · 未配置',
      children: (
        <DataServiceDefinitionPanel
          dataService={dataService}
          sourceName={sourceName ?? undefined}
          relatedModels={relatedModels}
          canViewModels={canViewModels}
          canUpdate={canUpdate}
          editable={editable}
        />
      ),
    },
    ...(dataService.type === 'SPATIAL_SERVICE' ? [{
      key: 'cartography',
      label: '在线制图',
      children: (
        <DataServiceSpatialPreviewPanel
          serviceId={dataService.id}
          status={dataService.status}
          deploymentStatus={dataService.deploymentStatus}
          definitionConfigured={dataService.definitionConfigured}
          canUpdate={canUpdate}
          canPublish={canPublish}
          enableLoading={commandLoading}
          onEnable={() => void enable(dataService)}
          onDirtyChange={setSpatialStyleDirty}
        />
      ),
    }] : []),
    {
      key: 'models',
      label: `关联模型 ${relatedModels.length}`,
      children: <DataServiceRelatedModelsPanel dataService={dataService} relatedModels={relatedModels} canViewModels={canViewModels} />,
    },
    ...(dataService.type !== 'SPATIAL_SERVICE' && canViewModels && canViewTasks ? [{
      key: 'lineage',
      label: '血缘分析',
      children: <DataServiceLineagePanel dataService={dataService} />,
    }] : []),
    {
      key: 'runtime',
      label: '运行与发布',
      children: <DataServiceRuntimePanel dataService={dataService} engine={enginesById.get(dataService.engineId)} onCopyCurl={() => void copyCurl(dataService)} />,
    },
  ];

  return (
    <div className="data-service-detail-page business-detail-page">
      {messageContext}
      {modalContext}
      <header className="data-service-detail-header business-detail-header">
        <div className="data-service-detail-identity">
          <div className="data-service-detail-title-row">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={backToList}>{(location.state as DataServiceDetailLocationState | null)?.returnLabel ?? '返回列表'}</Button>
            <span className="data-service-detail-type-icon business-detail-resource-icon business-detail-resource-icon-blue"><DataServiceTypeIcon type={dataService.type} /></span>
            <span className="data-service-detail-title">{dataService.name}</span>
            <code>{dataService.code}</code>
            <Tag>{dataServiceTypeLabels[dataService.type]}</Tag>
            <Tag color={serviceStatusColors[dataService.status]}>{dataServiceStatusLabels[dataService.status]}</Tag>
            <Tag color={dataService.definitionConfigured ? 'success' : 'default'}>
              {dataService.definitionConfigured ? `定义 v${dataService.definitionVersion}` : '定义未配置'}
            </Tag>
            {dataService.deploymentStatus && (
              <Tag color={deploymentStatusColors[dataService.deploymentStatus]}>
                {dataServiceDeploymentStatusLabels[dataService.deploymentStatus]}
              </Tag>
            )}
            {dataService.gatewayBindings.map((binding) => (
              <Tag key={binding.id} color={gatewayStatusColors[binding.publicationStatus]}>
                {gatewayProviderLabels[binding.provider]} · {gatewayServicePublicationStatusLabels[binding.publicationStatus]}
              </Tag>
            ))}
          </div>
          <div className="data-service-detail-subtitle">
            <code>{dataService.type === 'SPATIAL_SERVICE'
              ? `${enginesById.get(dataService.engineId)?.geoServerWorkspace ?? 'datascalpel'}:svc_${dataService.code}`
              : dataService.contextPath}</code>
            <span>·</span>
            <span>{engineNames.get(dataService.engineId) ?? dataService.engineId}</span>
            <span>·</span>
            <span>revision {dataService.revision}</span>
          </div>
        </div>
        <Space size={4} wrap>
          <Tooltip title="刷新数据服务">
            <Button icon={<ReloadOutlined />} aria-label="刷新数据服务详情" onClick={refreshDetail} />
          </Tooltip>
          {canUpdate && (
            <Tooltip title={editable ? '编辑数据服务' : '请先停用服务并完成部署清理'}>
              <span>
                <Button
                  icon={<EditOutlined />}
                  disabled={!editable}
                  onClick={() => setBasicEditorOpen(true)}
                >
                  编辑
                </Button>
              </span>
            </Tooltip>
          )}
          {canPublish && dataService.status !== 'ENABLED' && (
            <Tooltip title={!dataService.definitionConfigured ? '请先配置服务定义' : undefined}>
              <span>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  loading={commandLoading}
                  disabled={!dataService.definitionConfigured}
                  onClick={() => void enable(dataService)}
                >
                  {dataService.deploymentStatus === 'FAILED' || dataService.deploymentStatus === 'PENDING' ? '重试启用' : '启用'}
                </Button>
              </span>
            </Tooltip>
          )}
          {canPublish && dataService.status === 'ENABLED' && (
            <Button danger icon={<StopOutlined />} loading={commandLoading} onClick={() => disable(dataService)}>停用</Button>
          )}
          {dataService.type !== 'SPATIAL_SERVICE' && canPublish && dataService.status === 'ENABLED' && dataService.deploymentStatus === 'DEPLOYED' && !publishedBinding && (
            <Button type="primary" icon={<UploadOutlined />} loading={gatewayCommandLoading} onClick={() => setPublishTarget(dataService)}>发布到网关</Button>
          )}
          {dataService.type !== 'SPATIAL_SERVICE' && canPublish && publishedBinding && (
            <Button danger icon={<RollbackOutlined />} loading={gatewayCommandLoading} onClick={() => unpublish(dataService)}>取消发布</Button>
          )}
          {moreItems.length > 0 && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: moreItems,
                onClick: ({ key }) => {
                  if (key === 'subscriptions') setSubscriptionsOpen(true);
                  if (key === 'curl') void copyCurl(dataService);
                  if (key === 'reconcile') void reconcile(dataService);
                  if (key === 'republish') setPublishTarget(dataService);
                  if (key === 'cleanup') void cleanup(dataService);
                  if (key === 'delete') remove(dataService);
                },
              }}
            >
              <Button icon={<MoreOutlined />} aria-label="数据服务更多操作" />
            </Dropdown>
          )}
        </Space>
      </header>
      <Tabs
        activeKey={activeTab}
        className="data-service-detail-tabs business-detail-tabs"
        destroyOnHidden
        items={tabItems}
        onChange={(key) => {
          const changeTab = () => setSearchParams(
            { tab: key as DataServiceDetailTabKey },
            { replace: true, state: location.state },
          );
          if (activeTab === 'cartography' && key !== 'cartography' && spatialStyleDirty) {
            modalApi.confirm({
              title: '放弃未保存的样式修改？',
              content: '切换页签后，当前在线配图修改会丢失。',
              okText: '放弃并切换',
              okButtonProps: { danger: true },
              cancelText: '继续编辑',
              onOk: () => {
                allowStyleNavigationRef.current = true;
                setSpatialStyleDirty(false);
                changeTab();
                window.setTimeout(() => { allowStyleNavigationRef.current = false; }, 0);
              },
            });
            return;
          }
          changeTab();
        }}
      />
      <DataServiceSubscriptionsDrawer
        open={subscriptionsOpen}
        dataService={dataService}
        canManage={canPublish}
        onClose={() => setSubscriptionsOpen(false)}
      />
      <DataServiceBasicDrawer
        open={basicEditorOpen}
        type={null}
        dataService={dataService}
        canViewDirectories={canViewDirectories}
        canViewEngines={canViewEngines}
        onClose={() => setBasicEditorOpen(false)}
        onUpdated={() => setBasicEditorOpen(false)}
      />
      <PublishDataServiceModal
        service={publishTarget}
        loading={publishMutation.isPending}
        onCancel={() => setPublishTarget(null)}
        onPublish={(request) => publish(publishTarget as DataServiceDetail, request)}
      />
    </div>
  );
};
