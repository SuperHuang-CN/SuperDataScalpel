import { Collapse, Descriptions, Space, Tag, Typography } from 'antd';
import type { DataServiceRelatedModelView } from '../hooks/useDataServiceRelatedModels';
import {
  dataServiceAccessModeLabels,
  dataServiceStatusLabels,
  dataServiceTypeLabels,
  type DataServiceDetail,
  type DataServiceStatus,
  type PlatformTypeDefinition,
} from '../model/dataService';

interface DataServiceBasicPanelProps {
  dataService: DataServiceDetail;
  directoryName?: string;
  engineName?: string;
  sourceName?: string;
  relatedModels: DataServiceRelatedModelView[];
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

const typeDefinitionText = (definition: PlatformTypeDefinition) => {
  if (definition.type === 'STRING' && definition.length) return `STRING(${definition.length})`;
  if (definition.type === 'DECIMAL' && definition.precision) {
    return `DECIMAL(${definition.precision}, ${definition.scale ?? 0})`;
  }
  return definition.type;
};

export const DataServiceBasicPanel = ({
  dataService,
  directoryName,
  engineName,
  sourceName,
  relatedModels,
}: DataServiceBasicPanelProps) => {
  const primaryModel = relatedModels[0]?.model;
  const definitionVersion = dataService.standardDefinition?.version
    ?? dataService.sqlDefinition?.version
    ?? dataService.scriptDefinition?.version;
  const definitionItems = dataService.type === 'SQL_QUERY' && dataService.sqlDefinition
    ? [{
      key: 'sql',
      label: `查看 SQL 模板（${dataService.sqlDefinition.parameters.length} 个参数）`,
      children: (
        <div className="data-service-definition-detail">
          <pre>{dataService.sqlDefinition.sqlText}</pre>
          {dataService.sqlDefinition.parameters.length > 0 && (
            <Descriptions size="small" bordered column={2}>
              {dataService.sqlDefinition.parameters.map((parameter) => (
                <Descriptions.Item key={parameter.name} label={parameter.name}>
                  <Space size={4} wrap>
                    <code>{typeDefinitionText(parameter.typeDefinition)}</code>
                    <Tag color={parameter.required ? 'blue' : 'default'}>
                      {parameter.required ? '必填' : '可选'}
                    </Tag>
                    {parameter.description && <span>{parameter.description}</span>}
                  </Space>
                </Descriptions.Item>
              ))}
            </Descriptions>
          )}
        </div>
      ),
    }]
    : dataService.type === 'SCRIPT_API' && dataService.scriptDefinition
      ? [{
        key: 'script',
        label: `查看 Groovy 脚本（${dataService.scriptDefinition.examples.length} 个请求 Example）`,
        children: <div className="data-service-definition-detail"><pre>{dataService.scriptDefinition.script}</pre></div>,
      }]
      : [];

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
          <Descriptions.Item label="定义版本">{definitionVersion ?? '—'}</Descriptions.Item>
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
          <Descriptions.Item label="访问方式"><Tag>{dataServiceAccessModeLabels[dataService.accessMode]}</Tag></Descriptions.Item>
          <Descriptions.Item label="请求方法"><Tag color="blue">POST</Tag></Descriptions.Item>
          <Descriptions.Item label="公开路由" span={2}><code>{dataService.routePath}</code></Descriptions.Item>
          <Descriptions.Item label="来源资源">{sourceName ?? '—'}</Descriptions.Item>
        </Descriptions>
      </section>

      <section className="data-service-detail-section">
        <div className="data-service-detail-section-title">服务定义摘要</div>
        <Descriptions size="small" bordered column={3}>
          {dataService.type === 'STANDARD_TABLE' && (
            <>
              <Descriptions.Item label="主模型">{primaryModel?.name ?? dataService.standardDefinition?.modelId ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="模型编码"><code>{primaryModel?.code ?? '—'}</code></Descriptions.Item>
              <Descriptions.Item label="定义模式">标准分页查询</Descriptions.Item>
            </>
          )}
          {dataService.type === 'SQL_QUERY' && (
            <>
              <Descriptions.Item label="JDBC 数据源">{sourceName ?? dataService.sqlDefinition?.dataSourceId ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="引用模型">{dataService.sqlDefinition?.modelIds.length ?? 0} 个</Descriptions.Item>
              <Descriptions.Item label="请求参数">{dataService.sqlDefinition?.parameters.length ?? 0} 个</Descriptions.Item>
            </>
          )}
          {dataService.type === 'SCRIPT_API' && (
            <>
              <Descriptions.Item label="JDBC 数据源">{sourceName ?? dataService.scriptDefinition?.dataSourceId ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="请求 Example">{dataService.scriptDefinition?.examples.length ?? 0} 个</Descriptions.Item>
              <Descriptions.Item label="执行方式">Groovy 脚本</Descriptions.Item>
            </>
          )}
        </Descriptions>
        {definitionItems.length > 0 && (
          <Collapse size="small" className="data-service-definition-collapse" items={definitionItems} />
        )}
      </section>
    </div>
  );
};
