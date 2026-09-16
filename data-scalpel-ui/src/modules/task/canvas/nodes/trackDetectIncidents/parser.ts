import { parseIncidentLifecycleOptions } from "./readConfiguration";
import { parseFilterCondition, parseStringArray, stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, durationUnits, parseTrackBoundaries } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'TRACK_DETECT_INCIDENTS'>>(value, path, (configuration, errors) => {
      const distanceMethod = stringValue(configuration.distanceMethod);
      const resultMode = stringValue(configuration.resultMode);
      const durationUnit = stringValue(configuration.incidentDurationUnit);
      if (configuration.distanceMethod != null && !['PLANAR', 'GEODESIC'].includes(distanceMethod)) {
        errors.push(`${path}.distanceMethod 无效`);
      }
      if (!['INCIDENTS_ONLY', 'ALL_EVENTS'].includes(resultMode)) errors.push(`${path}.resultMode 无效`);
      if (!durationUnits.has(durationUnit)) errors.push(`${path}.incidentDurationUnit 无效`);
      if (configuration.pointGeometryColumnName !== null
        && configuration.pointGeometryColumnName !== undefined
        && typeof configuration.pointGeometryColumnName !== 'string') {
        errors.push(`${path}.pointGeometryColumnName 必须是字符串或 null`);
      }
      return {
        ...parseIncidentLifecycleOptions(configuration, path, errors),
        sourceTableName: stringValue(configuration.sourceTableName),
        pointGeometryColumnName: typeof configuration.pointGeometryColumnName === 'string'
          ? configuration.pointGeometryColumnName : null,
        trackIdColumns: parseStringArray(configuration.trackIdColumns, `${path}.trackIdColumns`, errors),
        timeColumnName: stringValue(configuration.timeColumnName),
        distanceMethod: ['PLANAR', 'GEODESIC'].includes(distanceMethod)
          ? distanceMethod as Configuration<'TRACK_DETECT_INCIDENTS'>['distanceMethod'] : null,
        boundaries: parseTrackBoundaries(configuration.boundaries, `${path}.boundaries`, errors),
        startCondition: parseFilterCondition(
          configuration.startCondition, `${path}.startCondition`, errors,
        ),
        endCondition: configuration.endCondition == null ? null : parseFilterCondition(
          configuration.endCondition, `${path}.endCondition`, errors,
        ),
        resultMode: ['INCIDENTS_ONLY', 'ALL_EVENTS'].includes(resultMode)
          ? resultMode as Configuration<'TRACK_DETECT_INCIDENTS'>['resultMode'] : null,
        outputTableName: stringValue(configuration.outputTableName),
        incidentIdColumnName: stringValue(configuration.incidentIdColumnName),
        incidentFlagColumnName: stringValue(configuration.incidentFlagColumnName),
        incidentStartTimeColumnName: stringValue(configuration.incidentStartTimeColumnName),
        incidentEndTimeColumnName: stringValue(configuration.incidentEndTimeColumnName),
        incidentDurationColumnName: stringValue(configuration.incidentDurationColumnName),
        incidentDurationUnit: durationUnits.has(durationUnit)
          ? durationUnit as Configuration<'TRACK_DETECT_INCIDENTS'>['incidentDurationUnit'] : 'MINUTES',
      };
    })
  );
