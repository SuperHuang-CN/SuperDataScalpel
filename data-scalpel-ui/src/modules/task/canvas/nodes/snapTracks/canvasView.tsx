import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge,
  NodeBadges,
  NodeContent,
  NodeEmpty,
  NodeFlow,
  NodePreviewList,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';
import { spatialDistanceUnitLabels } from '../spatialUnits';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SnapTracks>) => {
  const configuration = data.configuration;
  if (!configuration.pointTableName && !configuration.lineTableName) {
    return <NodeEmpty>请选择轨迹点表和道路网络</NodeEmpty>;
  }
  const unit = configuration.searchDistanceUnit
    ? spatialDistanceUnitLabels[configuration.searchDistanceUnit] : '待选单位';
  const previews = [{
    key: 'network',
    label: '路网',
    value: configuration.lineTableName || '待选择道路网络',
    meta: `${configuration.lineGeometryColumnName || '待选 LineString'} · ${configuration.lineIdColumnName || '待选线 ID'}`,
  }, {
    key: 'matching',
    label: '匹配',
    value: configuration.distanceMethod === 'GEODESIC' ? '测地线' : '平面',
    meta: `搜索范围单位 · ${unit}`,
  }];
  return <NodeContent variant="spatial">
    <NodeFlow source={configuration.pointTableName || '待选择轨迹点表'} operation="路网吸附"
      target={configuration.outputTableName || '待设置结果表'} />
    <NodePreviewList items={previews} total={previews.length} />
    <NodeBadges>
      <NodeBadge tone="spatial">{configuration.trackIdColumns.length} 个轨迹标识</NodeBadge>
      <NodeBadge>{configuration.directionMatching ? '方向匹配' : '双向路网'}</NodeBadge>
      {configuration.lineFields.length > 0
        && <NodeBadge>{configuration.lineFields.length} 个道路属性</NodeBadge>}
      <NodeBadge tone="info">{configuration.outputMode === 'MATCHED_FEATURES'
        ? '仅匹配观测' : '全部观测'}</NodeBadge>
    </NodeBadges>
  </NodeContent>;
};

export const snapTracksCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SnapTracks> = {
  resolveSize: configuration => resolvedNodeSize({
    width: 392,
    maxHeight: 232,
    configured: Boolean(configuration.pointTableName || configuration.lineTableName),
    listCount: 2,
  }),
  Body: body,
};
