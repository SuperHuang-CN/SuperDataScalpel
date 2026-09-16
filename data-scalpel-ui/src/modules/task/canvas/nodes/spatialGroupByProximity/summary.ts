import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialGroupByProximity = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialGroupByProximity>,
) => {
  const configuration = data.configuration;
  if (!configuration.sourceTableName || !configuration.geometryColumnName
    || !configuration.outputTableName) {
    return '请选择空间表、Geometry 和输出表';
  }
  const relation = configuration.spatialRelationship === 'TOUCHES' ? '接触'
    : configuration.spatialRelationship === 'NEAR_PLANAR' ? '平面邻近'
      : configuration.spatialRelationship === 'NEAR_GEODESIC' ? '测地邻近' : '相交';
  return `${configuration.sourceTableName} → ${configuration.outputTableName}`
    + ` · ${relation}连通组`
    + `${configuration.temporalCondition ? ' · 时间约束' : ''}`
    + `${configuration.attributeConditions.length > 0
      ? ` · ${configuration.attributeConditions.length} 个属性约束` : ''}`;
};
