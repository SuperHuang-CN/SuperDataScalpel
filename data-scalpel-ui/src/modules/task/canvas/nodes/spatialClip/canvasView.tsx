import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeDualFlow, NodeEmpty, NodeSplit } from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialClip>) => {
  const { sourceTableName, maskTableName, outputTableName, sourceGeometryColumnName, maskGeometryColumnName, outputColumnName } = data.configuration;
  if (!sourceTableName && !maskTableName) return <NodeEmpty>请选择来源表和 Mask 表</NodeEmpty>;
  return <NodeContent variant="dual">
    <NodeDualFlow left={sourceTableName} right={maskTableName} leftLabel="SOURCE" rightLabel="MASK" operation="CLIP ∩" target={outputTableName} />
    <NodeSplit leftLabel="来源 Geometry" left={sourceGeometryColumnName || '待选择'} rightLabel="Mask Geometry" right={maskGeometryColumnName || '待选择'} />
    <NodeBadges><NodeBadge tone="spatial">INTERSECTION</NodeBadge><NodeBadge>结果字段 {outputColumnName || '待设置'}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const spatialClipCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialClip> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 368, maxHeight: 216, emptyHeight: 116, configured: Boolean(configuration.sourceTableName || configuration.maskTableName) }),
  Body: body,
};
