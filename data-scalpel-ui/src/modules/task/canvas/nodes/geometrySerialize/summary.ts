import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeGeometrySerialize = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometrySerialize>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    outputColumnName,
    format,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName || !outputColumnName) {
    return '请选择 Geometry 字段和序列化格式';
  }
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName}.${outputColumnName} · ${format}`;
};
