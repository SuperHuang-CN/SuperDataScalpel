import { DatabaseOutlined, FieldStringOutlined } from '@ant-design/icons';
import { Segmented, Select, Space, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import { useDataModelFieldLineage, useDataModelLineage } from '../hooks/useDataModels';
import type {
  DataModel,
  DataModelField,
  LineageCoverage,
  LineageDirection,
  LineageGranularity,
} from '../model/dataModel';
import { LineageGraphCanvas } from './LineageGraphCanvas';
import { LineageFieldSelector } from './LineageFieldSelector';
import { LineageWarningHint } from './LineageWarningHint';

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
  const [fieldIds, setFieldIds] = useState<string[] | null>(null);
  const [submittedFieldIds, setSubmittedFieldIds] = useState<string[] | null>(null);
  const [direction, setDirection] = useState<LineageDirection>('BOTH');
  const [depth, setDepth] = useState<1 | 2>(2);
  useEffect(() => {
    const timer = window.setTimeout(() => setSubmittedFieldIds(fieldIds), 250);
    return () => window.clearTimeout(timer);
  }, [fieldIds]);
  const tableQuery = useDataModelLineage(model.id, 'TABLE', undefined, direction, depth, granularity === 'TABLE');
  const fieldQuery = useDataModelFieldLineage(
    model.id, submittedFieldIds, direction, depth, granularity === 'FIELD',
  );
  const fieldResult = fieldQuery.data;
  const effectiveFieldIds = fieldIds ?? fieldResult?.focusFields.map((field) => field.fieldKey) ?? [];
  const query = granularity === 'TABLE' ? tableQuery : fieldQuery;
  const graph = granularity === 'TABLE' ? tableQuery.data : fieldResult?.graph;
  const errorMessage = query.error instanceof ApiError ? query.error.message : query.error ? '血缘加载失败' : undefined;
  const emptyDescription = graph?.coverage === 'MODEL_ONLY'
    ? '当前路径只有表级血缘，尚未生成字段来源关系'
    : granularity === 'FIELD' ? '当前所选字段暂无血缘关系' : '当前模型暂无血缘关系';
  const focusSummary = fieldResult?.focusFields.reduce((result, field) => {
    const key = field.truncated ? 'truncated' : !field.hasLineage ? 'empty'
      : field.coverage === 'FIELD_COMPLETE' ? 'complete' : 'partial';
    result[key] += 1;
    return result;
  }, { complete: 0, partial: 0, empty: 0, truncated: 0 });

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
            <LineageFieldSelector
              value={effectiveFieldIds}
              placeholder="选择当前模型字段"
              onChange={setFieldIds}
              options={fields.map((field) => ({ key: field.id, label: `${field.name} (${field.code})` }))}
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
          <LineageWarningHint warnings={graph?.warnings} truncated={graph?.truncated} />
          {granularity === 'FIELD' && focusSummary && (
            <span className="model-lineage-field-summary">
              完整 {focusSummary.complete} · 部分 {focusSummary.partial} · 无路径 {focusSummary.empty} · 截断 {focusSummary.truncated}
            </span>
          )}
        </Space>
      </div>
      {granularity === 'FIELD' && fields.length === 0 ? (
        <LineageGraphCanvas
          graph={undefined} loading={false} emptyDescription="当前模型没有可选择的字段"
          ariaLabel="模型血缘关系图" onRetry={() => void query.refetch()}
        />
      ) : (
        <LineageGraphCanvas
          graph={graph} loading={query.isFetching} errorMessage={errorMessage}
          emptyDescription={emptyDescription} ariaLabel="模型血缘关系图"
          focusFields={fieldResult?.focusFields}
          onRetry={() => void query.refetch()}
        />
      )}
    </div>
  );
};
