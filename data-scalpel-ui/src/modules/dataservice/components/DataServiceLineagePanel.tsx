import { DatabaseOutlined, FieldStringOutlined } from '@ant-design/icons';
import { Segmented, Select, Space, Tag } from 'antd';
import { useEffect, useState } from 'react';
import { ApiError } from '../../../shared/api/http';
import {
  LineageGraphCanvas,
  LineageFieldSelector,
  LineageWarningHint,
  useDataModel,
  type LineageCoverage,
  type LineageGranularity,
} from '../../model';
import { useDataServiceFieldLineage, useDataServiceLineage } from '../hooks/useDataServices';
import type { DataServiceDetail } from '../model/dataService';

interface DataServiceLineagePanelProps {
  dataService: DataServiceDetail;
}

const coverageLabels: Record<LineageCoverage, string> = {
  MODEL_ONLY: '仅表级', FIELD_PARTIAL: '字段部分覆盖', FIELD_COMPLETE: '字段完整覆盖',
};

export const DataServiceLineagePanel = ({ dataService }: DataServiceLineagePanelProps) => {
  const [granularity, setGranularity] = useState<LineageGranularity>('TABLE');
  const [fieldIds, setFieldIds] = useState<string[] | null>(null);
  const [submittedFieldIds, setSubmittedFieldIds] = useState<string[] | null>(null);
  const [depth, setDepth] = useState<1 | 2>(2);
  const standardModelId = dataService.type === 'STANDARD_TABLE'
    ? dataService.standardDefinition?.modelId
    : undefined;
  const unsupported = dataService.type !== 'STANDARD_TABLE';
  const modelQuery = useDataModel(standardModelId, Boolean(standardModelId));
  const fields = modelQuery.data?.fields ?? [];
  useEffect(() => {
    const timer = window.setTimeout(() => setSubmittedFieldIds(fieldIds), 250);
    return () => window.clearTimeout(timer);
  }, [fieldIds]);
  const tableQuery = useDataServiceLineage(dataService.id, 'TABLE', undefined, depth, granularity === 'TABLE');
  const fieldQuery = useDataServiceFieldLineage(
    dataService.id, submittedFieldIds, depth, granularity === 'FIELD' && !unsupported,
  );
  const fieldResult = fieldQuery.data;
  const effectiveFieldIds = fieldIds ?? fieldResult?.focusFields.map((field) => field.fieldKey) ?? [];
  const query = granularity === 'TABLE' ? tableQuery : fieldQuery;
  const graph = granularity === 'TABLE' ? tableQuery.data : fieldResult?.graph;
  const activeError = granularity === 'FIELD' && modelQuery.error ? modelQuery.error : query.error;
  const errorMessage = activeError instanceof ApiError ? activeError.message
    : activeError ? '服务血缘加载失败' : undefined;
  const emptyDescription = unsupported
    ? '当前服务类型暂未接入血缘'
    : !standardModelId
      ? '标准服务定义尚未配置'
      : granularity === 'FIELD' ? '当前所选字段未被服务暴露或暂无上游血缘' : '当前服务关联模型暂无上游血缘';
  const fieldCounts = fieldResult?.focusFields.reduce((counts, field) => {
    if (field.truncated) counts.truncated += 1;
    else if (!field.hasLineage) counts.empty += 1;
    else if (field.coverage === 'FIELD_COMPLETE') counts.complete += 1;
    else counts.partial += 1;
    return counts;
  }, { complete: 0, partial: 0, empty: 0, truncated: 0 });

  return (
    <div className="data-service-detail-tab-panel">
      <div className="model-lineage-toolbar">
        <Space size={8} wrap>
          <Segmented<LineageGranularity>
            value={granularity}
            onChange={setGranularity}
            disabled={unsupported}
            options={[
              { value: 'TABLE', label: '表级血缘', icon: <DatabaseOutlined /> },
              { value: 'FIELD', label: '字段级血缘', icon: <FieldStringOutlined /> },
            ]}
          />
          {granularity === 'FIELD' && !unsupported && (
            <LineageFieldSelector
              value={effectiveFieldIds}
              loading={modelQuery.isPending}
              placeholder="选择关联模型字段"
              onChange={setFieldIds}
              options={fields.map((field) => ({ key: field.id, label: `${field.name} (${field.code})` }))}
            />
          )}
          <span>层级</span>
          <Select<1 | 2>
            value={depth} className="model-lineage-depth-select" onChange={setDepth}
            options={[{ value: 1, label: '1 层' }, { value: 2, label: '2 层' }]}
          />
          {graph?.coverage && <Tag>{coverageLabels[graph.coverage]}</Tag>}
          <LineageWarningHint warnings={graph?.warnings} truncated={graph?.truncated} />
          {granularity === 'FIELD' && fieldCounts && (
            <span className="model-lineage-field-summary">
              完整 {fieldCounts.complete} · 部分 {fieldCounts.partial} · 未暴露/无路径 {fieldCounts.empty} · 截断 {fieldCounts.truncated}
            </span>
          )}
        </Space>
      </div>
      {granularity === 'FIELD' && fields.length === 0 && !modelQuery.isPending && !modelQuery.error ? (
        <LineageGraphCanvas
          graph={undefined} loading={false} emptyDescription="当前关联模型没有可选择的字段"
          ariaLabel="数据服务血缘关系图" onRetry={() => void modelQuery.refetch()}
          sideLabels={{ UPSTREAM: '上游', CURRENT: '当前服务', DOWNSTREAM: '下游' }}
        />
      ) : (
        <LineageGraphCanvas
          graph={graph} loading={query.isFetching || (granularity === 'FIELD' && modelQuery.isPending)}
          errorMessage={errorMessage} emptyDescription={emptyDescription}
          ariaLabel="数据服务血缘关系图" onRetry={() => {
            if (granularity === 'FIELD' && modelQuery.error) void modelQuery.refetch();
            else void query.refetch();
          }}
          focusFields={fieldResult?.focusFields}
          sideLabels={{ UPSTREAM: '上游', CURRENT: '当前服务', DOWNSTREAM: '下游' }}
          legend={[
            '当前服务是血缘终点，模型和加工任务只向上游展开',
            '已启用服务的字段关系以实际部署字段快照为准',
            '橙色虚线节点表示模型结构与部署快照不一致',
          ]}
        />
      )}
    </div>
  );
};
