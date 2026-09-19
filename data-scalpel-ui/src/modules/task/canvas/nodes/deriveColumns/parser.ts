import { parseDerivations } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseProcessorOperations } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'DERIVE_COLUMNS'>>(value, path, (configuration, errors) => ({
      globalDerivations: parseDerivations(configuration.globalDerivations ?? [], `${path}.globalDerivations`, errors),
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({ derivations: parseDerivations(operation.derivations, `${operationPath}.derivations`, errors) })),
    }))
  );
