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
import { boundaryLabels, usesMethodPath, usesOrderedReconstruction, usesSplitExpression } from './reconstruction';
import { areaBufferLabel, usesAreaGeometry } from './areaGeometry';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.TrackReconstruct>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) return <NodeEmpty>请选择轨迹事件表</NodeEmpty>;
  const boundaryCount = [
    configuration.boundaries.maximumTimeGap,
    configuration.boundaries.maximumDistanceGap,
    configuration.boundaries.fixedTimeBoundary,
  ].filter((value) => value != null).length;
  return (
    <NodeContent variant="spatial">
      <NodeFlow source={configuration.sourceTableName} operation="按时间重建"
        target={configuration.outputTableName || '待设置'} />
      <NodeHeroMetric value={configuration.trackIdColumns.length} label="轨迹标识字段" />
      <NodeBadges>
        <NodeBadge tone="spatial">{usesAreaGeometry(configuration.reconstruction) ? '观测 → MultiPolygon'
          : `Point → ${usesMethodPath(configuration.reconstruction) ? 'MultiLineString' : 'LineString'}`}</NodeBadge>
        {usesAreaGeometry(configuration.reconstruction) && <NodeBadge>{areaBufferLabel(configuration.reconstruction?.areaGeometry?.bufferMode)}</NodeBadge>}
        {usesAreaGeometry(configuration.reconstruction) && configuration.distanceMethod === 'GEODESIC' && <NodeBadge>WGS84 测地面</NodeBadge>}
        {usesAreaGeometry(configuration.reconstruction) && configuration.reconstruction?.areaGeometry?.bufferMode === 'EXPRESSION'
          && (configuration.reconstruction.areaGeometry.windowBindings?.length ?? 0)>0
          && <NodeBadge>{configuration.reconstruction.areaGeometry.windowBindings!.length} 个缓冲窗口</NodeBadge>}
        <NodeBadge>{boundaryCount === 0 ? '不拆分' : `${boundaryCount} 项边界`}</NodeBadge>
        <NodeBadge>{configuration.summaryStatistics.length} 项汇总</NodeBadge>
        {usesOrderedReconstruction(configuration.reconstruction) && <NodeBadge>
          {boundaryLabels[configuration.reconstruction?.splitBoundaryOption ?? 'GAP']}
          {usesSplitExpression(configuration.reconstruction) ? ' · 表达式拆分' : ''}
        </NodeBadge>}
      </NodeBadges>
    </NodeContent>
  );
};

export const trackReconstructCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.TrackReconstruct> = {
  resolveSize: (configuration) => resolvedNodeSize({
    width: 352,
    maxHeight: 216,
    configured: Boolean(configuration.sourceTableName),
  }),
  Body: body,
};
