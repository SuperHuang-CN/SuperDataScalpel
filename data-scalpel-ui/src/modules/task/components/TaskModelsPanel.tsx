import { CompactAlert as Alert } from '../../../shared/components/ContextualFeedback';
import { EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';
import { Button, Empty, Space, Table, Tag, Tooltip } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  dataModelStatusLabels,
  physicalTableModeLabels,
  type DataModelStatus,
} from '../../model';
import { useTaskModelRelations } from '../hooks/useTasks';
import type {
  ModelTaskRelationRole,
  TaskModelReferenceLocation,
  TaskRelatedModel,
} from '../model/task';

interface TaskModelsPanelProps {
  taskId: string;
  canViewModels: boolean;
}

const modelStatusColors: Record<DataModelStatus, string> = {
  DRAFT: 'default',
  PUBLISHED: 'success',
  DISABLED: 'warning',
};

const roleLabel: Record<ModelTaskRelationRole, string> = {
  INPUT: '任务输入',
  OUTPUT: '任务输出',
};

const roleTooltip: Record<ModelTaskRelationRole, string> = {
  INPUT: '任务消费该模型',
  OUTPUT: '任务产出该模型',
};

const locationLabel = (location: TaskModelReferenceLocation) => {
  if (location.referenceType === 'LOCAL_SQL_INPUT') return `输入 #${location.ordinal}`;
  if (location.referenceType === 'LOCAL_SQL_OUTPUT') return '输出模型';
  if (location.referenceType === 'MODEL_QUALITY_TARGET') return '质检目标模型';
  if (location.referenceType === 'CURRENT_LINEAGE') return location.nodeName ? `当前血缘 · ${location.nodeName}` : '当前血缘';
  return location.nodeName || location.nodeId || 'Canvas 节点';
};

const Locations = ({ locations }: { locations: TaskModelReferenceLocation[] }) => (
  <Space size={[4, 4]} wrap>
    {locations.map((location, index) => (
      <Tooltip
        key={`${location.referenceType}-${location.nodeId ?? location.ordinal ?? index}`}
        title={location.nodeId ? `节点 ID：${location.nodeId}` : undefined}
      >
        <Tag>{locationLabel(location)}</Tag>
      </Tooltip>
    ))}
  </Space>
);

export const TaskModelsPanel = ({ taskId, canViewModels }: TaskModelsPanelProps) => {
  const navigate = useNavigate();
  const relationsQuery = useTaskModelRelations(taskId);
  const openModel = (model: TaskRelatedModel) => navigate(`/model/${model.modelId}`);
  const columns: TableProps<TaskRelatedModel>['columns'] = [
    {
      title: '模型',
      key: 'model',
      width: 240,
      fixed: 'left',
      render: (_value, model) => (
        <Space orientation="vertical" size={0}>
          {canViewModels ? (
            <Button type="link" className="model-task-name-button" onClick={() => openModel(model)}>
              {model.modelName}
            </Button>
          ) : <span>{model.modelName}</span>}
          <code>{model.modelCode}</code>
        </Space>
      ),
    },
    {
      title: '关联角色',
      dataIndex: 'roles',
      width: 180,
      render: (roles: ModelTaskRelationRole[]) => (
        <Space size={[4, 4]} wrap>
          {roles.map((role) => (
            <Tooltip key={role} title={roleTooltip[role]}>
              <Tag color={role === 'INPUT' ? 'blue' : 'purple'}>{roleLabel[role]}</Tag>
            </Tooltip>
          ))}
        </Space>
      ),
    },
    {
      title: '模型状态',
      dataIndex: 'modelStatus',
      width: 110,
      render: (status: DataModelStatus) => (
        <Tag color={modelStatusColors[status]}>{dataModelStatusLabels[status]}</Tag>
      ),
    },
    {
      title: '物理表模式',
      dataIndex: 'physicalTableMode',
      width: 120,
      render: (mode: TaskRelatedModel['physicalTableMode']) => physicalTableModeLabels[mode],
    },
    {
      title: 'Schema 版本',
      dataIndex: 'schemaVersion',
      width: 110,
      render: (version: number) => `v${version}`,
    },
    {
      title: '引用位置',
      dataIndex: 'locations',
      width: 300,
      render: (locations: TaskModelReferenceLocation[]) => <Locations locations={locations} />,
    },
    ...(canViewModels ? [{
      title: '操作',
      key: 'actions',
      width: 58,
      fixed: 'right' as const,
      render: (_value: unknown, model: TaskRelatedModel) => (
        <Tooltip title="查看模型">
          <Button
            type="text"
            icon={<EyeOutlined />}
            aria-label={`查看模型${model.modelName}`}
            onClick={() => openModel(model)}
          />
        </Tooltip>
      ),
    }] : []),
  ];

  if (relationsQuery.error) {
    return (
      <div className="model-detail-tab-panel">
        <Alert
          type="error"
          showIcon
          message="关联模型加载失败"
          description="请稍后重试。"
          action={<Button size="small" onClick={() => void relationsQuery.refetch()}>重试</Button>}
        />
      </div>
    );
  }

  if (!relationsQuery.isPending && !relationsQuery.data?.configured) {
    return (
      <div className="model-detail-tab-panel">
        <Empty description="尚未配置任务定义" />
      </div>
    );
  }

  return (
    <div className="model-detail-tab-panel">
      <div className="model-tab-toolbar">
        <span>
          {relationsQuery.data?.definitionVersion
            ? `当前展示最后保存的定义 v${relationsQuery.data.definitionVersion}`
            : '当前展示最后保存的任务定义'}
        </span>
        <Tooltip title="刷新关联模型">
          <Button
            icon={<ReloadOutlined />}
            aria-label="刷新关联模型"
            loading={relationsQuery.isFetching}
            onClick={() => void relationsQuery.refetch()}
          />
        </Tooltip>
      </div>
      <Table<TaskRelatedModel>
        size="small"
        className="management-table"
        rowKey="modelId"
        columns={columns}
        dataSource={relationsQuery.data?.models ?? []}
        loading={relationsQuery.isPending}
        scroll={{ x: 1060, y: '100%' }}
        pagination={false}
      />
    </div>
  );
};
