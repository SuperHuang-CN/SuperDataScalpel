import {
  CanvasNodeType,
  type CanvasNodeRuntimeDataByType,
} from '../canvasTypes';

const summarizeProcessorOperations = (
  operations: Array<{ sourceTableName: string; output: { outputTableName: string | null } }>,
  empty: string,
) => {
  if (operations.length === 0) return empty;
  const preview = operations.slice(0, 2)
    .map((operation) => `${operation.sourceTableName} → ${operation.output.outputTableName ?? operation.sourceTableName}`)
    .join('、');
  return operations.length > 2 ? `${preview} 等 ${operations.length} 张表` : preview;
};

export const summarizeModelInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ModelInput>,
) => {
  const models = data.configuration.models;
  if (models.length === 0) return '请选择来源模型';
  const preview = models.slice(0, 2).map((model) => model.modelId).join('、');
  return models.length <= 2 ? `模型 ${preview}` : `模型 ${preview} 等 ${models.length} 个`;
};

export const summarizeJdbcInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcInput>,
) => {
  const { tables } = data.configuration;
  if (tables.length === 0) return '请选择来源表';
  const preview = tables.slice(0, 2).map((table) => table.tableName).join('、');
  const tableText = tables.length <= 2 ? preview : `${preview} 等 ${tables.length} 张表`;
  return data.summary?.kind === 'JDBC'
    ? `${data.summary.dataSourceName} · ${tableText}`
    : `未知数据源 · ${tableText}`;
};

export const summarizeJdbcIncrementalInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcIncrementalInput>,
) => {
  const { tableName, incrementalTimeColumn, outputTableName, triggerIntervalSeconds } = data.configuration;
  if (!tableName || !incrementalTimeColumn || !outputTableName) return '请配置 JDBC 增量输入';
  const source = data.summary?.kind === 'JDBC' ? data.summary.dataSourceName : '未知数据源';
  return `${source} · ${tableName}.${incrementalTimeColumn} → ${outputTableName} · ${triggerIntervalSeconds ?? 60}s`;
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
  if (data.configuration.tables.length === 0) return '请选择文件数据集表';
  if (data.summary?.kind !== 'FILE_DATASET') return `文件表 ${data.configuration.tables.length} 张`;
  const geometry = data.summary.geometry;
  const spatial = geometry
    ? ` · ${geometry.fieldName}: ${geometry.kind} ${geometry.crs.authority}:${geometry.crs.code} ${geometry.dimension}`
    : '';
  return `${data.summary.fileDatasetName} · ${data.configuration.tables.length} 张表 · ${data.summary.status}${spatial}`;
};

export const summarizeHttpApiInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.HttpApiInput>,
) => {
  const { dataSourceId, resources } = data.configuration;
  if (!dataSourceId || resources.length === 0) return '请选择 API 资源并设置输出表';
  const preview = resources.slice(0, 2).map((resource) => resource.outputTableName).join('、');
  return `${data.summary?.kind === 'HTTP_API' ? data.summary.dataSourceName : 'API'} · ${preview}${resources.length > 2 ? ` 等 ${resources.length} 个` : ''}`;
};

export const summarizeSpatialServiceInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SpatialServiceInput>,
) => {
  const { dataSourceId, resources } = data.configuration;
  if (!dataSourceId || resources.length === 0) return '请选择空间要素资源并设置输出表';
  const preview = resources.slice(0, 2).map((resource) => resource.outputTableName).join('、');
  return `${data.summary?.kind === 'HTTP_API' ? data.summary.dataSourceName : '空间服务'} · ${preview}${resources.length > 2 ? ` 等 ${resources.length} 个` : ''}`;
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

export const summarizeTdEngineTmqInput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TdEngineTmqInput>,
) => {
  const { dataSourceId, topicName, supertableName, outputTableName } = data.configuration;
  if (!dataSourceId || !topicName || !supertableName || !outputTableName) return '请配置 TDengine TMQ 输入';
  return data.summary?.kind === 'TDENGINE_TMQ'
    ? `${data.summary.dataSourceName} · ${topicName} · ${supertableName} → ${outputTableName}`
    : `${topicName} · ${supertableName} → ${outputTableName}`;
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
  const {
    leftTableName,
    rightTableName,
    outputTableName,
    joinType,
    outputColumns,
  } = data.configuration;
  return !leftTableName || !rightTableName || !outputTableName || !joinType
    ? '请配置流-维 Join'
    : `${leftTableName} ${joinType} ${rightTableName} → ${outputTableName} · ${outputColumns.filter((column) => column.included).length} 个输出字段`;
};

export const summarizeRename = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Rename>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并设置新名称');

export const summarizeFilter = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.Filter>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置筛选条件');

export const summarizeSqlTransform = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SqlTransform>,
) => {
  const { outputTableName, sql } = data.configuration;
  if (!outputTableName || !sql.trim()) return '请设置输出表并配置 SELECT 查询';
  const output = data.compilation?.outputTables.find((table) => table.name === outputTableName);
  return `${outputTableName} · ${output?.columns.length ?? 0} 个字段`;
};

export const summarizeSelectColumns = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.SelectColumns>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表和保留字段');

export const summarizeDeriveColumns = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.DeriveColumns>,
) => {
  const operations = data.configuration.operations ?? [];
  const globalCount = data.configuration.globalDerivations?.length ?? 0;
  const localCount = operations.reduce((count, operation) => count + operation.derivations.length, 0);
  return operations.length === 0
    ? '请选择来源表并配置派生字段'
    : `${operations.length} 张处理表 · 全局 ${globalCount} 条 · 本表 ${localCount} 条`;
};

export const summarizeTypeCast = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.TypeCast>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置类型转换');

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
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置去重');

export const summarizeNullHandling = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.NullHandling>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置空值处理');

export const summarizeValueMapping = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ValueMapping>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置值映射');

export const summarizeMaskFields = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.MaskFields>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置字段脱敏');

export const summarizeJsonExtract = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JsonExtract>,
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择 JSON 来源字段并配置提取项');

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
) => summarizeProcessorOperations(data.configuration.operations ?? [], '请选择来源表并配置 Top N');

export const summarizeModelOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ModelOutput>,
) => {
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return '请至少配置一条模型写入';
  const target = data.summary?.kind === 'MODEL'
    ? `${data.summary.modelName} · ${data.summary.modelCode}`
    : `模型 ${first.targetModelId || '待选择'}`;
  const preview = `${first.sourceTableName || '待选择来源'} → ${target} (${first.writeMode ?? '待设置'})`;
  return writes.length > 1 ? `${preview}，另 ${writes.length - 1} 条写入` : preview;
};

export const summarizeJdbcOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcOutput>,
) => {
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return '请至少配置一条 JDBC 写入';
  const target = data.summary?.kind === 'JDBC'
    ? data.summary.qualifiedTableName
    : first.targetTableName || '待选择目标表';
  const preview = `${first.sourceTableName || '待选择来源'} → ${target} (${first.writeMode ?? '待设置'})`;
  return writes.length > 1 ? `${preview}，另 ${writes.length - 1} 条写入` : preview;
};

const snapshotDeleteSummary = (action: 'KEEP' | 'DELETE') => (
  action === 'DELETE' ? '删除目标独有行' : '保留目标独有行'
);

export const summarizeJdbcSnapshotSyncOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.JdbcSnapshotSyncOutput>,
) => {
  const { sourceTableName, dataSourceId, targetTableName, keyColumns, deletePolicy } = data.configuration;
  if (!sourceTableName || !dataSourceId || !targetTableName || keyColumns.length === 0) {
    return '请选择同步目标并配置 Key';
  }
  const target = data.summary?.kind === 'JDBC'
    ? `${data.summary.dataSourceName} · ${data.summary.qualifiedTableName}`
    : `未知数据源 · ${targetTableName}`;
  return `${sourceTableName} ⇄ ${target} · ${keyColumns.length} 个 Key · ${snapshotDeleteSummary(deletePolicy.action)}`;
};

export const summarizeModelSnapshotSyncOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.ModelSnapshotSyncOutput>,
) => {
  const { sourceTableName, targetModelId, keyColumns, deletePolicy } = data.configuration;
  if (!sourceTableName || !targetModelId || keyColumns.length === 0) {
    return '请选择目标模型并配置 Key';
  }
  const target = data.summary?.kind === 'MODEL'
    ? `${data.summary.modelName} · ${data.summary.modelCode}`
    : `模型 ${targetModelId}`;
  return `${sourceTableName} ⇄ ${target} · ${keyColumns.length} 个 Key · ${snapshotDeleteSummary(deletePolicy.action)}`;
};

export const summarizeKafkaOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.KafkaOutput>,
) => {
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return '请至少配置一条 Kafka 写入';
  const target = data.summary?.kind === 'KAFKA'
    ? `${data.summary.dataSourceName} · ${first.topic || '待选择 Topic'}`
    : first.topic || '待选择 Topic';
  const preview = `${first.sourceTableName || '待选择来源'} → ${target} · ${first.valueSchema.columns.length} 字段`;
  return writes.length > 1 ? `${preview}，另 ${writes.length - 1} 条写入` : preview;
};

export const summarizeFileOutput = (
  data: CanvasNodeRuntimeDataByType<typeof CanvasNodeType.FileOutput>,
) => {
  const { dataSourceId } = data.configuration;
  const writes = data.configuration.writes ?? [];
  const first = writes[0];
  if (!first) return '请至少配置一条文件写入';
  const { sourceTableName, targetPath, conflictPolicy, formatOptions } = first;
  const format = formatOptions.type === 'SHAPEFILE'
    ? `SHAPEFILE · ${formatOptions.targetShapeType || '未选类型'} · ${formatOptions.packageMode}`
    : formatOptions.type === 'GEOPARQUET'
      ? `GEOPARQUET · ${formatOptions.compression} · ${formatOptions.coveringMode}`
      : formatOptions.type === 'GEOJSON'
        ? `GEOJSON · ${formatOptions.baseName || '未命名'}`
        : formatOptions.type;
  const preview = !sourceTableName || !dataSourceId || !targetPath
    ? '请配置文件输出'
    : `${sourceTableName} → ${format} · ${targetPath} (${conflictPolicy})`;
  return writes.length > 1 ? `${preview}，另 ${writes.length - 1} 条写入` : preview;
};
