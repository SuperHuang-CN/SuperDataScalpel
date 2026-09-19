import { parseFilterCondition } from "../../canvasValueParsers";
import { findFilterSqlExpressionViolation } from "./filterSqlExpression";
import { parseConfiguration, type Configuration, parseProcessorOperations } from '../configurationParsing';

export const parseNodeConfiguration = (value: unknown, path: string) => (
    parseConfiguration<Configuration<'FILTER'>>(value, path, (configuration, errors) => ({
      operations: parseProcessorOperations(configuration.operations, `${path}.operations`, errors,
        (operation, operationPath) => {
          const rawMode = operation.mode ?? 'STRUCTURED';
          if (rawMode !== 'STRUCTURED' && rawMode !== 'SQL_EXPRESSION') {
            errors.push(`${operationPath}.mode 仅支持 STRUCTURED 或 SQL_EXPRESSION`);
          }
          if (operation.sqlExpression !== undefined
            && operation.sqlExpression !== null
            && typeof operation.sqlExpression !== 'string') {
            errors.push(`${operationPath}.sqlExpression 必须是字符串`);
          }
          const mode = rawMode === 'SQL_EXPRESSION' ? 'SQL_EXPRESSION' : 'STRUCTURED';
          const sqlExpression = typeof operation.sqlExpression === 'string'
            ? operation.sqlExpression
            : '';
          const sqlViolation = findFilterSqlExpressionViolation(sqlExpression);
          if (mode === 'SQL_EXPRESSION' && sqlViolation !== null && sqlViolation !== 'REQUIRED') {
            errors.push(`${operationPath}.sqlExpression 只能包含单个布尔谓词，不能包含 WHERE、完整 SQL、注释或分号`);
          }
          return {
            mode,
            condition: parseFilterCondition(operation.condition, `${operationPath}.condition`, errors),
            sqlExpression,
          };
        }),
    }))
  );
