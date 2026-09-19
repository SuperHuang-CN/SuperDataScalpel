import { CanvasNodeType, type CanvasNodeRuntimeDataByType } from "../../canvasTypes";
import { spatialAreaUnitLabels, spatialDistanceUnitLabels } from "../spatialUnits";

export const summarizeSpatialMeasure = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialMeasure>,
) => {
  const { sourceTableName, outputTableName, measurements } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置空间测量';
  const kinds = [...new Set(measurements.map((measurement) => measurement.kind))].join('/');
  const modes = [...new Set(measurements.flatMap((measurement) => (
    'mode' in measurement ? [measurement.mode] : []
  )))].join('/');
  const units = [...new Set(measurements.flatMap((measurement) => {
    if (measurement.kind === 'AREA') {
      return [measurement.outputUnit
        ? spatialAreaUnitLabels[measurement.outputUnit]
        : measurement.mode === 'SPHEROID' ? '平方米' : '来源 CRS 单位²'];
    }
    if ('mode' in measurement) {
      const effectiveUnit = measurement.outputUnit
        ?? (measurement.mode === 'SPHEROID' ? 'METERS' : 'SOURCE_CRS_UNIT');
      return [spatialDistanceUnitLabels[effectiveUnit]];
    }
    return [];
  }))].join('/');
  return `${sourceTableName} → ${outputTableName} · ${measurements.length} 项${
    kinds ? ` · ${kinds}` : ''
  }${modes ? ` · ${modes}` : ''}${units ? ` · ${units}` : ''}`;
};
