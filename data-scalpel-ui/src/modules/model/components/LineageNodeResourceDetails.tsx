import { Button, Skeleton, Tag } from 'antd';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useCurrentUser } from '../../system';
import { useTask, taskTypeLabels, taskStatusLabels, taskPageHref } from '../../task';
import { useDataService, dataServiceTypeLabels, dataServiceStatusLabels, dataServiceDeploymentStatusLabels } from '../../dataservice';
import { useDataSource, DataSourceTypeIcon, dataSourceTypeLabels } from '../../datasource';
import { useFileDatasetCanvasMetadata, fileDatasetTypeLabels, fileDatasetParseStatusLabels } from '../../filedataset';
import { useDataModel } from '../hooks/useDataModels';
import { dataModelStatusLabels, dataModelFieldTypeLabels, type LineageGraphNode } from '../model/dataModel';

type Props = { node: LineageGraphNode };
const Row = ({ label, children }: { label: string; children: ReactNode }) => <div className="lineage-property-row"><dt>{label}</dt><dd>{children ?? '—'}</dd></div>;
const Note = ({ children }: { children: ReactNode }) => <p className="lineage-property-note">{children}</p>;
const OpenResource = ({ to, children }: { to: string; children: ReactNode }) => <Link className="lineage-resource-link" to={to}>{children} →</Link>;
const Description = ({ text }: { text: string | null }) => text ? <p className="lineage-resource-description">{text}</p> : null;

function QueryState({ query }: { query: { isPending: boolean; isError: boolean; refetch: () => unknown } }) {
  if (query.isError) return <div className="lineage-property-error" role="status">当前属性加载失败<Button size="small" type="link" onClick={() => void query.refetch()}>重试</Button></div>;
  return query.isPending ? <Skeleton active title={false} paragraph={{ rows: 4 }} /> : null;
}

function ModelProperties({ node }: Props) {
  const query = useDataModel(node.modelId ?? undefined, true);
  if (query.isPending || query.isError || !query.data) return <QueryState query={query} />;
  const { model, fields } = query.data;
  const field = node.kind === 'FIELD' ? fields.find((item) => item.id === node.modelFieldId) : undefined;
  if (node.kind === 'FIELD' && !field) return <><Note>当前模型中已找不到该字段，请结合下方血缘快照查看。</Note><OpenResource to={`/model/${model.id}?tab=fields`}>查看所属模型</OpenResource></>;
  return <>
    <div className="lineage-property-status"><Tag>{dataModelStatusLabels[model.status]}</Tag><span>Schema v{model.schemaVersion}</span></div>
    <dl className="lineage-property-list">
      {field ? <>
        <Row label="字段编码">{field.code}</Row>
        <Row label="字段名称">{field.name}</Row>
        <Row label="数据类型">{dataModelFieldTypeLabels[field.fieldType]}{field.length != null ? ` (${field.length})` : field.precision != null ? ` (${field.precision}, ${field.scale ?? 0})` : ''}</Row>
        <Row label="字段约束">{field.primaryKey ? '业务主键 · ' : ''}{field.nullable ? '允许空值' : '不允许空值'}</Row>
        {field.geometry && <Row label="空间类型">{field.geometry.kind} · {field.geometry.dimension} · {field.geometry.crs.authority}:{field.geometry.crs.code}</Row>}
        {field.standardDictionary && <Row label="关联码表">{field.standardDictionary.name}</Row>}
        <Row label="所属模型">{model.name}</Row>
      </> : <>
        <Row label="模型编码">{model.code}</Row>
        <Row label="数仓分层">{model.warehouseLayer ? `${model.warehouseLayer.code} · ${model.warehouseLayer.name}` : '未分层'}</Row>
        <Row label="管理模式">{model.physicalTableMode === 'MANAGED' ? '托管表' : '逻辑注册'}</Row>
        <Row label="字段数量">{fields.length} 个</Row>
        <Row label="业务主键">{fields.filter((item) => item.primaryKey).map((item) => item.code).join('、') || '未设置'}</Row>
      </>}
      <Row label="数据存储">{model.storageDataSourceName || '未绑定'}</Row>
      <Row label="数据库 / Schema">{[model.catalogName, model.schemaName].filter(Boolean).join(' / ') || '—'}</Row>
      <Row label="物理表">{model.physicalTableName || '未绑定'}</Row>
    </dl>
    <Description text={field ? field.description : model.description} />
    <OpenResource to={`/model/${model.id}${field ? '?tab=fields' : ''}`}>{field ? '查看字段定义' : '查看模型详情'}</OpenResource>
  </>;
}

function TaskProperties({ node }: Props) {
  const query = useTask(node.taskId ?? undefined);
  if (query.isPending || query.isError || !query.data) return <QueryState query={query} />;
  const task = query.data;
  return <>
    <div className="lineage-property-status"><Tag>{taskStatusLabels[task.status]}</Tag><span>{taskTypeLabels[task.type]}</span></div>
    <dl className="lineage-property-list">
      <Row label="执行引擎">{task.computeEngineName || '未绑定'}</Row>
      <Row label="任务定义">{task.definitionConfigured ? `已配置${task.definitionVersion != null ? ` · v${task.definitionVersion}` : ''}` : '未配置'}</Row>
      {task.outputModelName && <Row label="输出模型">{task.outputModelName}</Row>}
      {task.qualityTargetModelName && <Row label="质检模型">{task.qualityTargetModelName}</Row>}
    </dl>
    <Description text={task.description} />
    <OpenResource to={taskPageHref(`/task/${task.id}`, '', task.type)}>查看任务详情</OpenResource>
  </>;
}

function ServiceProperties({ node }: Props) {
  const query = useDataService(node.dataServiceId ?? undefined);
  if (query.isPending || query.isError || !query.data) return <QueryState query={query} />;
  const service = query.data;
  return <>
    <div className="lineage-property-status"><Tag>{dataServiceStatusLabels[service.status]}</Tag><span>{dataServiceTypeLabels[service.type]}</span></div>
    <dl className="lineage-property-list">
      <Row label="服务编码">{service.code}</Row>
      <Row label="服务路径">{service.contextPath || '未配置'}</Row>
      <Row label="部署状态">{service.deploymentStatus ? dataServiceDeploymentStatusLabels[service.deploymentStatus] : '未部署'}</Row>
      <Row label="发布修订">r{service.revision}</Row>
      <Row label="定义版本">{service.definitionVersion != null ? `v${service.definitionVersion}` : '未配置'}</Row>
    </dl>
    <Description text={service.description} />
    <OpenResource to={`/dataservice/${service.id}`}>查看服务详情</OpenResource>
  </>;
}

function SourceProperties({ node }: Props) {
  const query = useDataSource(node.dataSourceId ?? undefined);
  if (query.isPending || query.isError || !query.data) return <QueryState query={query} />;
  const source = query.data;
  return <>
    <div className="lineage-property-status"><DataSourceTypeIcon type={source.type} /><strong>{source.name}</strong></div>
    <dl className="lineage-property-list">
      <Row label="连接类型">{dataSourceTypeLabels[source.type]}</Row>
      <Row label="启用状态">{source.enabled ? '已启用' : '已停用'}</Row>
      <Row label="数据源编码">{source.code}</Row>
      {source.connection.kind === 'JDBC' && <>
        <Row label="连接默认库">{source.connection.databaseName || '—'}</Row>
        <Row label="默认 Schema">{source.connection.schemaName || '—'}</Row>
      </>}
    </dl>
    <OpenResource to={`/datasource/${source.id}`}>查看数据源详情</OpenResource>
  </>;
}

function FileTableProperties({ node }: Props) {
  const query = useFileDatasetCanvasMetadata(node.resourceId ? [node.resourceId] : []);
  if (query.isPending || query.isError || !query.data) return <QueryState query={query} />;
  const table = query.data.tables.find((item) => item.fileDatasetTableId === node.resourceId);
  if (!table) return <Note>当前逻辑表不可用，保留下方血缘快照供查看。</Note>;
  return <>
    <div className="lineage-property-status"><Tag>{fileDatasetParseStatusLabels[table.parseStatus]}</Tag><span>{fileDatasetTypeLabels[table.datasetType]}</span></div>
    <dl className="lineage-property-list">
      <Row label="文件数据集">{table.fileDatasetName}</Row>
      <Row label="逻辑表">{table.name}</Row>
      <Row label="表编码">{table.code}</Row>
      <Row label="字段数量">{table.fields.length} 个</Row>
    </dl>
    <OpenResource to={`/file-dataset/${table.fileDatasetId}`}>查看文件数据集</OpenResource>
  </>;
}

const externalLabels = {
  KAFKA_TOPIC: 'Kafka Topic', FILE_DATASET_TABLE: '文件数据集逻辑表', HTTP_API_RESOURCE: 'HTTP API',
  SPATIAL_SERVICE_RESOURCE: '空间服务', OBJECT_STORAGE_PATH: '对象存储', JDBC_QUERY_RESULT: 'JDBC 查询结果',
};

export function LineageNodeResourceDetails({ node }: Props) {
  const user = useCurrentUser();
  const modelResource = (node.kind === 'MODEL' || node.kind === 'FIELD') && Boolean(node.modelId);
  const fileResource = node.kind === 'EXTERNAL_RESOURCE' && node.externalResourceType === 'FILE_DATASET_TABLE' && Boolean(node.resourceId);
  const permission = modelResource ? 'model.view' : node.kind === 'TASK' && node.taskId ? 'task.view'
    : node.kind === 'DATA_SERVICE' && node.dataServiceId ? 'service.view' : fileResource ? 'filedataset.view'
      : node.dataSourceId ? 'datasource.view' : null;
  return <section className="lineage-resource-properties" aria-label="当前资源属性">
    <h4>当前资源属性</h4>
    {node.kind === 'EXTERNAL_RESOURCE' && node.externalResourceType && <div className="lineage-property-status">{externalLabels[node.externalResourceType]}</div>}
    {!permission ? <Note>该节点仅提供血缘快照信息。</Note>
      : user.isPending ? <Skeleton active title={false} paragraph={{ rows: 3 }} />
        : user.isError ? <QueryState query={user} />
          : !user.data?.permissions.includes(permission) ? <Note>暂无查看该资源详情的权限，仍可查看血缘关系。</Note>
            : modelResource ? <ModelProperties node={node} />
              : node.kind === 'TASK' && node.taskId ? <TaskProperties node={node} />
                : node.kind === 'DATA_SERVICE' && node.dataServiceId ? <ServiceProperties node={node} />
                  : fileResource ? <FileTableProperties node={node} /> : <SourceProperties node={node} />}
  </section>;
}
