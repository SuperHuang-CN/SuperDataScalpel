import { stringValue } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SQL_TRANSFORM'>>(value, path, (configuration, errors) => {
      const sql = stringValue(configuration.sql);
      if (sql.length > 100_000) errors.push(`${path}.sql 不能超过 100000 个字符`);
      return {
        outputTableName: stringValue(configuration.outputTableName),
        sql,
      };
    })
  );
