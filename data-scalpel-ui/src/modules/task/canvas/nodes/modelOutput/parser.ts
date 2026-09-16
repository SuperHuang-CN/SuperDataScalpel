import { parseMappings, parseWriteMode, stringValue, validateOptionalUuid } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseOutputWrites } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'MODEL_OUTPUT'>>(value, path, (configuration, errors) => {
      const writes = parseOutputWrites(configuration.writes, `${path}.writes`, errors, (write, writePath) => ({
        writeId: validateOptionalUuid(stringValue(write.writeId), `${writePath}.writeId`, errors),
        sourceTableName: stringValue(write.sourceTableName),
        targetModelId: validateOptionalUuid(stringValue(write.targetModelId), `${writePath}.targetModelId`, errors),
        writeMode: parseWriteMode(write.writeMode, `${writePath}.writeMode`, errors),
        columnMappings: parseMappings(write.columnMappings, `${writePath}.columnMappings`, errors),
      }));
      return { writes };
    })
  );
