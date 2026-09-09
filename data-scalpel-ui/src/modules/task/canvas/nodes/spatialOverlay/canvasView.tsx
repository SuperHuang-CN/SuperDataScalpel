import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge,
  NodeBadges,
  NodeContent,
  NodeEmpty,
  NodeFlow,
  NodeHeroMetric,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';
import { overlayOperationLabels, usesOverlayFamily } from './geometryPolicy';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialOverlay>) => {
  const configuration = data.configuration;
  if (!configuration.leftTableName || !configuration.rightTableName) {
    return <NodeEmpty>请选择左右图层</NodeEmpty>;
  }
  const enabled = configuration.outputColumns.filter((item) => item.included).length;
  return (
    <NodeContent variant="spatial">
      <NodeFlow
        source={configuration.leftTableName}
        operation={configuration.operation ? overlayOperationLabels[configuration.operation] : '待选择'}
        target={configuration.rightTableName}
      />
      <NodeHeroMetric value={enabled} label="属性字段" />
      <NodeFlow source="叠加结果" operation="→" target={configuration.outputTableName || '待设置'} />
      <NodeBadges>
        <NodeBadge tone="spatial">{usesOverlayFamily(configuration) ? '家族多部件 · XY' : 'Geometry · 旧版'}</NodeBadge>
        <NodeBadge>{configuration.outputGeometryColumnName || '待设置结果字段'}</NodeBadge>
      </NodeBadges>
    </NodeContent>
  );
};

export const spatialOverlayCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialOverlay> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 376,
    maxHeight: 216,
    configured: Boolean(configuration.leftTableName && configuration.rightTableName),
  }),
  Body: body,
};
