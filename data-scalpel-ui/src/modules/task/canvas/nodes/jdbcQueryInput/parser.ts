import { parsePlatformTypeDefinition, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { type CanvasColumnSchema } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration } from '../configurationParsing';

export const parseJdbcQueryOutputColumns = (
  value: unknown,
  path: string,
  errors: string[],
): CanvasColumnSchema[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): CanvasColumnSchema[] => {
    const columnPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${columnPath} 必须是对象`);
      return [];
    }
    const type = parsePlatformTypeDefinition({
      type: item.fieldType,
      length: item.length,
      precision: item.precision,
      scale: item.scale,
      geometry: item.geometry,
    }, columnPath, errors);
    if (typeof item.nullable !== 'boolean') errors.push(`${columnPath}.nullable 必须是 Boolean`);
    if (item.defaultValue !== null && item.defaultValue !== undefined
      && typeof item.defaultValue !== 'string') {
      errors.push(`${columnPath}.defaultValue 必须是字符串或 null`);
    }
    if (typeof item.autoIncrement !== 'boolean') {
      errors.push(`${columnPath}.autoIncrement 必须是 Boolean`);
    }
    if (typeof item.generated !== 'boolean') {
      errors.push(`${columnPath}.generated 必须是 Boolean`);
    }
    if (item.comment !== null && item.comment !== undefined && typeof item.comment !== 'string') {
      errors.push(`${columnPath}.comment 必须是字符串或 null`);
    }
    return [{
      name: stringValue(item.name),
      fieldType: type.type,
      length: type.length,
      precision: type.precision,
      scale: type.scale,
      nullable: typeof item.nullable === 'boolean' ? item.nullable : true,
      defaultValue: typeof item.defaultValue === 'string' ? item.defaultValue : null,
      autoIncrement: typeof item.autoIncrement === 'boolean' ? item.autoIncrement : false,
      generated: typeof item.generated === 'boolean' ? item.generated : false,
      comment: typeof item.comment === 'string' ? item.comment : null,
      geometry: null,
    }];
  });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'JDBC_QUERY_INPUT'>>(value, path, (configuration, errors) => {
      const sql = stringValue(configuration.sql);
      const analyzedSqlSha256 = stringValue(configuration.analyzedSqlSha256);
      if (sql.length > 100_000) errors.push(`${path}.sql 不能超过 100000 个字符`);
      if (analyzedSqlSha256 && !/^[0-9a-f]{64}$/.test(analyzedSqlSha256)) {
        errors.push(`${path}.analyzedSqlSha256 必须是 64 位小写 SHA-256`);
      }
      return {
        dataSourceId: validateOptionalUuid(
          stringValue(configuration.dataSourceId),
          `${path}.dataSourceId`,
          errors,
        ),
        sql,
        outputTableName: stringValue(configuration.outputTableName),
        analyzedSqlSha256,
        outputColumns: parseJdbcQueryOutputColumns(
          configuration.outputColumns,
          `${path}.outputColumns`,
          errors,
        ),
      };
    })
  );
