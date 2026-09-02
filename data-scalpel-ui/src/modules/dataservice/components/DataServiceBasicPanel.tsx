import { ApiOutlined, ProfileOutlined } from '@ant-design/icons';
import { Descriptions, Space, Tag, Typography } from 'antd';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import {
  dataServiceStatusLabels,
  dataServiceTypeLabels,
  type DataServiceDetail,
  type DataServiceStatus,
} from '../model/dataService';

interface DataServiceBasicPanelProps {
  dataService: DataServiceDetail;
  directoryName?: string;
  engineName?: string;
  sourceName?: string;
}

const statusColors: Record<DataServiceStatus, string> = {
  DRAFT: 'default',
  ENABLED: 'success',
  DISABLED: 'warning',
};

const formatDateTime = (value: string) => new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'medium',
  hour12: false,
}).format(new Date(value));

export const DataServiceBasicPanel = ({
  dataService,
  directoryName,
  engineName,
  sourceName,
}: DataServiceBasicPanelProps) => {
  return (
    <div className="data-service-detail-tab-panel data-service-basic-panel">
      <BusinessDetailSection title="基本标识" description="服务身份、类型与版本状态" icon={<ProfileOutlined />}>
        <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 4 }}>
          <Descriptions.Item label="服务名称">{dataService.name}</Descriptions.Item>
          <Descriptions.Item label="服务编码"><code>{dataService.code}</code></Descriptions.Item>
          <Descriptions.Item label="服务状态">
            <Tag color={statusColors[dataService.status]}>{dataServiceStatusLabels[dataService.status]}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="所属目录">
            {directoryName ?? (dataService.directoryId ? '—' : '未分类')}
          </Descriptions.Item>
          <Descriptions.Item label="服务类型">{dataServiceTypeLabels[dataService.type]}</Descriptions.Item>
          <Descriptions.Item label="定义版本">{dataService.definitionVersion ? `v${dataService.definitionVersion}` : '未配置'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{formatDateTime(dataService.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{formatDateTime(dataService.updatedAt)}</Descriptions.Item>
          <Descriptions.Item label="当前 Revision">{dataService.revision}</Descriptions.Item>
          <Descriptions.Item label="说明" span={3}>
            {dataService.description || <Typography.Text type="secondary">未填写</Typography.Text>}
          </Descriptions.Item>
        </BusinessDetailDescriptions>
      </BusinessDetailSection>

      <BusinessDetailSection title="接口配置" description="服务引擎、访问路由与来源资源" icon={<ApiOutlined />}>
        <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 3 }}>
          <Descriptions.Item label={dataService.type === 'SPATIAL_SERVICE' ? 'GeoServer Engine' : 'Service Engine'}>{engineName ?? dataService.engineId}</Descriptions.Item>
          <Descriptions.Item label="协议">{dataService.type === 'SPATIAL_SERVICE' ? <Space><Tag>WMS</Tag><Tag>WFS</Tag></Space> : <Tag color="blue">POST</Tag>}</Descriptions.Item>
          <Descriptions.Item label="来源资源">{sourceName ?? '—'}</Descriptions.Item>
          <Descriptions.Item label={dataService.type === 'SPATIAL_SERVICE' ? '发布方式' : '服务 Context Path'} span={3}><code>{dataService.type === 'SPATIAL_SERVICE' ? 'GeoServer Workspace 图层' : dataService.contextPath}</code></Descriptions.Item>
        </BusinessDetailDescriptions>
      </BusinessDetailSection>
    </div>
  );
};
