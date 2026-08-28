import { CopyOutlined } from '@ant-design/icons';
import { Alert, Button, Descriptions, Empty, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { TableProps } from 'antd';
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

interface DataServiceRuntimePanelProps {
  dataService: DataServiceDetail;
  engineName?: string;
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
  engineName,
  onCopyCurl,
}: DataServiceRuntimePanelProps) => {
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

      <section className="data-service-detail-section">
        <div className="data-service-detail-section-title">Service Engine 运行状态</div>
        <Descriptions size="small" bordered column={3}>
          <Descriptions.Item label="Service Engine">{engineName ?? dataService.engineId}</Descriptions.Item>
          <Descriptions.Item label="部署状态">
            {dataService.deploymentStatus
              ? <Tag color={deploymentStatusColors[dataService.deploymentStatus]}>{dataServiceDeploymentStatusLabels[dataService.deploymentStatus]}</Tag>
              : <Tag>尚未部署</Tag>}
          </Descriptions.Item>
          <Descriptions.Item label="部署时间">{formatDateTime(dataService.deployedAt)}</Descriptions.Item>
          <Descriptions.Item label="当前 Revision">{dataService.revision}</Descriptions.Item>
          <Descriptions.Item label="请求方法"><Tag color="blue">POST</Tag></Descriptions.Item>
          <Descriptions.Item label="Engine 内部路由"><code>{dataService.engineRoutePath}</code></Descriptions.Item>
        </Descriptions>
      </section>

      <section className="data-service-detail-section data-service-gateway-section">
        <div className="data-service-detail-section-title">API 网关发布</div>
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
      </section>
    </div>
  );
};
