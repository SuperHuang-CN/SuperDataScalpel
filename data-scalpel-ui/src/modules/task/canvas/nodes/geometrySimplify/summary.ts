import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeGeometrySimplify = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometrySimplify>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    outputColumnName,
    algorithm,
    toleranceUnit,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName || !outputColumnName) {
    return '请选择 Geometry 字段并配置简化规则';
  }
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName}.${outputColumnName}`
    + ` · ${algorithm ?? '待选算法'} · ${toleranceUnit}`;
};
