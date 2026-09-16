import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'GEOMETRY_EXPLODE'>>(
      value,
      path,
      (configuration, errors) => {
        if (configuration.partIndexColumnName !== null
          && configuration.partIndexColumnName !== undefined
          && typeof configuration.partIndexColumnName !== 'string') {
          errors.push(`${path}.partIndexColumnName 必须是字符串或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          outputColumnName: stringValue(configuration.outputColumnName),
          partIndexColumnName: typeof configuration.partIndexColumnName === 'string'
            ? configuration.partIndexColumnName : null,
        };
      },
    )
  );
