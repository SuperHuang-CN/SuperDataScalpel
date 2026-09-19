import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize, sortText } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.TopN>) => {
  const operations = data.configuration.operations ?? [];
  if (operations.length === 0) return <NodeEmpty>请选择来源表并配置 Top N</NodeEmpty>;
  const operation = operations[0];
  return <NodeContent variant="aggregate">
    <NodeFlow source={operation.sourceTableName} operation="RANK" target={operation.output.outputTableName ?? operation.sourceTableName} />
    <NodeHeroMetric value={`TOP ${operation.limit || '?'}`} label={`${operations.length} 张表 · ${operation.partitionByColumns.length > 0 ? `按 ${operation.partitionByColumns.slice(0, 2).join(', ')} 分组` : '全局范围'}`} />
    <NodeBadges><NodeBadge tone="strong">{operation.tieStrategy}</NodeBadge><NodeBadge>{sortText(operation.orderBy[0])}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const topNCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.TopN> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 336, maxHeight: 200, configured: (configuration.operations ?? []).length > 0 }),
  Body: body,
};
