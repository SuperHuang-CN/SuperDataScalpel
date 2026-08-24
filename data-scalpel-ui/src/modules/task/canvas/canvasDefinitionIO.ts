import {
  CANVAS_LEGACY_SCHEMA_MINOR_VERSION,
  CANVAS_FILTER_MAX_CONDITION_NODES,
  CANVAS_FILTER_MAX_DEPTH,
  CANVAS_FILTER_MAX_VALUES_PER_PREDICATE,
  CANVAS_EXPRESSION_MAX_CASE_BRANCHES,
  CANVAS_EXPRESSION_MAX_DEPTH,
  CANVAS_EXPRESSION_MAX_DERIVATIONS,
  CANVAS_EXPRESSION_MAX_NODES,
  CANVAS_SCHEMA_MINOR_VERSION,
  CANVAS_SCHEMA_VERSION,
  CanvasNodeType,
  type CanvasDefinition,
  type CanvasEdgeDefinition,
  type CanvasNodeDefinition,
  type CanvasNodeType as CanvasNodeTypeValue,
  type CanvasColumnMapping,
  type CanvasFilterCondition,
  type CanvasLiteral,
  type CanvasExpression,
  type ColumnDerivation,
  type ColumnTypeCast,
  type CastFailureStrategy,
  type AggregateFunction,
  type AggregateItem,
  type UnionMode,
  type DeduplicateKeepStrategy,
  type NullOrdering,
  type SortDirection,
  type SortField,
  type PlatformTypeDefinition,
  type DeriveBinaryOperator,
  type DeriveFunction,
  type CanvasRuntimeValue,
  type FilterOperator,
  type JdbcWriteMode,
  type JoinCondition,
  type JoinOutputColumn,
  type JoinType,
  type StreamJoinType,
  type HttpApiRuntimeParameter,
  type KafkaValueColumn,
  type KafkaValueSchema,
  type FileOutputConflictPolicy,
  type FileOutputFormatOptions,
} from './canvasTypes';
import { canvasNodeRegistry } from './nodes/nodeRegistry';

export type CanvasDefinitionParseResult =
  | { success: true; definition: CanvasDefinition }
  | { success: false; errors: string[] };

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const sensitiveRuntimeParameterPattern = /(password|passwd|secret|token|credential|api[-_.]?key|access[-_.]?key|signature)/i;

export const isSensitiveRuntimeParameterName = (name: string) => (
  sensitiveRuntimeParameterPattern.test(name)
);

const isRecord = (value: unknown): value is Record<string, unknown> => (
  typeof value === 'object' && value !== null && !Array.isArray(value)
);

export const stringValue = (value: unknown) => typeof value === 'string' ? value : '';

export const validateOptionalUuid = (value: string, path: string, errors: string[]) => {
  if (value && !uuidPattern.test(value)) errors.push(`${path} 必须是 UUID`);
  return value;
};
export const legacyTableName = (value: unknown) => isRecord(value) ? stringValue(value.tableName) : '';

const parseLayout = (value: unknown, path: string, errors: string[]) => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是布局对象`);
    return null;
  }
  const coordinates = ['x', 'y', 'width', 'height'] as const;
  if (coordinates.some((key) => typeof value[key] !== 'number' || !Number.isFinite(value[key]))) {
    errors.push(`${path} 必须包含有限数值 x、y、width、height`);
    return null;
  }
  const x = value.x as number;
  const y = value.y as number;
  const width = value.width as number;
  const height = value.height as number;
  if (x < -100_000 || x > 100_000 || y < -100_000 || y > 100_000
    || width < 180 || width > 1000 || height < 96 || height > 1000) {
    errors.push(`${path} 超出允许范围`);
    return null;
  }
  return { x, y, width, height };
};

export const parseJoinType = (value: unknown, path: string, errors: string[]): JoinType | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'INNER' || value === 'LEFT' || value === 'RIGHT' || value === 'FULL') return value;
  errors.push(`${path} 不是受支持的 Join 类型`);
  return null;
};

export const parseStreamJoinType = (
  value: unknown,
  path: string,
  errors: string[],
): StreamJoinType | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'INNER' || value === 'LEFT') return value;
  errors.push(`${path} 不是受支持的流 Join 类型`);
  return null;
};

export const parseJoinConditions = (value: unknown, path: string, errors: string[]): JoinCondition[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): JoinCondition[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    if (item.operator !== undefined && item.operator !== 'EQUALS') {
      errors.push(`${path}[${index}].operator 仅支持 EQUALS`);
    }
    return [{
      leftColumnName: stringValue(item.leftColumnName),
      operator: 'EQUALS',
      rightColumnName: stringValue(item.rightColumnName),
    }];
  });
};

export const parseJoinOutputColumns = (
  value: unknown,
  path: string,
  errors: string[],
): JoinOutputColumn[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): JoinOutputColumn[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    if (item.sourceSide !== 'LEFT' && item.sourceSide !== 'RIGHT') {
      errors.push(`${path}[${index}].sourceSide 仅支持 LEFT 或 RIGHT`);
    }
    if (typeof item.included !== 'boolean') {
      errors.push(`${path}[${index}].included 必须是布尔值`);
    }
    return [{
      sourceSide: item.sourceSide === 'RIGHT' ? 'RIGHT' : 'LEFT',
      sourceColumnName: stringValue(item.sourceColumnName),
      outputColumnName: stringValue(item.outputColumnName),
      included: typeof item.included === 'boolean' ? item.included : true,
    }];
  });
};

export const parseWriteMode = (value: unknown, path: string, errors: string[]): JdbcWriteMode | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'APPEND' || value === 'OVERWRITE' || value === 'UPSERT') return value;
  errors.push(`${path} 不是受支持的写入模式`);
  return null;
};

export const parseFileOutputConflictPolicy = (
  value: unknown,
  path: string,
  errors: string[],
): FileOutputConflictPolicy => {
  if (value === 'FAIL_IF_EXISTS' || value === 'OVERWRITE') return value;
  errors.push(`${path} 仅支持 FAIL_IF_EXISTS 或 OVERWRITE`);
  return 'FAIL_IF_EXISTS';
};

export const parseFileOutputFormatOptions = (
  value: unknown,
  path: string,
  errors: string[],
): FileOutputFormatOptions => {
  const fallback: FileOutputFormatOptions = {
    type: 'CSV',
    header: true,
    delimiter: ',',
    quote: '"',
    escape: '\\',
    nullValue: '',
  };
  if (!isRecord(value)) {
    errors.push(`${path} 必须是文件格式配置对象`);
    return fallback;
  }
  if (value.type === 'CSV') {
    if (typeof value.header !== 'boolean') errors.push(`${path}.header 必须是布尔值`);
    for (const field of ['delimiter', 'quote', 'escape'] as const) {
      if (typeof value[field] !== 'string' || [...value[field]].length !== 1
        || value[field].includes('\r') || value[field].includes('\n')) {
        errors.push(`${path}.${field} 必须是一个非换行字符`);
      }
    }
    if (typeof value.nullValue !== 'string') errors.push(`${path}.nullValue 必须是字符串`);
    return {
      type: 'CSV',
      header: typeof value.header === 'boolean' ? value.header : true,
      delimiter: typeof value.delimiter === 'string' ? value.delimiter : ',',
      quote: typeof value.quote === 'string' ? value.quote : '"',
      escape: typeof value.escape === 'string' ? value.escape : '\\',
      nullValue: typeof value.nullValue === 'string' ? value.nullValue : '',
    };
  }
  if (value.type === 'JSON_LINES') {
    if (typeof value.ignoreNullFields !== 'boolean') {
      errors.push(`${path}.ignoreNullFields 必须是布尔值`);
    }
    return {
      type: 'JSON_LINES',
      ignoreNullFields: typeof value.ignoreNullFields === 'boolean'
        ? value.ignoreNullFields : false,
    };
  }
  if (value.type === 'PARQUET') return { type: 'PARQUET' };
  if (value.type === 'SHAPEFILE') {
    const packageMode = value.packageMode === 'ZIP' || value.packageMode === 'COMPONENT_DIRECTORY'
      ? value.packageMode : 'ZIP';
    if (value.packageMode !== 'ZIP' && value.packageMode !== 'COMPONENT_DIRECTORY') {
      errors.push(`${path}.packageMode 仅支持 ZIP 或 COMPONENT_DIRECTORY`);
    }
    const targetShapeType = value.targetShapeType === 'POINT'
      || value.targetShapeType === 'MULTIPOINT'
      || value.targetShapeType === 'POLYLINE'
      || value.targetShapeType === 'POLYGON'
      ? value.targetShapeType : 'POINT';
    if (!['POINT', 'MULTIPOINT', 'POLYLINE', 'POLYGON'].includes(String(value.targetShapeType))) {
      errors.push(`${path}.targetShapeType 不是受支持的 Shape 类型`);
    }
    if (typeof value.baseName !== 'string') errors.push(`${path}.baseName 必须是字符串`);
    if (typeof value.geometryColumnName !== 'string') {
      errors.push(`${path}.geometryColumnName 必须是字符串`);
    }
    const attributeMappings = Array.isArray(value.attributeMappings)
      ? value.attributeMappings.flatMap((item, index) => {
        if (!isRecord(item)) {
          errors.push(`${path}.attributeMappings[${index}] 必须是对象`);
          return [];
        }
        if (typeof item.sourceColumnName !== 'string') {
          errors.push(`${path}.attributeMappings[${index}].sourceColumnName 必须是字符串`);
        }
        if (typeof item.targetFieldName !== 'string') {
          errors.push(`${path}.attributeMappings[${index}].targetFieldName 必须是字符串`);
        }
        if (item.targetStringByteLength !== null
            && !Number.isInteger(item.targetStringByteLength)) {
          errors.push(
            `${path}.attributeMappings[${index}].targetStringByteLength 必须是 null 或整数`,
          );
        }
        return [{
          sourceColumnName: stringValue(item.sourceColumnName),
          targetFieldName: stringValue(item.targetFieldName),
          targetStringByteLength: Number.isInteger(item.targetStringByteLength)
            ? Number(item.targetStringByteLength) : null,
        }];
      })
      : [];
    if (!Array.isArray(value.attributeMappings)) {
      errors.push(`${path}.attributeMappings 必须是数组`);
    }
    return {
      type: 'SHAPEFILE',
      baseName: stringValue(value.baseName),
      packageMode,
      geometryColumnName: stringValue(value.geometryColumnName),
      targetShapeType,
      attributeMappings,
    };
  }
  if (value.type === 'GEOPARQUET') {
    const compression = value.compression === 'SNAPPY' || value.compression === 'ZSTD'
      ? value.compression : 'SNAPPY';
    const coveringMode = value.coveringMode === 'NONE' || value.coveringMode === 'ROW_BBOX'
      ? value.coveringMode : 'ROW_BBOX';
    if (typeof value.geometryColumnName !== 'string') {
      errors.push(`${path}.geometryColumnName 必须是字符串`);
    }
    if (value.compression !== 'SNAPPY' && value.compression !== 'ZSTD') {
      errors.push(`${path}.compression 仅支持 SNAPPY 或 ZSTD`);
    }
    if (value.coveringMode !== 'NONE' && value.coveringMode !== 'ROW_BBOX') {
      errors.push(`${path}.coveringMode 仅支持 NONE 或 ROW_BBOX`);
    }
    return {
      type: 'GEOPARQUET',
      geometryColumnName: stringValue(value.geometryColumnName),
      compression,
      coveringMode,
    };
  }
  if (value.type === 'GEOJSON') {
    if (typeof value.baseName !== 'string') errors.push(`${path}.baseName 必须是字符串`);
    if (typeof value.geometryColumnName !== 'string') {
      errors.push(`${path}.geometryColumnName 必须是字符串`);
    }
    if (value.idColumnName !== null && typeof value.idColumnName !== 'string') {
      errors.push(`${path}.idColumnName 必须是 null 或字符串`);
    }
    if (typeof value.ignoreNullProperties !== 'boolean') {
      errors.push(`${path}.ignoreNullProperties 必须是布尔值`);
    }
    return {
      type: 'GEOJSON',
      baseName: stringValue(value.baseName),
      geometryColumnName: stringValue(value.geometryColumnName),
      idColumnName: value.idColumnName === null ? null : stringValue(value.idColumnName),
      ignoreNullProperties: typeof value.ignoreNullProperties === 'boolean'
        ? value.ignoreNullProperties : false,
    };
  }
  errors.push(
    `${path}.type 仅支持 CSV、JSON_LINES、PARQUET、SHAPEFILE、GEOPARQUET 或 GEOJSON`,
  );
  return fallback;
};

export const parseMappings = (value: unknown, path: string, errors: string[]): CanvasColumnMapping[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): CanvasColumnMapping[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    return [{
      sourceColumnName: stringValue(item.sourceColumnName),
      targetColumnName: stringValue(item.targetColumnName),
    }];
  });
};

export const parseStringArray = (value: unknown, path: string, errors: string[]): string[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.map((item, index) => {
    if (typeof item !== 'string') {
      errors.push(`${path}[${index}] 必须是字符串`);
      return '';
    }
    return item;
  });
};

export const parseRuntimeParameters = (
  value: unknown,
  path: string,
  errors: string[],
): HttpApiRuntimeParameter[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): HttpApiRuntimeParameter[] => {
    if (!isRecord(item)) {
      errors.push(`${path}[${index}] 必须是对象`);
      return [];
    }
    const name = stringValue(item.name);
    const parameterValue = stringValue(item.value);
    if (name && !/^[A-Za-z][A-Za-z0-9_.-]{0,127}$/.test(name)) {
      errors.push(`${path}[${index}].name 无效`);
    }
    if (isSensitiveRuntimeParameterName(name)) {
      errors.push(`${path}[${index}].name 不允许用于密码、Token、密钥或签名`);
    }
    return [{ name, value: parameterValue }];
  });
};

const platformDataTypes = new Set([
  'BOOLEAN',
  'BYTE',
  'SHORT',
  'INTEGER',
  'LONG',
  'FLOAT',
  'DOUBLE',
  'DECIMAL',
  'STRING',
  'BINARY',
  'DATE',
  'TIMESTAMP',
  'TIMESTAMP_NTZ',
]);

const filterLiteralDataTypes = new Set([...platformDataTypes, 'GEOMETRY']);

const filterOperators = new Set<FilterOperator>([
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

const deriveBinaryOperators = new Set<DeriveBinaryOperator>([
  'ADD',
  'SUBTRACT',
  'MULTIPLY',
  'DIVIDE',
  'MODULO',
]);

const deriveFunctions = new Set<DeriveFunction>([
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
const canvasRuntimeValues = new Set<CanvasRuntimeValue>([
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

const parseCanvasExpression = (
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

const optionalInteger = (value: unknown, path: string, errors: string[]) => {
  if (value === null || value === undefined) return null;
  if (typeof value !== 'number' || !Number.isInteger(value)) {
    errors.push(`${path} 必须是整数或 null`);
    return null;
  }
  return value;
};

export const parsePlatformTypeDefinition = (
  value: unknown,
  path: string,
  errors: string[],
): PlatformTypeDefinition => {
  if (!isRecord(value)) {
    errors.push(`${path} 必须是平台类型对象`);
    return {
      type: 'STRING',
      length: null,
      precision: null,
      scale: null,
      geometry: null,
    };
  }
  const rawType = stringValue(value.type);
  if (!platformDataTypes.has(rawType)) {
    errors.push(`${path}.type 不是 Canvas 支持的平台标量类型`);
  }
  const type = platformDataTypes.has(rawType)
    ? rawType as PlatformTypeDefinition['type']
    : 'STRING';
  const length = optionalInteger(value.length, `${path}.length`, errors);
  const precision = optionalInteger(value.precision, `${path}.precision`, errors);
  const scale = optionalInteger(value.scale, `${path}.scale`, errors);
  if (type === 'STRING') {
    if (length !== null && length < 1) errors.push(`${path}.length 必须为正整数`);
    if (precision !== null || scale !== null) {
      errors.push(`${path} 的 STRING 不能配置 precision/scale`);
    }
  } else if (type === 'DECIMAL') {
    if (length !== null) errors.push(`${path} 的 DECIMAL 不能配置 length`);
    if (precision === null || precision < 1 || precision > 38) {
      errors.push(`${path}.precision 必须在 1..38`);
    }
    if (scale === null || scale < 0 || (precision !== null && scale > precision)) {
      errors.push(`${path}.scale 必须在 0..precision`);
    }
  } else if (length !== null || precision !== null || scale !== null) {
    errors.push(`${path} 的 ${type} 不能配置 length/precision/scale`);
  }
  if (value.geometry !== undefined && value.geometry !== null) {
    errors.push(`${path}.geometry 在 Canvas 标量处理器中不受支持`);
  }
  return {
    type,
    length: type === 'STRING' ? length : null,
    precision: type === 'DECIMAL' ? precision : null,
    scale: type === 'DECIMAL' ? scale : null,
    geometry: null,
  };
};

export const parseTypeCasts = (
  value: unknown,
  path: string,
  errors: string[],
): ColumnTypeCast[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): ColumnTypeCast[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const failureStrategy = item.failureStrategy === 'FAIL'
      || item.failureStrategy === 'SET_NULL'
      ? item.failureStrategy as CastFailureStrategy
      : null;
    if (item.failureStrategy !== undefined
      && item.failureStrategy !== null
      && failureStrategy === null) {
      errors.push(`${itemPath}.failureStrategy 仅支持 FAIL 或 SET_NULL`);
    }
    return [{
      columnName: stringValue(item.columnName),
      targetType: parsePlatformTypeDefinition(
        item.targetType,
        `${itemPath}.targetType`,
        errors,
      ),
      failureStrategy,
    }];
  });
};

const aggregateFunctions = new Set<AggregateFunction>([
  'COUNT',
  'SUM',
  'AVG',
  'MIN',
  'MAX',
]);

export const parseAggregations = (
  value: unknown,
  path: string,
  errors: string[],
): AggregateItem[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): AggregateItem[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const rawFunction = stringValue(item.function);
    const aggregateFunction = aggregateFunctions.has(rawFunction as AggregateFunction)
      ? rawFunction as AggregateFunction
      : null;
    if (item.function !== undefined
      && item.function !== null
      && aggregateFunction === null) {
      errors.push(`${itemPath}.function 不是受支持的聚合函数`);
    }
    if (item.sourceColumnName !== undefined
      && item.sourceColumnName !== null
      && typeof item.sourceColumnName !== 'string') {
      errors.push(`${itemPath}.sourceColumnName 必须是字符串或 null`);
    }
    if (item.distinct !== undefined && typeof item.distinct !== 'boolean') {
      errors.push(`${itemPath}.distinct 必须是布尔值`);
    }
    return [{
      function: aggregateFunction,
      sourceColumnName: typeof item.sourceColumnName === 'string'
        ? item.sourceColumnName
        : null,
      outputColumnName: stringValue(item.outputColumnName),
      distinct: item.distinct === true,
    }];
  });
};

export const parseUnionMode = (
  value: unknown,
  path: string,
  errors: string[],
): UnionMode | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'ALL' || value === 'DISTINCT') return value;
  errors.push(`${path} 仅支持 ALL 或 DISTINCT`);
  return null;
};

export const parseDeduplicateKeepStrategy = (
  value: unknown,
  path: string,
  errors: string[],
): DeduplicateKeepStrategy | null => {
  if (value === null || value === undefined || value === '') return null;
  if (value === 'ANY' || value === 'FIRST' || value === 'LAST') return value;
  errors.push(`${path} 仅支持 ANY、FIRST 或 LAST`);
  return null;
};

export const parseSortFields = (
  value: unknown,
  path: string,
  errors: string[],
): SortField[] => {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    errors.push(`${path} 必须是数组`);
    return [];
  }
  return value.flatMap((item, index): SortField[] => {
    const itemPath = `${path}[${index}]`;
    if (!isRecord(item)) {
      errors.push(`${itemPath} 必须是对象`);
      return [];
    }
    const direction: SortDirection | null = item.direction === 'ASC'
      || item.direction === 'DESC'
      ? item.direction
      : null;
    const nullOrdering: NullOrdering | null = item.nullOrdering === 'FIRST'
      || item.nullOrdering === 'LAST'
      ? item.nullOrdering
      : null;
    if (item.direction !== undefined && item.direction !== null && direction === null) {
      errors.push(`${itemPath}.direction 仅支持 ASC 或 DESC`);
    }
    if (item.nullOrdering !== undefined
      && item.nullOrdering !== null
      && nullOrdering === null) {
      errors.push(`${itemPath}.nullOrdering 仅支持 FIRST 或 LAST`);
    }
    return [{
      columnName: stringValue(item.columnName),
      direction,
      nullOrdering,
    }];
  });
};

export const parseKafkaValueSchema = (
  value: unknown,
  path: string,
  errors: string[],
): KafkaValueSchema => {
  if (value === undefined || value === null) return { columns: [] };
  if (!isRecord(value) || !Array.isArray(value.columns)) {
    errors.push(`${path}.columns 必须是数组`);
    return { columns: [] };
  }
  return {
    columns: value.columns.flatMap((item, index) => {
      const columnPath = `${path}.columns[${index}]`;
      if (!isRecord(item)) {
        errors.push(`${columnPath} 必须是对象`);
        return [];
      }
      const fieldType = stringValue(item.fieldType);
      if (!platformDataTypes.has(fieldType)) {
        errors.push(`${columnPath}.fieldType 不是受支持的平台类型`);
      }
      if (typeof item.nullable !== 'boolean') {
        errors.push(`${columnPath}.nullable 必须是 Boolean`);
      }
      if (item.comment !== null && item.comment !== undefined && typeof item.comment !== 'string') {
        errors.push(`${columnPath}.comment 必须是字符串或 null`);
      }
      return [{
        name: stringValue(item.name),
        fieldType: platformDataTypes.has(fieldType)
          ? fieldType as KafkaValueColumn['fieldType']
          : 'STRING',
        length: optionalInteger(item.length, `${columnPath}.length`, errors),
        precision: optionalInteger(item.precision, `${columnPath}.precision`, errors),
        scale: optionalInteger(item.scale, `${columnPath}.scale`, errors),
        nullable: typeof item.nullable === 'boolean' ? item.nullable : true,
        comment: typeof item.comment === 'string' ? item.comment : null,
      }];
    }),
  };
};

const supportedNodeTypes = new Set<string>(Object.values(CanvasNodeType));

const parseNode = (value: unknown, index: number, errors: string[]): CanvasNodeDefinition | null => {
  const path = `nodes[${index}]`;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象`);
    return null;
  }
  const id = stringValue(value.id);
  if (!uuidPattern.test(id)) errors.push(`${path}.id 必须是 UUID`);
  if (typeof value.name !== 'string') errors.push(`${path}.name 必须是字符串`);
  const name = stringValue(value.name);
  const layout = parseLayout(value.layout, `${path}.layout`, errors);
  if (!layout) return null;

  const rawType = stringValue(value.type);
  if (!supportedNodeTypes.has(rawType)) {
    errors.push(`${path}.type 不是受支持的节点类型`);
    return null;
  }
  const type = rawType as CanvasNodeTypeValue;
  const parsedConfiguration = canvasNodeRegistry.require(type).parseConfiguration(
    value.configuration,
    `${path}.configuration`,
  );
  if (!parsedConfiguration.success) {
    errors.push(...parsedConfiguration.errors);
    return null;
  }

  return {
    id,
    type,
    name,
    layout,
    configuration: parsedConfiguration.value,
  } as CanvasNodeDefinition;
};

const parseEdge = (value: unknown, index: number, errors: string[]): CanvasEdgeDefinition | null => {
  const path = `edges[${index}]`;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象`);
    return null;
  }
  const id = stringValue(value.id);
  const sourceNodeId = stringValue(value.sourceNodeId);
  const targetNodeId = stringValue(value.targetNodeId);
  if (!uuidPattern.test(id)) errors.push(`${path}.id 必须是 UUID`);
  if (!uuidPattern.test(sourceNodeId)) errors.push(`${path}.sourceNodeId 必须是 UUID`);
  if (!uuidPattern.test(targetNodeId)) errors.push(`${path}.targetNodeId 必须是 UUID`);
  return { id, sourceNodeId, targetNodeId };
};

export const parseCanvasDefinition = (value: unknown): CanvasDefinitionParseResult => {
  const errors: string[] = [];
  if (!isRecord(value)) return { success: false, errors: ['Canvas 定义必须是 JSON 对象'] };
  if (value.schemaVersion !== CANVAS_SCHEMA_VERSION) {
    errors.push(`schemaVersion 仅支持 ${CANVAS_SCHEMA_VERSION}`);
  }
  const sourceSchemaMinorVersion = value.schemaMinorVersion === undefined
    ? CANVAS_LEGACY_SCHEMA_MINOR_VERSION
    : value.schemaMinorVersion;
  if (typeof sourceSchemaMinorVersion !== 'number'
      || !Number.isInteger(sourceSchemaMinorVersion)
      || sourceSchemaMinorVersion < CANVAS_LEGACY_SCHEMA_MINOR_VERSION
      || sourceSchemaMinorVersion > CANVAS_SCHEMA_MINOR_VERSION) {
    errors.push(
      `schemaMinorVersion 仅支持 ${CANVAS_LEGACY_SCHEMA_MINOR_VERSION} 到 ${CANVAS_SCHEMA_MINOR_VERSION}`,
    );
  }
  if (!Array.isArray(value.nodes)) errors.push('nodes 必须是数组');
  if (!Array.isArray(value.edges)) errors.push('edges 必须是数组');
  if (errors.length > 0) return { success: false, errors };

  const nodes = (value.nodes as unknown[]).flatMap((item, index): CanvasNodeDefinition[] => {
    const parsed = parseNode(item, index, errors);
    return parsed ? [parsed] : [];
  });
  const edges = (value.edges as unknown[]).flatMap((item, index): CanvasEdgeDefinition[] => {
    const parsed = parseEdge(item, index, errors);
    return parsed ? [parsed] : [];
  });

  if (typeof sourceSchemaMinorVersion === 'number') {
    nodes.forEach((node) => {
      const spec = canvasNodeRegistry.require(node.type);
      if (sourceSchemaMinorVersion < spec.introducedInMinor) {
        errors.push(
          `${spec.type} 从 Canvas ${CANVAS_SCHEMA_VERSION}.${spec.introducedInMinor} 开始支持`,
        );
      }
      if (sourceSchemaMinorVersion < 1
          && node.type === CanvasNodeType.JdbcInput
          && node.configuration.tables.some((table) => table.readOptions.length > 0)) {
        errors.push(`JDBC_INPUT.readOptions 从 Canvas ${CANVAS_SCHEMA_VERSION}.1 开始支持`);
      }
    });
  }

  const nodeIds = new Set<string>();
  nodes.forEach((node) => {
    if (nodeIds.has(node.id)) errors.push(`节点 ID ${node.id} 重复`);
    nodeIds.add(node.id);
  });
  const edgeIds = new Set<string>();
  edges.forEach((edge) => {
    if (edgeIds.has(edge.id)) errors.push(`连线 ID ${edge.id} 重复`);
    edgeIds.add(edge.id);
    if (!nodeIds.has(edge.sourceNodeId) || !nodeIds.has(edge.targetNodeId)) {
      errors.push(`连线 ${edge.id} 引用了不存在的节点`);
    }
  });

  return errors.length > 0
    ? { success: false, errors }
    : {
      success: true,
      definition: {
        schemaVersion: CANVAS_SCHEMA_VERSION,
        schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
        nodes,
        edges,
      },
    };
};

export const parseCanvasDefinitionJson = (content: string): CanvasDefinitionParseResult => {
  try {
    return parseCanvasDefinition(JSON.parse(content) as unknown);
  } catch (error) {
    return {
      success: false,
      errors: [`JSON 解析失败：${error instanceof Error ? error.message : '未知错误'}`],
    };
  }
};

export const formatCanvasDefinition = (definition: CanvasDefinition) => JSON.stringify(definition, null, 2);

export const CANVAS_DEFINITION_FILE_NAME = 'canvas-task-definition.json';

export const downloadCanvasDefinition = (definition: CanvasDefinition) => {
  const blob = new Blob([formatCanvasDefinition(definition)], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = CANVAS_DEFINITION_FILE_NAME;
  anchor.click();
  URL.revokeObjectURL(url);
};
