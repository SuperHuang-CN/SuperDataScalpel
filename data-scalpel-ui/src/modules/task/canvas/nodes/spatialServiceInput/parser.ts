import { stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const parseSpatialServiceInputResources = (
  value: unknown,
  path: string,
  errors: string[],
): Configuration<'SPATIAL_SERVICE_INPUT'>['resources'] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index) => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    return [{
      resourceId: validateOptionalUuid(stringValue(item.resourceId), `${path}[${index}].resourceId`, errors),
      outputTableName: stringValue(item.outputTableName),
    }];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SPATIAL_SERVICE_INPUT'>>(value, path, (configuration, errors) => ({
      dataSourceId: validateOptionalUuid(stringValue(configuration.dataSourceId), `${path}.dataSourceId`, errors),
      resources: parseSpatialServiceInputResources(configuration.resources, `${path}.resources`, errors),
    }))
  );
