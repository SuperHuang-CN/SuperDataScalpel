import { CanvasNodeType } from '../../canvasTypes';
import { NodeBadge, NodeBadges, NodeContent, NodeDualFlow, NodeEmpty, NodePreviewList } from '../../components/nodeView/CanvasNodePrimitives';
import { fieldCountText, outputTable, resolvedNodeSize } from '../canvasNodePresentation';
import type { CanvasNodeBodyProps, CanvasNodeCanvasView } from '../nodeSpec';
import { spatialDistanceUnitLabels } from '../spatialUnits';

const body = ({ data }: CanvasNodeBodyProps<typeof CanvasNodeType.SpatialJoin>) => {
  const {
    leftTableName,
    rightTableName,
    outputTableName,
    joinType,
    conditions,
    attributeConditions,
    outputColumns,
    joinOperation,
    oneToOne,
    temporalCondition,
    spatialNear,
    distanceOutput,
  } = data.configuration;
  if (!leftTableName && !rightTableName) return <NodeEmpty>请选择两张空间表</NodeEmpty>;
  const result = outputTable(data, outputTableName);
  const oneToOneEnabled = joinOperation === 'JOIN_ONE_TO_ONE';
  const grain = oneToOneEnabled
    ? oneToOne?.mode === 'SUMMARIZE_MATCHES'
      ? `1:1 · Count+${oneToOne.summaryStatistics?.length ?? 0}`
      : `1:1 · ${oneToOne?.keepRule?.strategy ?? '待配置'}`
    : '1:N';
  const outputFieldCount = result
    ? fieldCountText(result)
    : outputColumns == null
      ? '旧版字段'
      : `${outputColumns.filter((column) => column.included).length} 字段`;
  const previews = [
    ...conditions.map((item, index) => ({ key: `topology-${index}`, label: item.leftGeometryColumnName || '左 Geometry', value: item.predicate ?? '?', meta: item.rightGeometryColumnName || '右 Geometry' })),
    ...(spatialNear ? [{
      key: 'near',
      label: '空间距离',
      value: spatialNear.distanceMethod === 'GEODESIC' ? 'NEAR GEODESIC' : 'NEAR',
      meta: spatialNear.distanceUnit
        ? spatialDistanceUnitLabels[spatialNear.distanceUnit]
        : '单位待配置',
    }] : []),
  ];
  return <NodeContent variant="dual">
    <NodeDualFlow left={leftTableName} right={rightTableName} leftLabel="TARGET" rightLabel="JOIN" operation="SPATIAL JOIN" target={outputTableName} />
    <NodePreviewList items={previews.slice(0, 2)} total={previews.length} />
    <NodeBadges><NodeBadge tone="spatial">{joinType}</NodeBadge><NodeBadge>{grain}</NodeBadge><NodeBadge>{conditions.length} 拓扑 · {attributeConditions?.length ?? 0} 属性</NodeBadge>{spatialNear && <NodeBadge tone="spatial">{spatialNear.distanceMethod === 'GEODESIC' ? 'Near Geodesic' : 'Near'}</NodeBadge>}{temporalCondition && <NodeBadge>时间 {temporalCondition.relationship ?? '待配置'}</NodeBadge>}{distanceOutput?.enabled && <NodeBadge>输出距离</NodeBadge>}<NodeBadge>{outputFieldCount}</NodeBadge></NodeBadges>
  </NodeContent>;
};

export const spatialJoinCanvasView: CanvasNodeCanvasView<typeof CanvasNodeType.SpatialJoin> = {
  resolveSize: (configuration) => resolvedNodeSize({ width: 368, maxHeight: 224, emptyHeight: 116, configured: Boolean(configuration.leftTableName || configuration.rightTableName), listCount: configuration.conditions.length + (configuration.spatialNear ? 1 : 0) }),
  Body: body,
};
