import { parseRuntimeParameters, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const parseHttpApiInputResources = (
  value: unknown,
  path: string,
  errors: string[],
): Configuration<'HTTP_API_INPUT'>['resources'] => {
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
      runtimeParameters: parseRuntimeParameters(item.runtimeParameters, `${path}[${index}].runtimeParameters`, errors),
    }];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'HTTP_API_INPUT'>>(value, path, (configuration, errors) => ({
      dataSourceId: validateOptionalUuid(
        stringValue(configuration.dataSourceId),
        `${path}.dataSourceId`,
        errors,
      ),
      resources: parseHttpApiInputResources(configuration.resources, `${path}.resources`, errors),
    }))
  );
