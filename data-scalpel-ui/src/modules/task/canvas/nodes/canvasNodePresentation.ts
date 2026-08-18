import type {
  CanvasColumnSchema,
  CanvasExpression,
  CanvasFilterCondition,
  CanvasNodeRuntimeData,
  CanvasTableSchema,
  PlatformTypeDefinition,
  SortField,
} from '../canvasTypes';
import type { CanvasNodeSize } from './nodeSpec';

export const resolvedNodeSize = ({
  width,
  maxHeight,
  configured,
  listCount,
  emptyHeight = 104,
}: {
  width: number;
  maxHeight: number;
  configured: boolean;
  listCount?: number;
  emptyHeight?: number;
}): CanvasNodeSize => {
  if (!configured) return { width, height: emptyHeight };
  if (listCount === undefined) return { width, height: maxHeight };
  return { width, height: maxHeight - ((2 - Math.min(2, listCount)) * 24) };
};

export const inputTable = (
  data: CanvasNodeRuntimeData,
  name?: string,
): CanvasTableSchema | undefined => (
  name
    ? data.compilation?.inputTables.find((table) => table.name === name)
    : data.compilation?.inputTables[0]
);

export const outputTable = (
  data: CanvasNodeRuntimeData,
  name?: string,
): CanvasTableSchema | undefined => (
  name
    ? data.compilation?.outputTables.find((table) => table.name === name)
    : data.compilation?.outputTables[0]
);

export const fieldCountText = (table: CanvasTableSchema | undefined): string => (
  table ? `${table.columns.length} 个字段` : '等待字段解析'
);

export const geometryColumn = (table: CanvasTableSchema | undefined): CanvasColumnSchema | undefined => (
  table?.columns.find((column) => column.fieldType === 'GEOMETRY' && column.geometry)
);

export const geometryText = (column: CanvasColumnSchema | undefined): string | null => {
  const geometry = column?.geometry;
  return geometry
    ? `${geometry.kind} · ${geometry.crs.authority}:${geometry.crs.code} · ${geometry.dimension}`
    : null;
};

export const typeDefinitionText = (type: PlatformTypeDefinition): string => {
  if (type.type === 'STRING' && type.length !== null) return `STRING(${type.length})`;
  if (type.type === 'DECIMAL') return `DECIMAL(${type.precision ?? '?'},${type.scale ?? '?'})`;
  if (type.type === 'GEOMETRY' && type.geometry) {
    return `${type.geometry.kind}(${type.geometry.crs.authority}:${type.geometry.crs.code},${type.geometry.dimension})`;
  }
  return type.type;
};

export const columnTypeText = (
  table: CanvasTableSchema | undefined,
  columnName: string,
): string => {
  const column = table?.columns.find((candidate) => candidate.name === columnName);
  if (!column) return '?';
  if (column.fieldType === 'STRING' && column.length !== null) return `STRING(${column.length})`;
  if (column.fieldType === 'DECIMAL') return `DECIMAL(${column.precision ?? '?'},${column.scale ?? '?'})`;
  return column.fieldType;
};

export const filterPredicates = (condition: CanvasFilterCondition): Array<{
  columnName: string;
  operator: string;
  valueCount: number;
}> => condition.kind === 'PREDICATE'
  ? [{ columnName: condition.columnName, operator: condition.operator, valueCount: condition.values.length }]
  : condition.children.flatMap(filterPredicates);

export const expressionSignature = (expression: CanvasExpression): string => {
  switch (expression.kind) {
    case 'COLUMN': return expression.columnName || '字段';
    case 'LITERAL': return '字面量';
    case 'BINARY': return expression.operator;
    case 'FUNCTION': return `${expression.function}(…)`;
    case 'CASE_WHEN': return `CASE · ${expression.branches.length} 分支`;
  }
};

export const sortText = (sort: SortField | undefined): string => (
  sort ? `${sort.columnName || '字段'} ${sort.direction ?? '?'} · NULL ${sort.nullOrdering ?? '?'}` : '未配置排序'
);

export const metadataSourceName = (data: CanvasNodeRuntimeData): string => {
  const summary = data.summary;
  if (!summary) return '等待元数据';
  switch (summary.kind) {
    case 'JDBC':
    case 'HTTP_API':
    case 'KAFKA':
    case 'TDENGINE_TMQ': return summary.dataSourceName;
    case 'MODEL': return summary.modelName;
    case 'FILE_DATASET': return summary.fileDatasetName;
    case 'S3': return summary.dataSourceName;
  }
};

export const metadataObjectName = (data: CanvasNodeRuntimeData): string => {
  const summary = data.summary;
  if (!summary) return '等待元数据';
  switch (summary.kind) {
    case 'JDBC':
    case 'HTTP_API':
    case 'KAFKA':
    case 'TDENGINE_TMQ': return summary.qualifiedTableName;
    case 'MODEL': return summary.modelCode;
    case 'FILE_DATASET': return summary.tableName;
    case 'S3': return summary.bucketName;
  }
};
