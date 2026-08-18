import { DatabaseOutlined, FieldStringOutlined } from '@ant-design/icons';
import { Alert, Segmented, Select, Space, Tag } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useDataModelLineage } from '../hooks/useDataModels';
import type {
  DataModel,
  DataModelField,
  LineageCoverage,
  LineageDirection,
  LineageGranularity,
} from '../model/dataModel';
import { LineageGraphCanvas } from './LineageGraphCanvas';

interface DataModelLineagePanelProps {
  model: DataModel;
  fields: DataModelField[];
}

const coverageLabels: Record<LineageCoverage, string> = {
  MODEL_ONLY: '仅表级', FIELD_PARTIAL: '字段部分覆盖', FIELD_COMPLETE: '字段完整覆盖',
};

const coverageColors: Record<LineageCoverage, string> = {
  MODEL_ONLY: 'default', FIELD_PARTIAL: 'warning', FIELD_COMPLETE: 'success',
};

export const DataModelLineagePanel = ({ model, fields }: DataModelLineagePanelProps) => {
  const [granularity, setGranularity] = useState<LineageGranularity>('TABLE');
  const [fieldId, setFieldId] = useState<string>();
  const [direction, setDirection] = useState<LineageDirection>('BOTH');
  const [depth, setDepth] = useState<1 | 2>(2);
  const effectiveFieldId = fieldId && fields.some((field) => field.id === fieldId)
    ? fieldId
    : fields[0]?.id;
  const query = useDataModelLineage(
    model.id, granularity, effectiveFieldId, direction, depth,
    granularity === 'TABLE' || Boolean(effectiveFieldId),
  );
  const graph = query.data;
  const statusMessage = graph?.warnings.join('；');
  const errorMessage = query.error instanceof ApiError ? query.error.message : query.error ? '血缘加载失败' : undefined;
  const emptyDescription = graph?.coverage === 'MODEL_ONLY'
    ? '当前路径只有表级血缘，尚未生成字段来源关系'
    : granularity === 'FIELD' ? '当前字段暂无血缘关系' : '当前模型暂无血缘关系';

  return (
    <div className="model-detail-tab-panel">
      <div className="model-lineage-toolbar">
        <Space size={8} wrap>
          <Segmented<LineageGranularity>
            value={granularity}
            onChange={setGranularity}
            options={[
              { value: 'TABLE', label: '表级血缘', icon: <DatabaseOutlined /> },
              { value: 'FIELD', label: '字段级血缘', icon: <FieldStringOutlined /> },
            ]}
          />
          {granularity === 'FIELD' && (
            <Select
              showSearch value={effectiveFieldId} className="model-lineage-field-select"
              placeholder="选择当前模型字段" optionFilterProp="label" onChange={setFieldId}
              options={fields.map((field) => ({ value: field.id, label: `${field.name} (${field.code})` }))}
            />
          )}
          <span>方向</span>
          <Select<LineageDirection>
            value={direction} className="model-lineage-direction-select" onChange={setDirection}
            options={[
              { value: 'UPSTREAM', label: '仅上游' },
              { value: 'DOWNSTREAM', label: '仅下游' },
              { value: 'BOTH', label: '上下游' },
            ]}
          />
          <span>层级</span>
          <Select<1 | 2>
            value={depth} className="model-lineage-depth-select" onChange={setDepth}
            options={[{ value: 1, label: '1 层' }, { value: 2, label: '2 层' }]}
          />
          {graph?.coverage && <Tag color={coverageColors[graph.coverage]}>{coverageLabels[graph.coverage]}</Tag>}
        </Space>
      </div>
      {statusMessage && (
        <Alert
          className="model-lineage-alert" type={graph?.truncated ? 'warning' : 'info'} showIcon
          message={graph?.truncated ? '血缘图已截断' : '血缘覆盖提示'} description={statusMessage}
        />
      )}
      {granularity === 'FIELD' && !effectiveFieldId ? (
        <LineageGraphCanvas
          graph={undefined} loading={false} emptyDescription="当前模型没有可选择的字段"
          ariaLabel="模型血缘关系图" onRetry={() => void query.refetch()}
        />
      ) : (
        <LineageGraphCanvas
          graph={graph} loading={query.isFetching} errorMessage={errorMessage}
          emptyDescription={emptyDescription} ariaLabel="模型血缘关系图"
          onRetry={() => void query.refetch()}
        />
      )}
    </div>
  );
};
