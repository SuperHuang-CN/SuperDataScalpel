import { CANVAS_FILTER_MAX_CONDITION_NODES, CANVAS_FILTER_MAX_DEPTH, CANVAS_FILTER_MAX_VALUES_PER_PREDICATE, CANVAS_EXPRESSION_MAX_CASE_BRANCHES, CANVAS_EXPRESSION_MAX_DEPTH, CANVAS_EXPRESSION_MAX_DERIVATIONS, CANVAS_EXPRESSION_MAX_NODES, type CanvasFilterCondition, type CanvasLiteral, type CanvasExpression, type ColumnDerivation, type DeriveBinaryOperator, type DeriveFunction, type CanvasRuntimeValue, type FilterOperator } from "../canvasTypes";
import { platformDataTypes, isRecord, stringValue } from './scalars';

export const filterLiteralDataTypes = new Set([...platformDataTypes, 'GEOMETRY']);

export const filterOperators = new Set<FilterOperator>([
  'EQUALS',
  'NOT_EQUALS',
  'GREATER_THAN',
  'GREATER_THAN_OR_EQUALS',
  'LESS_THAN',
  'LESS_THAN_OR_EQUALS',
  'IN',
  'NOT_IN',
  'IS_NULL',
  'IS_NOT_NULL',
  'CONTAINS',
  'STARTS_WITH',
  'ENDS_WITH',
]);

export const deriveBinaryOperators = new Set<DeriveBinaryOperator>([
  'ADD',
  'SUBTRACT',
  'MULTIPLY',
  'DIVIDE',
  'MODULO',
]);

export const deriveFunctions = new Set<DeriveFunction>([
  'TRIM',
  'LTRIM',
  'RTRIM',
  'LOWER',
  'UPPER',
  'REPLACE',
  'SUBSTRING',
  'COALESCE',
  'CONCAT',
  'DATE_FORMAT',
  'DATE_ADD',
  'DATE_SUB',
]);

export const canvasRuntimeValues = new Set<CanvasRuntimeValue>([
  'EXECUTION_ID',
  'EXECUTION_STARTED_AT',
]);

export const parseCanvasLiteral = (
  value: unknown,
  path: string,
  errors: string[],
): CanvasLiteral => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是 Literal 对象`);
    return { dataType: 'STRING', value: null };
  }
  const dataType = stringValue(value.dataType);
  if (!filterLiteralDataTypes.has(dataType)) {
    errors.push(`${path}.dataType 不是平台数据类型`);
  }
  if (value.value !== null && typeof value.value !== 'string') {
    errors.push(`${path}.value 必须是字符串或 null`);
  }
  return {
    dataType: filterLiteralDataTypes.has(dataType)
      ? dataType as CanvasLiteral['dataType']
      : 'STRING',
    value: typeof value.value === 'string' ? value.value : null,
  };
};

export const parseFilterCondition = (
  value: unknown,
  path: string,
  errors: string[],
  depth = 1,
  nodeCount: { value: number } = { value: 0 },
): CanvasFilterCondition => {
  const fallback: CanvasFilterCondition = { kind: 'GROUP', operator: 'AND', children: [] };
  if (value === undefined || value === null) return fallback;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是筛选条件对象`);
    return fallback;
  }
  nodeCount.value += 1;
  if (depth > CANVAS_FILTER_MAX_DEPTH) {
    errors.push(`${path} 超过最大嵌套深度 ${CANVAS_FILTER_MAX_DEPTH}`);
    return fallback;
  }
  if (nodeCount.value > CANVAS_FILTER_MAX_CONDITION_NODES) {
    errors.push(`筛选条件节点不能超过 ${CANVAS_FILTER_MAX_CONDITION_NODES}`);
    return fallback;
  }
  if (value.kind === 'GROUP') {
    if (value.operator !== 'AND' && value.operator !== 'OR') {
      errors.push(`${path}.operator 仅支持 AND 或 OR`);
    }
    if (value.children !== undefined && value.children !== null && !Array.isArray(value.children)) {
      errors.push(`${path}.children 必须是数组`);
    }
    const children = Array.isArray(value.children)
      ? value.children.map((child, index) => parseFilterCondition(
        child,
        `${path}.children[${index}]`,
        errors,
        depth + 1,
        nodeCount,
      ))
      : [];
    return {
      kind: 'GROUP',
      operator: value.operator === 'OR' ? 'OR' : 'AND',
      children,
    };
  }
  if (value.kind === 'PREDICATE') {
    const rawOperator = stringValue(value.operator);
    if (!filterOperators.has(rawOperator as FilterOperator)) {
      errors.push(`${path}.operator 不是受支持的筛选操作符`);
    }
    if (value.values !== undefined && value.values !== null && !Array.isArray(value.values)) {
      errors.push(`${path}.values 必须是数组`);
    }
    const rawValues = Array.isArray(value.values) ? value.values : [];
    if (rawValues.length > CANVAS_FILTER_MAX_VALUES_PER_PREDICATE) {
      errors.push(`${path}.values 不能超过 ${CANVAS_FILTER_MAX_VALUES_PER_PREDICATE} 项`);
    }
    const values = rawValues.slice(0, CANVAS_FILTER_MAX_VALUES_PER_PREDICATE)
      .map((item, index) => parseCanvasLiteral(
        item,
        `${path}.values[${index}]`,
        errors,
      ));
    return {
      kind: 'PREDICATE',
      columnName: stringValue(value.columnName),
      operator: filterOperators.has(rawOperator as FilterOperator)
        ? rawOperator as FilterOperator
        : 'EQUALS',
      values,
    };
  }
  errors.push(`${path}.kind 仅支持 GROUP 或 PREDICATE`);
  return fallback;
};

export const parseCanvasExpression = (
  value: unknown,
  path: string,
  errors: string[],
  depth = 1,
  nodeCount: { value: number } = { value: 0 },
): CanvasExpression => {
  const fallback: CanvasExpression = { kind: 'COLUMN', columnName: '' };
  if (value === undefined || value === null) return fallback;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是表达式对象`);
    return fallback;
  }
  nodeCount.value += 1;
  if (depth > CANVAS_EXPRESSION_MAX_DEPTH) {
    errors.push(`${path} 超过最大嵌套深度 ${CANVAS_EXPRESSION_MAX_DEPTH}`);
    return fallback;
  }
  if (nodeCount.value > CANVAS_EXPRESSION_MAX_NODES) {
    errors.push(`派生表达式节点不能超过 ${CANVAS_EXPRESSION_MAX_NODES}`);
    return fallback;
  }
  if (value.kind === 'COLUMN') {
    return { kind: 'COLUMN', columnName: stringValue(value.columnName) };
  }
  if (value.kind === 'LITERAL') {
    return {
      kind: 'LITERAL',
      literal: parseCanvasLiteral(value.literal, `${path}.literal`, errors),
    };
  }
  if (value.kind === 'RUNTIME_VALUE') {
    const rawRuntimeValue = stringValue(value.value);
    if (!canvasRuntimeValues.has(rawRuntimeValue as CanvasRuntimeValue)) {
      errors.push(`${path}.value 不是受支持的运行时变量`);
    }
    return {
      kind: 'RUNTIME_VALUE',
      value: canvasRuntimeValues.has(rawRuntimeValue as CanvasRuntimeValue)
        ? rawRuntimeValue as CanvasRuntimeValue
        : 'EXECUTION_ID',
    };
  }
  if (value.kind === 'BINARY') {
    const rawOperator = stringValue(value.operator);
    if (!deriveBinaryOperators.has(rawOperator as DeriveBinaryOperator)) {
      errors.push(`${path}.operator 不是受支持的二元操作符`);
    }
    return {
      kind: 'BINARY',
      operator: deriveBinaryOperators.has(rawOperator as DeriveBinaryOperator)
        ? rawOperator as DeriveBinaryOperator
        : 'ADD',
      left: parseCanvasExpression(
        value.left,
        `${path}.left`,
        errors,
        depth + 1,
        nodeCount,
      ),
      right: parseCanvasExpression(
        value.right,
        `${path}.right`,
        errors,
        depth + 1,
        nodeCount,
      ),
    };
  }
  if (value.kind === 'FUNCTION') {
    const rawFunction = stringValue(value.function);
    if (!deriveFunctions.has(rawFunction as DeriveFunction)) {
      errors.push(`${path}.function 不是受支持的表达式函数`);
    }
    if (value.arguments !== undefined
      && value.arguments !== null
      && !Array.isArray(value.arguments)) {
      errors.push(`${path}.arguments 必须是数组`);
    }
    const rawArguments = Array.isArray(value.arguments) ? value.arguments : [];
    return {
      kind: 'FUNCTION',
      function: deriveFunctions.has(rawFunction as DeriveFunction)
        ? rawFunction as DeriveFunction
        : 'TRIM',
      arguments: rawArguments.map((argument, index) => parseCanvasExpression(
        argument,
        `${path}.arguments[${index}]`,
        errors,
        depth + 1,
        nodeCount,
      )),
    };
  }
  if (value.kind === 'CASE_WHEN') {
    if (value.branches !== undefined
      && value.branches !== null
      && !Array.isArray(value.branches)) {
      errors.push(`${path}.branches 必须是数组`);
    }
    const rawBranches = Array.isArray(value.branches) ? value.branches : [];
    if (rawBranches.length > CANVAS_EXPRESSION_MAX_CASE_BRANCHES) {
      errors.push(`${path}.branches 不能超过 ${CANVAS_EXPRESSION_MAX_CASE_BRANCHES} 项`);
    }
    const branches = rawBranches.slice(0, CANVAS_EXPRESSION_MAX_CASE_BRANCHES)
      .flatMap((branch, index) => {
        const branchPath = `${path}.branches[${index}]`;
        if (!isRecord(branch)) {
          errors.push(`${branchPath} 必须是对象`);
          return [];
        }
        return [{
          condition: parseFilterCondition(
            branch.condition,
            `${branchPath}.condition`,
            errors,
          ),
          result: parseCanvasExpression(
            branch.result,
            `${branchPath}.result`,
            errors,
            depth + 1,
            nodeCount,
          ),
        }];
      });
    return {
      kind: 'CASE_WHEN',
      branches,
      elseExpression: value.elseExpression === null || value.elseExpression === undefined
        ? null
        : parseCanvasExpression(
          value.elseExpression,
          `${path}.elseExpression`,
          errors,
          depth + 1,
          nodeCount,
        ),
    };
  }
  errors.push(`${path}.kind 不是受支持的表达式类型`);
  return fallback;
};

export const parseDerivations = (
  value: unknown,
  path: string,
  errors: string[],
): ColumnDerivation[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  if (value.length > CANVAS_EXPRESSION_MAX_DERIVATIONS) {
    errors.push(`${path} 不能超过 ${CANVAS_EXPRESSION_MAX_DERIVATIONS} 项`);
  }
  const expressionNodes = { value: 0 };
  return value.slice(0, CANVAS_EXPRESSION_MAX_DERIVATIONS)
    .flatMap((item, index): ColumnDerivation[] => {
      const itemPath = `${path}[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${itemPath} 必须是对象`);
        return [];
      }
      if (item.replaceExisting !== undefined) {
        errors.push(`${itemPath}.replaceExisting 已不再支持；派生字段会按目标字段名自动新增或覆盖`);
      }
      return [{
        targetColumnName: stringValue(item.targetColumnName),
        expression: parseCanvasExpression(
          item.expression,
          `${itemPath}.expression`,
          errors,
          1,
          expressionNodes,
        ),
      }];
    });
};
