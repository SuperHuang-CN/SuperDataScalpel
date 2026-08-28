import { Descriptions, Tag, Typography } from 'antd';
import { ManagementDateTime } from '../../../shared/components/ManagementListCells';
import type { ServiceEngine } from '../model/serviceEngine';

interface ServiceEngineBasicPanelProps {
  engine: ServiceEngine;
}

export const ServiceEngineBasicPanel = ({ engine }: ServiceEngineBasicPanelProps) => (
  <Descriptions bordered column={{ xs: 1, sm: 2 }} size="small">
    <Descriptions.Item label="管理地址">
      <Typography.Text code copyable>{engine.adminUrl}</Typography.Text>
    </Descriptions.Item>
    <Descriptions.Item label="运行地址">
      <Typography.Text code copyable>{engine.runtimeUrl}</Typography.Text>
    </Descriptions.Item>
    <Descriptions.Item label="Management Token">
      <Tag color={engine.managementTokenConfigured ? 'success' : 'default'}>
        {engine.managementTokenConfigured ? '已配置' : '未配置'}
      </Tag>
    </Descriptions.Item>
    <Descriptions.Item label="运行状态">
      <Tag color={engine.enabled ? 'success' : 'default'}>{engine.enabled ? '启用' : '停用'}</Tag>
    </Descriptions.Item>
    <Descriptions.Item label="说明" span={2}>{engine.description || '—'}</Descriptions.Item>
    <Descriptions.Item label="创建时间"><ManagementDateTime value={engine.createdAt} /></Descriptions.Item>
    <Descriptions.Item label="更新时间"><ManagementDateTime value={engine.updatedAt} /></Descriptions.Item>
  </Descriptions>
);
