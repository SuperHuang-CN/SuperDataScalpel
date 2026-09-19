import {
  ApiOutlined,
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  ExportOutlined,
  MoreOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { Button, Dropdown, Modal, Result, Skeleton, Space, Tabs, Tag, Tooltip, message } from 'antd';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/http';
import { useCurrentUser } from '../../system';
import { ServiceEngineAccessPolicyPanel } from '../components/ServiceEngineAccessPolicyPanel';
import { ServiceEngineBasicPanel } from '../components/ServiceEngineBasicPanel';
import { ServiceEngineDataSourcePanel } from '../components/ServiceEngineDataSourcePanel';
import { ServiceEngineDrawer } from '../components/ServiceEngineDrawer';
import { ServiceEngineServicesPanel } from '../components/ServiceEngineServicesPanel';
import {
  useDeleteServiceEngine,
  useServiceEngine,
  useTestServiceEngine,
} from '../hooks/useServiceEngines';
import { normalizeServiceEngineDetailTab, type ServiceEngineDetailTabKey } from '../model/serviceEngineDetail';

interface ServiceEngineDetailLocationState {
  fromServiceEngineList?: boolean;
}

export const ServiceEngineDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [editing, setEditing] = useState(false);
  const [messageApi, messageContext] = message.useMessage();
  const [modalApi, modalContext] = Modal.useModal();
  const detailQuery = useServiceEngine(id, Boolean(id));
  const currentUserQuery = useCurrentUser();
  const permissions = new Set(currentUserQuery.data?.permissions ?? []);
  const canViewServices = permissions.has('service.view');
  const canViewDataSources = permissions.has('datasource.view');
  const canUpdate = permissions.has('service.engine.update');
  const canDelete = permissions.has('service.engine.delete');
  const canTest = permissions.has('service.engine.test');
  const permissionsLoaded = Boolean(currentUserQuery.data);
  const deleteMutation = useDeleteServiceEngine();
  const testMutation = useTestServiceEngine();
  const requestedTab = normalizeServiceEngineDetailTab(searchParams.get('tab'));
  const tabParameter = searchParams.get('tab');
  const servicesUnavailable = requestedTab === 'services' && (!permissionsLoaded || !canViewServices);
  const engine = detailQuery.data;
  const activeTab = servicesUnavailable || (engine?.type === 'GEOSERVER' && requestedTab === 'access-policy')
    ? 'basic'
    : requestedTab;

  useEffect(() => {
    if (permissionsLoaded && requestedTab === 'services' && !canViewServices) {
      setSearchParams({ tab: 'basic' }, { replace: true });
    }
  }, [canViewServices, permissionsLoaded, requestedTab, setSearchParams]);

  useEffect(() => {
    if (tabParameter && tabParameter !== requestedTab) {
      setSearchParams({ tab: 'basic' }, { replace: true });
    }
  }, [requestedTab, setSearchParams, tabParameter]);

  const backToList = () => {
    const state = location.state as ServiceEngineDetailLocationState | null;
    if (state?.fromServiceEngineList) navigate(-1);
    else navigate('/service-engine');
  };

  const test = async () => {
    if (!engine) return;
    try {
      const result = await testMutation.mutateAsync({ id: engine.id });
      const support = result.type === 'GEOSERVER'
        ? `${result.version ?? '未知版本'}，${result.capabilities?.join(' / ') || '未返回协议能力'}`
        : `支持：${result.databaseTypes.join('、') || '无'}`;
      messageApi.success(`${engine.name} 连接成功，${support}，耗时 ${result.elapsedMs} ms`);
    } catch (error) {
      messageApi.error(error instanceof ApiError ? error.message : '测试 Service Engine 失败');
    }
  };

  const remove = () => {
    if (!engine) return;
    modalApi.confirm({
      rootClassName: 'business-overlay business-modal-overlay',
      title: '删除 Service Engine',
      content: `确认删除“${engine.name}”吗？`,
      okText: '删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteMutation.mutateAsync(engine.id);
          messageApi.success('Service Engine 已删除');
          navigate('/service-engine', { replace: true });
        } catch (error) {
          messageApi.error(error instanceof ApiError ? error.message : '删除 Service Engine 失败');
          throw error;
        }
      },
    });
  };

  const openRuntimeConsole = () => {
    if (!engine) return;
    const suffix = engine.type === 'GEOSERVER' ? '/web/' : '/modern-ui/';
    window.open(`${engine.adminUrl.replace(/\/+$/, '')}${suffix}`, '_blank', 'noopener,noreferrer');
  };

  if (!id) {
    return <Result status="404" title="服务引擎地址无效" extra={<Button type="primary" onClick={() => navigate('/service-engine')}>返回服务引擎列表</Button>} />;
  }

  if (detailQuery.isPending) {
    return <div className="service-engine-detail-loading"><Skeleton active paragraph={{ rows: 8 }} /></div>;
  }

  if (!engine || detailQuery.error) {
    const notFound = detailQuery.error instanceof ApiError && detailQuery.error.status === 404;
    return (
      <Result
        status={notFound ? '404' : 'error'}
        title={notFound ? '服务引擎不存在' : '服务引擎详情加载失败'}
        subTitle={detailQuery.error instanceof ApiError ? detailQuery.error.message : '请确认服务引擎是否存在。'}
        extra={<Space><Button onClick={backToList}>返回列表</Button><Button type="primary" onClick={() => void detailQuery.refetch()}>重试</Button></Space>}
      />
    );
  }

  const tabItems = [
    { key: 'basic', label: '基本信息', children: <ServiceEngineBasicPanel engine={engine} /> },
    ...(canViewServices ? [{ key: 'services', label: '数据服务', children: <ServiceEngineServicesPanel engineId={engine.id} geoServerWorkspace={engine.geoServerWorkspace} /> }] : []),
    {
      key: 'datasources',
      label: '数据源',
      children: <ServiceEngineDataSourcePanel engine={engine} canUpdate={canUpdate} canTest={canTest} canViewDataSources={canViewDataSources} />,
    },
    ...(engine.type !== 'GEOSERVER' ? [{
      key: 'access-policy',
      label: '访问策略',
      children: <ServiceEngineAccessPolicyPanel engine={engine} canUpdate={canUpdate} />,
    }] : []),
  ];

  return (
    <div className="service-engine-detail-page business-detail-page">
      {messageContext}
      {modalContext}
      <header className="service-engine-detail-header business-detail-header">
        <div className="service-engine-detail-identity">
          <div className="service-engine-detail-title-row">
            <Button type="text" icon={<ArrowLeftOutlined />} onClick={backToList}>返回列表</Button>
            <span className="business-detail-resource-icon business-detail-resource-icon-cyan"><ApiOutlined /></span>
            <span className="service-engine-detail-title">{engine.name}</span>
            <code>{engine.code}</code>
            <Tag color={engine.enabled ? 'success' : 'default'}>{engine.enabled ? '启用' : '停用'}</Tag>
            <Tag color="blue">{engine.type === 'GEOSERVER' ? 'GeoServer 空间引擎' : 'DataScalpel 服务引擎'}</Tag>
            <Tag color={(engine.type === 'GEOSERVER' ? engine.geoServerCredentialConfigured : engine.managementTokenConfigured) ? 'success' : 'default'}>
              {engine.type === 'GEOSERVER'
                ? engine.geoServerCredentialConfigured ? 'GeoServer 凭据已配置' : 'GeoServer 凭据未配置'
                : engine.managementTokenConfigured ? 'Management Token 已配置' : 'Management Token 未配置'}
            </Tag>
          </div>
          <div className="service-engine-detail-subtitle">
            <span>管理</span><code>{engine.adminUrl}</code><span>·</span><span>运行</span><code>{engine.runtimeUrl}</code>
          </div>
        </div>
        <Space size={4} wrap>
          <Button icon={<ExportOutlined />} onClick={openRuntimeConsole}>{engine.type === 'GEOSERVER' ? '打开 GeoServer' : '打开 API Studio'}</Button>
          <Tooltip title="刷新服务引擎详情"><Button icon={<ReloadOutlined />} aria-label="刷新服务引擎详情" onClick={() => void detailQuery.refetch()} /></Tooltip>
          {canTest && <Button icon={<ApiOutlined />} loading={testMutation.isPending} onClick={() => void test()}>测试连接</Button>}
          {canUpdate && <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>修改</Button>}
          {canDelete && (
            <Dropdown trigger={['click']} menu={{ items: [{ key: 'delete', label: '删除 Service Engine', icon: <DeleteOutlined />, danger: true }], onClick: ({ key }) => key === 'delete' && remove() }}>
              <Button icon={<MoreOutlined />} aria-label="服务引擎更多操作" loading={deleteMutation.isPending} />
            </Dropdown>
          )}
        </Space>
      </header>
      <Tabs
        activeKey={activeTab}
        className="service-engine-detail-tabs business-detail-tabs"
        destroyOnHidden
        items={tabItems}
        onChange={(key) => setSearchParams({ tab: key as ServiceEngineDetailTabKey }, { replace: true, state: location.state })}
      />
      <ServiceEngineDrawer open={editing} engine={engine} canTest={canTest} onClose={() => setEditing(false)} />
    </div>
  );
};
