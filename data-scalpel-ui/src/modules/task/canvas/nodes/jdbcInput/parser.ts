import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const parseJdbcInputTables = (
  configuration: Record<string, unknown>,
  path: string,
  errors: string[],
): Configuration<'JDBC_INPUT'>['tables'] => {
  if (configuration.tables === undefined || configuration.tables === null) {
    return [];
  }
  if (!Array.isArray(configuration.tables)) {
    errors.push(`${path}.tables 必须是数组`);
    return [];
  }
  return configuration.tables.flatMap((item, index) => {
    if (!isRecord(item)) {
      errors.push(`${path}.tables[${index}] 必须是对象`);
      return [];
    }
    const readOptionsValue = item.readOptions;
    if (readOptionsValue !== undefined && readOptionsValue !== null && !Array.isArray(readOptionsValue)) {
      errors.push(`${path}.tables[${index}].readOptions 必须是数组`);
    }
    const readOptions = Array.isArray(readOptionsValue)
      ? readOptionsValue.flatMap((option, optionIndex) => {
        if (!isRecord(option)) {
          errors.push(`${path}.tables[${index}].readOptions[${optionIndex}] 必须是对象`);
          return [];
        }
        return [{ name: stringValue(option.name), value: stringValue(option.value) }];
      })
      : [];
    return [{ tableName: stringValue(item.tableName), readOptions }];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'JDBC_INPUT'>>(value, path, (configuration, errors) => ({
      dataSourceId: stringValue(configuration.dataSourceId),
      tables: parseJdbcInputTables(configuration, path, errors),
    }))
  );
