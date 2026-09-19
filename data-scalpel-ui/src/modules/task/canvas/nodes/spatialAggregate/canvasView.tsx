import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList, NodeSplit } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialAggregate>) => {
  const {
    sourceTableName,
    outputTableName,
    groupByColumns,
    aggregations,
    dissolve,
  } = data.configuration;
  const dissolveEnabled = dissolve?.enabled === true;
  const connectedDissolve = dissolveEnabled
    && dissolve.groupingMode === 'CONNECTED_COMPONENTS';
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置空间聚合</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow
      source={sourceTableName}
      operation={connectedDissolve
        ? 'CONNECTED DISSOLVE'
        : dissolveEnabled ? 'DISSOLVE' : 'SPATIAL AGG'}
      target={outputTableName}
    />
    <NodeSplit
      leftLabel="分组"
      left={connectedDissolve
        ? '空间连通组'
        : groupByColumns.slice(0, 2).join(', ') || '全局'}
      rightLabel={dissolveEnabled ? '结果部件' : '空间指标'}
      right={dissolveEnabled
        ? dissolve.multipart ? 'Multipart' : 'Singlepart'
        : `${aggregations.length} 项`}
    />
    <NodePreviewList items={aggregations.slice(0, 2).map((item, index) => ({ key: `${index}`, label: `${item.kind}(${item.geometryColumnName || '?'})`, value: '→', meta: item.outputColumnName || '输出字段' }))} total={aggregations.length} />
    <NodeBadges>
      <NodeBadge tone="spatial">
        {connectedDissolve ? '相交 / 接触传递闭包' : `${groupByColumns.length} 个分组字段`}
      </NodeBadge>
      <NodeBadge>
        {dissolveEnabled
          ? `${dissolve.summaryStatistics.length} 个标量统计`
          : `${aggregations.length} 个空间聚合`}
      </NodeBadge>
    </NodeBadges>
  </NodeContent>;
};

export const spatialAggregateCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialAggregate> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 352,
    maxHeight: 224,
    configured: Boolean(configuration.sourceTableName),
    listCount: configuration.aggregations.length,
  }),
  Body: body,
};
