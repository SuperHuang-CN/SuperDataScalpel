import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'GEOMETRY_SERIALIZE'>>(
      value,
      path,
      (configuration, errors) => {
        if (configuration.format !== 'WKT'
          && configuration.format !== 'WKB'
          && configuration.format !== 'GEOJSON') {
          errors.push(`${path}.format 仅支持 WKT、WKB 或 GEOJSON`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          outputTableName: stringValue(configuration.outputTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          outputColumnName: stringValue(configuration.outputColumnName),
          format: configuration.format === 'WKB'
            ? 'WKB' : configuration.format === 'GEOJSON' ? 'GEOJSON' : 'WKT',
        };
      },
    )
  );
