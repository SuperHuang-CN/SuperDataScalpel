import { parseStringArray } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseProcessorOperations } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'SELECT_COLUMNS'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ columns: parseStringArray(operation.columns, `${operationPath}.columns`, errors) })),
    }))
  );
