import { DatabaseOutlined, FieldStringOutlined } from '@ant-design/icons';
import { Segmented, Select, Space, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  LineageGraphCanvas,
  LineageFieldSelector,
  LineageWarningHint,
  type LineageCoverage,
  type LineageGranularity,
} from '../../model';
import { useTaskFieldLineage, useTaskLineage } from '../hooks/useTasks';

const coverageLabels: Record<LineageCoverage, string> = {
  MODEL_ONLY: '仅表级', FIELD_PARTIAL: '字段部分覆盖', FIELD_COMPLETE: '字段完整覆盖',
};

export const TaskLineagePanel = ({ taskId }: { taskId: string }) => {
  const [granularity, setGranularity] = useState<LineageGranularity>('TABLE');
  const [flowKey, setFlowKey] = useState<string>();
  const [fieldKeys, setFieldKeys] = useState<string[] | null>(null);
  const [submittedFieldKeys, setSubmittedFieldKeys] = useState<string[] | null>(null);
  useEffect(() => {
    const timer = window.setTimeout(() => setSubmittedFieldKeys(fieldKeys), 250);
    return () => window.clearTimeout(timer);
  }, [fieldKeys]);
  const tableQuery = useTaskLineage(taskId, 'TABLE', flowKey, undefined, granularity === 'TABLE');
  const fieldQuery = useTaskFieldLineage(taskId, flowKey, submittedFieldKeys, granularity === 'FIELD');
  const query = granularity === 'TABLE' ? tableQuery : fieldQuery;
  const data = granularity === 'TABLE' ? tableQuery.data : fieldQuery.data;
  const effectiveFlow = data?.flows.find((flow) => flow.flowKey === data.selectedFlowKey)
    ?? data?.flows[0];
  const fieldResult = fieldQuery.data?.fieldGraph;
  const graph = granularity === 'TABLE' ? tableQuery.data?.graph : fieldResult?.graph;
  const effectiveFieldKeys = fieldKeys ?? fieldResult?.focusFields.map((field) => field.fieldKey) ?? [];
  const errorMessage = query.error instanceof ApiError
    ? query.error.message
    : query.error ? '任务血缘加载失败' : undefined;
  const emptyDescription = data?.flows.length === 0
    ? '当前任务尚未发布正式血缘快照'
    : granularity === 'FIELD'
      ? '当前链路只有表级血缘或没有可展示的字段来源'
      : '当前链路暂无血缘节点';
  const fieldCounts = fieldResult?.focusFields.reduce((counts, field) => {
    if (field.truncated) counts.truncated += 1;
    else if (!field.hasLineage) counts.empty += 1;
    else if (field.coverage === 'FIELD_COMPLETE') counts.complete += 1;
    else counts.partial += 1;
    return counts;
  }, { complete: 0, partial: 0, empty: 0, truncated: 0 });

  const onFlowChange = (next: string) => {
    setFlowKey(next);
    setFieldKeys(null);
    setSubmittedFieldKeys(null);
  };

  return (
    <div className="model-detail-tab-panel">
      <div className="model-lineage-toolbar">
        <Space size={8} wrap>
          <Segmented<LineageGranularity>
            value={granularity}
            onChange={(value) => { setGranularity(value); setFieldKeys(null); setSubmittedFieldKeys(null); }}
            options={[
              { value: 'TABLE', label: '表级血缘', icon: <DatabaseOutlined /> },
              { value: 'FIELD', label: '字段级血缘', icon: <FieldStringOutlined /> },
            ]}
          />
          <Select
            value={flowKey ?? data?.selectedFlowKey ?? undefined}
            placeholder="选择输出链路"
            className="task-lineage-flow-select"
            onChange={onFlowChange}
            options={(data?.flows ?? []).map((flow) => ({
              value: flow.flowKey, label: flow.outputLabel,
            }))}
          />
          {granularity === 'FIELD' && (
            <LineageFieldSelector
              value={effectiveFieldKeys}
              placeholder="选择输出字段"
              onChange={setFieldKeys}
              options={(effectiveFlow?.outputFields ?? []).map((field) => ({
                key: field.fieldKey, label: `${field.name} (${field.code})`,
              }))}
            />
          )}
          {effectiveFlow?.coverage && <Tag>{coverageLabels[effectiveFlow.coverage]}</Tag>}
          <LineageWarningHint warnings={graph?.warnings} truncated={graph?.truncated} />
          {data?.definitionVersion != null && <Tag>定义 v{data.definitionVersion}</Tag>}
          {granularity === 'FIELD' && fieldCounts && (
            <span className="model-lineage-field-summary">
              完整 {fieldCounts.complete} · 部分 {fieldCounts.partial} · 无路径 {fieldCounts.empty} · 截断 {fieldCounts.truncated}
            </span>
          )}
        </Space>
      </div>
      <LineageGraphCanvas
        graph={graph} loading={query.isFetching} errorMessage={errorMessage}
        emptyDescription={emptyDescription} ariaLabel="任务血缘关系图"
        focusFields={fieldResult?.focusFields}
        onRetry={() => void query.refetch()}
        sideLabels={{ UPSTREAM: '输入', CURRENT: '任务', DOWNSTREAM: '输出' }}
        legend={[
          '每个输出节点是一条独立链路，输入不会跨输出串联',
          '橙色虚线节点表示模型结构已变化',
          '字段用途与值来源分别展示',
        ]}
      />
    </div>
  );
};
