import { parseMotionWindowOptions } from "./windowOptions";
import { parseStringArray, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { type TrackMotionMetric } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, parseTrackBoundaries, durationUnits, trackDistanceUnits, parseNullableFiniteNumber } from '../configurationParsing';

export const trackSpeedUnits = new Set([
  'METERS_PER_SECOND', 'KILOMETERS_PER_HOUR', 'FEET_PER_SECOND', 'MILES_PER_HOUR', 'KNOTS',
]);

export const trackAccelerationUnits = new Set([
  'METERS_PER_SECOND_SQUARED', 'FEET_PER_SECOND_SQUARED',
]);

export const parseTrackMotionMetrics = (
  value: unknown,
  path: string,
  errors: string[],
): TrackMotionMetric[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > 16) errors.push(`${path} 不能超过 16 项`);
  return value.slice(0, 16).flatMap((item, index): TrackMotionMetric[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const metricId = validateOptionalUuid(stringValue(item.metricId), `${itemPath}.metricId`, errors);
    const outputColumnName = stringValue(item.outputColumnName);
    const outputUnit = item.outputUnit == null ? null : stringValue(item.outputUnit);
    if (item.kind === 'DISTANCE' || item.kind === 'ELEVATION_CHANGE') {
      if (outputUnit === null || !trackDistanceUnits.has(outputUnit)) {
        errors.push(`${itemPath}.outputUnit 不是受支持的距离单位`);
      }
      return [{ kind: item.kind, metricId, outputColumnName,
        outputUnit: trackDistanceUnits.has(outputUnit ?? '')
          ? outputUnit as Extract<TrackMotionMetric, { kind: 'DISTANCE' }>['outputUnit']
          : 'SOURCE_CRS_UNIT' }];
    }
    if (item.kind === 'DURATION') {
      if (outputUnit === null || !durationUnits.has(outputUnit)) {
        errors.push(`${itemPath}.outputUnit 不是受支持的时长单位`);
      }
      return [{ kind: 'DURATION', metricId, outputColumnName,
        outputUnit: durationUnits.has(outputUnit ?? '')
          ? outputUnit as Extract<TrackMotionMetric, { kind: 'DURATION' }>['outputUnit'] : 'SECONDS' }];
    }
    if (item.kind === 'SPEED') {
      if (outputUnit === null || !trackSpeedUnits.has(outputUnit)) {
        errors.push(`${itemPath}.outputUnit 不是受支持的速度单位`);
      }
      return [{ kind: 'SPEED', metricId, outputColumnName,
        outputUnit: trackSpeedUnits.has(outputUnit ?? '')
          ? outputUnit as Extract<TrackMotionMetric, { kind: 'SPEED' }>['outputUnit'] : 'METERS_PER_SECOND' }];
    }
    if (item.kind === 'ACCELERATION') {
      if (outputUnit === null || !trackAccelerationUnits.has(outputUnit)) {
        errors.push(`${itemPath}.outputUnit 不是受支持的加速度单位`);
      }
      return [{ kind: 'ACCELERATION', metricId, outputColumnName,
        outputUnit: trackAccelerationUnits.has(outputUnit ?? '')
          ? outputUnit as Extract<TrackMotionMetric, { kind: 'ACCELERATION' }>['outputUnit']
          : 'METERS_PER_SECOND_SQUARED' }];
    }
    if (item.kind === 'BEARING') {
      if (outputUnit !== 'DEGREES') errors.push(`${itemPath}.outputUnit 必须是 DEGREES`);
      return [{ kind: 'BEARING', metricId, outputColumnName, outputUnit: 'DEGREES' }];
    }
    if (item.kind === 'SLOPE') {
      if (outputUnit !== 'PERCENT') errors.push(`${itemPath}.outputUnit 必须是 PERCENT`);
      return [{ kind: 'SLOPE', metricId, outputColumnName, outputUnit: 'PERCENT' }];
    }
    if (item.kind === 'IDLE') {
      if (item.outputUnit !== null) errors.push(`${itemPath}.outputUnit 必须是 null`);
      return [{ kind: 'IDLE', metricId, outputColumnName, outputUnit: null }];
    }
    errors.push(`${itemPath}.kind 不是受支持的运动指标`);
    return [];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'TRACK_MOTION_STATISTICS'>>(value, path, (configuration, errors) => {
      const distanceMethod = stringValue(configuration.distanceMethod);
      if (configuration.distanceMethod != null && !['PLANAR', 'GEODESIC'].includes(distanceMethod)) {
        errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
      }
      return {
        ...parseMotionWindowOptions(configuration, path, errors),
        sourceTableName: stringValue(configuration.sourceTableName),
        pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
        trackIdColumns: parseStringArray(configuration.trackIdColumns, `${path}.trackIdColumns`, errors),
        timeColumnName: stringValue(configuration.timeColumnName),
        distanceMethod: ['PLANAR', 'GEODESIC'].includes(distanceMethod)
          ? distanceMethod as Configuration<'TRACK_MOTION_STATISTICS'>['distanceMethod'] : null,
        boundaries: parseTrackBoundaries(configuration.boundaries, `${path}.boundaries`, errors),
        historyPoints: typeof configuration.historyPoints === 'number'
          && Number.isInteger(configuration.historyPoints) ? configuration.historyPoints : 0,
        idleDistanceThreshold: parseNullableFiniteNumber(
          configuration.idleDistanceThreshold, `${path}.idleDistanceThreshold`, errors,
        ),
        idleDistanceThresholdUnit: trackDistanceUnits.has(
          stringValue(configuration.idleDistanceThresholdUnit),
        ) ? stringValue(configuration.idleDistanceThresholdUnit) as Configuration<'TRACK_MOTION_STATISTICS'>['idleDistanceThresholdUnit'] : null,
        metrics: parseTrackMotionMetrics(configuration.metrics, `${path}.metrics`, errors),
        outputTableName: stringValue(configuration.outputTableName),
      };
    })
  );
