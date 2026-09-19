import {
  ApiOutlined,
  DeleteOutlined,
  EditOutlined,
  MoreOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Modal, Result, Skeleton, Space, Tabs, Tag, Tooltip, message } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useDirectoryTree, type DirectoryTreeNode } from '../../directory';
import { useCurrentUser } from '../../system';
import { ApiResourcePanel } from '../components/ApiResourceListDrawer';
import { ConnectionTestResultModal } from '../components/ConnectionTestResultModal';
import { DataSourceBasicPanel } from '../components/DataSourceBasicPanel';
import { DataSourceDrawer } from '../components/DataSourceDrawer';
import { DataSourceMetadataPanel } from '../components/DataSourceMetadataDrawer';
import { TdEngineTmqTopicPanel } from '../components/TdEngineTmqTopicPanel';
import {
  DataSourceRelatedModelsPanel,
  DataSourceRelatedServicesPanel,
  DataSourceRelatedTasksPanel,
} from '../components/DataSourceRelationPanels';
import { DataSourceTypeIcon } from '../components/DataSourceTypeIcon';
import { KafkaTopicPanel } from '../components/KafkaTopicPanel';
import { SpatialFeatureResourcePanel } from '../components/SpatialFeatureResourceListDrawer';
import {
  useDataSource,
  useDataSourceTypes,
  useDeleteDataSource,
  useTestSavedDataSourceConnection,
} from '../hooks/useDataSources';
import {
  dataSourcePurposeLabels,
  dataSourceTypeLabels,
  type ConnectionTestResult,
  type DataSource,
  type DataSourceTypeDefinition,
} from '../model/dataSource';

type DataSourceDetailTabKey = 'basic' | 'resources' | 'models' | 'tasks' | 'services';

interface DataSourceDetailLocationState {
  fromDataSourceList?: boolean;
}

type TdEngineResourceTabKey = 'supertables' | 'tmq-topics';

const TdEngineResourceTabs = ({
  dataSource,
  active,
}: {
  dataSource: DataSource;
  active: boolean;
}) => {
  const [resourceTab, setResourceTab] = useState<TdEngineResourceTabKey>('supertables');
  return (
    <Tabs
      className="data-source-inner-resource-tabs"
      activeKey={resourceTab}
      onChange={(key) => setResourceTab(key as TdEngineResourceTabKey)}
      items={[
        {
          key: 'supertables',
          label: '超级表',
          children: <DataSourceMetadataPanel dataSource={dataSource} active={active && resourceTab === 'supertables'} />,
        },
        {
          key: 'tmq-topics',
          label: 'TMQ Topic',
          children: <TdEngineTmqTopicPanel dataSource={dataSource} active={active && resourceTab === 'tmq-topics'} />,
        },
      ]}
    />
  );
};

const normalizeTab = (value: string | null): DataSourceDetailTabKey => {
  if (value === 'resources' || value === 'models' || value === 'tasks' || value === 'services') return value;
  return 'basic';
};


const connectionSummary = (dataSource: DataSource) => {
  const connection = dataSource.connection;
  switch (connection.kind) {
    case 'JDBC':
      return `${connection.host}:${connection.port}/${connection.databaseName}${connection.schemaName ? ` · ${connection.schemaName}` : ''}`;
    case 'KAFKA':
      return connection.bootstrapServers;
    case 'S3':
      return `${connection.endpoint} · ${connection.bucket}${connection.rootPrefix ? `/${connection.rootPrefix}` : ''}`;
    case 'HTTP_API':
      return connection.configuration.baseUrl;
  }
};

const connectionTarget = (dataSource: DataSource) => {
  const connection = dataSource.connection;
  if (connection.kind === 'JDBC') {
    return `${dataSource.name} · ${connection.host}:${connection.port}/${connection.databaseName}`;
  }
  return `${dataSource.name} · ${connectionSummary(dataSource)}`;
};

const resourceTabLabel = (definition: DataSourceTypeDefinition) => {
  switch (definition.resourceBrowserKind) {
    case 'JDBC_TABLES': return '数据表';
    case 'TDENGINE_SUPERTABLES': return '超级表';
    case 'KAFKA_TOPICS': return 'Topic';
    case 'API_RESOURCES': return 'API 资源';
    case 'SPATIAL_RESOURCES': return '空间资源';
    case 'NONE': return '资源';
  }
};

export const DataSourceDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [editing, setEditing] = useState(false);
  const [testFailure, setTestFailure] = useState<{
    result: ConnectionTestResult;
    targetLabel: string;
  } | null>(null);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const detailQuery = useDataSource(id, Boolean(id));
  const typeDefinitionsQuery = useDataSourceTypes();
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const permissionsLoaded = Boolean(currentUserQuery.data);
  const canViewDirectories = permissions.has('directory.view');
  const canCreate = permissions.has('datasource.create');
  const canUpdate = permissions.has('datasource.update');
  const canDelete = permissions.has('datasource.delete');
  const canTest = permissions.has('datasource.test');
  const canReadMetadata = permissions.has('datasource.metadata');
  const canViewModels = permissions.has('model.view');
  const canViewTasks = permissions.has('task.view');
  const canViewServices = permissions.has('service.view');
  const directoriesQuery = useDirectoryTree('DATA_SOURCE', canViewDirectories);
  const deleteMutation = useDeleteDataSource();
  const testMutation = useTestSavedDataSourceConnection();
  const dataSource = detailQuery.data;
  const typeDefinition = typeDefinitionsQuery.data?.find((definition) => definition.id === dataSource?.type);
  const requestedTab = normalizeTab(searchParams.get('tab'));
  const canBrowseResources = Boolean(typeDefinition && typeDefinition.resourceBrowserKind !== 'NONE'
    && (!['JDBC_TABLES', 'TDENGINE_SUPERTABLES'].includes(typeDefinition.resourceBrowserKind) || canReadMetadata));
  const availableTabs = new Set<DataSourceDetailTabKey>([
    'basic',
    ...(canBrowseResources ? ['resources' as const] : []),
    ...(canViewModels ? ['models' as const] : []),
    ...(canViewTasks ? ['tasks' as const] : []),
    ...(canViewServices ? ['services' as const] : []),
  ]);
  const activeTab: DataSourceDetailTabKey = availableTabs.has(requestedTab) ? requestedTab : 'basic';


  useEffect(() => {
    if (!permissionsLoaded || !dataSource || typeDefinitionsQuery.isPending) return;
    if (requestedTab !== activeTab) {
      setSearchParams({ tab: activeTab }, { replace: true, state: location.state });
    }
  }, [activeTab, dataSource, location.state, permissionsLoaded, requestedTab, setSearchParams, typeDefinitionsQuery.isPending]);

  const directoryNameById = useMemo(() => {
    const names = new Map<string, string>();
    const collect = (nodes: DirectoryTreeNode[]) => nodes.forEach((node) => {
      names.set(node.id, node.name);
      collect(node.children);
    });
    collect(directoriesQuery.data ?? []);
    return names;
  }, [directoriesQuery.data]);

  const backToList = () => {
    const state = location.state as DataSourceDetailLocationState | null;
    if (state?.fromDataSourceList) navigate(-1);
    else navigate('/datasource');
  };

  const refreshDetail = () => {
    void Promise.all([detailQuery.refetch(), typeDefinitionsQuery.refetch()]);
  };

  const testConnection = async (target: DataSource) => {
    try {
      setTestFailure(null);
      const result = await testMutation.mutateAsync(target.id);
      if (result.success) {
        messageApi.success(`${target.name}：${result.message}（${result.elapsedMs} ms）`);
      } else {
        setTestFailure({ result, targetLabel: connectionTarget(target) });
      }
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '测试连接失败');
    }
  };

  const remove = (target: DataSource) => modalApi.confirm({
    rootClassName: 'business-overlay business-modal-overlay',
    title: '删除数据源',
    content: `确认删除“${target.name}”吗？被任务直接引用的数据源不能删除。`,
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteMutation.mutateAsync(target.id);
        messageApi.success('数据源已删除');
        navigate('/datasource', { replace: true });
      } catch (error) {
        messageApi.error(error instanceof ApiError ? error.message : '删除数据源失败');
        throw error;
      }
    },
  });

  if (!id) {
    return <Result status="404" title="数据源地址无效" extra={<Button type="primary" onClick={() => navigate('/datasource')}>返回数据源列表</Button>} />;
  }

  if (detailQuery.isPending) {
    return <div className="data-source-detail-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }

  if (!dataSource || detailQuery.error) {
    const status = detailQuery.error instanceof ApiError && detailQuery.error.status === 404
      ? '404'
      : detailQuery.error instanceof ApiError && detailQuery.error.status === 403 ? '403' : 'error';
    return (
      <Result
        status={status}
        title={status === '404' ? '数据源不存在' : status === '403' ? '无权查看数据源' : '数据源详情加载失败'}
        subTitle={detailQuery.error instanceof ApiError ? detailQuery.error.message : '请稍后重试。'}
        extra={(
          <Space>
            <Button onClick={backToList}>返回列表</Button>
            <Button type="primary" onClick={() => void detailQuery.refetch()}>重试</Button>
          </Space>
        )}
      />
    );
  }

  const resourcePanel = (() => {
    switch (typeDefinition?.resourceBrowserKind) {
      case 'JDBC_TABLES':
        return <DataSourceMetadataPanel dataSource={dataSource} active={activeTab === 'resources'} />;
      case 'TDENGINE_SUPERTABLES':
        return dataSource.type === 'TDENGINE_WEBSOCKET'
          && typeDefinition.capabilities.includes('TMQ_SUBSCRIBE')
          ? <TdEngineResourceTabs dataSource={dataSource} active={activeTab === 'resources'} />
          : <DataSourceMetadataPanel dataSource={dataSource} active={activeTab === 'resources'} />;
      case 'KAFKA_TOPICS':
        return <KafkaTopicPanel dataSource={dataSource} active={activeTab === 'resources'} />;
      case 'API_RESOURCES':
        return <ApiResourcePanel dataSource={dataSource} canCreate={canCreate} canUpdate={canUpdate} canDelete={canDelete} canTest={canTest} />;
      case 'SPATIAL_RESOURCES':
        return <SpatialFeatureResourcePanel dataSource={dataSource} canCreate={canCreate} canUpdate={canUpdate} canDelete={canDelete} canReadMetadata={canReadMetadata} />;
      case 'NONE':
      case undefined:
        return null;
    }
  })();
  const testAvailable = canTest && Boolean(typeDefinition?.connectionTestAvailable);
  const tabItems = [
    {
      key: 'basic',
      label: '基本信息',
      children: (
        <DataSourceBasicPanel
          dataSource={dataSource}
          directoryName={dataSource.directoryId ? directoryNameById.get(dataSource.directoryId) : undefined}
        />
      ),
    },
    ...(canBrowseResources && typeDefinition
      ? [{ key: 'resources', label: resourceTabLabel(typeDefinition), children: resourcePanel }]
      : []),
    ...(canViewModels
      ? [{ key: 'models', label: '关联模型', children: <DataSourceRelatedModelsPanel dataSourceId={dataSource.id} active={activeTab === 'models'} /> }]
      : []),
    ...(canViewTasks
      ? [{ key: 'tasks', label: '关联任务', children: <DataSourceRelatedTasksPanel dataSourceId={dataSource.id} active={activeTab === 'tasks'} /> }]
      : []),
    ...(canViewServices
      ? [{ key: 'services', label: '关联数据服务', children: <DataSourceRelatedServicesPanel dataSourceId={dataSource.id} active={activeTab === 'services'} /> }]
      : []),
  ];

  return (
    <div className="data-source-detail-page business-detail-page">
      {messageContext}
      {modalContext}
      <div className="data-source-detail-header business-detail-header">
        <div className="data-source-detail-identity">
          <div className="data-source-detail-title-row">
            <span className="business-detail-resource-icon business-detail-resource-icon-blue">
              <DataSourceTypeIcon type={dataSource.type} />
            </span>
            <span className="data-source-detail-title">{dataSource.name}</span>
            <code>{dataSource.code}</code>
            <Tag>{typeDefinition?.displayName ?? dataSourceTypeLabels[dataSource.type]}</Tag>
            <Tag color={dataSource.enabled ? 'success' : 'default'}>{dataSource.enabled ? '启用' : '停用'}</Tag>
          </div>
          <div className="data-source-detail-subtitle">
            <span>{dataSource.purposes.map((purpose) => dataSourcePurposeLabels[purpose]).join(' / ')}</span>
            <span>·</span>
            <code>{connectionSummary(dataSource)}</code>
            {dataSource.description && <><span>·</span><span className="data-source-detail-description">{dataSource.description}</span></>}
          </div>
        </div>
        <Space size={4}>
          <Tooltip title="刷新数据源">
            <Button icon={<ReloadOutlined />} aria-label="刷新数据源详情" loading={detailQuery.isFetching} onClick={refreshDetail} />
          </Tooltip>
          {testAvailable && (
            <Button icon={<ApiOutlined />} loading={testMutation.isPending} onClick={() => void testConnection(dataSource)}>
              测试连接
            </Button>
          )}
          {canUpdate && <Button type="primary" icon={<EditOutlined />} onClick={() => { setEditing(true); }}>修改</Button>}
          {canDelete && (
            <Dropdown
              trigger={['click']}
              menu={{
                items: [{ key: 'delete', label: '删除数据源', icon: <DeleteOutlined />, danger: true }],
                onClick: ({ key }) => key === 'delete' && remove(dataSource),
              }}
            >
              <Button icon={<MoreOutlined />} aria-label="数据源更多操作" />
            </Dropdown>
          )}
        </Space>
      </div>
      <Tabs
        activeKey={activeTab}
        className="data-source-detail-tabs business-detail-tabs"
        destroyOnHidden
        items={tabItems}
        onChange={(key) => setSearchParams(
          { tab: key as DataSourceDetailTabKey },
          { replace: true, state: location.state },
        )}
      />
      <DataSourceDrawer
        open={editing}
        dataSource={dataSource}
        canViewDirectories={canViewDirectories}
        canTest={canTest}
        onClose={() => { setEditing(false); }}
      />
      {testFailure && (
        <ConnectionTestResultModal
          open
          result={testFailure.result}
          targetLabel={testFailure.targetLabel}
          onClose={() => setTestFailure(null)}
        />
      )}
    </div>
  );
};
