import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodeHeroMetric,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialDensity>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) return <NodeEmpty>请选择投影 Point 表</NodeEmpty>;
  return <NodeContent variant="spatial">
    <NodeFlow source={configuration.sourceTableName}
      operation={configuration.weighting === 'KERNEL' ? 'Kernel 密度' : 'Uniform 密度'}
      target={configuration.outputTableName || '待设置'} />
    <NodeHeroMetric value={configuration.fields.length + 1} label="密度字段" />
    <NodeBadges>
      <NodeBadge tone="spatial">{configuration.binShape === 'HEXAGON' ? '六边形' : '方格'} {configuration.binSize} {configuration.binSizeUnit}</NodeBadge>
      <NodeBadge>半径 {configuration.radius} {configuration.radiusUnit}</NodeBadge>
      <NodeBadge>{configuration.areaUnit}</NodeBadge>
      {configuration.temporalSlicing && <NodeBadge>时间切片</NodeBadge>}
    </NodeBadges>
  </NodeContent>;
};

export const spatialDensityCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialDensity> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 368,
    maxHeight: 224,
    configured: Boolean(configuration.sourceTableName),
    listCount: configuration.fields.length,
  }),
  Body: body,
};
