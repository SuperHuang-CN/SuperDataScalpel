import { EditOutlined, FileTextOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Descriptions, Empty, Space, Table, Tag, Typography } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { dataModelStatusLabels, useDataModel, type DataModelField } from '../../model';
import type { DataServiceRelatedModelView } from '../hooks/useDataServiceRelatedModels';
import type { DataServiceDetail, SqlServiceParameterDefinition } from '../model/dataService';
import { typeDescription } from '../model/dataServiceEditor';

interface DataServiceDefinitionPanelProps {
  dataService: DataServiceDetail;
  sourceName?: string;
  relatedModels: DataServiceRelatedModelView[];
  canViewModels: boolean;
  canUpdate: boolean;
  editable: boolean;
}

export const DataServiceDefinitionPanel = ({
  dataService,
  sourceName,
  relatedModels,
  canViewModels,
  canUpdate,
  editable,
}: DataServiceDefinitionPanelProps) => {
  const navigate = useNavigate();
  const location = useLocation();
  const standardModelId = dataService.standardDefinition?.modelId;
  const modelQuery = useDataModel(standardModelId, canViewModels && Boolean(standardModelId));
  const openEditor = () => navigate(`/dataservice/${dataService.id}/definition/edit`, { state: location.state });
  const editButton = canUpdate ? (
    <Button
      type="primary"
      icon={dataService.definitionConfigured ? <EditOutlined /> : <PlusOutlined />}
      disabled={!editable}
      onClick={openEditor}
    >
      {dataService.definitionConfigured ? '编辑定义' : '配置定义'}
    </Button>
  ) : null;

  if (!dataService.definitionConfigured) {
    return (
      <div className="data-service-definition-panel data-service-definition-empty">
        <Empty
          image={<FileTextOutlined />}
          description={(
            <div>
              <Typography.Text strong>服务定义尚未配置</Typography.Text>
              <div><Typography.Text type="secondary">基础信息已经保存，可以稍后继续配置。</Typography.Text></div>
            </div>
          )}
        >
          {editButton}
        </Empty>
      </div>
    );
  }

  const standardModel = modelQuery.data?.model;
  const standardFields = modelQuery.data?.fields ?? [];

  return (
    <div className="data-service-definition-panel">
      <div className="data-service-detail-section-toolbar">
        <div>
          <Space size={8} wrap>
            <Typography.Title level={5}>已保存服务定义</Typography.Title>
            <Tag color="success">v{dataService.definitionVersion}</Tag>
          </Space>
          <Typography.Text type="secondary">启用时会基于该版本生成不可变部署快照。</Typography.Text>
        </div>
        {editButton}
      </div>

      {dataService.type === 'STANDARD_TABLE' && (
        <>
          <Descriptions bordered size="small" column={{ xs: 1, md: 2, xl: 4 }}>
            <Descriptions.Item label="发布模型">{standardModel?.name ?? relatedModels[0]?.name ?? '模型已删除'}</Descriptions.Item>
            <Descriptions.Item label="模型编码"><Typography.Text code>{standardModel?.code ?? relatedModels[0]?.code ?? standardModelId}</Typography.Text></Descriptions.Item>
            <Descriptions.Item label="模型状态">{standardModel ? dataModelStatusLabels[standardModel.status] : '—'}</Descriptions.Item>
            <Descriptions.Item label="Schema 版本">{standardModel ? `v${standardModel.schemaVersion}` : '—'}</Descriptions.Item>
            <Descriptions.Item label="存储数据源">{standardModel?.storageDataSourceName ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="物理表" span={3}>{standardModel?.physicalTableName ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="字段 / 主键">{standardFields.length} / {standardFields.filter((field) => field.primaryKey).length}</Descriptions.Item>
          </Descriptions>
          <Table<DataModelField>
            rowKey="id"
            size="small"
            pagination={false}
            loading={modelQuery.isFetching}
            dataSource={standardFields.slice(0, 12)}
            columns={[
              { title: '字段', render: (_, field) => <div><strong>{field.name}</strong><div><Typography.Text code type="secondary">{field.code}</Typography.Text></div></div> },
              { title: '类型', width: 160, render: (_, field) => typeDescription({ type: field.fieldType, length: field.length, precision: field.precision, scale: field.scale }) },
              { title: '可空', width: 90, render: (_, field) => field.nullable ? '是' : '否' },
              { title: '主键', width: 90, render: (_, field) => field.primaryKey ? <Tag color="blue">主键</Tag> : '—' },
              { title: '说明', dataIndex: 'description', render: (value) => value || '—' },
            ]}
          />
        </>
      )}

      {dataService.type === 'SQL_QUERY' && dataService.sqlDefinition && (
        <>
          <Descriptions bordered size="small" column={{ xs: 1, md: 2, xl: 3 }}>
            <Descriptions.Item label="数据源">{sourceName ?? dataService.sqlDefinition.dataSourceId}</Descriptions.Item>
            <Descriptions.Item label="关联模型">{dataService.sqlDefinition.modelIds.length} 个</Descriptions.Item>
            <Descriptions.Item label="参数">{dataService.sqlDefinition.parameters.length} 个</Descriptions.Item>
          </Descriptions>
          <div className="data-service-definition-code-block">
            <Typography.Text strong>SQL 模板</Typography.Text>
            <pre>{dataService.sqlDefinition.sqlText}</pre>
          </div>
          <Table<SqlServiceParameterDefinition>
            rowKey="name"
            size="small"
            pagination={false}
            dataSource={dataService.sqlDefinition.parameters}
            columns={[
              { title: '参数', dataIndex: 'name' },
              { title: '类型', width: 180, render: (_, parameter) => typeDescription(parameter.typeDefinition) },
              { title: '必填', width: 90, render: (_, parameter) => parameter.required ? '是' : '否' },
              { title: '说明', dataIndex: 'description', render: (value) => value || '—' },
            ]}
          />
        </>
      )}

      {dataService.type === 'SCRIPT_API' && dataService.scriptDefinition && (
        <>
          <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label="默认数据源">{sourceName ?? dataService.scriptDefinition.dataSourceId}</Descriptions.Item>
            <Descriptions.Item label="请求 Example">{dataService.scriptDefinition.examples.length} 个</Descriptions.Item>
          </Descriptions>
          <div className="data-service-definition-code-block">
            <Typography.Text strong>Groovy 脚本</Typography.Text>
            <pre>{dataService.scriptDefinition.script}</pre>
          </div>
        </>
      )}
    </div>
  );
};
