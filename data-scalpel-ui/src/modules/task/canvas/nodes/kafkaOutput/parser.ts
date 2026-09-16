import { parseKafkaValueSchema, parseMappings, parseStringArray, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseOutputWrites } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'KAFKA_OUTPUT'>>(value, path, (configuration, errors) => {
      const writes = parseOutputWrites(configuration.writes, `${path}.writes`, errors, (write, writePath) => {
        const rawFormat = write.valueFormat;
        const valueFormat = rawFormat == null
          ? null
          : rawFormat === 'JSON' || rawFormat === 'TEXT' || rawFormat === 'BINARY'
            ? rawFormat
            : null;
        if (rawFormat != null && valueFormat === null) {
          errors.push(`${writePath}.valueFormat 不是支持的 Kafka Value 格式`);
        }
        const valueColumnNames = parseStringArray(
          write.valueColumnNames, `${writePath}.valueColumnNames`, errors,
        );
        const valueSchema = write.valueSchema == null
          ? null
          : parseKafkaValueSchema(write.valueSchema, `${writePath}.valueSchema`, errors);
        const columnMappings = parseMappings(
          write.columnMappings, `${writePath}.columnMappings`, errors,
        );
        valueColumnNames.forEach((columnName, index) => {
          if (!columnName.trim()) {
            errors.push(`${writePath}.valueColumnNames[${index}] 不能为空`);
          }
        });
        if (valueFormat === null) {
          if (valueSchema === null) errors.push(`${writePath}.valueSchema 不能为空`);
          if (valueColumnNames.length > 0) {
            errors.push(`${writePath}.valueColumnNames 在旧版 JSON 映射模式下必须为空`);
          }
        } else {
          if (valueSchema && valueSchema.columns.length > 0) {
            errors.push(`${writePath}.valueSchema.columns 在新 Kafka Value 模式下必须为空`);
          }
          if (columnMappings.length > 0) {
            errors.push(`${writePath}.columnMappings 在新 Kafka Value 模式下必须为空`);
          }
          if (valueColumnNames.length === 0) {
            errors.push(`${writePath}.valueColumnNames 至少需要一个字段`);
          }
          if (new Set(valueColumnNames).size !== valueColumnNames.length) {
            errors.push(`${writePath}.valueColumnNames 不能包含重复字段`);
          }
          if (valueFormat !== 'JSON' && valueColumnNames.length !== 1) {
            errors.push(`${writePath}.valueColumnNames 在 ${valueFormat} 格式下必须且只能有一个字段`);
          }
        }
        return {
          writeId: validateOptionalUuid(stringValue(write.writeId), `${writePath}.writeId`, errors),
          sourceTableName: stringValue(write.sourceTableName),
          topic: stringValue(write.topic),
          valueFormat,
          valueColumnNames,
          keyColumnName: stringValue(write.keyColumnName),
          valueSchema,
          columnMappings,
        };
      });
      return {
        dataSourceId: validateOptionalUuid(
          stringValue(configuration.dataSourceId), `${path}.dataSourceId`, errors,
        ),
        writes,
      };
    })
  );
