import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeSpatialTransform = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialTransform>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    targetCrs,
  } = data.configuration;
  return !sourceTableName || !outputTableName || !geometryColumnName || !targetCrs
    ? '请选择 Geometry 字段和目标 CRS'
    : `${sourceTableName}.${geometryColumnName} → ${outputTableName} · EPSG:${targetCrs.code}`;
};
