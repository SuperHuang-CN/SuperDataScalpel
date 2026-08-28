import { Descriptions, Tag, Typography } from 'antd';
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
      <section className="data-service-detail-section">
        <div className="data-service-detail-section-title">基本标识</div>
        <Descriptions size="small" bordered column={3}>
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
        </Descriptions>
      </section>

      <section className="data-service-detail-section">
        <div className="data-service-detail-section-title">接口配置</div>
        <Descriptions size="small" bordered column={3}>
          <Descriptions.Item label="Service Engine">{engineName ?? dataService.engineId}</Descriptions.Item>
          <Descriptions.Item label="请求方法"><Tag color="blue">POST</Tag></Descriptions.Item>
          <Descriptions.Item label="Engine 内部路由" span={2}><code>{dataService.engineRoutePath}</code></Descriptions.Item>
          <Descriptions.Item label="来源资源">{sourceName ?? '—'}</Descriptions.Item>
        </Descriptions>
      </section>
    </div>
  );
};
