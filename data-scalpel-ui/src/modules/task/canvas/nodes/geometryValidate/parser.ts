import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'GEOMETRY_VALIDATE'>>(
      value,
      path,
      (configuration, errors) => {
        if (configuration.reasonColumnName !== null
          && configuration.reasonColumnName !== undefined
          && typeof configuration.reasonColumnName !== 'string') {
          errors.push(`${path}.reasonColumnName 必须是字符串或 null`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          validColumnName: stringValue(configuration.validColumnName),
          reasonColumnName: typeof configuration.reasonColumnName === 'string'
            ? configuration.reasonColumnName : null,
        };
      },
    )
  );
