import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.GeometryRepair>) => {
  const { sourceTableName, outputTableName, geometryColumnName, outputColumnName } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择需要修复的 Geometry 字段</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={`${sourceTableName}.${geometryColumnName || '?'}`} operation="MAKE VALID" target={`${outputTableName || '?'}.${outputColumnName || '?'}`} />
    <NodeHeroMetric value="破损 ◇ → 完整 ◆" label="拓扑修复" />
    <NodeBadges><NodeBadge tone="spatial">MAKE_VALID</NodeBadge><NodeBadge tone="info">保留原字段</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const geometryRepairCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.GeometryRepair> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 320, maxHeight: 180, configured: Boolean(configuration.sourceTableName) }),
  Body: body,
};
