import { DownOutlined, LinkOutlined, RightOutlined } from '@ant-design/icons';
import { Alert, Button, Empty, Space, Table, Tag, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { ManagementCode } from '../../../../shared/components/ManagementListCells';
import {
  dataModelStatusLabels,
  useDataModel,
  type DataModelField,
  type DataModelStatus,
} from '../../../model';
import type { DataServiceRelatedModelView } from '../../hooks/useDataServiceRelatedModels';
import { typeDescription } from '../../model/dataServiceEditor';
import type { ModelSelectionCandidate } from './ModelSelectionWorkspace';
import { SqlModelPickerDrawer } from './SqlModelPickerDrawer';

const modelStatusColors: Record<DataModelStatus, string> = {
  DRAFT: 'default', PUBLISHED: 'success', DISABLED: 'warning',
};

interface SqlModelSelectionProps {
  selectedModelIds: string[];
  relatedModels: DataServiceRelatedModelView[];
  dataSourceId?: string;
  dataSourceName?: string;
  dataSourceSelected: boolean;
  readOnly: boolean;
  canViewModels: boolean;
  onChange: (modelIds: string[]) => void;
}

const unresolvedCandidate = (id: string, reason: string): ModelSelectionCandidate => ({
  id,
  code: null,
  name: null,
  status: null,
  directoryId: null,
  directoryName: null,
  warehouseLayerId: null,
  warehouseLayerCode: null,
  warehouseLayerName: null,
  storageDataSourceId: null,
  storageDataSourceCode: null,
  storageDataSourceName: null,
  catalogName: null,
  schemaName: null,
  physicalTableName: null,
  schemaVersion: null,
  selectable: false,
  unavailableReason: reason,
  updatedAt: null,
});

const relatedCandidate = (
  model: DataServiceRelatedModelView,
  dataSourceId?: string,
): ModelSelectionCandidate => {
  const reason = model.error
    ? '模型摘要加载失败'
    : model.loading
      ? '正在加载模型摘要'
      : !model.resolved
        ? '模型无法解析'
        : model.storageDataSourceId !== dataSourceId
          ? '模型已不属于当前数据源'
          : null;
  return {
    id: model.modelId,
    code: model.code,
    name: model.name,
    status: model.status,
    directoryId: model.directoryId,
    directoryName: model.directoryName,
    warehouseLayerId: model.warehouseLayerId,
    warehouseLayerCode: model.warehouseLayerCode,
    warehouseLayerName: model.warehouseLayerName,
    storageDataSourceId: model.storageDataSourceId,
    storageDataSourceCode: model.storageDataSourceCode,
    storageDataSourceName: model.storageDataSourceName,
    catalogName: model.catalogName,
    schemaName: model.schemaName,
    physicalTableName: model.physicalTableName,
    schemaVersion: model.schemaVersion,
    selectable: reason === null,
    unavailableReason: reason,
    updatedAt: model.updatedAt,
  };
};

const physicalTableName = (model: ModelSelectionCandidate) => model.physicalTableName ?? '';

interface SqlModelStructureProps {
  modelId: string;
}

const SqlModelStructure = ({ modelId }: SqlModelStructureProps) => {
  const modelQuery = useDataModel(modelId, true);
  if (modelQuery.isError) {
    return (
      <Alert
        type="error"
        showIcon
        message="字段结构加载失败"
        action={<Button size="small" onClick={() => void modelQuery.refetch()}>重试</Button>}
      />
    );
  }
  const fields = modelQuery.data?.fields ?? [];
  return (
    <div className="sql-selected-model-structure" id={`sql-model-structure-${modelId}`}>
      <div className="sql-selected-model-structure-meta">
        <Typography.Text type="secondary">
          {modelQuery.isFetching
            ? '正在加载字段结构…'
            : `Schema v${modelQuery.data?.model.schemaVersion ?? '—'} · ${fields.length} 个字段`}
        </Typography.Text>
      </div>
      <Table<DataModelField>
        rowKey="id"
        size="small"
        loading={modelQuery.isFetching}
        pagination={false}
        dataSource={fields}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="模型尚未定义字段" /> }}
        columns={[
          {
            title: '字段',
            render: (_, field) => (
              <div className="sql-selected-model-field-identity">
                <Typography.Text strong ellipsis={{ tooltip: field.name }}>{field.name}</Typography.Text>
                <Typography.Text code ellipsis={{ tooltip: field.code }}>{field.code}</Typography.Text>
                {field.description && (
                  <Typography.Text type="secondary" ellipsis={{ tooltip: field.description }}>
                    {field.description}
                  </Typography.Text>
                )}
              </div>
            ),
          },
          {
            title: '类型 / 约束',
            width: 120,
            render: (_, field) => (
              <div className="sql-selected-model-field-type">
                <Typography.Text code>{typeDescription({
                  type: field.fieldType,
                  length: field.length,
                  precision: field.precision,
                  scale: field.scale,
                })}</Typography.Text>
                <Typography.Text type="secondary">
                  {field.primaryKey ? '主键 · ' : ''}{field.nullable ? '可空' : '非空'}
                </Typography.Text>
              </div>
            ),
          },
        ]}
      />
    </div>
  );
};

export const SqlModelSelection = ({
  selectedModelIds,
  relatedModels,
  dataSourceId,
  dataSourceName,
  dataSourceSelected,
  readOnly,
  canViewModels,
  onChange,
}: SqlModelSelectionProps) => {
  const [pickerOpen, setPickerOpen] = useState(false);
  const [expandedModelId, setExpandedModelId] = useState<string>();
  const [confirmedCandidates, setConfirmedCandidates] = useState<Map<string, ModelSelectionCandidate>>(new Map());
  const relatedCandidates = useMemo(
    () => new Map(relatedModels.map((model) => [model.modelId, relatedCandidate(model, dataSourceId)])),
    [dataSourceId, relatedModels],
  );
  const resolvedModelIds = useMemo(
    () => new Set(relatedModels
      .filter((model) => model.resolved && !model.loading && !model.error)
      .map((model) => model.modelId)),
    [relatedModels],
  );

  const selectedCandidates = selectedModelIds.map((id) => (
    confirmedCandidates.get(id)
      ?? relatedCandidates.get(id)
      ?? unresolvedCandidate(id, canViewModels ? '正在加载模型摘要' : '缺少模型查看权限')
  ));
  const invalidCount = selectedCandidates.filter((candidate) => !candidate.selectable).length;
  const openPicker = () => {
    if (dataSourceSelected && canViewModels) setPickerOpen(true);
  };

  return (
    <div className="sql-model-selection">
      <div className="sql-model-selection-heading">
        <div>
          <Typography.Text strong>关联模型</Typography.Text>
          <div>
            <Typography.Text type="secondary">
              {selectedModelIds.length > 0 ? `已关联 ${selectedModelIds.length} 个模型` : '尚未关联模型'}
            </Typography.Text>
          </div>
        </div>
        {!readOnly && (
          <Button
            size="small"
            icon={<LinkOutlined />}
            disabled={!dataSourceSelected || !canViewModels}
            onClick={openPicker}
          >
            {selectedModelIds.length > 0 ? '管理模型' : '选择模型'}
          </Button>
        )}
      </div>
      {!canViewModels && selectedModelIds.length > 0 ? (
        <Alert
          type="warning"
          showIcon
          message="缺少模型查看权限"
          description="当前仅保留并展示定义中的模型引用 ID，不会加载模型详情。"
        />
      ) : invalidCount > 0 && (
        <Alert
          type="warning"
          showIcon
          message={`${invalidCount} 个已关联模型需要处理`}
          description="模型可能无法解析或已不属于当前数据源，请打开选择器移除或重新选择。"
        />
      )}
      {selectedCandidates.length > 0 ? (
        <div className="sql-model-selection-summary">
          {selectedCandidates.map((model) => {
            const expanded = expandedModelId === model.id;
            const canExpand = canViewModels
              && (confirmedCandidates.has(model.id) || resolvedModelIds.has(model.id));
            const location = physicalTableName(model);
            return (
              <div className="data-service-selected-model" key={model.id}>
                <div className="sql-selected-model-header">
                  <Button
                    type="text"
                    size="small"
                    className="sql-selected-model-expand"
                    icon={expanded ? <DownOutlined /> : <RightOutlined />}
                    disabled={!canExpand}
                    aria-label={`${expanded ? '收起' : '展开'} ${model.name ?? model.id} 字段结构`}
                    aria-expanded={expanded}
                    aria-controls={`sql-model-structure-${model.id}`}
                    onClick={() => setExpandedModelId(expanded ? undefined : model.id)}
                  />
                  <div className="sql-selected-model-summary-content">
                    <Space size={5} wrap>
                      {model.status && <Tag color={modelStatusColors[model.status]}>{dataModelStatusLabels[model.status]}</Tag>}
                      <Typography.Text strong>{model.name ?? '模型无法解析'}</Typography.Text>
                      <ManagementCode value={model.code ?? model.id} />
                    </Space>
                    <Typography.Text
                      copyable={Boolean(location)}
                      code
                      type="secondary"
                      ellipsis={{ tooltip: location || model.unavailableReason || undefined }}
                    >
                      {location || model.unavailableReason || '—'}
                    </Typography.Text>
                  </div>
                </div>
                {expanded && <SqlModelStructure modelId={model.id} />}
              </div>
            );
          })}
        </div>
      ) : (
        <div className="sql-model-selection-empty">
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未关联模型" />
        </div>
      )}
      {pickerOpen && dataSourceId && (
        <SqlModelPickerDrawer
          open
          dataSourceId={dataSourceId}
          dataSourceName={dataSourceName}
          selectedModelIds={selectedModelIds}
          selectedCandidates={selectedCandidates}
          readOnly={readOnly}
          onClose={() => setPickerOpen(false)}
          onConfirm={(modelIds, candidates) => {
            setConfirmedCandidates(new Map(candidates.map((candidate) => [candidate.id, candidate])));
            onChange(modelIds);
          }}
        />
      )}
    </div>
  );
};
