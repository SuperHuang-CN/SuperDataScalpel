import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeGeometryValidate = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryValidate>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    reasonColumnName,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName) {
    return '请选择 Geometry 字段并设置诊断字段';
  }
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName} · 合法性${reasonColumnName ? ' + 原因' : ''}`;
};
