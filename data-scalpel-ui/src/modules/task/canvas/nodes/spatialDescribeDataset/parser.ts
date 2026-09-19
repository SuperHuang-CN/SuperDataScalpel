import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseInteger } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_DESCRIBE_DATASET'>>(
      value,
      path,
      (configuration, errors) => {
        if (typeof configuration.extentOutput !== 'boolean') {
          errors.push(`${path}.extentOutput 必须是布尔值`);
        }
        return {
          sourceTableName: stringValue(configuration.sourceTableName),
          geometryColumnName: stringValue(configuration.geometryColumnName),
          statisticsTableName: stringValue(configuration.statisticsTableName),
          descriptionTableName: stringValue(configuration.descriptionTableName),
          sampleSize: parseInteger(configuration.sampleSize, `${path}.sampleSize`, errors, 0),
          sampleTableName: stringValue(configuration.sampleTableName),
          extentOutput: configuration.extentOutput === true,
          extentTableName: stringValue(configuration.extentTableName),
        };
      },
    )
  );
