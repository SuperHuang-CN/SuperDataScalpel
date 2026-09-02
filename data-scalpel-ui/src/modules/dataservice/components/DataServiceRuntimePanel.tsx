import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { CopyOutlined, DeploymentUnitOutlined, GlobalOutlined } from '@ant-design/icons';
import { Button, Descriptions, Empty, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { TableProps } from 'antd';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { gatewayProviderLabels } from '../model/apiConsumer';
import {
  dataServiceDeploymentStatusLabels,
  gatewayServicePublicationStatusLabels,
  type DataServiceDeploymentStatus,
  type DataServiceDetail,
  type GatewayServiceBinding,
  type GatewayServicePublicationStatus,
} from '../model/dataService';
import { GatewayReconciliationTag } from './GatewayReconciliationTag';
import type { ServiceEngine } from '../../serviceengine';

interface DataServiceRuntimePanelProps {
  dataService: DataServiceDetail;
  engine?: ServiceEngine;
  onCopyCurl: () => void;
}

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

const formatDateTime = (value: string | null) => value ? new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'medium',
  hour12: false,
}).format(new Date(value)) : '—';

export const DataServiceRuntimePanel = ({
  dataService,
  engine,
  onCopyCurl,
}: DataServiceRuntimePanelProps) => {
  const geoBase = engine?.runtimeUrl.replace(/\/+$/, '') ?? '';
  const workspace = engine?.geoServerWorkspace ?? 'datascalpel';
  const layerName = `svc_${dataService.code}`;
  const qualifiedLayerName = `${workspace}:${layerName}`;
  const wmsEndpoint = `${geoBase}/${workspace}/wms`;
  const wfsEndpoint = `${geoBase}/${workspace}/wfs`;
  const wmsCapabilities = `${wmsEndpoint}?service=WMS&version=1.3.0&request=GetCapabilities`;
  const wfsCapabilities = `${wfsEndpoint}?service=WFS&version=2.0.0&request=GetCapabilities`;
  const geoJsonExample = `${wfsEndpoint}?service=WFS&version=2.0.0&request=GetFeature&typeNames=${encodeURIComponent(qualifiedLayerName)}&outputFormat=application%2Fjson&count=100`;
  const wmsReflect = `${geoBase}/wms/reflect?layers=${encodeURIComponent(qualifiedLayerName)}`;
  const columns: TableProps<GatewayServiceBinding>['columns'] = [
    {
      title: '网关',
      dataIndex: 'provider',
      width: 130,
      render: (value: GatewayServiceBinding['provider']) => gatewayProviderLabels[value],
    },
    {
      title: '发布状态',
      dataIndex: 'publicationStatus',
      width: 120,
      render: (value: GatewayServicePublicationStatus) => (
        <Tag color={gatewayStatusColors[value]}>{gatewayServicePublicationStatusLabels[value]}</Tag>
      ),
    },
    {
      title: '发布 Revision',
      dataIndex: 'publishedRevision',
      width: 130,
      render: (value: number) => (
        <Space size={4}>
          <span>{value}</span>
          {value === dataService.revision && <Tag color="success">当前</Tag>}
        </Space>
      ),
    },
    {
      title: '公开路由',
      dataIndex: 'gatewayRoutePath',
      width: 220,
      render: (value: string) => <code>{value}</code>,
    },
    {
      title: '访问方式',
      dataIndex: 'accessMode',
      width: 120,
      render: (value: GatewayServiceBinding['accessMode']) => <Tag>{value === 'PUBLIC' ? '公开' : '订阅访问'}</Tag>,
    },
    {
      title: '网关地址',
      dataIndex: 'gatewayUrl',
      width: 320,
      ellipsis: true,
      render: (value: string | null) => value
        ? <Typography.Text copyable={{ text: value }}><code>{value}</code></Typography.Text>
        : '—',
    },
    {
      title: '对账状态',
      key: 'reconciliation',
      width: 150,
      render: (_value, binding) => <GatewayReconciliationTag state={binding} />,
    },
    {
      title: '发布时间',
      dataIndex: 'publishedAt',
      width: 180,
      render: formatDateTime,
    },
    {
      title: '操作',
      key: 'actions',
      width: 74,
      fixed: 'right',
      render: (_value, binding) => binding.gatewayUrl ? (
        <Tooltip title="复制访问 cURL">
          <Button
            type="text"
            size="small"
            icon={<CopyOutlined />}
            aria-label={`复制${dataService.name}的网关访问 cURL`}
            onClick={onCopyCurl}
          />
        </Tooltip>
      ) : '—',
    },
  ];

  return (
    <div className="data-service-detail-tab-panel data-service-runtime-panel">
      {(dataService.deploymentError || dataService.gatewayBindings.some((binding) => binding.lastError)) && (
        <Alert
          type="error"
          showIcon
          message="运行或发布存在异常"
          description={dataService.deploymentError
            ?? dataService.gatewayBindings.find((binding) => binding.lastError)?.lastError}
        />
      )}

      <BusinessDetailSection
        title={dataService.type === 'SPATIAL_SERVICE' ? 'GeoServer 运行状态' : 'Service Engine 运行状态'}
        description={dataService.type === 'SPATIAL_SERVICE' ? '图层部署状态与 GeoServer 资源标识' : '部署状态、版本与引擎内部访问地址'}
        icon={<DeploymentUnitOutlined />}
      >
        <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 3 }}>
          <Descriptions.Item label={dataService.type === 'SPATIAL_SERVICE' ? 'GeoServer Engine' : 'Service Engine'}>{engine?.name ?? dataService.engineId}</Descriptions.Item>
          <Descriptions.Item label="部署状态">
            {dataService.deploymentStatus
              ? <Tag color={deploymentStatusColors[dataService.deploymentStatus]}>{dataServiceDeploymentStatusLabels[dataService.deploymentStatus]}</Tag>
              : <Tag>尚未部署</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="部署时间">{formatDateTime(dataService.deployedAt)}</Descriptions.Item>
          <Descriptions.Item label="当前 Revision">{dataService.revision}</Descriptions.Item>
          <Descriptions.Item label="协议">{dataService.type === 'SPATIAL_SERVICE' ? <Space><Tag>WMS</Tag><Tag>WFS</Tag></Space> : <Tag color="blue">POST</Tag>}</Descriptions.Item>
          <Descriptions.Item label={dataService.type === 'SPATIAL_SERVICE' ? 'Qualified Layer Name' : '服务 Context Path'}><code>{dataService.type === 'SPATIAL_SERVICE' ? qualifiedLayerName : dataService.contextPath}</code></Descriptions.Item>
        </BusinessDetailDescriptions>
      </BusinessDetailSection>

      {dataService.type === 'SPATIAL_SERVICE' ? (
        <BusinessDetailSection title="GeoServer 服务入口" description="空间服务直连 GeoServer，不经过 API 网关" icon={<GlobalOutlined />}>
          <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 3 }}>
            <Descriptions.Item label="WMS 端点"><Typography.Text copyable><code>{wmsEndpoint}</code></Typography.Text></Descriptions.Item>
            <Descriptions.Item label="WFS 端点"><Typography.Text copyable><code>{wfsEndpoint}</code></Typography.Text></Descriptions.Item>
            <Descriptions.Item label="WMS 预览"><Typography.Link href={wmsReflect} target="_blank" rel="noreferrer">打开 Reflect 预览</Typography.Link></Descriptions.Item>
            <Descriptions.Item label="WMS GetCapabilities" span={3}><Typography.Text copyable={{ text: wmsCapabilities }}><code>{wmsCapabilities}</code></Typography.Text></Descriptions.Item>
            <Descriptions.Item label="WFS GetCapabilities" span={3}><Typography.Text copyable={{ text: wfsCapabilities }}><code>{wfsCapabilities}</code></Typography.Text></Descriptions.Item>
            <Descriptions.Item label="WFS GeoJSON 示例" span={3}><Typography.Text copyable={{ text: geoJsonExample }}><code>{geoJsonExample}</code></Typography.Text></Descriptions.Item>
          </BusinessDetailDescriptions>
        </BusinessDetailSection>
      ) : <BusinessDetailSection title="API 网关发布" description="各网关实例的发布结果与访问入口" icon={<GlobalOutlined />} className="data-service-gateway-section">
        {dataService.gatewayBindings.length > 0 ? (
          <Table<GatewayServiceBinding>
            size="small"
            rowKey="id"
            columns={columns}
            dataSource={dataService.gatewayBindings}
            pagination={false}
            scroll={{ x: 1130 }}
          />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前服务尚未发布到 API 网关" />
        )}
      </BusinessDetailSection>}
    </div>
  );
};
