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
import { spatialDistanceUnitLabels } from '../spatialUnits';
import { outputsNearestLines, usesExactNearest } from './matching';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialNearest>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.candidateTableName) {
    return <NodeEmpty>请选择来源和候选 Geometry</NodeEmpty>;
  }
  return (
    <NodeContent variant="spatial">
      <NodeFlow
        source={configuration.sourceTableName}
        operation="NEAREST"
        target={configuration.candidateTableName}
      />
      <NodeHeroMetric value={`TOP ${configuration.nearestCount}`} label="每个来源的最近候选" />
      <NodeFlow source="结果" operation="→" target={configuration.outputTableName || '待设置'} />
      <NodeBadges>
        <NodeBadge tone="spatial">
          {configuration.distanceMethod === 'GEODESIC' ? '测地线' : configuration.distanceMethod === 'PLANAR' ? '平面' : '待选距离方法'}
        </NodeBadge>
        <NodeBadge>{configuration.includeUnmatched ? '保留未命中' : '仅命中'}</NodeBadge>
        <NodeBadge>{configuration.outputColumns.filter((item) => item.included).length} 个字段</NodeBadge>
        <NodeBadge>{usesExactNearest(configuration) ? '真实距离' : '旧版 KNN'}</NodeBadge>
        {configuration.maximumDistance != null && <NodeBadge>
          {`范围 ≤ ${configuration.maximumDistance} ${configuration.maximumDistanceUnit == null
            ? '单位待选'
            : spatialDistanceUnitLabels[configuration.maximumDistanceUnit]}`}
        </NodeBadge>}
        {outputsNearestLines(configuration) && <NodeBadge>含连接线表</NodeBadge>}
      </NodeBadges>
    </NodeContent>
  );
};

export const spatialNearestCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.SpatialNearest
> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 368,
    maxHeight: 216,
    configured: Boolean(configuration.sourceTableName && configuration.candidateTableName),
  }),
  Body: body,
};
