import { CanvasNodeType } from '../../canvasTypes';
import {
  NodeBadge, NodeBadges, NodeContent, NodeEmpty, NodeFlow, NodePreviewList,
} from '../../components/nodeView/CanvasNodePrimitives';
import { resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';

const relationshipLabels = {
  INTERSECTS: '相交',
  TOUCHES: '接触',
  NEAR_PLANAR: '平面邻近',
  NEAR_GEODESIC: '测地邻近',
} as const;

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialGroupByProximity>) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName) {
    return <NodeEmpty>请选择要执行连通分组的空间表</NodeEmpty>;
  }
  const previews = [{
    key: 'spatial', label: '空间', value: configuration.spatialRelationship
      ? relationshipLabels[configuration.spatialRelationship] : '待设置',
    meta: configuration.spatialRelationship?.startsWith('NEAR')
      ? `${configuration.spatialNearDistance ?? '?'} ${configuration.spatialNearDistanceUnit ?? ''}`
      : configuration.geometryColumnName || '待选择 Geometry',
  }];
  if (configuration.temporalCondition) previews.push({
    key: 'temporal', label: '时间',
    value: configuration.temporalCondition.relationship === 'NEAR' ? '邻近' : '相交',
    meta: configuration.temporalCondition.relationship === 'NEAR'
      ? `${configuration.temporalCondition.nearDistance ?? '?'} ${configuration.temporalCondition.nearDistanceUnit ?? ''}`
      : configuration.temporalCondition.startColumnName || '待选择时间',
  });
  if (configuration.attributeConditions.length > 0) previews.push({
    key: 'attributes', label: '属性', value: `${configuration.attributeConditions.length} 项`,
    meta: '全部满足',
  });
  return <NodeContent variant="spatial">
    <NodeFlow
      source={configuration.sourceTableName}
      operation="连通分组"
      target={configuration.outputTableName || '待设置'}
    />
    <NodePreviewList items={previews} total={previews.length}
      moreLabel={count => configuration.attributeConditions.length > 0
        ? `属性 ${configuration.attributeConditions.length} 项`
        : `另 ${count} 类关系`} />
    <NodeBadges>
      <NodeBadge tone="spatial">传递闭包</NodeBadge>
      <NodeBadge>{configuration.groupIdColumnName || 'group_id'}</NodeBadge>
      <NodeBadge tone="info">原要素保留</NodeBadge>
    </NodeBadges>
  </NodeContent>;
};

export const spatialGroupByProximityCanvasView: CanvasNodeCanvasView<
  typeof CanvasNodeType.SpatialGroupByProximity
> = {
  resolveSize: configuration => resolvedNodeSize({
    width: 384,
    maxHeight: 224,
    configured: Boolean(configuration.sourceTableName),
    listCount: 1 + Number(Boolean(configuration.temporalCondition))
      + Number(configuration.attributeConditions.length > 0),
  }),
  Body: body,
};
