import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.GeometrySerialize>) => {
  const { sourceTableName, outputTableName, geometryColumnName, outputColumnName, format } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择 Geometry 字段和序列化格式</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={`${sourceTableName}.${geometryColumnName || '?'}`} operation={format} target={`${outputTableName || '?'}.${outputColumnName || '?'}`} />
    <NodeHeroMetric value={`GEOMETRY → ${format}`} label={format === 'WKB' ? '输出 BINARY' : '输出 STRING'} />
    <NodeBadges><NodeBadge tone="spatial">{format}</NodeBadge><NodeBadge tone="info">保留原 Geometry</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const geometrySerializeCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.GeometrySerialize> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 320, maxHeight: 188, configured: Boolean(configuration.sourceTableName) }),
  Body: body,
};
