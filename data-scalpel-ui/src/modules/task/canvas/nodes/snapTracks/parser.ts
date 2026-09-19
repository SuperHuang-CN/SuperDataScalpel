import { spatialDistanceUnits } from "../spatialUnits";
import { parseStringArray, stringValue } from "../../canvasValueParsers";
import { CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS, type SnapTracksDirectionMatching, type SnapTracksLineField, type SpatialDistanceUnit } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, parseNullableFiniteNumber, parseTrackBoundaries } from '../configurationParsing';

export const parseSnapTracksDirection = (
  value: unknown,
  path: string,
  errors: string[],
): SnapTracksDirectionMatching | null => {
  if (value == null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return null;
  }
  const fields = [
    'directionColumnName', 'forwardValue', 'backwardValue', 'bothValue', 'noneValue',
  ] as const;
  fields.forEach((field) => {
    if (typeof value[field] !== 'string') errors.push(`${path}.${field} 必须是字符串`);
  });
  return {
    directionColumnName: stringValue(value.directionColumnName),
    forwardValue: stringValue(value.forwardValue),
    backwardValue: stringValue(value.backwardValue),
    bothValue: stringValue(value.bothValue),
    noneValue: stringValue(value.noneValue),
  };
};

export const parseSnapTracksLineFields = (
  value: unknown,
  path: string,
  errors: string[],
): SnapTracksLineField[] => {
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS) {
    errors.push(`${path} 不能超过 ${CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS} 项`);
  }
  return value.slice(0, CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS)
    .flatMap((item, index): SnapTracksLineField[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      if (typeof item.sourceColumnName !== 'string') {
        errors.push(`${itemPath}.sourceColumnName 必须是字符串`);
      }
      if (typeof item.outputColumnName !== 'string') {
        errors.push(`${itemPath}.outputColumnName 必须是字符串`);
      }
      return [{
        sourceColumnName: stringValue(item.sourceColumnName),
        outputColumnName: stringValue(item.outputColumnName),
      }];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SNAP_TRACKS'>>(value, path, (configuration, errors) => {
      const distanceMethod = configuration.distanceMethod == null
        ? null : stringValue(configuration.distanceMethod);
      const distanceUnit = configuration.searchDistanceUnit == null
        ? null : stringValue(configuration.searchDistanceUnit);
      const outputMode = configuration.outputMode == null
        ? null : stringValue(configuration.outputMode);
      if (distanceMethod !== null
        && distanceMethod !== 'PLANAR' && distanceMethod !== 'GEODESIC') {
        errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
      }
      if (distanceUnit !== null && !spatialDistanceUnits.has(distanceUnit)) {
        errors.push(`${path}.searchDistanceUnit 不是受支持的距离单位`);
      }
      if (outputMode !== null
        && outputMode !== 'ALL_FEATURES' && outputMode !== 'MATCHED_FEATURES') {
        errors.push(`${path}.outputMode 仅支持 ALL_FEATURES 或 MATCHED_FEATURES`);
      }
      return {
        pointTableName: stringValue(configuration.pointTableName),
        pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
        trackIdColumns: parseStringArray(
          configuration.trackIdColumns, `${path}.trackIdColumns`, errors,
        ),
        timeColumnName: stringValue(configuration.timeColumnName),
        orderByColumns: parseStringArray(
          configuration.orderByColumns, `${path}.orderByColumns`, errors,
        ),
        lineTableName: stringValue(configuration.lineTableName),
        lineGeometryColumnName: stringValue(configuration.lineGeometryColumnName),
        lineIdColumnName: stringValue(configuration.lineIdColumnName),
        fromNodeColumnName: stringValue(configuration.fromNodeColumnName),
        toNodeColumnName: stringValue(configuration.toNodeColumnName),
        searchDistance: parseNullableFiniteNumber(
          configuration.searchDistance, `${path}.searchDistance`, errors,
        ),
        searchDistanceUnit: distanceUnit !== null && spatialDistanceUnits.has(distanceUnit)
          ? distanceUnit as SpatialDistanceUnit : null,
        distanceMethod: distanceMethod === 'PLANAR' || distanceMethod === 'GEODESIC'
          ? distanceMethod : null,
        boundaries: parseTrackBoundaries(configuration.boundaries, `${path}.boundaries`, errors),
        directionMatching: parseSnapTracksDirection(
          configuration.directionMatching, `${path}.directionMatching`, errors,
        ),
        lineFields: parseSnapTracksLineFields(
          configuration.lineFields, `${path}.lineFields`, errors,
        ),
        outputMode: outputMode === 'ALL_FEATURES' || outputMode === 'MATCHED_FEATURES'
          ? outputMode : null,
        outputTableName: stringValue(configuration.outputTableName),
        snappedGeometryColumnName: stringValue(configuration.snappedGeometryColumnName),
        matchedLineIdColumnName: stringValue(configuration.matchedLineIdColumnName),
        matchStatusColumnName: stringValue(configuration.matchStatusColumnName),
        originalXColumnName: stringValue(configuration.originalXColumnName),
        originalYColumnName: stringValue(configuration.originalYColumnName),
        matchXColumnName: stringValue(configuration.matchXColumnName),
        matchYColumnName: stringValue(configuration.matchYColumnName),
        matchDistanceColumnName: stringValue(configuration.matchDistanceColumnName),
      };
    })
  );
