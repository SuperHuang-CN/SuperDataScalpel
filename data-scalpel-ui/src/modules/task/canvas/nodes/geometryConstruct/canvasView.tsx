import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeSplit } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.GeometryConstruct>) => {
  const { sourceTableName, outputTableName, outputColumnName, source, targetGeometry } = data.configuration;
  if (!sourceTableName) return <NodeEmpty>请选择来源表和 Geometry 来源字段</NodeEmpty>;
  const sourceFields = source.kind === 'POINT_FROM_XY' ? `${source.xColumnName || 'X'} + ${source.yColumnName || 'Y'}` : source.columnName || '待选择字段';
  return <NodeContent variant="spatial">
    <NodeFlow source={`${sourceTableName}.${sourceFields}`} operation={source.kind} target={`${outputTableName || '?'}.${outputColumnName || '?'}`} />
    <NodeSplit leftLabel="来源" left={sourceFields} rightLabel="目标 Geometry" right={targetGeometry ? `${targetGeometry.kind} · ${targetGeometry.dimension}` : '待设置'} />
    <NodeBadges><NodeBadge tone="spatial">{source.kind}</NodeBadge><NodeBadge tone="strong">{targetGeometry ? `${targetGeometry.crs.authority}:${targetGeometry.crs.code}` : '待设置 CRS'}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const geometryConstructCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.GeometryConstruct> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 332, maxHeight: 196, configured: Boolean(configuration.sourceTableName) }),
  Body: body,
};
