import { spatialDistanceUnits, spatialAreaUnits } from "../spatialUnits";
import { stringValue } from "../../canvasValueParsers";
import { CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS, type SpatialAreaUnit, type SpatialDistanceUnit, type SpatialMeasurement } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const parseSpatialMeasurements = (
  value: unknown,
  path: string,
  errors: string[],
): SpatialMeasurement[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS) {
    errors.push(`${path} 不能超过 ${CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS} 项`);
  }
  return value.slice(0, CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS)
    .flatMap((item, index): SpatialMeasurement[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      const outputColumnName = stringValue(item.outputColumnName);
      if (item.kind === 'X' || item.kind === 'Y') {
        return [{
          kind: item.kind,
          geometryColumnName: stringValue(item.geometryColumnName),
          outputColumnName,
        }];
      }
      if (item.kind === 'AREA') {
        if (item.mode !== 'PLANAR' && item.mode !== 'SPHEROID') {
          errors.push(`${itemPath}.mode 仅支持 PLANAR 或 SPHEROID`);
        }
        const outputUnit = item.outputUnit == null ? null : stringValue(item.outputUnit);
        if (outputUnit !== null && !spatialAreaUnits.has(outputUnit)) {
          errors.push(`${itemPath}.outputUnit 不是受支持的面积单位`);
        }
        return [{
          kind: 'AREA',
          geometryColumnName: stringValue(item.geometryColumnName),
          mode: item.mode === 'SPHEROID' ? 'SPHEROID' : 'PLANAR',
          outputColumnName,
          outputUnit: outputUnit !== null && spatialAreaUnits.has(outputUnit)
            ? outputUnit as SpatialAreaUnit : null,
        }];
      }
      if (item.kind === 'LENGTH' || item.kind === 'PERIMETER') {
        if (item.mode !== 'PLANAR' && item.mode !== 'SPHEROID') {
          errors.push(`${itemPath}.mode 仅支持 PLANAR 或 SPHEROID`);
        }
        const outputUnit = item.outputUnit == null ? null : stringValue(item.outputUnit);
        if (outputUnit !== null && !spatialDistanceUnits.has(outputUnit)) {
          errors.push(`${itemPath}.outputUnit 不是受支持的距离单位`);
        }
        return [{
          kind: item.kind,
          geometryColumnName: stringValue(item.geometryColumnName),
          mode: item.mode === 'SPHEROID' ? 'SPHEROID' : 'PLANAR',
          outputColumnName,
          outputUnit: outputUnit !== null && spatialDistanceUnits.has(outputUnit)
            ? outputUnit as SpatialDistanceUnit : null,
        }];
      }
      if (item.kind === 'DISTANCE') {
        if (item.mode !== 'PLANAR' && item.mode !== 'SPHEROID') {
          errors.push(`${itemPath}.mode 仅支持 PLANAR 或 SPHEROID`);
        }
        const outputUnit = item.outputUnit == null ? null : stringValue(item.outputUnit);
        if (outputUnit !== null && !spatialDistanceUnits.has(outputUnit)) {
          errors.push(`${itemPath}.outputUnit 不是受支持的距离单位`);
        }
        return [{
          kind: 'DISTANCE',
          leftGeometryColumnName: stringValue(item.leftGeometryColumnName),
          rightGeometryColumnName: stringValue(item.rightGeometryColumnName),
          mode: item.mode === 'SPHEROID' ? 'SPHEROID' : 'PLANAR',
          outputColumnName,
          outputUnit: outputUnit !== null && spatialDistanceUnits.has(outputUnit)
            ? outputUnit as SpatialDistanceUnit : null,
        }];
      }
      errors.push(`${itemPath}.kind 不是受支持的空间测量类型`);
      return [];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_MEASURE'>>(
      value,
      path,
      (configuration, errors) => ({
        sourceTableName: stringValue(configuration.sourceTableName),
        outputTableName: stringValue(configuration.outputTableName),
        measurements: parseSpatialMeasurements(
          configuration.measurements,
          `${path}.measurements`,
          errors,
        ),
      }),
    )
  );
