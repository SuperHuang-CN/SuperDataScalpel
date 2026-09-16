import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'GEOMETRY_REPAIR'>>(
      value,
      path,
      (configuration) => ({
        sourceTableName: stringValue(configuration.sourceTableName),
        outputTableName: stringValue(configuration.outputTableName),
        geometryColumnName: stringValue(configuration.geometryColumnName),
        outputColumnName: stringValue(configuration.outputColumnName),
      }),
    )
  );
