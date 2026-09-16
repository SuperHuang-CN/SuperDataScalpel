import { parsePlatformTypeDefinition, stringValue } from "../../canvasValueParsers";
import { CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS, CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH, type JsonExtraction } from "../../canvasTypes";
import { parseConfiguration, isRecord, type Configuration, parseProcessorOperations } from '../configurationParsing';

export const parseJsonExtractions = (
  value: unknown,
  path: string,
  errors: string[],
): JsonExtraction[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS) {
    errors.push(`${path} 不能超过 ${CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS} 项`);
  }
  return value.slice(0, CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS)
    .flatMap((item, index): JsonExtraction[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      if (typeof item.jsonPath !== 'string') {
        errors.push(`${itemPath}.jsonPath 必须是字符串`);
      }
      if (typeof item.outputColumnName !== 'string') {
        errors.push(`${itemPath}.outputColumnName 必须是字符串`);
      }
      const jsonPath = stringValue(item.jsonPath);
      if (jsonPath.length > CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH) {
        errors.push(
          `${itemPath}.jsonPath 不能超过 ${CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH} 个字符`,
        );
      }
      return [{
        jsonPath,
        outputColumnName: stringValue(item.outputColumnName),
        targetType: parsePlatformTypeDefinition(
          item.targetType,
          `${itemPath}.targetType`,
          errors,
        ),
      }];
    });
};

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'JSON_EXTRACT'>>(value, path, (configuration, errors) => {
      return {
        operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
          (operation, operationPath) => {
            if (operation.failureStrategy !== 'ERROR' && operation.failureStrategy !== 'SET_NULL') {
              errors.push(`${operationPath}.failureStrategy 仅支持 ERROR 或 SET_NULL`);
            }
            return {
              sourceColumnName: stringValue(operation.sourceColumnName),
              extractions: parseJsonExtractions(operation.extractions, `${operationPath}.extractions`, errors),
              failureStrategy: operation.failureStrategy === 'SET_NULL' ? 'SET_NULL' : 'ERROR',
            };
          }),
      };
    })
  );
