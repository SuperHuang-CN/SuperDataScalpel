import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList, NodeSplit } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialAggregate>) => {
  const { sourceTableName, outputTableName, groupByColumns, aggregations } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置空间聚合</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={sourceTableName} operation="SPATIAL AGG" target={outputTableName} />
    <NodeSplit leftLabel="分组" left={groupByColumns.slice(0, 2).join(', ') || '全局'} rightLabel="空间指标" right={`${aggregations.length} 项`} />
    <NodePreviewList items={aggregations.slice(0, 2).map((item, index) => ({ key: `${index}`, label: `${item.kind}(${item.geometryColumnName || '?'})`, value: '→', meta: item.outputColumnName || '输出字段' }))} total={aggregations.length} />
    <NodeBadges><NodeBadge tone="spatial">{groupByColumns.length} 个分组字段</NodeBadge><NodeBadge>{aggregations.length} 个空间聚合</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const spatialAggregateCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialAggregate> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: Boolean(configuration.sourceTableName), listCount: configuration.aggregations.length }),
  Body: body,
};
