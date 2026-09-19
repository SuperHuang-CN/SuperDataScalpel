import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.GeometryExplode>) => {
  const { sourceTableName, outputTableName, geometryColumnName, outputColumnName, partIndexColumnName } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择需要拆分的 Geometry 字段</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={`${sourceTableName}.${geometryColumnName || '?'}`} operation="EXPLODE" target={`${outputTableName || '?'}.${outputColumnName || '?'}`} />
    <NodeHeroMetric value="◆ → ◇ ◇ ◇" label="多部件拆分为多行" />
    <NodeBadges><NodeBadge tone="spatial">EXPLODE</NodeBadge><NodeBadge>{partIndexColumnName ? `序号 ${partIndexColumnName}` : '不输出序号'}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const geometryExplodeCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.GeometryExplode> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 320, maxHeight: 188, configured: Boolean(configuration.sourceTableName) }),
  Body: body,
};
