import { parseReconstruction } from "./reconstruction";
import { parseStringArray, stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseTrackBoundaries, parseTrackSummaries } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'TRACK_RECONSTRUCT'>>(value, path, (configuration, errors) => {
      const distanceMethod = stringValue(configuration.distanceMethod);
      if (!['PLANAR', 'GEODESIC'].includes(distanceMethod)) {
        errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
      }
      return {
        ...parseReconstruction(configuration, path, errors),
        sourceTableName: stringValue(configuration.sourceTableName),
        pointGeometryColumnName: stringValue(configuration.pointGeometryColumnName),
        trackIdColumns: parseStringArray(configuration.trackIdColumns, `${path}.trackIdColumns`, errors),
        timeColumnName: stringValue(configuration.timeColumnName),
        distanceMethod: ['PLANAR', 'GEODESIC'].includes(distanceMethod)
          ? distanceMethod as Configuration<'TRACK_RECONSTRUCT'>['distanceMethod'] : null,
        boundaries: parseTrackBoundaries(configuration.boundaries, `${path}.boundaries`, errors),
        summaryStatistics: parseTrackSummaries(
          configuration.summaryStatistics, `${path}.summaryStatistics`, errors,
        ),
        outputTableName: stringValue(configuration.outputTableName),
        outputGeometryColumnName: stringValue(configuration.outputGeometryColumnName),
        startTimeColumnName: stringValue(configuration.startTimeColumnName),
        endTimeColumnName: stringValue(configuration.endTimeColumnName),
        pointCountColumnName: stringValue(configuration.pointCountColumnName),
      };
    })
  );
