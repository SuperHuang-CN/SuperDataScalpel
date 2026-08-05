import {
  CanvasNodeType,
  type CanvasExpression,
  type CanvasFilterCondition,
  type CanvasNodeRuntimeDataByType,
} from '../canvasTypes';

const filterPredicateCount = (condition: CanvasFilterCondition): number => (
  condition.kind === 'PREDICATE'
    ? 1
    : condition.children.reduce((count, child) => count + filterPredicateCount(child), 0)
);

const collectExpressionFunctions = (expression: CanvasExpression, functions: Set<string>) => {
  if (expression.kind === 'BINARY') {
    collectExpressionFunctions(expression.left, functions);
    collectExpressionFunctions(expression.right, functions);
  } else if (expression.kind === 'FUNCTION') {
    functions.add(expression.function);
    expression.arguments.forEach((argument) => collectExpressionFunctions(argument, functions));
  } else if (expression.kind === 'CASE_WHEN') {
    expression.branches.forEach((branch) => collectExpressionFunctions(branch.result, functions));
    if (expression.elseExpression) collectExpressionFunctions(expression.elseExpression, functions);
  }
};

export const summarizeModelInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ModelInput>,
) => {
  if (!data.configuration.modelId) return '请选择来源模型';
  return data.summary?.kind === 'MODEL'
    ? `${data.summary.modelName} · ${data.summary.modelCode} · v${data.summary.modelSchemaVersion}`
    : `模型 ${data.configuration.modelId}`;
};

export const summarizeJdbcInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcInput>,
) => {
  if (!data.configuration.tableName) return '请选择来源表';
  return data.summary?.kind === 'JDBC'
    ? `${data.summary.dataSourceName} · ${data.summary.qualifiedTableName}`
    : `未知数据源 · ${data.configuration.tableName}`;
};

export const summarizeJdbcQueryInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcQueryInput>,
) => {
  const { dataSourceId, outputTableName, outputColumns } = data.configuration;
  if (!dataSourceId || !outputTableName || outputColumns.length === 0) return '请分析 SQL 并设置输出表';
  return data.summary?.kind === 'JDBC'
    ? `${data.summary.dataSourceName} · ${outputTableName} · ${outputColumns.length} 个字段`
    : `${outputTableName} · ${outputColumns.length} 个字段`;
};

export const summarizeFileDatasetInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.FileDatasetInput>,
) => {
  if (!data.configuration.fileDatasetTableId) return '请选择文件数据集表';
  if (data.summary?.kind !== 'FILE_DATASET') return `文件表 ${data.configuration.fileDatasetTableId}`;
  const geometry = data.summary.geometry;
  const spatial = geometry
    ? ` · ${geometry.fieldName}: ${geometry.kind} ${geometry.crs.authority}:${geometry.crs.code} ${geometry.dimension}`
    : '';
  return `${data.summary.fileDatasetName} · ${data.summary.tableName} (${data.summary.tableCode}) · ${data.summary.status}${spatial}`;
};

export const summarizeHttpApiInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.HttpApiInput>,
) => {
  const { dataSourceId, resourceId, outputTableName } = data.configuration;
  if (!dataSourceId || !resourceId || !outputTableName) return '请选择 API 资源并设置输出表';
  return data.summary?.kind === 'HTTP_API'
    ? `${data.summary.dataSourceName} · ${data.summary.qualifiedTableName} → ${outputTableName}`
    : `${resourceId} → ${outputTableName}`;
};

export const summarizeSpatialServiceInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialServiceInput>,
) => {
  const { dataSourceId, resourceId, outputTableName } = data.configuration;
  if (!dataSourceId || !resourceId || !outputTableName) return '请选择空间要素资源并设置输出表';
  return data.summary?.kind === 'HTTP_API'
    ? `${data.summary.dataSourceName} · ${data.summary.qualifiedTableName} → ${outputTableName}`
    : `${resourceId} → ${outputTableName}`;
};

export const summarizeKafkaInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.KafkaInput>,
) => {
  const { dataSourceId, topic, valueSchema, outputTableName } = data.configuration;
  if (!dataSourceId || !topic || valueSchema.columns.length === 0 || !outputTableName) {
    return '请配置 Kafka 输入';
  }
  return data.summary?.kind === 'KAFKA'
    ? `${data.summary.dataSourceName} · ${topic} → ${outputTableName} · ${valueSchema.columns.length} 字段`
    : `${topic} → ${outputTableName} · ${valueSchema.columns.length} 字段`;
};

export const summarizeJoin = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Join>,
) => {
  const { leftTableName, rightTableName, outputTableName, joinType } = data.configuration;
  return !leftTableName || !rightTableName || !outputTableName || !joinType
    ? '请配置 Join'
    : `${leftTableName} ${joinType} ${rightTableName} → ${outputTableName}`;
};

export const summarizeSpatialTransform = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialTransform>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    targetCrs,
  } = data.configuration;
  return !sourceTableName || !outputTableName || !geometryColumnName || !targetCrs
    ? '请选择 Geometry 字段和目标 CRS'
    : `${sourceTableName}.${geometryColumnName} → ${outputTableName} · EPSG:${targetCrs.code}`;
};

export const summarizeGeometryConstruct = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryConstruct>,
) => {
  const {
    sourceTableName,
    outputTableName,
    outputColumnName,
    source,
    targetGeometry,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !outputColumnName || !targetGeometry) {
    return '请选择来源字段和目标 Geometry 类型';
  }
  return `${sourceTableName} · ${source.kind} → ${outputTableName}.${outputColumnName} · ${targetGeometry.kind} EPSG:${targetGeometry.crs.code}`;
};

export const summarizeGeometryValidate = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryValidate>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    reasonColumnName,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName) {
    return '请选择 Geometry 字段并设置诊断字段';
  }
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName} · 合法性${reasonColumnName ? ' + 原因' : ''}`;
};

export const summarizeGeometryRepair = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryRepair>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    outputColumnName,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName || !outputColumnName) {
    return '请选择需要修复的 Geometry 字段';
  }
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName}.${outputColumnName} · MAKE_VALID`;
};

export const summarizeGeometryBuffer = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryBuffer>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    outputColumnName,
    distance,
    mode,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName || !outputColumnName) {
    return '请选择 Geometry 字段并配置 Buffer';
  }
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName}.${outputColumnName} · ${distance} · ${mode}`;
};

export const summarizeGeometryExplode = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometryExplode>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    outputColumnName,
    partIndexColumnName,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName || !outputColumnName) {
    return '请选择需要拆分的 Geometry 字段';
  }
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName}.${outputColumnName}${
    partIndexColumnName ? ` · 序号 ${partIndexColumnName}` : ''
  }`;
};

export const summarizeSpatialMeasure = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialMeasure>,
) => {
  const { sourceTableName, outputTableName, measurements } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置空间测量';
  const kinds = [...new Set(measurements.map((measurement) => measurement.kind))].join('/');
  const modes = [...new Set(measurements.flatMap((measurement) => (
    'mode' in measurement ? [measurement.mode] : []
  )))].join('/');
  return `${sourceTableName} → ${outputTableName} · ${measurements.length} 项${
    kinds ? ` · ${kinds}` : ''
  }${modes ? ` · ${modes}` : ''}`;
};

export const summarizeGeometrySerialize = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.GeometrySerialize>,
) => {
  const {
    sourceTableName,
    outputTableName,
    geometryColumnName,
    outputColumnName,
    format,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !geometryColumnName || !outputColumnName) {
    return '请选择 Geometry 字段和序列化格式';
  }
  return `${sourceTableName}.${geometryColumnName} → ${outputTableName}.${outputColumnName} · ${format}`;
};

export const summarizeSpatialClip = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialClip>,
) => {
  const {
    sourceTableName,
    maskTableName,
    outputTableName,
    sourceGeometryColumnName,
    maskGeometryColumnName,
    outputColumnName,
  } = data.configuration;
  if (!sourceTableName || !maskTableName || !outputTableName
    || !sourceGeometryColumnName || !maskGeometryColumnName || !outputColumnName) {
    return '请选择来源 Geometry 和面状 Mask';
  }
  return `${sourceTableName}.${sourceGeometryColumnName} ∩ ${maskTableName}.${maskGeometryColumnName} → ${outputTableName}.${outputColumnName}`;
};

export const summarizeSpatialAggregate = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialAggregate>,
) => {
  const { sourceTableName, outputTableName, groupByColumns, aggregations } = data.configuration;
  if (!sourceTableName || !outputTableName || aggregations.length === 0) {
    return '请选择来源表并配置空间聚合';
  }
  const kinds = [...new Set(aggregations.map((item) => item.kind))].join('/');
  return `${sourceTableName} → ${outputTableName} · ${groupByColumns.length} 个分组字段 · ${aggregations.length} 项 ${kinds}`;
};

export const summarizeSpatialJoin = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialJoin>,
) => {
  const {
    leftTableName,
    rightTableName,
    outputTableName,
    conditions,
  } = data.configuration;
  if (!leftTableName || !rightTableName || !outputTableName || conditions.length === 0) {
    return '请选择两张空间表并配置空间谓词';
  }
  const predicates = [...new Set(conditions.map((condition) => condition.predicate)
    .filter(Boolean))].join('/');
  return `${leftTableName} INNER ${rightTableName} → ${outputTableName} · ${conditions.length} 条 ${predicates}`;
};

export const summarizeStreamJoin = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.StreamJoin>,
) => {
  const { leftTableName, rightTableName, outputTableName, joinType } = data.configuration;
  return !leftTableName || !rightTableName || !outputTableName || !joinType
    ? '请配置流-维 Join'
    : `${leftTableName} ${joinType} ${rightTableName} → ${outputTableName}`;
};

export const summarizeRename = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Rename>,
) => {
  const { sourceTableName, outputTableName, columnMappings } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并设置输出表名';
  const tableSummary = sourceTableName === outputTableName
    ? sourceTableName
    : `${sourceTableName} → ${outputTableName}`;
  return columnMappings.length > 0
    ? `${tableSummary} · ${columnMappings.length} 个字段`
    : tableSummary;
};

export const summarizeFilter = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Filter>,
) => {
  const { sourceTableName, outputTableName, condition } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置筛选条件';
  const conditionCount = filterPredicateCount(condition);
  const rootOperator = condition.kind === 'GROUP' ? ` · 顶层 ${condition.operator}` : '';
  return conditionCount > 0
    ? `${sourceTableName} → ${outputTableName} · ${conditionCount} 个条件${rootOperator}`
    : `${sourceTableName} → ${outputTableName} · 未配置条件`;
};

export const summarizeSelectColumns = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SelectColumns>,
) => {
  const { sourceTableName, outputTableName, columns } = data.configuration;
  return !sourceTableName || !outputTableName
    ? '请选择来源表和输出表名'
    : `${sourceTableName} → ${outputTableName} · ${columns.length} 个字段`;
};

export const summarizeDeriveColumns = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.DeriveColumns>,
) => {
  const { sourceTableName, outputTableName, derivations } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置派生字段';
  const replaceCount = derivations.filter((item) => item.replaceExisting).length;
  const functions = new Set<string>();
  derivations.forEach((item) => collectExpressionFunctions(item.expression, functions));
  const functionSummary = functions.size > 0 ? ` · ${[...functions].join('/')}` : '';
  return `${sourceTableName} → ${outputTableName} · 新增 ${derivations.length - replaceCount} / 覆盖 ${replaceCount}${functionSummary}`;
};

export const summarizeTypeCast = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TypeCast>,
) => {
  const { sourceTableName, outputTableName, casts } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置类型转换';
  const setNullCount = casts.filter((item) => item.failureStrategy === 'SET_NULL').length;
  return `${sourceTableName} → ${outputTableName} · ${casts.length} 个字段 · FAIL ${casts.length - setNullCount} / SET_NULL ${setNullCount}`;
};

export const summarizeAggregate = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Aggregate>,
) => {
  const { sourceTableName, outputTableName, groupByColumns, aggregations } = data.configuration;
  return !sourceTableName || !outputTableName
    ? '请选择来源表并配置聚合'
    : `${sourceTableName} → ${outputTableName} · ${groupByColumns.length} 个分组字段 · ${aggregations.length} 个指标`;
};

export const summarizeUnion = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Union>,
) => {
  const { inputTableNames, outputTableName, mode } = data.configuration;
  return inputTableNames.length < 2 || !outputTableName || !mode
    ? '请选择至少两张输入表'
    : `${inputTableNames.length} 张表 → ${outputTableName} · ${mode}`;
};

export const summarizeDeduplicate = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Deduplicate>,
) => {
  const { sourceTableName, outputTableName, keyColumns, keepStrategy, orderBy } = data.configuration;
  if (!sourceTableName || !outputTableName || !keepStrategy) return '请选择来源表并配置去重';
  const keySummary = keyColumns.length === 0 ? '全部字段' : `${keyColumns.length} 个键`;
  return `${sourceTableName} → ${outputTableName} · ${keySummary} · ${keepStrategy} · ${orderBy.length} 个排序`;
};

export const summarizeNullHandling = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.NullHandling>,
) => {
  const { sourceTableName, outputTableName, rules } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置空值处理';
  const dropCount = rules.filter((rule) => rule.kind === 'DROP_ROW').length;
  return `${sourceTableName} → ${outputTableName} · 删除 ${dropCount} · 填充 ${rules.length - dropCount}`;
};

export const summarizeValueMapping = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ValueMapping>,
) => {
  const { sourceTableName, outputTableName, rules } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置值映射';
  const entryCount = rules.reduce((count, rule) => count + rule.entries.length, 0);
  const strategies = [...new Set(rules.map((rule) => rule.unmatchedStrategy))].join('/');
  return `${sourceTableName} → ${outputTableName} · ${rules.length} 个字段 · ${entryCount} 项 · ${strategies || '未配置'}`;
};

export const summarizeMaskFields = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.MaskFields>,
) => {
  const { sourceTableName, outputTableName, fieldRules } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置字段脱敏';
  const globalCount = fieldRules.filter((rule) => rule.ruleSource === 'GLOBAL').length;
  const strategies = [...new Set(fieldRules.map((rule) => rule.definition.strategy))].join('/');
  return `${sourceTableName} → ${outputTableName} · ${fieldRules.length} 个字段 · 全局 ${globalCount} / 自定义 ${fieldRules.length - globalCount} · ${strategies || '未配置'}`;
};

export const summarizeJsonExtract = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JsonExtract>,
) => {
  const {
    sourceTableName,
    outputTableName,
    sourceColumnName,
    extractions,
    failureStrategy,
  } = data.configuration;
  if (!sourceTableName || !outputTableName || !sourceColumnName) {
    return '请选择 JSON 来源字段并配置提取项';
  }
  return `${sourceTableName}.${sourceColumnName} → ${outputTableName} · ${extractions.length} 个字段 · ${failureStrategy}`;
};

export const summarizeWindow = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Window>,
) => {
  const {
    sourceTableName,
    outputTableName,
    partitionByColumns,
    orderBy,
    functions,
  } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置窗口计算';
  const kinds = [...new Set(functions.map((item) => item.kind))].join('/');
  return `${sourceTableName} → ${outputTableName} · 分区 ${partitionByColumns.length} · 排序 ${orderBy.length} · ${functions.length} 个函数${kinds ? ` (${kinds})` : ''}`;
};

export const summarizeTopN = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TopN>,
) => {
  const {
    sourceTableName,
    outputTableName,
    partitionByColumns,
    orderBy,
    limit,
    tieStrategy,
  } = data.configuration;
  if (!sourceTableName || !outputTableName) return '请选择来源表并配置 Top N';
  const scope = partitionByColumns.length === 0 ? '全局' : `${partitionByColumns.length} 个分组字段`;
  return `${sourceTableName} → ${outputTableName} · ${scope} · Top ${limit} · ${tieStrategy} · ${orderBy.length} 个排序`;
};

export const summarizeModelOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ModelOutput>,
) => {
  const { sourceTableName, targetModelId, writeMode } = data.configuration;
  if (!sourceTableName || !targetModelId || !writeMode) return '请选择输出模型';
  const target = data.summary?.kind === 'MODEL'
    ? `${data.summary.modelName} · ${data.summary.modelCode}`
    : `模型 ${targetModelId}`;
  return `${sourceTableName} → ${target} (${writeMode})`;
};

export const summarizeJdbcOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcOutput>,
) => {
  const { sourceTableName, dataSourceId, targetTableName, writeMode } = data.configuration;
  if (!sourceTableName || !targetTableName || !writeMode) return '请选择输出目标';
  const target = data.summary?.kind === 'JDBC'
    ? data.summary.qualifiedTableName
    : `${dataSourceId || '未知数据源'}.${targetTableName}`;
  return `${sourceTableName} → ${target} (${writeMode})`;
};

export const summarizeKafkaOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.KafkaOutput>,
) => {
  const { sourceTableName, dataSourceId, topic, valueSchema } = data.configuration;
  if (!sourceTableName || !dataSourceId || !topic || valueSchema.columns.length === 0) {
    return '请配置 Kafka 输出';
  }
  return data.summary?.kind === 'KAFKA'
    ? `${sourceTableName} → ${data.summary.dataSourceName} · ${topic} · ${valueSchema.columns.length} 字段`
    : `${sourceTableName} → ${topic} · ${valueSchema.columns.length} 字段`;
};

export const summarizeFileOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.FileOutput>,
) => {
  const { sourceTableName, dataSourceId, targetPath, conflictPolicy, formatOptions } = data.configuration;
  const format = formatOptions.type === 'SHAPEFILE'
    ? `SHAPEFILE · ${formatOptions.targetShapeType || '未选类型'} · ${formatOptions.packageMode}`
    : formatOptions.type === 'GEOPARQUET'
      ? `GEOPARQUET · ${formatOptions.compression} · ${formatOptions.coveringMode}`
      : formatOptions.type === 'GEOJSON'
        ? `GEOJSON · ${formatOptions.baseName || '未命名'}`
        : formatOptions.type;
  return !sourceTableName || !dataSourceId || !targetPath
    ? '请配置文件输出'
    : `${sourceTableName} → ${format} · ${targetPath} (${conflictPolicy})`;
};
