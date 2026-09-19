import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList, NodeSplit } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.Aggregate>) => {
  const { sourceTableName, outputTableName, groupByColumns, aggregations } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置聚合</NodeEmpty>;
  const result = outputTable(data, outputTableName);
  return <NodeContent variant="aggregate">
    <NodeFlow source={sourceTableName} operation="AGG" target={outputTableName} />
    <NodeSplit leftLabel="分组" left={groupByColumns.slice(0, 2).join(', ') || '全局聚合'} rightLabel="指标" right={`${aggregations.length} 项`} />
    <NodePreviewList items={aggregations.slice(0, 2).map((item, index) => ({ key: `${index}`, label: `${item.function ?? '?'}(${item.sourceColumnName ?? '*'})`, value: '→', meta: `${item.outputColumnName || '输出字段'}${item.distinct ? ' · DISTINCT' : ''}` }))} total={aggregations.length} />
    <NodeBadges><NodeBadge tone="strong">{groupByColumns.length} 个分组字段</NodeBadge><NodeBadge>{fieldCountText(result)}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const aggregateCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.Aggregate> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 352, maxHeight: 224, configured: Boolean(configuration.sourceTableName), listCount: configuration.aggregations.length }),
  Body: body,
};
