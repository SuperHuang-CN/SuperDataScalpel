import { parseKafkaValueSchema, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'KAFKA_INPUT'>>(value, path, (configuration, errors) => {
      if (configuration.startingOffsets !== undefined
        && configuration.startingOffsets !== null
        && configuration.startingOffsets !== ''
        && configuration.startingOffsets !== 'EARLIEST'
      && configuration.startingOffsets !== 'LATEST') {
        errors.push(`${path}.startingOffsets 仅支持 EARLIEST 或 LATEST`);
      }
      const valueFormat = configuration.valueFormat === undefined || configuration.valueFormat === null
        ? 'JSON'
        : configuration.valueFormat === 'JSON'
          || configuration.valueFormat === 'TEXT'
          || configuration.valueFormat === 'BINARY'
          ? configuration.valueFormat
          : null;
      if (valueFormat === null) {
        errors.push(`${path}.valueFormat 仅支持 JSON、TEXT 或 BINARY`);
      }
      const metadataFields = configuration.metadataFields === undefined
        || configuration.metadataFields === null
        ? []
        : Array.isArray(configuration.metadataFields)
          ? configuration.metadataFields.flatMap((item, index) => {
            if (item === 'KEY' || item === 'TOPIC' || item === 'PARTITION'
              || item === 'OFFSET' || item === 'TIMESTAMP') return [item];
            errors.push(`${path}.metadataFields[${index}] 不是受支持的 Kafka 元数据字段`);
            return [];
          })
          : [];
      if (configuration.metadataFields !== undefined
        && configuration.metadataFields !== null
        && !Array.isArray(configuration.metadataFields)) {
        errors.push(`${path}.metadataFields 必须是数组`);
      }
      const duplicateMetadataFields = metadataFields.filter(
        (field, index) => metadataFields.indexOf(field) !== index,
      );
      if (duplicateMetadataFields.length > 0) {
        errors.push(`${path}.metadataFields 不能重复配置：${duplicateMetadataFields[0]}`);
      }
      const valueSchema = parseKafkaValueSchema(
        configuration.valueSchema,
        `${path}.valueSchema`,
        errors,
      );
      if (valueFormat !== null && valueFormat !== 'JSON' && valueSchema.columns.length > 0) {
        errors.push(`${path}.valueSchema.columns 在 TEXT/BINARY 格式下必须为空`);
      }
      const metadataColumnNames: Record<string, string> = {
        KEY: '_kafka_key',
        TOPIC: '_kafka_topic',
        PARTITION: '_kafka_partition',
        OFFSET: '_kafka_offset',
        TIMESTAMP: '_kafka_timestamp',
      };
      if (valueFormat === 'JSON') {
        valueSchema.columns.forEach((column, index) => {
          if (metadataFields.some((field) => metadataColumnNames[field] === column.name)) {
            errors.push(`${path}.valueSchema.columns[${index}].name 与 Kafka 元数据字段重名`);
          }
        });
      }
      return {
        dataSourceId: validateOptionalUuid(
          stringValue(configuration.dataSourceId),
          `${path}.dataSourceId`,
          errors,
        ),
        topic: stringValue(configuration.topic),
        valueSchema,
        outputTableName: stringValue(configuration.outputTableName),
        startingOffsets: configuration.startingOffsets === 'EARLIEST'
          || configuration.startingOffsets === 'LATEST'
          ? configuration.startingOffsets
          : null,
        triggerIntervalSeconds: typeof configuration.triggerIntervalSeconds === 'number'
          ? configuration.triggerIntervalSeconds : 10,
        valueFormat: valueFormat ?? 'JSON',
        metadataFields,
      };
    })
  );
