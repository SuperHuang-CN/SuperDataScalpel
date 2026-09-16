import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";

export const summarizeGeometryConstruct = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryConstruct>,
) => {
  const {
    sourceTableName,
    outputTableName,
    outputColumnName,
    source,
    targetGeometry,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !outputColumnName || !targetGeometry) {
    return '请选择来源字段和目标 Geometry 类型';
  }
  return `${sourceTableName} · ${source.kind} → ${outputTableName}.${outputColumnName} · ${targetGeometry.kind} EPSG:${targetGeometry.crs.code}`;
};
