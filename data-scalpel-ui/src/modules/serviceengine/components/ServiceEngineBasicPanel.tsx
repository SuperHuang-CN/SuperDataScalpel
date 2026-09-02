import { ApiOutlined } from '@ant-design/icons';
import { Descriptions, Tag, Typography } from 'antd';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { ManagementDateTime } from '../../../shared/components/ManagementListCells';
import type { ServiceEngine } from '../model/serviceEngine';

interface ServiceEngineBasicPanelProps {
  engine: ServiceEngine;
}

export const ServiceEngineBasicPanel = ({ engine }: ServiceEngineBasicPanelProps) => (
  <BusinessDetailSection title="服务引擎配置" description="管理入口、运行地址与访问凭据状态" icon={<ApiOutlined />}>
    <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 3 }}>
      <Descriptions.Item label="管理地址">
        <Typography.Text code copyable>{engine.adminUrl}</Typography.Text>
      </Descriptions.Item>
      <Descriptions.Item label="运行地址">
        <Typography.Text code copyable>{engine.runtimeUrl}</Typography.Text>
      </Descriptions.Item>
      <Descriptions.Item label="引擎类型">
        <Tag color="blue">{engine.type === 'GEOSERVER' ? 'GeoServer 空间引擎' : 'DataScalpel 服务引擎'}</Tag>
      </Descriptions.Item>
      <Descriptions.Item label={engine.type === 'GEOSERVER' ? 'GeoServer 凭据' : 'Management Token'}>
        <Tag color={(engine.type === 'GEOSERVER' ? engine.geoServerCredentialConfigured : engine.managementTokenConfigured) ? 'success' : 'default'}>
          {(engine.type === 'GEOSERVER' ? engine.geoServerCredentialConfigured : engine.managementTokenConfigured) ? '已配置' : '未配置'}
        </Tag>
      </Descriptions.Item>
      {engine.type === 'GEOSERVER' && <Descriptions.Item label="Workspace"><Typography.Text code>{engine.geoServerWorkspace ?? '—'}</Typography.Text></Descriptions.Item>}
      {engine.type === 'GEOSERVER' && <Descriptions.Item label="管理用户名">{engine.geoServerUsername ?? '—'}</Descriptions.Item>}
      <Descriptions.Item label="运行状态">
        <Tag color={engine.enabled ? 'success' : 'default'}>{engine.enabled ? '启用' : '停用'}</Tag>
      </Descriptions.Item>
      <Descriptions.Item label="说明" span={2}>{engine.description || '—'}</Descriptions.Item>
      <Descriptions.Item label="创建时间"><ManagementDateTime value={engine.createdAt} /></Descriptions.Item>
      <Descriptions.Item label="更新时间"><ManagementDateTime value={engine.updatedAt} /></Descriptions.Item>
    </BusinessDetailDescriptions>
  </BusinessDetailSection>
);
