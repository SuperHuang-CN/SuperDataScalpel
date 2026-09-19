import { legacyTableName, parseMappings, parseStringArray, parseWriteMode, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseOutputWrites } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'JDBC_OUTPUT'>>(value, path, (configuration, errors) => {
      const writes = parseOutputWrites(configuration.writes, `${path}.writes`, errors, (write, writePath) => ({
        writeId: validateOptionalUuid(stringValue(write.writeId), `${writePath}.writeId`, errors),
        sourceTableName: stringValue(write.sourceTableName),
        targetTableName: stringValue(write.targetTableName) || legacyTableName(write.targetTable),
        writeMode: parseWriteMode(write.writeMode, `${writePath}.writeMode`, errors),
        columnMappings: parseMappings(write.columnMappings, `${writePath}.columnMappings`, errors),
        upsertKeyColumns: parseStringArray(write.upsertKeyColumns, `${writePath}.upsertKeyColumns`, errors),
      }));
      return { dataSourceId: stringValue(configuration.dataSourceId), writes };
    })
  );
