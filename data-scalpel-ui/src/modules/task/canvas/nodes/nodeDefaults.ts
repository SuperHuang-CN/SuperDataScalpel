import type {
  AggregateConfiguration,
  DeduplicateConfiguration,
  DeriveColumnsConfiguration,
  FileDatasetInputConfiguration,
  FileOutputConfiguration,
  FilterConfiguration,
  GeometryConstructConfiguration,
  GeometryBufferConfiguration,
  GeometryExplodeConfiguration,
  GeometryRepairConfiguration,
  GeometrySerializeConfiguration,
  GeometryValidateConfiguration,
  HttpApiInputConfiguration,
  SpatialServiceInputConfiguration,
  JdbcInputConfiguration,
  JdbcIncrementalInputConfiguration,
  JdbcQueryInputConfiguration,
  JdbcOutputConfiguration,
  JdbcSnapshotSyncOutputConfiguration,
  JoinConfiguration,
  JsonExtractConfiguration,
  KafkaInputConfiguration,
  TdEngineTmqInputConfiguration,
  KafkaOutputConfiguration,
  ModelInputConfiguration,
  ModelOutputConfiguration,
  ModelSnapshotSyncOutputConfiguration,
  MaskFieldsConfiguration,
  NullHandlingConfiguration,
  RenameConfiguration,
  SelectColumnsConfiguration,
  SpatialJoinConfiguration,
  SpatialClipConfiguration,
  SpatialAggregateConfiguration,
  SpatialMeasureConfiguration,
  SpatialTransformConfiguration,
  StreamJoinConfiguration,
  TopNConfiguration,
  TypeCastConfiguration,
  UnionConfiguration,
  ValueMappingConfiguration,
  WindowConfiguration,
} from '../canvasTypes';

export const createModelInputConfiguration = (): ModelInputConfiguration => ({ modelId: '' });

export const createJdbcInputConfiguration = (): JdbcInputConfiguration => ({
  dataSourceId: '',
  tableName: '',
});

export const createJdbcIncrementalInputConfiguration = (): JdbcIncrementalInputConfiguration => ({
  dataSourceId: '',
  tableName: '',
  outputTableName: '',
  incrementalTimeColumn: '',
  startPosition: 'LATEST',
  startTime: null,
  cursorTimeZone: 'UTC',
  visibilityDelaySeconds: 30,
  triggerIntervalSeconds: 60,
});

export const createJdbcQueryInputConfiguration = (): JdbcQueryInputConfiguration => ({
  dataSourceId: '',
  sql: '',
  outputTableName: '',
  analyzedSqlSha256: '',
  outputColumns: [],
});

export const createFileDatasetInputConfiguration = (): FileDatasetInputConfiguration => ({
  fileDatasetTableId: '',
});

export const createHttpApiInputConfiguration = (): HttpApiInputConfiguration => ({
  dataSourceId: '',
  resourceId: '',
  outputTableName: '',
  runtimeParameters: [],
});

export const createSpatialServiceInputConfiguration = (): SpatialServiceInputConfiguration => ({
  dataSourceId: '', resourceId: '', outputTableName: '',
});

export const createKafkaInputConfiguration = (): KafkaInputConfiguration => ({
  dataSourceId: '',
  topic: '',
  valueSchema: { columns: [] },
  outputTableName: '',
  startingOffsets: null,
  triggerIntervalSeconds: 10,
});

export const createTdEngineTmqInputConfiguration = (): TdEngineTmqInputConfiguration => ({
  dataSourceId: '',
  topicName: '',
  catalogName: '',
  supertableName: '',
  topicDefinitionFingerprint: '',
  outputTableName: '',
  startingOffsets: 'EARLIEST',
  maxOffsetsPerVGroupPerTrigger: 10_000,
  triggerIntervalSeconds: 10,
});

export const createJoinConfiguration = (): JoinConfiguration => ({
  leftTableName: '',
  rightTableName: '',
  outputTableName: '',
  joinType: null,
  conditions: [],
});

export const createGeometryConstructConfiguration = (): GeometryConstructConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  outputColumnName: '',
  source: { kind: 'WKT', columnName: '' },
  targetGeometry: null,
});

export const createSpatialTransformConfiguration = (): SpatialTransformConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  targetCrs: null,
});

export const createGeometryValidateConfiguration = (): GeometryValidateConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  validColumnName: 'geometry_valid',
  reasonColumnName: null,
});

export const createGeometryRepairConfiguration = (): GeometryRepairConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  outputColumnName: 'repaired_geometry',
});

export const createGeometryBufferConfiguration = (): GeometryBufferConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  outputColumnName: 'buffer_geometry',
  distance: 100,
  mode: 'PLANAR',
});

export const createGeometryExplodeConfiguration = (): GeometryExplodeConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  outputColumnName: 'geometry_part',
  partIndexColumnName: null,
});

export const createSpatialMeasureConfiguration = (): SpatialMeasureConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  measurements: [],
});

export const createGeometrySerializeConfiguration = (): GeometrySerializeConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  outputColumnName: '',
  format: 'WKT',
});

export const createSpatialClipConfiguration = (): SpatialClipConfiguration => ({
  sourceTableName: '',
  maskTableName: '',
  outputTableName: '',
  sourceGeometryColumnName: '',
  maskGeometryColumnName: '',
  outputColumnName: 'clipped_geometry',
});

export const createSpatialAggregateConfiguration = (): SpatialAggregateConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  groupByColumns: [],
  aggregations: [],
});

export const createSpatialJoinConfiguration = (): SpatialJoinConfiguration => ({
  leftTableName: '',
  rightTableName: '',
  outputTableName: '',
  joinType: 'INNER',
  conditions: [],
});

export const createStreamJoinConfiguration = (): StreamJoinConfiguration => ({
  leftTableName: '',
  rightTableName: '',
  outputTableName: '',
  joinType: null,
  conditions: [],
});

export const createRenameConfiguration = (): RenameConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  columnMappings: [],
});

export const createFilterConfiguration = (): FilterConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  condition: { kind: 'GROUP', operator: 'AND', children: [] },
});

export const createSelectColumnsConfiguration = (): SelectColumnsConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  columns: [],
});

export const createDeriveColumnsConfiguration = (): DeriveColumnsConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  derivations: [],
});

export const createTypeCastConfiguration = (): TypeCastConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  casts: [],
});

export const createAggregateConfiguration = (): AggregateConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  groupByColumns: [],
  aggregations: [],
});

export const createUnionConfiguration = (): UnionConfiguration => ({
  inputTableNames: [],
  outputTableName: '',
  mode: 'ALL',
});

export const createDeduplicateConfiguration = (): DeduplicateConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  keyColumns: [],
  keepStrategy: 'ANY',
  orderBy: [],
});

export const createNullHandlingConfiguration = (): NullHandlingConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  rules: [],
});

export const createValueMappingConfiguration = (): ValueMappingConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  rules: [],
});

export const createMaskFieldsConfiguration = (): MaskFieldsConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  fieldRules: [],
});

export const createJsonExtractConfiguration = (): JsonExtractConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  sourceColumnName: '',
  extractions: [],
  failureStrategy: 'ERROR',
});

export const createWindowConfiguration = (): WindowConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  partitionByColumns: [],
  orderBy: [],
  functions: [],
});

export const createTopNConfiguration = (): TopNConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  partitionByColumns: [],
  orderBy: [],
  limit: 10,
  tieStrategy: 'EXACT',
});

export const createModelOutputConfiguration = (): ModelOutputConfiguration => ({
  sourceTableName: '',
  targetModelId: '',
  writeMode: null,
  columnMappings: [],
});

export const createJdbcOutputConfiguration = (): JdbcOutputConfiguration => ({
  sourceTableName: '',
  dataSourceId: '',
  targetTableName: '',
  writeMode: null,
  columnMappings: [],
  upsertKeyColumns: [],
});

const createSnapshotDeletePolicy = () => ({
  action: 'KEEP' as const,
  maxDeleteRows: null,
  maxDeleteRatio: null,
});

export const createJdbcSnapshotSyncOutputConfiguration = (
): JdbcSnapshotSyncOutputConfiguration => ({
  sourceTableName: '',
  dataSourceId: '',
  targetTableName: '',
  keyColumns: [],
  columnMappings: [],
  deletePolicy: createSnapshotDeletePolicy(),
});

export const createModelSnapshotSyncOutputConfiguration = (
): ModelSnapshotSyncOutputConfiguration => ({
  sourceTableName: '',
  targetModelId: '',
  keyColumns: [],
  columnMappings: [],
  deletePolicy: createSnapshotDeletePolicy(),
});

export const createKafkaOutputConfiguration = (): KafkaOutputConfiguration => ({
  sourceTableName: '',
  dataSourceId: '',
  topic: '',
  valueSchema: { columns: [] },
  keyColumnName: '',
  columnMappings: [],
});

export const createFileOutputConfiguration = (): FileOutputConfiguration => ({
  sourceTableName: '',
  dataSourceId: '',
  targetPath: '',
  conflictPolicy: 'FAIL_IF_EXISTS',
  formatOptions: {
    type: 'CSV',
    header: true,
    delimiter: ',',
    quote: '"',
    escape: '\\',
    nullValue: '',
  },
});
