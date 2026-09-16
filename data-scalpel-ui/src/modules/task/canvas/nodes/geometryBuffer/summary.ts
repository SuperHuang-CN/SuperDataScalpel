import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { spatialDistanceUnitLabels } from "../spatialUnits";

export const summarizeGeometryBuffer = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryBuffer>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    outputColumnName,
    distance,
    mode,
    distanceUnit,
    distanceSource,
    distanceFieldName,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName || !outputColumnName) {
    return '请选择 Geometry 字段并配置 Buffer';
  }
  const effectiveUnit = distanceUnit ?? (mode === 'SPHEROID' ? 'METERS' : 'SOURCE_CRS_UNIT');
  const distanceSummary = (distanceSource ?? 'CONSTANT') === 'CONSTANT'
    ? `${distance} ${spatialDistanceUnitLabels[effectiveUnit]}`
    : distanceSource === 'FIELD'
      ? `字段 ${distanceFieldName || '?'} · ${spatialDistanceUnitLabels[effectiveUnit]}`
      : `逐行表达式 · ${spatialDistanceUnitLabels[effectiveUnit]}`;
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName}.${outputColumnName} · ${distanceSummary} · ${mode}`;
};
