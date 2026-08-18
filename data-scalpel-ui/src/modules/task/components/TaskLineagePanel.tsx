import { DatabaseOutlined, FieldStringOutlined } from '@ant-design/icons';
import { Alert, Segmented, Select, Space, Tag } from 'antd';
import { useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  LineageGraphCanvas,
  type LineageCoverage,
  type LineageGranularity,
} from '../../model';
import { useTaskLineage } from '../hooks/useTasks';

const coverageLabels: Record<LineageCoverage, string> = {
  MODEL_ONLY: '仅表级', FIELD_PARTIAL: '字段部分覆盖', FIELD_COMPLETE: '字段完整覆盖',
};

export const TaskLineagePanel = ({ taskId }: { taskId: string }) => {
  const [granularity, setGranularity] = useState<LineageGranularity>('TABLE');
  const [flowKey, setFlowKey] = useState<string>();
  const [fieldKey, setFieldKey] = useState<string>();
  const query = useTaskLineage(taskId, granularity, flowKey, fieldKey);
  const data = query.data;
  const effectiveFlow = data?.flows.find((flow) => flow.flowKey === data.selectedFlowKey)
    ?? data?.flows[0];
  const graph = data?.graph;
  const errorMessage = query.error instanceof ApiError
    ? query.error.message
    : query.error ? '任务血缘加载失败' : undefined;
  const emptyDescription = data?.flows.length === 0
    ? '当前任务尚未发布正式血缘快照'
    : granularity === 'FIELD'
      ? '当前链路只有表级血缘或没有可展示的字段来源'
      : '当前链路暂无血缘节点';

  const onFlowChange = (next: string) => {
    setFlowKey(next);
    setFieldKey(undefined);
  };

  return (
    <div className="model-detail-tab-panel">
      <div className="model-lineage-toolbar">
        <Space size={8} wrap>
          <Segmented<LineageGranularity>
            value={granularity}
            onChange={(value) => { setGranularity(value); setFieldKey(undefined); }}
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
            <Select
              showSearch
              value={fieldKey ?? data?.selectedOutputFieldKey ?? undefined}
              placeholder="选择输出字段"
              className="model-lineage-field-select"
              optionFilterProp="label"
              onChange={setFieldKey}
              options={(effectiveFlow?.outputFields ?? []).map((field) => ({
                value: field.fieldKey, label: `${field.name} (${field.code})`,
              }))}
            />
          )}
          {effectiveFlow?.coverage && <Tag>{coverageLabels[effectiveFlow.coverage]}</Tag>}
          {data?.definitionVersion != null && <Tag>定义 v{data.definitionVersion}</Tag>}
        </Space>
      </div>
      {graph?.warnings.length ? (
        <Alert
          className="model-lineage-alert" type={graph.truncated ? 'warning' : 'info'} showIcon
          message={graph.truncated ? '血缘图已截断' : '血缘提示'}
          description={graph.warnings.join('；')}
        />
      ) : null}
      <LineageGraphCanvas
        graph={graph} loading={query.isFetching} errorMessage={errorMessage}
        emptyDescription={emptyDescription} ariaLabel="任务血缘关系图"
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
