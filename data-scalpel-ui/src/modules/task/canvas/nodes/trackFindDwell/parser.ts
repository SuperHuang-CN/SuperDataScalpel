import { parseDwellOptions } from "./rangeOptions";
import { parseStringArray, stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, trackDistanceUnits, durationUnits, parseFiniteNumber, parseTrackBoundaries, parseTrackSummaries } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'TRACK_FIND_DWELL'>>(value, path, (configuration, errors) => {
      const distanceMethod = stringValue(configuration.distanceMethod);
      const distanceUnit = stringValue(configuration.distanceThresholdUnit);
      const durationUnit = stringValue(configuration.minimumDurationUnit);
      const geometryKind = stringValue(configuration.outputGeometryKind);
      if (configuration.distanceMethod != null && !['PLANAR', 'GEODESIC'].includes(distanceMethod)) errors.push(`${path}.distanceMethod 无效`);
      if (!trackDistanceUnits.has(distanceUnit)) errors.push(`${path}.distanceThresholdUnit 无效`);
      if (!durationUnits.has(durationUnit)) errors.push(`${path}.minimumDurationUnit 无效`);
      if (configuration.outputGeometryKind != null && !['CENTROID', 'CONVEX_HULL'].includes(geometryKind)) errors.push(`${path}.outputGeometryKind 无效`);
      return {
        ...parseDwellOptions(configuration, path, errors),
        sourceTableName: stringValue(configuration.sourceTableName),
        pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
        trackIdColumns: parseStringArray(configuration.trackIdColumns, `${path}.trackIdColumns`, errors),
        timeColumnName: stringValue(configuration.timeColumnName),
        distanceMethod: ['PLANAR', 'GEODESIC'].includes(distanceMethod)
          ? distanceMethod as Configuration<'TRACK_FIND_DWELL'>['distanceMethod'] : null,
        distanceThreshold: parseFiniteNumber(configuration.distanceThreshold, `${path}.distanceThreshold`, errors, 0),
        distanceThresholdUnit: trackDistanceUnits.has(distanceUnit)
          ? distanceUnit as Configuration<'TRACK_FIND_DWELL'>['distanceThresholdUnit'] : 'METERS',
        minimumDuration: parseFiniteNumber(configuration.minimumDuration, `${path}.minimumDuration`, errors, 0),
        minimumDurationUnit: durationUnits.has(durationUnit)
          ? durationUnit as Configuration<'TRACK_FIND_DWELL'>['minimumDurationUnit'] : 'MINUTES',
        boundaries: parseTrackBoundaries(configuration.boundaries, `${path}.boundaries`, errors),
        summaryStatistics: parseTrackSummaries(
          configuration.summaryStatistics, `${path}.summaryStatistics`, errors,
        ),
        outputGeometryKind: ['CENTROID', 'CONVEX_HULL'].includes(geometryKind)
          ? geometryKind as Configuration<'TRACK_FIND_DWELL'>['outputGeometryKind'] : null,
        outputTableName: stringValue(configuration.outputTableName),
        dwellIdColumnName: stringValue(configuration.dwellIdColumnName),
        startTimeColumnName: stringValue(configuration.startTimeColumnName),
        endTimeColumnName: stringValue(configuration.endTimeColumnName),
        durationColumnName: stringValue(configuration.durationColumnName),
        pointCountColumnName: stringValue(configuration.pointCountColumnName),
        outputGeometryColumnName: stringValue(configuration.outputGeometryColumnName),
      };
    })
  );
