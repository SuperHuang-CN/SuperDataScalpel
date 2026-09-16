import { parseMappings } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseProcessorOperations } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'RENAME'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ columnMappings: parseMappings(operation.columnMappings, `${operationPath}.columnMappings`, errors) })),
    }))
  );
