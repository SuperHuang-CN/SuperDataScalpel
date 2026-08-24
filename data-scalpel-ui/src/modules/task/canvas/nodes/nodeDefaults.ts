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
  SqlTransformConfiguration,
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

export const createModelInputConfiguration = (): ModelInputConfiguration => ({ models: [] });

export const createJdbcInputConfiguration = (): JdbcInputConfiguration => ({
  dataSourceId: '',
  tables: [],
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
  fileDatasetId: '', tables: [],
});

export const createHttpApiInputConfiguration = (): HttpApiInputConfiguration => ({
  dataSourceId: '', resources: [],
});

export const createSpatialServiceInputConfiguration = (): SpatialServiceInputConfiguration => ({
  dataSourceId: '', resources: [],
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
  outputColumns: [],
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
  outputColumns: [],
});

export const createRenameConfiguration = (): RenameConfiguration => ({ operations: [] } as unknown as RenameConfiguration);

export const createFilterConfiguration = (): FilterConfiguration => ({ operations: [] } as unknown as FilterConfiguration);

export const createSqlTransformConfiguration = (): SqlTransformConfiguration => ({
  outputTableName: '',
  sql: '',
});

export const createSelectColumnsConfiguration = (): SelectColumnsConfiguration => ({ operations: [] } as unknown as SelectColumnsConfiguration);

export const createDeriveColumnsConfiguration = (): DeriveColumnsConfiguration => ({
  globalDerivations: [],
  operations: [],
} as unknown as DeriveColumnsConfiguration);

export const createTypeCastConfiguration = (): TypeCastConfiguration => ({ operations: [] } as unknown as TypeCastConfiguration);

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

export const createDeduplicateConfiguration = (): DeduplicateConfiguration => ({ operations: [] } as unknown as DeduplicateConfiguration);

export const createNullHandlingConfiguration = (): NullHandlingConfiguration => ({ operations: [] } as unknown as NullHandlingConfiguration);

export const createValueMappingConfiguration = (): ValueMappingConfiguration => ({ operations: [] } as unknown as ValueMappingConfiguration);

export const createMaskFieldsConfiguration = (): MaskFieldsConfiguration => ({ operations: [] } as unknown as MaskFieldsConfiguration);

export const createJsonExtractConfiguration = (): JsonExtractConfiguration => ({ operations: [] } as unknown as JsonExtractConfiguration);

export const createWindowConfiguration = (): WindowConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  partitionByColumns: [],
  orderBy: [],
  functions: [],
});

export const createTopNConfiguration = (): TopNConfiguration => ({ operations: [] } as unknown as TopNConfiguration);

export const createModelOutputConfiguration = (): ModelOutputConfiguration => ({
  writes: [],
  sourceTableName: '', targetModelId: '', writeMode: 'OVERWRITE', columnMappings: [],
});

export const createJdbcOutputConfiguration = (): JdbcOutputConfiguration => ({
  dataSourceId: '',
  writes: [],
  sourceTableName: '', targetTableName: '', writeMode: 'OVERWRITE', columnMappings: [], upsertKeyColumns: [],
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
  dataSourceId: '',
  writes: [],
  sourceTableName: '', topic: '', valueSchema: { columns: [] }, keyColumnName: '', columnMappings: [],
});

export const createFileOutputConfiguration = (): FileOutputConfiguration => ({
  dataSourceId: '',
  writes: [],
  sourceTableName: '', targetPath: '', conflictPolicy: 'FAIL_IF_EXISTS',
  formatOptions: { type: 'CSV', header: true, delimiter: ',', quote: '"', escape: '\\', nullValue: '' },
});
