import { parseNearestMatching } from "./matching";
import { spatialDistanceUnits } from "../spatialUnits";
import { parseJoinOutputColumns, stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_NEAREST'>>(
      value,
      path,
      (configuration, errors) => {
        const distanceMethod = stringValue(configuration.distanceMethod);
        const maximumDistanceUnit = configuration.maximumDistanceUnit == null
          ? null : stringValue(configuration.maximumDistanceUnit);
        const distanceOutputUnit = stringValue(configuration.distanceOutputUnit);
        const distanceUnits = spatialDistanceUnits;
        if (configuration.distanceMethod != null && distanceMethod !== 'PLANAR' && distanceMethod !== 'GEODESIC') {
          errors.push(`${path}.distanceMethod 仅支持 PLANAR 或 GEODESIC`);
        }
        if (typeof configuration.nearestCount !== 'number'
          || !Number.isInteger(configuration.nearestCount)) {
          errors.push(`${path}.nearestCount 必须是整数`);
        }
        if (configuration.maximumDistance !== null
          && configuration.maximumDistance !== undefined
          && (typeof configuration.maximumDistance !== 'number'
            || !Number.isFinite(configuration.maximumDistance))) {
          errors.push(`${path}.maximumDistance 必须是有限数值或 null`);
        }
        if (maximumDistanceUnit !== null && !distanceUnits.has(maximumDistanceUnit)) {
          errors.push(`${path}.maximumDistanceUnit 不是受支持的距离单位`);
        }
        if (!distanceUnits.has(distanceOutputUnit)) {
          errors.push(`${path}.distanceOutputUnit 不是受支持的距离单位`);
        }
        if (configuration.rankColumnName !== null
          && configuration.rankColumnName !== undefined
          && typeof configuration.rankColumnName !== 'string') {
          errors.push(`${path}.rankColumnName 必须是字符串或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          sourceGeometryColumnName: stringValue(configuration.sourceGeometryColumnName),
          candidateTableName: stringValue(configuration.candidateTableName),
          candidateGeometryColumnName: stringValue(configuration.candidateGeometryColumnName),
          candidateIdColumnName: stringValue(configuration.candidateIdColumnName),
          distanceMethod: distanceMethod === 'GEODESIC' ? 'GEODESIC'
            : distanceMethod === 'PLANAR' ? 'PLANAR' : null,
          nearestCount: typeof configuration.nearestCount === 'number'
            ? configuration.nearestCount : 0,
          maximumDistance: typeof configuration.maximumDistance === 'number'
            ? configuration.maximumDistance : null,
          maximumDistanceUnit: maximumDistanceUnit !== null
            && distanceUnits.has(maximumDistanceUnit)
            ? maximumDistanceUnit as Configuration<'SPATIAL_NEAREST'>['maximumDistanceUnit']
            : null,
          includeUnmatched: configuration.includeUnmatched === true,
          outputTableName: stringValue(configuration.outputTableName),
          distanceColumnName: stringValue(configuration.distanceColumnName),
          distanceOutputUnit: distanceUnits.has(distanceOutputUnit)
            ? distanceOutputUnit as Configuration<'SPATIAL_NEAREST'>['distanceOutputUnit']
            : 'SOURCE_CRS_UNIT',
          rankColumnName: typeof configuration.rankColumnName === 'string'
            ? configuration.rankColumnName : null,
          outputColumns: parseJoinOutputColumns(
            configuration.outputColumns,
            `${path}.outputColumns`,
            errors,
          ),
          ...parseNearestMatching(configuration, path, errors),
        };
      },
    )
  );
