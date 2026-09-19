import { spatialDistanceUnits } from "../spatialUnits";
import { parseStringArray, stringValue } from "../../canvasValueParsers";
import { CANVAS_TRACE_PROXIMITY_MAX_INTERESTS, type SpatialGroupByProximityTemporalUnit, type TraceProximityEntityOfInterest, type TraceProximityInterestSource, type SpatialDistanceUnit } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, groupByProximityTemporalUnits } from '../configurationParsing';

export const traceProximityInterestSources = new Set(['ENTITY_IDS', 'TABLE']);

export const parseTraceProximityInterests = (
  value: unknown,
  path: string,
  errors: string[],
): TraceProximityEntityOfInterest[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_TRACE_PROXIMITY_MAX_INTERESTS) {
    errors.push(`${path} 不能超过 ${CANVAS_TRACE_PROXIMITY_MAX_INTERESTS} 项`);
  }
  return value.flatMap((item, index): TraceProximityEntityOfInterest[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    if (typeof item.entityId !== 'string') {
      errors.push(`${itemPath}.entityId 必须是字符串`);
    }
    if (item.startEpochMillis != null
      && (typeof item.startEpochMillis !== 'number'
        || !Number.isSafeInteger(item.startEpochMillis))) {
      errors.push(`${itemPath}.startEpochMillis 必须是安全整数或 null`);
    }
    return [{
      entityId: stringValue(item.entityId),
      startEpochMillis: typeof item.startEpochMillis === 'number'
        && Number.isSafeInteger(item.startEpochMillis) ? item.startEpochMillis : null,
    }];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'TRACE_PROXIMITY_EVENTS'>>(
      value,
      path,
      (configuration, errors) => {
        const distanceMethod = configuration.distanceMethod == null
          ? null : stringValue(configuration.distanceMethod);
        const spatialUnit = configuration.spatialSearchDistanceUnit == null
          ? null : stringValue(configuration.spatialSearchDistanceUnit);
        const temporalUnit = configuration.temporalSearchDistanceUnit == null
          ? null : stringValue(configuration.temporalSearchDistanceUnit);
        const interestSource = configuration.interestSource == null
          ? null : stringValue(configuration.interestSource);
        if (distanceMethod !== null
          && distanceMethod !== 'PLANAR' && distanceMethod !== 'GEODESIC') {
          errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
        }
        if (spatialUnit !== null && !spatialDistanceUnits.has(spatialUnit)) {
          errors.push(`${path}.spatialSearchDistanceUnit 不是受支持的距离单位`);
        }
        if (temporalUnit !== null && !groupByProximityTemporalUnits.has(temporalUnit)) {
          errors.push(`${path}.temporalSearchDistanceUnit 不是受支持的时间单位`);
        }
        if (interestSource !== null && !traceProximityInterestSources.has(interestSource)) {
          errors.push(`${path}.interestSource 不是受支持的起始实体来源`);
        }
        if (configuration.spatialSearchDistance != null
          && (typeof configuration.spatialSearchDistance !== 'number'
            || !Number.isFinite(configuration.spatialSearchDistance))) {
          errors.push(`${path}.spatialSearchDistance 必须是有限数值或 null`);
        }
        for (const [key, field] of [
          ['temporalSearchDistance', configuration.temporalSearchDistance],
          ['maxTraceDepth', configuration.maxTraceDepth],
        ] as const) {
          if (field != null && (typeof field !== 'number' || !Number.isSafeInteger(field))) {
            errors.push(`${path}.${key} 必须是安全整数或 null`);
          }
        }
        if (configuration.interestStartTimeColumnName != null
          && typeof configuration.interestStartTimeColumnName !== 'string') {
          errors.push(`${path}.interestStartTimeColumnName 必须是字符串或 null`);
        }
        if (typeof configuration.includeTracks !== 'boolean') {
          errors.push(`${path}.includeTracks 必须是布尔值`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
          entityIdColumnName: stringValue(configuration.entityIdColumnName),
          timeColumnName: stringValue(configuration.timeColumnName),
          distanceMethod: distanceMethod === 'PLANAR' || distanceMethod === 'GEODESIC'
            ? distanceMethod : null,
          spatialSearchDistance: typeof configuration.spatialSearchDistance === 'number'
            && Number.isFinite(configuration.spatialSearchDistance)
            ? configuration.spatialSearchDistance : null,
          spatialSearchDistanceUnit: spatialUnit !== null && spatialDistanceUnits.has(spatialUnit)
            ? spatialUnit as SpatialDistanceUnit : null,
          temporalSearchDistance: typeof configuration.temporalSearchDistance === 'number'
            && Number.isSafeInteger(configuration.temporalSearchDistance)
            ? configuration.temporalSearchDistance : null,
          temporalSearchDistanceUnit: temporalUnit !== null
            && groupByProximityTemporalUnits.has(temporalUnit)
            ? temporalUnit as SpatialGroupByProximityTemporalUnit : null,
          interestSource: interestSource !== null && traceProximityInterestSources.has(interestSource)
            ? interestSource as TraceProximityInterestSource : null,
          entitiesOfInterest: parseTraceProximityInterests(
            configuration.entitiesOfInterest, `${path}.entitiesOfInterest`, errors,
          ),
          entitiesOfInterestTableName: stringValue(configuration.entitiesOfInterestTableName),
          interestEntityIdColumnName: stringValue(configuration.interestEntityIdColumnName),
          interestStartTimeColumnName: typeof configuration.interestStartTimeColumnName === 'string'
            ? configuration.interestStartTimeColumnName : null,
          maxTraceDepth: typeof configuration.maxTraceDepth === 'number'
            && Number.isSafeInteger(configuration.maxTraceDepth)
            ? configuration.maxTraceDepth : null,
          attributeMatchColumns: parseStringArray(
            configuration.attributeMatchColumns, `${path}.attributeMatchColumns`, errors,
          ),
          includeTracks: configuration.includeTracks === true,
          outputTableName: stringValue(configuration.outputTableName),
          tracksOutputTableName: stringValue(configuration.tracksOutputTableName),
          fromEntityIdColumnName: stringValue(configuration.fromEntityIdColumnName),
          toEntityIdColumnName: stringValue(configuration.toEntityIdColumnName),
          depthColumnName: stringValue(configuration.depthColumnName),
          durationMinutesColumnName: stringValue(configuration.durationMinutesColumnName),
          eventTimeColumnName: stringValue(configuration.eventTimeColumnName),
        };
      },
    )
  );
