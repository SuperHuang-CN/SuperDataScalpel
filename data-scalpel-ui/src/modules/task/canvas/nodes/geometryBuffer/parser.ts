import { spatialDistanceUnits } from "../spatialUnits";
import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseFiniteNumber } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'GEOMETRY_BUFFER'>>(
      value,
      path,
      (configuration, errors) => {
        const distanceUnit = configuration.distanceUnit == null
          ? null : stringValue(configuration.distanceUnit);
        const distanceSource = configuration.distanceSource == null
          ? null : stringValue(configuration.distanceSource);
        if (configuration.mode !== 'PLANAR' && configuration.mode !== 'SPHEROID') {
          errors.push(`${path}.mode 仅支持 PLANAR 或 SPHEROID`);
        }
        if (distanceUnit !== null && !spatialDistanceUnits.has(distanceUnit)) {
          errors.push(`${path}.distanceUnit 不是受支持的距离单位`);
        }
        if (distanceSource !== null
          && distanceSource !== 'CONSTANT'
          && distanceSource !== 'FIELD'
          && distanceSource !== 'EXPRESSION') {
          errors.push(`${path}.distanceSource 仅支持 CONSTANT、FIELD 或 EXPRESSION`);
        }
        if (configuration.distanceFieldName !== null
          && configuration.distanceFieldName !== undefined
          && typeof configuration.distanceFieldName !== 'string') {
          errors.push(`${path}.distanceFieldName 必须是字符串或 null`);
        }
        if (configuration.distanceExpression !== null
          && configuration.distanceExpression !== undefined
          && typeof configuration.distanceExpression !== 'string') {
          errors.push(`${path}.distanceExpression 必须是字符串或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          outputColumnName: stringValue(configuration.outputColumnName),
          distance: parseFiniteNumber(
            configuration.distance,
            `${path}.distance`,
            errors,
            100,
          ),
          mode: configuration.mode === 'SPHEROID' ? 'SPHEROID' : 'PLANAR',
          distanceUnit: distanceUnit !== null && spatialDistanceUnits.has(distanceUnit)
            ? distanceUnit as Configuration<'GEOMETRY_BUFFER'>['distanceUnit'] : null,
          distanceSource: distanceSource === 'CONSTANT'
            || distanceSource === 'FIELD'
            || distanceSource === 'EXPRESSION'
            ? distanceSource : null,
          distanceFieldName: typeof configuration.distanceFieldName === 'string'
            ? configuration.distanceFieldName : null,
          distanceExpression: typeof configuration.distanceExpression === 'string'
            ? configuration.distanceExpression : null,
        };
      },
    )
  );
