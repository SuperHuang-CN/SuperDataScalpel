import { usesExplicitWithinStatistics, requiresWithinWeightedDispersionVersion } from './nodes/spatialSummarizeWithin/statisticOptions';
import { unsupportedSpatialUnitPaths, unsupportedSpatialDurationPaths } from './nodes/spatialUnits';
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
  type EpochTimestampUnit,
  type StringTemporalParseOptions,
  type StringTimestampZoneMode,
  type TemporalStringFormatOptions,
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
import { isValidIanaZoneId } from '../model/taskScheduleValidation';

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
    const epochTimestampUnit = item.epochTimestampUnit === 'SECONDS'
      || item.epochTimestampUnit === 'MILLISECONDS'
      || item.epochTimestampUnit === 'MICROSECONDS'
      ? item.epochTimestampUnit as EpochTimestampUnit
      : item.epochTimestampUnit === null ? null : undefined;
    if (item.epochTimestampUnit !== undefined && epochTimestampUnit === undefined) {
      errors.push(`${itemPath}.epochTimestampUnit 仅支持 SECONDS、MILLISECONDS 或 MICROSECONDS`);
    }
    const targetType = parsePlatformTypeDefinition(
      item.targetType,
      `${itemPath}.targetType`,
      errors,
    );
    const stringTemporalParseOptions = parseStringTemporalParseOptions(
      item.stringTemporalParseOptions,
      `${itemPath}.stringTemporalParseOptions`,
      errors,
    );
    validateStringTemporalParseOptions(
      stringTemporalParseOptions,
      targetType,
      epochTimestampUnit,
      `${itemPath}.stringTemporalParseOptions`,
      errors,
    );
    const temporalStringFormatOptions = parseTemporalStringFormatOptions(
      item.temporalStringFormatOptions,
      `${itemPath}.temporalStringFormatOptions`,
      errors,
    );
    validateTemporalStringFormatOptions(
      temporalStringFormatOptions,
      targetType,
      epochTimestampUnit,
      stringTemporalParseOptions,
      `${itemPath}.temporalStringFormatOptions`,
      errors,
    );
    return [{
      columnName: stringValue(item.columnName),
      targetType,
      failureStrategy,
      ...(item.epochTimestampUnit === undefined ? {} : { epochTimestampUnit }),
      ...(item.stringTemporalParseOptions === undefined ? {} : { stringTemporalParseOptions }),
      ...(item.temporalStringFormatOptions === undefined ? {} : { temporalStringFormatOptions }),
    }];
  });
};

const parseTemporalStringFormatOptions = (
  value: unknown,
  path: string,
  errors: string[],
): TemporalStringFormatOptions | null | undefined => {
  if (value === undefined) return undefined;
  if (value === null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return undefined;
  }
  if (typeof value.pattern !== 'string') errors.push(`${path}.pattern 必须是字符串`);
  const targetTimeZone = value.targetTimeZone === null || value.targetTimeZone === undefined
    ? null
    : typeof value.targetTimeZone === 'string' ? value.targetTimeZone : undefined;
  if (targetTimeZone === undefined) errors.push(`${path}.targetTimeZone 必须是字符串或 null`);
  return {
    pattern: stringValue(value.pattern),
    targetTimeZone: targetTimeZone ?? null,
  };
};

const validateTemporalStringFormatOptions = (
  options: TemporalStringFormatOptions | null | undefined,
  targetType: PlatformTypeDefinition,
  epochTimestampUnit: EpochTimestampUnit | null | undefined,
  parseOptions: StringTemporalParseOptions | null | undefined,
  path: string,
  errors: string[],
) => {
  if (options == null) return;
  if (epochTimestampUnit != null || parseOptions != null) {
    errors.push(`${path} 不能与其他特殊时间转换配置同时使用`);
  }
  if (targetType.type !== 'STRING') {
    errors.push(`${path} 仅支持 DATE、TIMESTAMP 或 TIMESTAMP_NTZ 转换为 STRING`);
  }
  if (!options.pattern.trim()) {
    errors.push(`${path}.pattern 不能为空`);
  } else if (options.pattern.length > 128) {
    errors.push(`${path}.pattern 不能超过 128 个字符`);
  } else if (containsUnquotedZonePatternSymbol(options.pattern)) {
    errors.push(`${path}.pattern 不能包含时区或偏移符号`);
  }
  if (options.targetTimeZone !== null) {
    if (!options.targetTimeZone.trim()) {
      errors.push(`${path}.targetTimeZone 不能为空字符串`);
    } else if (options.targetTimeZone.length > 64) {
      errors.push(`${path}.targetTimeZone 不能超过 64 个字符`);
    } else if (!isValidIanaZoneId(options.targetTimeZone)) {
      errors.push(`${path}.targetTimeZone 必须是有效的 IANA Zone ID`);
    }
  }
};

const parseStringTemporalParseOptions = (
  value: unknown,
  path: string,
  errors: string[],
): StringTemporalParseOptions | null | undefined => {
  if (value === undefined) return undefined;
  if (value === null) return null;
  if (!isRecord(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return undefined;
  }
  if (typeof value.pattern !== 'string') errors.push(`${path}.pattern 必须是字符串`);
  const zoneMode = value.zoneMode === 'SOURCE_TIME_ZONE' || value.zoneMode === 'EMBEDDED_OFFSET'
    ? value.zoneMode as StringTimestampZoneMode
    : value.zoneMode === null || value.zoneMode === undefined ? null : undefined;
  if (zoneMode === undefined) {
    errors.push(`${path}.zoneMode 仅支持 SOURCE_TIME_ZONE 或 EMBEDDED_OFFSET`);
  }
  const sourceTimeZone = value.sourceTimeZone === null || value.sourceTimeZone === undefined
    ? null
    : typeof value.sourceTimeZone === 'string' ? value.sourceTimeZone : undefined;
  if (sourceTimeZone === undefined) errors.push(`${path}.sourceTimeZone 必须是字符串或 null`);
  return {
    pattern: stringValue(value.pattern),
    zoneMode: zoneMode ?? null,
    sourceTimeZone: sourceTimeZone ?? null,
  };
};

const validateStringTemporalParseOptions = (
  options: StringTemporalParseOptions | null | undefined,
  targetType: PlatformTypeDefinition,
  epochTimestampUnit: EpochTimestampUnit | null | undefined,
  path: string,
  errors: string[],
) => {
  if (options == null) return;
  if (epochTimestampUnit != null) {
    errors.push(`${path} 不能与 epochTimestampUnit 同时配置`);
  }
  if (targetType.type !== 'DATE' && targetType.type !== 'TIMESTAMP') {
    errors.push(`${path} 仅支持 STRING 转换为 DATE 或 TIMESTAMP`);
    return;
  }
  if (!options.pattern.trim()) {
    errors.push(`${path}.pattern 不能为空`);
  } else if (options.pattern.length > 128) {
    errors.push(`${path}.pattern 不能超过 128 个字符`);
  }
  if (targetType.type === 'DATE') {
    if (options.zoneMode !== null || options.sourceTimeZone !== null) {
      errors.push(`${path} 的 DATE 解析不能配置时区`);
    }
    return;
  }
  if (options.zoneMode === null) {
    errors.push(`${path}.zoneMode 不能为空`);
    return;
  }
  const patternContainsZone = containsUnquotedZonePatternSymbol(options.pattern);
  if (options.zoneMode === 'SOURCE_TIME_ZONE') {
    if ((options.sourceTimeZone?.length ?? 0) > 64) {
      errors.push(`${path}.sourceTimeZone 不能超过 64 个字符`);
    } else if (!isValidIanaZoneId(options.sourceTimeZone ?? '')) {
      errors.push(`${path}.sourceTimeZone 必须是有效的 IANA Zone ID`);
    }
    if (patternContainsZone) {
      errors.push(`${path}.pattern 在指定来源时区模式下不能包含时区或偏移符号`);
    }
    return;
  }
  if (options.sourceTimeZone !== null) {
    errors.push(`${path}.sourceTimeZone 在字符串自带偏移模式下必须为空`);
  }
  if (!patternContainsZone) {
    errors.push(`${path}.pattern 在字符串自带偏移模式下必须包含时区或偏移符号`);
  }
};

const containsUnquotedZonePatternSymbol = (pattern: string) => {
  let quoted = false;
  for (let index = 0; index < pattern.length; index += 1) {
    const symbol = pattern[index];
    if (symbol === "'") {
      if (quoted && pattern[index + 1] === "'") {
        index += 1;
      } else {
        quoted = !quoted;
      }
      continue;
    }
    if (!quoted && 'XxZOVz'.includes(symbol)) return true;
  }
  return false;
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
    nodes.forEach((node, index) => {
      const spec = canvasNodeRegistry.require(node.type);
      if (sourceSchemaMinorVersion < 41 && node.type === CanvasNodeType.SpatialSummarizeWithin && node.configuration.regions != null)
        errors.push(`SPATIAL_WITHIN_REGIONS_REQUIRE_SCHEMA_VERSION：nodes[${index}].configuration.regions 从 Canvas 4.41 开始支持`);
      if (sourceSchemaMinorVersion < 40 && node.type === CanvasNodeType.SpatialPointCluster && node.configuration.dbscan != null)
        errors.push(`SPATIAL_DBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION：nodes[${index}].configuration.dbscan 从 Canvas 4.40 开始支持`);
      if (sourceSchemaMinorVersion < 39 && (node.type === CanvasNodeType.SpatialBinAggregate || node.type === CanvasNodeType.SpatialSummarizeWithin)
          && node.configuration.temporalSlicing?.calendar != null) {
        errors.push(`SPATIAL_CALENDAR_WINDOW_REQUIRE_SCHEMA_VERSION：nodes[${index}].configuration.temporalSlicing.calendar 从 Canvas 4.39 开始支持`);
      }
      if (sourceSchemaMinorVersion < 38 && node.type === CanvasNodeType.SpatialBinAggregate && node.configuration.planarGrid != null) {
        errors.push(`SPATIAL_PLANAR_GRID_REQUIRE_SCHEMA_VERSION：nodes[${index}].configuration.planarGrid 从 Canvas 4.38 开始支持`);
      }
      if (sourceSchemaMinorVersion < 37 && node.type === CanvasNodeType.SpatialSummarizeWithin) {
        node.configuration.statistics.forEach((item, statisticIndex) => {
          if (requiresWithinWeightedDispersionVersion(item)) errors.push(`SPATIAL_WITHIN_WEIGHTED_DISPERSION_REQUIRE_SCHEMA_VERSION：nodes[${index}].configuration.statistics[${statisticIndex}].weighting 从 Canvas 4.37 开始支持`);
        });
      }
      for (const path of unsupportedSpatialUnitPaths(node, sourceSchemaMinorVersion)) {
        errors.push(`SPATIAL_EXTENDED_UNITS_REQUIRE_SCHEMA_VERSION：nodes[${index}].${path} 的扩展单位从 Canvas 4.36 开始支持`);
      }
      for (const path of unsupportedSpatialDurationPaths(node, sourceSchemaMinorVersion)) {
        errors.push(`SPATIAL_DURATION_WEEKS_REQUIRE_SCHEMA_VERSION：nodes[${index}].${path} 的固定周单位从 Canvas 4.47 开始支持`);
      }
      if (sourceSchemaMinorVersion < 35 && (node.type === CanvasNodeType.TrackReconstruct || node.type === CanvasNodeType.TrackFindDwell)
          && node.configuration.summaryStatistics.some(s => s.kind === 'COUNT_FIELD' || s.kind === 'ANY'))
        errors.push('TRACK_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION：轨迹字段 Count/Any 从 Canvas 4.35 开始支持');
      if (sourceSchemaMinorVersion < 34 && node.type === CanvasNodeType.SpatialBinAggregate
          && node.configuration.statistics.some(s => s.kind === 'COUNT_FIELD' || s.kind === 'ANY'))
        errors.push('SPATIAL_BIN_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION：格网字段 Count/Any 从 Canvas 4.34 开始支持');
      if (sourceSchemaMinorVersion < 33 && node.type === CanvasNodeType.SpatialBinAggregate
          && (node.configuration.binShape === 'H3' || node.configuration.h3 != null))
        errors.push('SPATIAL_H3_REQUIRE_SCHEMA_VERSION：H3 格网从 Canvas 4.33 开始支持');
      if (sourceSchemaMinorVersion < 32 && node.type === CanvasNodeType.SpatialCenterDispersion
          && node.configuration.analyses.some(a => a.centralFeatureColumns != null))
        errors.push('SPATIAL_CENTER_PROJECTION_REQUIRE_SCHEMA_VERSION：中央要素字段投影从 Canvas 4.32 开始支持');
      if (sourceSchemaMinorVersion < 31 && node.type === CanvasNodeType.SpatialCenterDispersion
          && (node.configuration.resultMode != null || node.configuration.analyses.some(a => a.outputTableName != null)))
        errors.push('SPATIAL_CENTER_RESULTS_REQUIRE_SCHEMA_VERSION：中心独立结果配置从 Canvas 4.31 开始支持');
      if (sourceSchemaMinorVersion < 30 && node.type === CanvasNodeType.SpatialNearest && node.configuration.matching != null) {
        errors.push('SPATIAL_NEAREST_MATCHING_REQUIRE_SCHEMA_VERSION：显式最近邻匹配策略从 Canvas 4.30 开始支持');
      }
      if (sourceSchemaMinorVersion < 48 && node.type === CanvasNodeType.SpatialNearest
          && node.configuration.matching?.geodesicGeometryMode === 'GEOMETRY') {
        errors.push('SPATIAL_NEAREST_GEODESIC_GEOMETRY_REQUIRE_SCHEMA_VERSION：非点 WGS84 真实最近位置从 Canvas 4.48 开始支持');
      }
      if (sourceSchemaMinorVersion < 49 && node.type === CanvasNodeType.GeometryBuffer
          && node.configuration.distanceUnit != null) {
        errors.push('GEOMETRY_BUFFER_UNIT_REQUIRE_SCHEMA_VERSION：Geometry Buffer 显式距离单位从 Canvas 4.49 开始支持');
      }
      if (sourceSchemaMinorVersion < 52 && node.type === CanvasNodeType.GeometryBuffer
          && (node.configuration.distanceSource != null
          || node.configuration.distanceFieldName != null
          || node.configuration.distanceExpression != null)) {
        errors.push('GEOMETRY_BUFFER_DISTANCE_SOURCE_REQUIRE_SCHEMA_VERSION：Geometry Buffer 逐行距离来源从 Canvas 4.52 开始支持');
      }
      if (sourceSchemaMinorVersion < 53 && node.type === CanvasNodeType.SpatialAggregate
          && node.configuration.dissolve != null) {
        errors.push('SPATIAL_AGGREGATE_DISSOLVE_REQUIRE_SCHEMA_VERSION：空间聚合 Dissolve 选项从 Canvas 4.53 开始支持');
      }
      if (sourceSchemaMinorVersion < 61 && node.type === CanvasNodeType.SpatialAggregate
          && node.configuration.dissolve?.groupingMode != null) {
        errors.push('SPATIAL_DISSOLVE_GROUPING_MODE_REQUIRE_SCHEMA_VERSION：空间聚合 Dissolve 分组方式从 Canvas 4.61 开始支持');
      }
      if (sourceSchemaMinorVersion < 62 && node.type === CanvasNodeType.Union
          && node.configuration.mergingTables != null) {
        errors.push('UNION_MERGE_LAYERS_REQUIRE_SCHEMA_VERSION：Union 的 Merge Layers 字段处理从 Canvas 4.62 开始支持');
      }
      if (sourceSchemaMinorVersion < 54 && node.type === CanvasNodeType.SpatialJoin
          && node.configuration.outputColumns != null) {
        errors.push('SPATIAL_JOIN_OUTPUT_COLUMNS_REQUIRE_SCHEMA_VERSION：空间连接输出字段投影从 Canvas 4.54 开始支持');
      }
      if (sourceSchemaMinorVersion < 55 && node.type === CanvasNodeType.SpatialJoin
          && node.configuration.attributeConditions != null) {
        errors.push('SPATIAL_JOIN_ATTRIBUTE_CONDITIONS_REQUIRE_SCHEMA_VERSION：空间连接属性匹配条件从 Canvas 4.55 开始支持');
      }
      if (sourceSchemaMinorVersion < 56 && node.type === CanvasNodeType.SpatialJoin
          && node.configuration.joinType === 'LEFT') {
        errors.push('SPATIAL_JOIN_KEEP_ALL_REQUIRE_SCHEMA_VERSION：空间连接保留全部目标要素从 Canvas 4.56 开始支持');
      }
      if (sourceSchemaMinorVersion < 57 && node.type === CanvasNodeType.SpatialJoin
          && node.configuration.joinOperation != null) {
        errors.push('SPATIAL_JOIN_OPERATION_REQUIRE_SCHEMA_VERSION：空间连接显式结果粒度从 Canvas 4.57 开始支持');
      }
      if (sourceSchemaMinorVersion < 58 && node.type === CanvasNodeType.SpatialJoin
          && (node.configuration.joinOperation === 'JOIN_ONE_TO_ONE'
          || node.configuration.oneToOne != null)) {
        errors.push('SPATIAL_JOIN_ONE_TO_ONE_REQUIRE_SCHEMA_VERSION：空间连接一对一规则从 Canvas 4.58 开始支持');
      }
      if (sourceSchemaMinorVersion < 59 && node.type === CanvasNodeType.SpatialJoin
          && node.configuration.temporalCondition != null) {
        errors.push('SPATIAL_JOIN_TEMPORAL_CONDITION_REQUIRE_SCHEMA_VERSION：空间连接时间关系从 Canvas 4.59 开始支持');
      }
      if (sourceSchemaMinorVersion < 60 && node.type === CanvasNodeType.SpatialJoin
          && (node.configuration.spatialNear != null
          || node.configuration.distanceOutput != null)) {
        errors.push('SPATIAL_JOIN_NEAR_REQUIRE_SCHEMA_VERSION：空间连接 Near 和距离输出从 Canvas 4.60 开始支持');
      }
      if (sourceSchemaMinorVersion < 50 && node.type === CanvasNodeType.SpatialMeasure
          && node.configuration.measurements.some((measurement) => (
            'outputUnit' in measurement && measurement.outputUnit != null
          ))) {
        errors.push('SPATIAL_MEASURE_UNIT_REQUIRE_SCHEMA_VERSION：空间测量显式输出单位从 Canvas 4.50 开始支持');
      }
      if (sourceSchemaMinorVersion < 51 && node.type === CanvasNodeType.SpatialClip
          && node.configuration.geometryPolicy != null) {
        errors.push('SPATIAL_CLIP_GEOMETRY_POLICY_REQUIRE_SCHEMA_VERSION：空间裁剪显式几何策略从 Canvas 4.51 开始支持');
      }
      if (sourceSchemaMinorVersion < 77 && node.type === CanvasNodeType.SpatialClip
          && node.configuration.maskCombination != null) {
        errors.push('SPATIAL_CLIP_MASK_COMBINATION_REQUIRE_SCHEMA_VERSION：空间裁剪多 Mask 组合方式从 Canvas 4.77 开始支持');
      }
      if (sourceSchemaMinorVersion < 29 && (
        node.type === CanvasNodeType.GeometryDerive && node.configuration.derivations.some(item => item.geometryPolicy != null)
        || node.type === CanvasNodeType.GeometrySimplify && node.configuration.geometryPolicy != null
      )) errors.push('GEOMETRY_UNARY_POLICY_REQUIRE_SCHEMA_VERSION：显式一元几何策略从 Canvas 4.29 开始支持');
      if (sourceSchemaMinorVersion < 46 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionWindows?.length ?? 0) > 0) {
        errors.push('TRACK_INCIDENT_WINDOWS_REQUIRE_SCHEMA_VERSION：事件窗口指标从 Canvas 4.46 开始支持');
      }
      if (sourceSchemaMinorVersion < 63 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionWindows ?? []).some(window => window.source === 'TRACK_DISTANCE')) {
        errors.push('TRACK_INCIDENT_DISTANCE_WINDOWS_REQUIRE_SCHEMA_VERSION：事件轨迹距离窗口从 Canvas 4.63 开始支持');
      }
      if (sourceSchemaMinorVersion < 64 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionWindows ?? []).some(window => window.source === 'TRACK_SPEED')) {
        errors.push('TRACK_INCIDENT_SPEED_WINDOWS_REQUIRE_SCHEMA_VERSION：事件轨迹速度窗口从 Canvas 4.64 开始支持');
      }
      if (sourceSchemaMinorVersion < 65 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionWindows ?? []).some(window => window.source === 'TRACK_ACCELERATION')) {
        errors.push('TRACK_INCIDENT_ACCELERATION_WINDOWS_REQUIRE_SCHEMA_VERSION：事件轨迹加速度窗口从 Canvas 4.65 开始支持');
      }
      if (sourceSchemaMinorVersion < 66 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionScalars?.length ?? 0) > 0) {
        errors.push('TRACK_INCIDENT_SCALARS_REQUIRE_SCHEMA_VERSION：事件轨迹标量从 Canvas 4.66 开始支持');
      }
      if (sourceSchemaMinorVersion < 67 && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.conditionScalars ?? []).some(scalar => scalar.source === 'TRACK_POINT_X_AT'
            || scalar.source === 'TRACK_POINT_Y_AT')) {
        errors.push('TRACK_INCIDENT_POINT_COORDINATES_REQUIRE_SCHEMA_VERSION：事件 Point 坐标标量从 Canvas 4.67 开始支持');
      }
      if (sourceSchemaMinorVersion < 45 && node.type === CanvasNodeType.SpatialPointCluster && node.configuration.hdbscan != null) {
        errors.push('SPATIAL_HDBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION：HDBSCAN 诊断配置从 Canvas 4.45 开始支持');
      }
      if (sourceSchemaMinorVersion < 44 && node.type === CanvasNodeType.TrackReconstruct && node.configuration.reconstruction?.areaGeometry?.geodesicBoundary != null) {
        errors.push('TRACK_GEODESIC_AREA_REQUIRE_SCHEMA_VERSION：测地面边界配置从 Canvas 4.44 开始支持');
      }
      if (sourceSchemaMinorVersion < 43 && node.type === CanvasNodeType.TrackReconstruct && (node.configuration.reconstruction?.areaGeometry?.windowBindings?.length ?? 0) > 0) {
        errors.push('TRACK_BUFFER_WINDOWS_REQUIRE_SCHEMA_VERSION：轨迹缓冲窗口绑定从 Canvas 4.43 开始支持');
      }
      if (sourceSchemaMinorVersion < 42 && node.type === CanvasNodeType.TrackReconstruct && node.configuration.reconstruction?.areaGeometry != null) {
        errors.push('TRACK_AREA_GEOMETRY_REQUIRE_SCHEMA_VERSION：显式面轨迹从 Canvas 4.42 开始支持');
      }
      if (sourceSchemaMinorVersion < 28 && node.type === CanvasNodeType.TrackReconstruct && node.configuration.reconstruction?.pathGeometry != null) {
        errors.push('TRACK_PATH_GEOMETRY_REQUIRE_SCHEMA_VERSION：显式轨迹路径从 Canvas 4.28 开始支持');
      }
      if (sourceSchemaMinorVersion < 27 && node.type === CanvasNodeType.TrackReconstruct && node.configuration.reconstruction != null) {
        errors.push('TRACK_RECONSTRUCT_OPTIONS_REQUIRE_SCHEMA_VERSION：显式轨迹重建次序与拆分从 Canvas 4.27 开始支持');
      }
      if (sourceSchemaMinorVersion < 26 && node.type === CanvasNodeType.SpatialOverlay
          && (node.configuration.geometryPolicy != null || node.configuration.operation === 'IDENTITY'
            || node.configuration.operation === 'SYMMETRICAL_DIFFERENCE')) {
        errors.push('SPATIAL_OVERLAY_FAMILY_REQUIRE_SCHEMA_VERSION：五模式及显式几何输出从 Canvas 4.26 开始支持');
      }
      if (sourceSchemaMinorVersion < 25 && node.type === CanvasNodeType.SpatialSummarizeWithin
          && node.configuration.groupResult != null) {
        errors.push('SPATIAL_WITHIN_GROUP_RESULT_REQUIRE_SCHEMA_VERSION：区域关联分组结果从 Canvas 4.25 开始支持');
      }
      if (sourceSchemaMinorVersion < 24 && node.type === CanvasNodeType.SpatialSummarizeWithin
          && usesExplicitWithinStatistics(node.configuration.statistics)) {
        errors.push('SPATIAL_WITHIN_STATISTICS_REQUIRE_SCHEMA_VERSION：显式区域统计从 Canvas 4.24 开始支持');
      }
      if (sourceSchemaMinorVersion < 23 && node.type === CanvasNodeType.TrackMotionStatistics
          && (node.configuration.motionSemantics != null || node.configuration.windowOptions != null)) {
        errors.push('TRACK_MOTION_STATISTICS 历史窗口配置从 Canvas 4.23 开始支持');
      }
      if (sourceSchemaMinorVersion < 22 && node.type === CanvasNodeType.TrackFindDwell
          && (node.configuration.dwellSemantics != null || node.configuration.rangeOptions != null)) {
        errors.push('TRACK_FIND_DWELL 候选范围配置从 Canvas 4.22 开始支持');
      }
      if (sourceSchemaMinorVersion < 21 && node.type === CanvasNodeType.SpatialBinAggregate
          && node.configuration.binSizeSemantics != null) {
        errors.push('SPATIAL_BIN_AGGREGATE 显式格网尺寸语义从 Canvas 4.21 开始支持');
      }
      if (sourceSchemaMinorVersion < 21 && 'boundaries' in node.configuration
          && node.configuration.boundaries?.fixedTimeBoundary != null) {
        errors.push(`${node.type} 固定时间边界从 Canvas 4.21 开始支持`);
      }
      if (sourceSchemaMinorVersion < 21
          && node.type === CanvasNodeType.TrackDetectIncidents
          && (node.configuration.incidentSemantics === 'CONDITION_LIFECYCLE'
            || node.configuration.incidentStatusColumnName != null
            || (node.configuration.orderByColumns?.length ?? 0) > 0)) {
        errors.push('TRACK_DETECT_INCIDENTS 事件生命周期配置从 Canvas 4.21 开始支持');
      }
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
      if (sourceSchemaMinorVersion < 2
          && node.type === CanvasNodeType.TypeCast
          && (node.configuration.operations ?? []).some((operation) => (
            operation.casts.some((cast) => cast.epochTimestampUnit != null)
          ))) {
        errors.push(`TYPE_CAST.epochTimestampUnit 从 Canvas ${CANVAS_SCHEMA_VERSION}.2 开始支持`);
      }
      if (sourceSchemaMinorVersion >= 2
          && sourceSchemaMinorVersion < 8
          && node.type === CanvasNodeType.TypeCast
          && (node.configuration.operations ?? []).some((operation) => (
            operation.casts.some((cast) => (
              cast.epochTimestampUnit != null && cast.targetType.type === 'LONG'
            ))
          ))) {
        errors.push(`TYPE_CAST DATE/TIMESTAMP 转 LONG 从 Canvas ${CANVAS_SCHEMA_VERSION}.8 开始支持`);
      }
      if (sourceSchemaMinorVersion < 3
          && node.type === CanvasNodeType.TypeCast
          && (node.configuration.operations ?? []).some((operation) => (
            operation.casts.some((cast) => cast.stringTemporalParseOptions != null)
          ))) {
        errors.push(`TYPE_CAST.stringTemporalParseOptions 从 Canvas ${CANVAS_SCHEMA_VERSION}.3 开始支持`);
      }
      if (sourceSchemaMinorVersion < 7
          && node.type === CanvasNodeType.TypeCast
          && (node.configuration.operations ?? []).some((operation) => (
            operation.casts.some((cast) => cast.temporalStringFormatOptions != null)
          ))) {
        errors.push(`TYPE_CAST.temporalStringFormatOptions 从 Canvas ${CANVAS_SCHEMA_VERSION}.7 开始支持`);
      }
      if (sourceSchemaMinorVersion < 4 && node.type === CanvasNodeType.KafkaInput) {
        const rawNode = (value.nodes as unknown[]).find((candidate) => (
          isRecord(candidate) && candidate.id === node.id
        ));
        const rawConfiguration = isRecord(rawNode) && isRecord(rawNode.configuration)
          ? rawNode.configuration : null;
        if (rawConfiguration
          && (rawConfiguration.valueFormat != null || rawConfiguration.metadataFields != null)) {
          errors.push(`KAFKA_INPUT.valueFormat/metadataFields 从 Canvas ${CANVAS_SCHEMA_VERSION}.4 开始支持`);
        }
      }
      if (sourceSchemaMinorVersion < 5 && node.type === CanvasNodeType.TdEngineTmqInput) {
        const rawNode = (value.nodes as unknown[]).find((candidate) => (
          isRecord(candidate) && candidate.id === node.id
        ));
        const rawConfiguration = isRecord(rawNode) && isRecord(rawNode.configuration)
          ? rawNode.configuration : null;
        if (rawConfiguration
          && (rawConfiguration.eventTimeColumn != null
            || rawConfiguration.watermarkDelaySeconds != null)) {
          errors.push(`TDENGINE_TMQ_INPUT 事件时间配置从 Canvas ${CANVAS_SCHEMA_VERSION}.5 开始支持`);
        }
      }
      if (sourceSchemaMinorVersion < 6 && node.type === CanvasNodeType.KafkaOutput) {
        const rawNode = (value.nodes as unknown[]).find((candidate) => (
          isRecord(candidate) && candidate.id === node.id
        ));
        const rawConfiguration = isRecord(rawNode) && isRecord(rawNode.configuration)
          ? rawNode.configuration : null;
        const rawWrites = rawConfiguration && Array.isArray(rawConfiguration.writes)
          ? rawConfiguration.writes : [];
        if (rawWrites.some((write) => isRecord(write)
          && (Object.prototype.hasOwnProperty.call(write, 'valueFormat')
            || Object.prototype.hasOwnProperty.call(write, 'valueColumnNames')))) {
          errors.push(`KAFKA_OUTPUT.valueFormat/valueColumnNames 从 Canvas ${CANVAS_SCHEMA_VERSION}.6 开始支持`);
        }
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
