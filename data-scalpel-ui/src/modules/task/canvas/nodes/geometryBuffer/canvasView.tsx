import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.GeometryBuffer>) => {
  const { sourceTableName, outputTableName, geometryColumnName, outputColumnName, distance, mode } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择 Geometry 字段并设置 Buffer</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={`${sourceTableName}.${geometryColumnName || '?'}`} operation="BUFFER" target={`${outputTableName || '?'}.${outputColumnName || '?'}`} />
    <NodeHeroMetric value={`${distance} ${mode === 'SPHEROID' ? 'm' : '坐标单位'}`} label="缓冲距离" />
    <NodeBadges><NodeBadge tone="spatial">◎ BUFFER</NodeBadge><NodeBadge tone="strong">{mode}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const geometryBufferCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.GeometryBuffer> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 320, maxHeight: 188, configured: Boolean(configuration.sourceTableName) }),
  Body: body,
};
