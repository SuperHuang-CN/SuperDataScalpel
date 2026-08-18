import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize, sortText } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.TopN>) => {
  const { sourceTableName, outputTableName, partitionByColumns, orderBy, limit, tieStrategy } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表并配置 Top N</NodeEmpty>;
  return <NodeContent variant="aggregate">
    <NodeFlow source={sourceTableName} operation="RANK" target={outputTableName} />
    <NodeHeroMetric value={`TOP ${limit || '?'}`} label={partitionByColumns.length > 0 ? `按 ${partitionByColumns.slice(0, 2).join(', ')} 分组` : '全局范围'} />
    <NodeBadges><NodeBadge tone="strong">{tieStrategy}</NodeBadge><NodeBadge>{sortText(orderBy[0])}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const topNCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.TopN> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 336, maxHeight: 200, configured: Boolean(configuration.sourceTableName) }),
  Body: body,
};
