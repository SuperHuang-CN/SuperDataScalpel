import { parseSortFields, parseStringArray } from "../../canvasValueParsers";
import { CANVAS_TOP_N_MAX_LIMIT } from "../../canvasTypes";
import { parseConfiguration, type Configuration, parseProcessorOperations, parseInteger } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'TOP_N'>>(value, path, (configuration, errors) => {
      return {
        operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
          (operation, operationPath) => {
            const limit = parseInteger(operation.limit, `${operationPath}.limit`, errors, 10);
            if (limit < 1 || limit > CANVAS_TOP_N_MAX_LIMIT) {
              errors.push(`${operationPath}.limit 必须在 1..${CANVAS_TOP_N_MAX_LIMIT}`);
            }
            if (operation.tieStrategy !== 'EXACT' && operation.tieStrategy !== 'WITH_TIES') {
              errors.push(`${operationPath}.tieStrategy 仅支持 EXACT 或 WITH_TIES`);
            }
            return {
              partitionByColumns: parseStringArray(operation.partitionByColumns, `${operationPath}.partitionByColumns`, errors),
              orderBy: parseSortFields(operation.orderBy, `${operationPath}.orderBy`, errors),
              limit,
              tieStrategy: operation.tieStrategy === 'WITH_TIES' ? 'WITH_TIES' : 'EXACT',
            };
          }),
      };
    })
  );
