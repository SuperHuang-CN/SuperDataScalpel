import { parseTypeCasts } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseProcessorOperations } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'TYPE_CAST'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ casts: parseTypeCasts(operation.casts, `${operationPath}.casts`, errors) })),
    }))
  );
