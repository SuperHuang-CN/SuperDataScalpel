import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeGeometryRepair = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryRepair>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    outputColumnName,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName || !outputColumnName) {
    return '请选择需要修复的 Geometry 字段';
  }
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName}.${outputColumnName} · MAKE_VALID`;
};
