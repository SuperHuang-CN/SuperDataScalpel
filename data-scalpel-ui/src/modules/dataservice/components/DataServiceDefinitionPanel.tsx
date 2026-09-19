import { EditOutlined, FileTextOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Descriptions, Empty, Space, Table, Tag, Typography } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { BusinessDetailSection } from '../../../shared/components/BusinessDetailSection';
import { BusinessDetailDescriptions } from '../../../shared/components/BusinessDetailDescriptions';
import { dataModelStatusLabels, useDataModel, type DataModelField } from '../../model';
import { useDataServiceSpatialStyle } from '../hooks/useDataServices';
import type { DataServiceRelatedModelView } from '../hooks/useDataServiceRelatedModels';
import type {
  DataServiceDetail,
  SpatialStyleSyncStatus,
  SqlServiceParameterDefinition,
} from '../model/dataService';
import { typeDescription } from '../model/dataServiceEditor';

interface DataServiceDefinitionPanelProps {
  dataService: DataServiceDetail;
  sourceName?: string;
  relatedModels: DataServiceRelatedModelView[];
  canViewModels: boolean;
  canUpdate: boolean;
  editable: boolean;
}

const styleModeLabels = { CARTOGRAPHY: '在线制图', UPLOADED_SLD: '上传 SLD' } as const;
const styleSyncStatusLabels: Record<SpatialStyleSyncStatus, string> = {
  NOT_APPLIED: '未应用',
  OUT_OF_SYNC: '待应用',
  SYNCING: '应用中',
  IN_SYNC: '已同步',
  SYNC_FAILED: '同步失败',
};
const styleSyncStatusColors: Record<SpatialStyleSyncStatus, string> = {
  NOT_APPLIED: 'default',
  OUT_OF_SYNC: 'warning',
  SYNCING: 'processing',
  IN_SYNC: 'success',
  SYNC_FAILED: 'error',
};

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
  const standardModelId = dataService.standardDefinition?.modelId ?? dataService.spatialDefinition?.modelId;
  const modelQuery = useDataModel(standardModelId, canViewModels && Boolean(standardModelId));
  const styleQuery = useDataServiceSpatialStyle(dataService.id, dataService.type === 'SPATIAL_SERVICE');
  const openEditor = () => navigate(`/dataservice/${dataService.id}/definition/edit`, { state: location.state });
  const openCartography = () => navigate(
    { pathname: location.pathname, search: '?tab=cartography' },
    { state: location.state },
  );
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
      <BusinessDetailSection
        title={<Space size={8}>已保存服务定义<Tag color="success">v{dataService.definitionVersion}</Tag></Space>}
        description="启用时会基于该版本生成不可变部署快照"
        icon={<FileTextOutlined />}
        extra={editButton}
      >

      {dataService.type === 'STANDARD_TABLE' && (
        <>
          <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 4 }}>
            <Descriptions.Item label="发布模型">{standardModel?.name ?? relatedModels[0]?.name ?? '模型已删除'}</Descriptions.Item>
            <Descriptions.Item label="模型编码"><Typography.Text code>{standardModel?.code ?? relatedModels[0]?.code ?? standardModelId}</Typography.Text></Descriptions.Item>
            <Descriptions.Item label="模型状态">{standardModel ? dataModelStatusLabels[standardModel.status] : '—'}</Descriptions.Item>
            <Descriptions.Item label="Schema 版本">{standardModel ? `v${standardModel.schemaVersion}` : '—'}</Descriptions.Item>
            <Descriptions.Item label="存储数据源">{standardModel?.storageDataSourceName ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="物理表" span={3}>{standardModel?.physicalTableName ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="字段 / 主键">{standardFields.length} / {standardFields.filter((field) => field.primaryKey).length}</Descriptions.Item>
          </BusinessDetailDescriptions>
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

      {dataService.type === 'SPATIAL_SERVICE' && dataService.spatialDefinition && (
        <>
          <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 4 }}>
            <Descriptions.Item label="空间模型">{standardModel?.name ?? relatedModels[0]?.name ?? '模型已删除'}</Descriptions.Item>
            <Descriptions.Item label="模型编码"><Typography.Text code>{standardModel?.code ?? relatedModels[0]?.code ?? standardModelId}</Typography.Text></Descriptions.Item>
            <Descriptions.Item label="存储数据源">{standardModel?.storageDataSourceName ?? relatedModels[0]?.storageDataSourceName ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="物理表"><Typography.Text code>{standardModel ? `${standardModel.schemaName ?? 'public'}.${standardModel.physicalTableName}` : '—'}</Typography.Text></Descriptions.Item>
            <Descriptions.Item label="Geometry">{standardFields.find((field) => field.fieldType === 'GEOMETRY')?.geometry?.kind ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="EPSG">{standardFields.find((field) => field.fieldType === 'GEOMETRY')?.geometry?.crs.code ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="主键"><Typography.Text code>{standardFields.find((field) => field.primaryKey)?.code ?? '—'}</Typography.Text></Descriptions.Item>
            <Descriptions.Item label="发布协议"><Space><Tag>只读 WMS</Tag><Tag>只读 WFS</Tag></Space></Descriptions.Item>
          </BusinessDetailDescriptions>
          <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 4 }}>
            <Descriptions.Item label="样式来源">{styleQuery.data ? styleModeLabels[styleQuery.data.mode] : '—'}</Descriptions.Item>
            <Descriptions.Item label="样式版本">{styleQuery.data ? `草稿 v${styleQuery.data.styleVersion} / 已应用 ${styleQuery.data.appliedStyleVersion == null ? '—' : `v${styleQuery.data.appliedStyleVersion}`}` : '—'}</Descriptions.Item>
            <Descriptions.Item label="同步状态">{styleQuery.data ? <Tag color={styleSyncStatusColors[styleQuery.data.syncStatus]}>{styleSyncStatusLabels[styleQuery.data.syncStatus]}</Tag> : '—'}</Descriptions.Item>
            <Descriptions.Item label="样式管理"><Button size="small" type="link" onClick={openCartography}>进入在线制图</Button></Descriptions.Item>
          </BusinessDetailDescriptions>
        </>
      )}

      {dataService.type === 'SQL_QUERY' && dataService.sqlDefinition && (
        <>
          <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 3 }}>
            <Descriptions.Item label="数据源">{sourceName ?? dataService.sqlDefinition.dataSourceId}</Descriptions.Item>
            <Descriptions.Item label="关联模型">{dataService.sqlDefinition.modelIds.length} 个</Descriptions.Item>
            <Descriptions.Item label="参数">{dataService.sqlDefinition.parameters.length} 个</Descriptions.Item>
          </BusinessDetailDescriptions>
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
          <BusinessDetailDescriptions column={{ xs: 1, md: 2, xl: 3 }}>
            <Descriptions.Item label="默认数据源">{sourceName ?? dataService.scriptDefinition.dataSourceId}</Descriptions.Item>
            <Descriptions.Item label="请求 Example">{dataService.scriptDefinition.examples.length} 个</Descriptions.Item>
          </BusinessDetailDescriptions>
          <div className="data-service-definition-code-block">
            <Typography.Text strong>Groovy 脚本</Typography.Text>
            <pre>{dataService.scriptDefinition.script}</pre>
          </div>
        </>
      )}
      </BusinessDetailSection>
    </div>
  );
};
