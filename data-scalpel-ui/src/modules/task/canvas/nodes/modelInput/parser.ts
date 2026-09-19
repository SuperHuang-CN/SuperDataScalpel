import { stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const parseModelInputSelections = (
  value: unknown,
  path: string,
  errors: string[],
): Configuration<'MODEL_INPUT'>['models'] => {
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
    return [{ modelId: validateOptionalUuid(stringValue(item.modelId), `${path}[${index}].modelId`, errors) }];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'MODEL_INPUT'>>(value, path, (configuration, errors) => ({
      models: parseModelInputSelections(configuration.models, `${path}.models`, errors),
    }))
  );
