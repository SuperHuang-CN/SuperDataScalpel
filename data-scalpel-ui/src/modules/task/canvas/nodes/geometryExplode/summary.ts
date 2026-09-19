import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeGeometryExplode = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryExplode>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    outputColumnName,
    partIndexColumnName,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName || !outputColumnName) {
    return '请选择需要拆分的 Geometry 字段';
  }
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName}.${outputColumnName}${
    partIndexColumnName ? ` · 序号 ${partIndexColumnName}` : ''
  }`;
};
