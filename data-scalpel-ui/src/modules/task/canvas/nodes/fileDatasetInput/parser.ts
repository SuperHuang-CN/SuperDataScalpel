import { stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const parseFileDatasetInputTables = (
  value: unknown,
  path: string,
  errors: string[],
): Configuration<'FILE_DATASET_INPUT'>['tables'] => {
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
      fileDatasetTableId: validateOptionalUuid(
        stringValue(item.fileDatasetTableId), `${path}[${index}].fileDatasetTableId`, errors,
      ),
    }];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'FILE_DATASET_INPUT'>>(
      value,
      path,
      (configuration, errors) => ({
        fileDatasetId: validateOptionalUuid(
          stringValue(configuration.fileDatasetId), `${path}.fileDatasetId`, errors,
        ),
        tables: parseFileDatasetInputTables(configuration.tables, `${path}.tables`, errors),
      }),
    )
  );
