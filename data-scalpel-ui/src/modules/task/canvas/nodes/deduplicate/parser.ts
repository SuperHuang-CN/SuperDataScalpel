import { parseDeduplicateKeepStrategy, parseSortFields, parseStringArray } from "../../canvasValueParsers";
import { parseConfiguration, type Configuration, parseProcessorOperations } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'DEDUPLICATE'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => ({
          keyColumns: parseStringArray(operation.keyColumns, `${operationPath}.keyColumns`, errors),
          keepStrategy: parseDeduplicateKeepStrategy(operation.keepStrategy, `${operationPath}.keepStrategy`, errors),
          orderBy: parseSortFields(operation.orderBy, `${operationPath}.orderBy`, errors),
        })),
    }))
  );
