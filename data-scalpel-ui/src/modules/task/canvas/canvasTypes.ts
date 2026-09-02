import type {
  CrsReference,
  GeometryTypeDefinition,
  PlatformDataType,
  PlatformTypeDefinition,
} from '../../model';
import type { MaskingRuleDefinition } from '../model/maskingRule';
import {
  createAggregateConfiguration,
  createDeduplicateConfiguration,
  createDeriveColumnsConfiguration,
  createFileDatasetInputConfiguration,
  createFileOutputConfiguration,
  createFilterConfiguration,
  createGeometryConstructConfiguration,
  createGeometryBufferConfiguration,
  createGeometryExplodeConfiguration,
  createGeometryRepairConfiguration,
  createGeometrySerializeConfiguration,
  createGeometryValidateConfiguration,
  createHttpApiInputConfiguration,
  createSpatialServiceInputConfiguration,
  createJdbcInputConfiguration,
  createJdbcIncrementalInputConfiguration,
  createJdbcQueryInputConfiguration,
  createJdbcOutputConfiguration,
  createJdbcSnapshotSyncOutputConfiguration,
  createJoinConfiguration,
  createJsonExtractConfiguration,
  createKafkaInputConfiguration,
  createTdEngineTmqInputConfiguration,
  createKafkaOutputConfiguration,
  createModelInputConfiguration,
  createModelOutputConfiguration,
  createModelSnapshotSyncOutputConfiguration,
  createMaskFieldsConfiguration,
  createNullHandlingConfiguration,
  createRenameConfiguration,
  createSelectColumnsConfiguration,
  createSqlTransformConfiguration,
  createSpatialJoinConfiguration,
  createSpatialClipConfiguration,
  createSpatialAggregateConfiguration,
  createSpatialMeasureConfiguration,
  createSpatialTransformConfiguration,
  createStreamJoinConfiguration,
  createTopNConfiguration,
  createTypeCastConfiguration,
  createUnionConfiguration,
  createValueMappingConfiguration,
  createWindowConfiguration,
} from './nodes/nodeDefaults';
export type { PlatformDataType, PlatformTypeDefinition } from '../../model';
export type {
  MaskingRuleDefinition,
  MaskingStrategy,
} from '../model/maskingRule';

export const CANVAS_SCHEMA_VERSION = 4 as const;
export const CANVAS_SCHEMA_MINOR_VERSION = 8 as const;
export const CANVAS_LEGACY_SCHEMA_MINOR_VERSION = 0 as const;
export const CANVAS_FILTER_MAX_DEPTH = 12 as const;
export const CANVAS_FILTER_MAX_CONDITION_NODES = 256 as const;
export const CANVAS_FILTER_MAX_VALUES_PER_PREDICATE = 100 as const;
export const CANVAS_FILTER_MAX_SQL_EXPRESSION_LENGTH = 8_192 as const;
export const CANVAS_EXPRESSION_MAX_DEPTH = 16 as const;
export const CANVAS_EXPRESSION_MAX_NODES = 512 as const;
export const CANVAS_EXPRESSION_MAX_CASE_BRANCHES = 64 as const;
export const CANVAS_EXPRESSION_MAX_DERIVATIONS = 100 as const;
export const CANVAS_NULL_HANDLING_MAX_RULES = 100 as const;
export const CANVAS_VALUE_MAPPING_MAX_RULES = 100 as const;
export const CANVAS_VALUE_MAPPING_MAX_ENTRIES_PER_RULE = 200 as const;
export const CANVAS_VALUE_MAPPING_MAX_TOTAL_ENTRIES = 2_000 as const;
export const CANVAS_MASKING_MAX_FIELD_RULES = 100 as const;
export const CANVAS_JSON_EXTRACT_MAX_EXTRACTIONS = 100 as const;
export const CANVAS_JSON_EXTRACT_MAX_PATH_LENGTH = 512 as const;
export const CANVAS_WINDOW_MAX_FUNCTIONS = 100 as const;
export const CANVAS_WINDOW_MAX_OFFSET = 10_000 as const;
export const CANVAS_WINDOW_MAX_FRAME_OFFSET = 1_000_000 as const;
export const CANVAS_TOP_N_MAX_LIMIT = 1_000_000 as const;
export const CANVAS_SPATIAL_MEASURE_MAX_MEASUREMENTS = 32 as const;
export const CANVAS_SPATIAL_AGGREGATE_MAX_AGGREGATIONS = 32 as const;

export const CanvasNodeType = {
  ModelInput: 'MODEL_INPUT',
  JdbcInput: 'JDBC_INPUT',
  JdbcIncrementalInput: 'JDBC_INCREMENTAL_INPUT',
  JdbcQueryInput: 'JDBC_QUERY_INPUT',
  FileDatasetInput: 'FILE_DATASET_INPUT',
  HttpApiInput: 'HTTP_API_INPUT',
  SpatialServiceInput: 'SPATIAL_SERVICE_INPUT',
  KafkaInput: 'KAFKA_INPUT',
  TdEngineTmqInput: 'TDENGINE_TMQ_INPUT',
  Join: 'JOIN',
  GeometryConstruct: 'GEOMETRY_CONSTRUCT',
  SpatialTransform: 'SPATIAL_TRANSFORM',
  GeometryValidate: 'GEOMETRY_VALIDATE',
  GeometryRepair: 'GEOMETRY_REPAIR',
  GeometryBuffer: 'GEOMETRY_BUFFER',
  GeometryExplode: 'GEOMETRY_EXPLODE',
  SpatialMeasure: 'SPATIAL_MEASURE',
  GeometrySerialize: 'GEOMETRY_SERIALIZE',
  SpatialClip: 'SPATIAL_CLIP',
  SpatialAggregate: 'SPATIAL_AGGREGATE',
  SpatialJoin: 'SPATIAL_JOIN',
  StreamJoin: 'STREAM_JOIN',
  Rename: 'RENAME',
  Filter: 'FILTER',
  SqlTransform: 'SQL_TRANSFORM',
  SelectColumns: 'SELECT_COLUMNS',
  DeriveColumns: 'DERIVE_COLUMNS',
  TypeCast: 'TYPE_CAST',
  Aggregate: 'AGGREGATE',
  Union: 'UNION',
  Deduplicate: 'DEDUPLICATE',
  NullHandling: 'NULL_HANDLING',
  ValueMapping: 'VALUE_MAPPING',
  MaskFields: 'MASK_FIELDS',
  JsonExtract: 'JSON_EXTRACT',
  Window: 'WINDOW',
  TopN: 'TOP_N',
  ModelOutput: 'MODEL_OUTPUT',
  JdbcOutput: 'JDBC_OUTPUT',
  JdbcSnapshotSyncOutput: 'JDBC_SNAPSHOT_SYNC_OUTPUT',
  ModelSnapshotSyncOutput: 'MODEL_SNAPSHOT_SYNC_OUTPUT',
  KafkaOutput: 'KAFKA_OUTPUT',
  FileOutput: 'FILE_OUTPUT',
} as const;

export type CanvasNodeType = typeof CanvasNodeType[keyof typeof CanvasNodeType];

export const CanvasNodeCategory = {
  Input: 'INPUT',
  Processor: 'PROCESSOR',
  Output: 'OUTPUT',
} as const;

export type CanvasNodeCategory = typeof CanvasNodeCategory[keyof typeof CanvasNodeCategory];

export type CanvasExecutionMode = 'BATCH' | 'STREAMING';

export type CanvasDatasetKind = 'BOUNDED' | 'UNBOUNDED';

export interface CanvasNodeLayout {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface JdbcInputReadOption {
  name: string;
  value: string;
}

export interface JdbcInputTableSelection {
  tableName: string;
  readOptions: JdbcInputReadOption[];
}

export interface JdbcInputConfiguration {
  dataSourceId: string;
  tables: JdbcInputTableSelection[];
}

export type JdbcIncrementalStartPosition = 'LATEST' | 'EARLIEST' | 'AT_TIME';

export interface JdbcIncrementalInputConfiguration {
  dataSourceId: string;
  tableName: string;
  outputTableName: string;
  incrementalTimeColumn: string;
  startPosition: JdbcIncrementalStartPosition;
  startTime: string | null;
  cursorTimeZone: string;
  visibilityDelaySeconds: number;
  triggerIntervalSeconds?: number;
}

export interface JdbcQueryInputConfiguration {
  dataSourceId: string;
  sql: string;
  outputTableName: string;
  analyzedSqlSha256: string;
  outputColumns: CanvasColumnSchema[];
}

export interface ModelInputSelection {
  modelId: string;
}

export interface ModelInputConfiguration {
  models: ModelInputSelection[];
}

export interface FileDatasetInputTableSelection {
  fileDatasetTableId: string;
}

export interface FileDatasetInputConfiguration {
  fileDatasetId: string;
  tables: FileDatasetInputTableSelection[];
}

export interface HttpApiRuntimeParameter {
  name: string;
  value: string;
}

export interface HttpApiInputResourceSelection {
  resourceId: string;
  outputTableName: string;
  runtimeParameters: HttpApiRuntimeParameter[];
}

export interface HttpApiInputConfiguration {
  dataSourceId: string;
  resources: HttpApiInputResourceSelection[];
}

export interface SpatialServiceInputResourceSelection {
  resourceId: string;
  outputTableName: string;
}

export interface SpatialServiceInputConfiguration {
  dataSourceId: string;
  resources: SpatialServiceInputResourceSelection[];
}

export type KafkaStartingOffsets = 'EARLIEST' | 'LATEST';

export interface KafkaValueColumn {
  name: string;
  fieldType: PlatformDataType;
  length: number | null;
  precision: number | null;
  scale: number | null;
  nullable: boolean;
  comment: string | null;
}

export interface KafkaValueSchema {
  columns: KafkaValueColumn[];
}

export type KafkaInputValueFormat = 'JSON' | 'TEXT' | 'BINARY';
export type KafkaInputMetadataField = 'KEY' | 'TOPIC' | 'PARTITION' | 'OFFSET' | 'TIMESTAMP';

export interface KafkaInputConfiguration {
  dataSourceId: string;
  topic: string;
  valueSchema: KafkaValueSchema;
  outputTableName: string;
  startingOffsets: KafkaStartingOffsets | null;
  triggerIntervalSeconds?: number;
  valueFormat: KafkaInputValueFormat;
  metadataFields: KafkaInputMetadataField[];
}

export type TdEngineTmqStartingOffsets = 'EARLIEST' | 'LATEST';

export interface TdEngineTmqInputConfiguration {
  dataSourceId: string;
  topicName: string;
  catalogName: string;
  supertableName: string;
  topicDefinitionFingerprint: string;
  outputTableName: string;
  startingOffsets: TdEngineTmqStartingOffsets;
  maxOffsetsPerVGroupPerTrigger: number;
  triggerIntervalSeconds: number;
  eventTimeColumn: string | null;
  watermarkDelaySeconds: number | null;
}

export type JoinType = 'INNER' | 'LEFT' | 'RIGHT' | 'FULL';

export interface JoinCondition {
  leftColumnName: string;
  operator: 'EQUALS';
  rightColumnName: string;
}

export type JoinOutputColumnSource = 'LEFT' | 'RIGHT';

export interface JoinOutputColumn {
  sourceSide: JoinOutputColumnSource;
  sourceColumnName: string;
  outputColumnName: string;
  included: boolean;
}

export interface JoinConfiguration {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: JoinType | null;
  conditions: JoinCondition[];
  outputColumns: JoinOutputColumn[];
}

export interface SpatialTransformConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  targetCrs: CrsReference | null;
}

export type GeometryConstructSource =
  | { kind: 'WKT'; columnName: string }
  | { kind: 'WKB'; columnName: string }
  | { kind: 'GEOJSON'; columnName: string }
  | { kind: 'POINT_FROM_XY'; xColumnName: string; yColumnName: string };

export interface GeometryConstructConfiguration {
  sourceTableName: string;
  outputTableName: string;
  outputColumnName: string;
  source: GeometryConstructSource;
  targetGeometry: GeometryTypeDefinition | null;
}

export interface GeometryValidateConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  validColumnName: string;
  reasonColumnName: string | null;
}

export interface GeometryRepairConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
}

export interface GeometryBufferConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
  distance: number;
  mode: SpatialMeasureMode;
}

export interface GeometryExplodeConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
  partIndexColumnName: string | null;
}

export type SpatialMeasureMode = 'PLANAR' | 'SPHEROID';

export type SpatialMeasurement =
  | {
    kind: 'AREA';
    geometryColumnName: string;
    mode: SpatialMeasureMode;
    outputColumnName: string;
  }
  | {
    kind: 'LENGTH';
    geometryColumnName: string;
    mode: SpatialMeasureMode;
    outputColumnName: string;
  }
  | {
    kind: 'PERIMETER';
    geometryColumnName: string;
    mode: SpatialMeasureMode;
    outputColumnName: string;
  }
  | {
    kind: 'DISTANCE';
    leftGeometryColumnName: string;
    rightGeometryColumnName: string;
    mode: SpatialMeasureMode;
    outputColumnName: string;
  }
  | { kind: 'X'; geometryColumnName: string; outputColumnName: string }
  | { kind: 'Y'; geometryColumnName: string; outputColumnName: string };

export interface SpatialMeasureConfiguration {
  sourceTableName: string;
  outputTableName: string;
  measurements: SpatialMeasurement[];
}

export type GeometrySerializationFormat = 'WKT' | 'WKB' | 'GEOJSON';

export interface GeometrySerializeConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
  format: GeometrySerializationFormat;
}

export interface SpatialClipConfiguration {
  sourceTableName: string;
  maskTableName: string;
  outputTableName: string;
  sourceGeometryColumnName: string;
  maskGeometryColumnName: string;
  outputColumnName: string;
}

export type SpatialAggregationKind =
  | 'UNION'
  | 'INTERSECTION'
  | 'COLLECT'
  | 'ENVELOPE';

export interface SpatialAggregation {
  kind: SpatialAggregationKind;
  geometryColumnName: string;
  outputColumnName: string;
}

export interface SpatialAggregateConfiguration {
  sourceTableName: string;
  outputTableName: string;
  groupByColumns: string[];
  aggregations: SpatialAggregation[];
}

export type SpatialPredicate =
  | 'INTERSECTS'
  | 'CONTAINS'
  | 'WITHIN'
  | 'COVERS'
  | 'COVERED_BY'
  | 'TOUCHES'
  | 'OVERLAPS'
  | 'CROSSES'
  | 'EQUALS';

export interface SpatialJoinCondition {
  leftGeometryColumnName: string;
  predicate: SpatialPredicate | null;
  rightGeometryColumnName: string;
}

export interface SpatialJoinConfiguration {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: 'INNER';
  conditions: SpatialJoinCondition[];
}

export type StreamJoinType = 'INNER' | 'LEFT';

export interface StreamJoinConfiguration {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: StreamJoinType | null;
  conditions: JoinCondition[];
  outputColumns: JoinOutputColumn[];
}

export type ProcessorOutput =
  | { mode: 'REPLACE_SOURCE'; outputTableName: string | null }
  | { mode: 'CREATE_NEW_TABLE'; outputTableName: string };

export interface RenameOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  columnMappings: CanvasColumnMapping[];
}

export interface RenameConfiguration {
  operations?: RenameOperation[];
  /** @deprecated Inspector compatibility view; never serialized. */
  sourceTableName: string;
  /** @deprecated Inspector compatibility view; never serialized. */
  outputTableName: string;
  columnMappings: CanvasColumnMapping[];
}

export type FilterGroupOperator = 'AND' | 'OR';

export type FilterOperator =
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'GREATER_THAN'
  | 'GREATER_THAN_OR_EQUALS'
  | 'LESS_THAN'
  | 'LESS_THAN_OR_EQUALS'
  | 'IN'
  | 'NOT_IN'
  | 'IS_NULL'
  | 'IS_NOT_NULL'
  | 'CONTAINS'
  | 'STARTS_WITH'
  | 'ENDS_WITH';

export interface CanvasLiteral {
  dataType: PlatformDataType;
  value: string | null;
}

export type CanvasFilterCondition = CanvasFilterGroup | CanvasFieldPredicate;

export interface CanvasFilterGroup {
  kind: 'GROUP';
  operator: FilterGroupOperator;
  children: CanvasFilterCondition[];
}

export interface CanvasFieldPredicate {
  kind: 'PREDICATE';
  columnName: string;
  operator: FilterOperator;
  values: CanvasLiteral[];
}

export type FilterConditionMode = 'STRUCTURED' | 'SQL_EXPRESSION';

export interface FilterOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  mode: FilterConditionMode;
  condition: CanvasFilterCondition;
  sqlExpression: string;
}

export interface FilterConfiguration {
  operations?: FilterOperation[];
  sourceTableName: string;
  outputTableName: string;
  mode: FilterConditionMode;
  condition: CanvasFilterCondition;
  sqlExpression: string;
}

export interface SqlTransformConfiguration {
  outputTableName: string;
  sql: string;
}

export interface SelectColumnsOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  columns: string[];
}

export interface SelectColumnsConfiguration {
  operations?: SelectColumnsOperation[];
  sourceTableName: string;
  outputTableName: string;
  columns: string[];
}

export type DeriveBinaryOperator =
  | 'ADD'
  | 'SUBTRACT'
  | 'MULTIPLY'
  | 'DIVIDE'
  | 'MODULO';

export type DeriveFunction =
  | 'TRIM'
  | 'LTRIM'
  | 'RTRIM'
  | 'LOWER'
  | 'UPPER'
  | 'REPLACE'
  | 'SUBSTRING'
  | 'COALESCE'
  | 'CONCAT'
  | 'DATE_FORMAT'
  | 'DATE_ADD'
  | 'DATE_SUB';

export type CanvasExpression =
  | CanvasColumnExpression
  | CanvasLiteralExpression
  | CanvasRuntimeValueExpression
  | CanvasBinaryExpression
  | CanvasFunctionExpression
  | CanvasCaseWhenExpression;

export interface CanvasColumnExpression {
  kind: 'COLUMN';
  columnName: string;
}

export interface CanvasLiteralExpression {
  kind: 'LITERAL';
  literal: CanvasLiteral;
}

export type CanvasRuntimeValue = 'EXECUTION_ID' | 'EXECUTION_STARTED_AT';

export interface CanvasRuntimeValueExpression {
  kind: 'RUNTIME_VALUE';
  value: CanvasRuntimeValue;
}

export interface CanvasBinaryExpression {
  kind: 'BINARY';
  operator: DeriveBinaryOperator;
  left: CanvasExpression;
  right: CanvasExpression;
}

export interface CanvasFunctionExpression {
  kind: 'FUNCTION';
  function: DeriveFunction;
  arguments: CanvasExpression[];
}

export interface CanvasCaseWhenBranch {
  condition: CanvasFilterCondition;
  result: CanvasExpression;
}

export interface CanvasCaseWhenExpression {
  kind: 'CASE_WHEN';
  branches: CanvasCaseWhenBranch[];
  elseExpression: CanvasExpression | null;
}

export interface ColumnDerivation {
  targetColumnName: string;
  expression: CanvasExpression;
}

export interface DeriveColumnsOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  derivations: ColumnDerivation[];
}

export interface DeriveColumnsConfiguration {
  globalDerivations?: ColumnDerivation[];
  operations?: DeriveColumnsOperation[];
  sourceTableName: string;
  outputTableName: string;
  derivations: ColumnDerivation[];
}

export type CastFailureStrategy = 'FAIL' | 'SET_NULL';

export type EpochTimestampUnit = 'SECONDS' | 'MILLISECONDS' | 'MICROSECONDS';

export type StringTimestampZoneMode = 'SOURCE_TIME_ZONE' | 'EMBEDDED_OFFSET';

export interface StringTemporalParseOptions {
  pattern: string;
  zoneMode: StringTimestampZoneMode | null;
  sourceTimeZone: string | null;
}

export interface TemporalStringFormatOptions {
  pattern: string;
  /** Only TIMESTAMP uses a target zone; DATE and TIMESTAMP_NTZ keep this null. */
  targetTimeZone: string | null;
}

export interface ColumnTypeCast {
  columnName: string;
  targetType: PlatformTypeDefinition;
  failureStrategy: CastFailureStrategy | null;
  /** LONG→TIMESTAMP since 4.2; DATE/TIMESTAMP→LONG since 4.8. */
  epochTimestampUnit?: EpochTimestampUnit | null;
  /** Absent on pre-4.3 definitions, which retain Spark's legacy STRING cast semantics. */
  stringTemporalParseOptions?: StringTemporalParseOptions | null;
  /** Absent on pre-4.7 definitions, which retain Spark's legacy temporal STRING format. */
  temporalStringFormatOptions?: TemporalStringFormatOptions | null;
}

export interface TypeCastOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  casts: ColumnTypeCast[];
}

export interface TypeCastConfiguration {
  operations?: TypeCastOperation[];
  sourceTableName: string;
  outputTableName: string;
  casts: ColumnTypeCast[];
}

export type AggregateFunction = 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX';

export interface AggregateItem {
  function: AggregateFunction | null;
  sourceColumnName: string | null;
  outputColumnName: string;
  distinct: boolean;
}

export interface AggregateConfiguration {
  sourceTableName: string;
  outputTableName: string;
  groupByColumns: string[];
  aggregations: AggregateItem[];
}

export type UnionMode = 'ALL' | 'DISTINCT';

export interface UnionConfiguration {
  inputTableNames: string[];
  outputTableName: string;
  mode: UnionMode | null;
}

export type DeduplicateKeepStrategy = 'ANY' | 'FIRST' | 'LAST';
export type SortDirection = 'ASC' | 'DESC';
export type NullOrdering = 'FIRST' | 'LAST';

export interface SortField {
  columnName: string;
  direction: SortDirection | null;
  nullOrdering: NullOrdering | null;
}

export interface DeduplicateOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  keyColumns: string[];
  keepStrategy: DeduplicateKeepStrategy | null;
  orderBy: SortField[];
}

export interface DeduplicateConfiguration {
  operations?: DeduplicateOperation[];
  sourceTableName: string;
  outputTableName: string;
  keyColumns: string[];
  keepStrategy: DeduplicateKeepStrategy | null;
  orderBy: SortField[];
}

export type NullMatchMode = 'ANY_NULL' | 'ALL_NULL';

export type NullHandlingRule = DropNullRowsRule | FillNullLiteralRule;

export interface DropNullRowsRule {
  kind: 'DROP_ROW';
  columnNames: string[];
  matchMode: NullMatchMode;
}

export interface FillNullLiteralRule {
  kind: 'FILL_LITERAL';
  columnName: string;
  value: CanvasLiteral;
}

export interface NullHandlingOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  rules: NullHandlingRule[];
}

export interface NullHandlingConfiguration {
  operations?: NullHandlingOperation[];
  sourceTableName: string;
  outputTableName: string;
  rules: NullHandlingRule[];
}

export type ValueMappingUnmatchedStrategy =
  | 'KEEP'
  | 'SET_NULL'
  | 'SET_LITERAL'
  | 'ERROR';

export interface ValueMappingEntry {
  sourceValue: CanvasLiteral;
  targetValue: CanvasLiteral | null;
}

export interface ValueMappingRule {
  columnName: string;
  entries: ValueMappingEntry[];
  unmatchedStrategy: ValueMappingUnmatchedStrategy;
  unmatchedValue: CanvasLiteral | null;
}

export interface ValueMappingOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  rules: ValueMappingRule[];
}

export interface ValueMappingConfiguration {
  operations?: ValueMappingOperation[];
  sourceTableName: string;
  outputTableName: string;
  rules: ValueMappingRule[];
}

export type MaskingRuleSource = 'GLOBAL' | 'INLINE';

export interface MaskingSourceRuleReference {
  ruleId: string;
  ruleCode: string;
  ruleName: string;
}

export interface MaskFieldRule {
  fieldName: string;
  ruleSource: MaskingRuleSource;
  sourceRuleRef: MaskingSourceRuleReference | null;
  definition: MaskingRuleDefinition;
}

export interface MaskFieldsOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  fieldRules: MaskFieldRule[];
}

export interface MaskFieldsConfiguration {
  operations?: MaskFieldsOperation[];
  sourceTableName: string;
  outputTableName: string;
  fieldRules: MaskFieldRule[];
}

export type JsonExtractFailureStrategy = 'ERROR' | 'SET_NULL';

export interface JsonExtraction {
  jsonPath: string;
  outputColumnName: string;
  targetType: PlatformTypeDefinition;
}

export interface JsonExtractOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  sourceColumnName: string;
  extractions: JsonExtraction[];
  failureStrategy: JsonExtractFailureStrategy;
}

export interface JsonExtractConfiguration {
  operations?: JsonExtractOperation[];
  sourceTableName: string;
  outputTableName: string;
  sourceColumnName: string;
  extractions: JsonExtraction[];
  failureStrategy: JsonExtractFailureStrategy;
}

export type WindowFunctionItem =
  | RankingWindowFunction
  | OffsetWindowFunction
  | AggregateWindowFunction
  | ValueWindowFunction;

export interface RankingWindowFunction {
  kind: 'ROW_NUMBER' | 'RANK' | 'DENSE_RANK';
  outputColumnName: string;
}

export interface OffsetWindowFunction {
  kind: 'LAG' | 'LEAD';
  sourceColumnName: string;
  offset: number;
  defaultValue: CanvasLiteral | null;
  outputColumnName: string;
}

export interface AggregateWindowFunction {
  kind: 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX';
  sourceColumnName: string | null;
  outputColumnName: string;
  frame: RowsWindowFrame;
}

export interface ValueWindowFunction {
  kind: 'FIRST_VALUE' | 'LAST_VALUE';
  sourceColumnName: string;
  ignoreNulls: boolean;
  outputColumnName: string;
  frame: RowsWindowFrame;
}

export interface RowsWindowFrame {
  type: 'ROWS';
  start: RowsFrameBoundary;
  end: RowsFrameBoundary;
}

export type RowsFrameBoundary =
  | { kind: 'UNBOUNDED_PRECEDING' }
  | { kind: 'PRECEDING'; offset: number }
  | { kind: 'CURRENT_ROW' }
  | { kind: 'FOLLOWING'; offset: number }
  | { kind: 'UNBOUNDED_FOLLOWING' };

export interface WindowConfiguration {
  sourceTableName: string;
  outputTableName: string;
  partitionByColumns: string[];
  orderBy: SortField[];
  functions: WindowFunctionItem[];
}

export type TopNTieStrategy = 'EXACT' | 'WITH_TIES';

export interface TopNOperation {
  operationId: string;
  sourceTableName: string;
  output: ProcessorOutput;
  partitionByColumns: string[];
  orderBy: SortField[];
  limit: number;
  tieStrategy: TopNTieStrategy;
}

export interface TopNConfiguration {
  operations?: TopNOperation[];
  sourceTableName: string;
  outputTableName: string;
  partitionByColumns: string[];
  orderBy: SortField[];
  limit: number;
  tieStrategy: TopNTieStrategy;
}

export type JdbcWriteMode = 'APPEND' | 'OVERWRITE' | 'UPSERT';

export interface CanvasColumnMapping {
  sourceColumnName: string;
  targetColumnName: string;
}

export type JdbcColumnMapping = CanvasColumnMapping;

export interface JdbcOutputWrite {
  writeId: string;
  sourceTableName: string;
  targetTableName: string;
  writeMode: JdbcWriteMode | null;
  columnMappings: CanvasColumnMapping[];
  upsertKeyColumns: string[];
}

export interface JdbcOutputConfiguration {
  dataSourceId: string;
  writes?: JdbcOutputWrite[];
  /** @deprecated Canvas 4.0 transitional editor field; normalized before persistence. */
  sourceTableName: string;
  targetTableName: string;
  writeMode: JdbcWriteMode | null;
  columnMappings: CanvasColumnMapping[];
  upsertKeyColumns: string[];
}

export interface ModelOutputWrite {
  writeId: string;
  sourceTableName: string;
  targetModelId: string;
  writeMode: JdbcWriteMode | null;
  columnMappings: CanvasColumnMapping[];
}

export interface ModelOutputConfiguration {
  writes?: ModelOutputWrite[];
  /** @deprecated Canvas 4.0 transitional editor field; normalized before persistence. */
  sourceTableName?: string;
  targetModelId?: string;
  writeMode?: JdbcWriteMode | null;
  columnMappings?: CanvasColumnMapping[];
}

export type SnapshotTargetOnlyAction = 'KEEP' | 'DELETE';

export interface SnapshotDeletePolicy {
  action: SnapshotTargetOnlyAction;
  maxDeleteRows: number | null;
  maxDeleteRatio: number | null;
}

export interface SnapshotSyncConfiguration {
  sourceTableName: string;
  keyColumns: string[];
  columnMappings: CanvasColumnMapping[];
  deletePolicy: SnapshotDeletePolicy;
}

export interface JdbcSnapshotSyncOutputConfiguration extends SnapshotSyncConfiguration {
  dataSourceId: string;
  targetTableName: string;
}

export interface ModelSnapshotSyncOutputConfiguration extends SnapshotSyncConfiguration {
  targetModelId: string;
}

export type KafkaOutputValueFormat = 'JSON' | 'TEXT' | 'BINARY';

export interface KafkaOutputWrite {
  writeId: string;
  sourceTableName: string;
  topic: string;
  /** Null identifies the Canvas 4.0-4.5 JSON schema/mapping compatibility mode. */
  valueFormat: KafkaOutputValueFormat | null;
  valueColumnNames: string[];
  keyColumnName: string;
  valueSchema: KafkaValueSchema | null;
  columnMappings: CanvasColumnMapping[];
}

export interface KafkaOutputConfiguration {
  dataSourceId: string;
  writes?: KafkaOutputWrite[];
  /** @deprecated Canvas 4.0 transitional editor field; normalized before persistence. */
  sourceTableName: string;
  topic: string;
  valueSchema: KafkaValueSchema;
  keyColumnName: string;
  columnMappings: CanvasColumnMapping[];
}

export type FileOutputConflictPolicy = 'FAIL_IF_EXISTS' | 'OVERWRITE';

export type ShapefilePackageMode = 'ZIP' | 'COMPONENT_DIRECTORY';

export type ShapefileShapeType = 'POINT' | 'MULTIPOINT' | 'POLYLINE' | 'POLYGON';

export interface ShapefileAttributeMapping {
  sourceColumnName: string;
  targetFieldName: string;
  targetStringByteLength: number | null;
}

export type GeoParquetCompressionCodec = 'SNAPPY' | 'ZSTD';

export type GeoParquetCoveringMode = 'NONE' | 'ROW_BBOX';

export type FileOutputFormatOptions =
  | {
    type: 'CSV';
    header: boolean;
    delimiter: string;
    quote: string;
    escape: string;
    nullValue: string;
  }
  | {
    type: 'JSON_LINES';
    ignoreNullFields: boolean;
  }
  | {
    type: 'PARQUET';
  }
  | {
    type: 'SHAPEFILE';
    baseName: string;
    packageMode: ShapefilePackageMode;
    geometryColumnName: string;
    targetShapeType: ShapefileShapeType;
    attributeMappings: ShapefileAttributeMapping[];
  }
  | {
    type: 'GEOPARQUET';
    geometryColumnName: string;
    compression: GeoParquetCompressionCodec;
    coveringMode: GeoParquetCoveringMode;
  }
  | {
    type: 'GEOJSON';
    baseName: string;
    geometryColumnName: string;
    idColumnName: string | null;
    ignoreNullProperties: boolean;
  };

export interface FileOutputWrite {
  writeId: string;
  sourceTableName: string;
  targetPath: string;
  conflictPolicy: FileOutputConflictPolicy;
  formatOptions: FileOutputFormatOptions;
}

export interface FileOutputConfiguration {
  dataSourceId: string;
  writes?: FileOutputWrite[];
  /** @deprecated Canvas 4.0 transitional editor field; normalized before persistence. */
  sourceTableName: string;
  targetPath: string;
  conflictPolicy: FileOutputConflictPolicy;
  formatOptions: FileOutputFormatOptions;
}

export const normalizeFileOutputPath = (value: string): string => (
  value.trim().replace(/\/{2,}/g, '/').replace(/\/+$/, '')
);

interface CanvasNodeBase<T extends CanvasNodeType, C> {
  id: string;
  type: T;
  name: string;
  layout: CanvasNodeLayout;
  configuration: C;
}

export type ModelInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.ModelInput,
  ModelInputConfiguration
>;

export type JdbcInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.JdbcInput,
  JdbcInputConfiguration
>;

export type JdbcIncrementalInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.JdbcIncrementalInput,
  JdbcIncrementalInputConfiguration
>;

export type JdbcQueryInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.JdbcQueryInput,
  JdbcQueryInputConfiguration
>;

export type FileDatasetInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.FileDatasetInput,
  FileDatasetInputConfiguration
>;

export type HttpApiInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.HttpApiInput,
  HttpApiInputConfiguration
>;

export type SpatialServiceInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialServiceInput,
  SpatialServiceInputConfiguration
>;

export type KafkaInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.KafkaInput,
  KafkaInputConfiguration
>;

export type TdEngineTmqInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.TdEngineTmqInput,
  TdEngineTmqInputConfiguration
>;

export type JoinNodeDefinition = CanvasNodeBase<typeof CanvasNodeType.Join, JoinConfiguration>;

export type GeometryConstructNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.GeometryConstruct,
  GeometryConstructConfiguration
>;

export type SpatialTransformNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialTransform,
  SpatialTransformConfiguration
>;

export type GeometryValidateNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.GeometryValidate,
  GeometryValidateConfiguration
>;

export type GeometryRepairNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.GeometryRepair,
  GeometryRepairConfiguration
>;

export type GeometryBufferNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.GeometryBuffer,
  GeometryBufferConfiguration
>;

export type GeometryExplodeNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.GeometryExplode,
  GeometryExplodeConfiguration
>;

export type SpatialMeasureNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialMeasure,
  SpatialMeasureConfiguration
>;

export type GeometrySerializeNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.GeometrySerialize,
  GeometrySerializeConfiguration
>;

export type SpatialClipNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialClip,
  SpatialClipConfiguration
>;

export type SpatialAggregateNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialAggregate,
  SpatialAggregateConfiguration
>;

export type SpatialJoinNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialJoin,
  SpatialJoinConfiguration
>;

export type StreamJoinNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.StreamJoin,
  StreamJoinConfiguration
>;

export type RenameNodeDefinition = CanvasNodeBase<typeof CanvasNodeType.Rename, RenameConfiguration>;

export type FilterNodeDefinition = CanvasNodeBase<typeof CanvasNodeType.Filter, FilterConfiguration>;

export type SqlTransformNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SqlTransform,
  SqlTransformConfiguration
>;

export type SelectColumnsNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SelectColumns,
  SelectColumnsConfiguration
>;

export type DeriveColumnsNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.DeriveColumns,
  DeriveColumnsConfiguration
>;

export type TypeCastNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.TypeCast,
  TypeCastConfiguration
>;

export type AggregateNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.Aggregate,
  AggregateConfiguration
>;

export type UnionNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.Union,
  UnionConfiguration
>;

export type DeduplicateNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.Deduplicate,
  DeduplicateConfiguration
>;

export type NullHandlingNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.NullHandling,
  NullHandlingConfiguration
>;

export type ValueMappingNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.ValueMapping,
  ValueMappingConfiguration
>;

export type MaskFieldsNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.MaskFields,
  MaskFieldsConfiguration
>;

export type JsonExtractNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.JsonExtract,
  JsonExtractConfiguration
>;

export type WindowNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.Window,
  WindowConfiguration
>;

export type TopNNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.TopN,
  TopNConfiguration
>;

export type JdbcOutputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.JdbcOutput,
  JdbcOutputConfiguration
>;

export type ModelOutputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.ModelOutput,
  ModelOutputConfiguration
>;

export type JdbcSnapshotSyncOutputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.JdbcSnapshotSyncOutput,
  JdbcSnapshotSyncOutputConfiguration
>;

export type ModelSnapshotSyncOutputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.ModelSnapshotSyncOutput,
  ModelSnapshotSyncOutputConfiguration
>;

export type KafkaOutputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.KafkaOutput,
  KafkaOutputConfiguration
>;

export type FileOutputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.FileOutput,
  FileOutputConfiguration
>;

export type CanvasNodeDefinition =
  | ModelInputNodeDefinition
  | JdbcInputNodeDefinition
  | JdbcIncrementalInputNodeDefinition
  | JdbcQueryInputNodeDefinition
  | FileDatasetInputNodeDefinition
  | HttpApiInputNodeDefinition
  | SpatialServiceInputNodeDefinition
  | KafkaInputNodeDefinition
  | TdEngineTmqInputNodeDefinition
  | JoinNodeDefinition
  | GeometryConstructNodeDefinition
  | SpatialTransformNodeDefinition
  | GeometryValidateNodeDefinition
  | GeometryRepairNodeDefinition
  | GeometryBufferNodeDefinition
  | GeometryExplodeNodeDefinition
  | SpatialMeasureNodeDefinition
  | GeometrySerializeNodeDefinition
  | SpatialClipNodeDefinition
  | SpatialAggregateNodeDefinition
  | SpatialJoinNodeDefinition
  | StreamJoinNodeDefinition
  | RenameNodeDefinition
  | FilterNodeDefinition
  | SqlTransformNodeDefinition
  | SelectColumnsNodeDefinition
  | DeriveColumnsNodeDefinition
  | TypeCastNodeDefinition
  | AggregateNodeDefinition
  | UnionNodeDefinition
  | DeduplicateNodeDefinition
  | NullHandlingNodeDefinition
  | ValueMappingNodeDefinition
  | MaskFieldsNodeDefinition
  | JsonExtractNodeDefinition
  | WindowNodeDefinition
  | TopNNodeDefinition
  | ModelOutputNodeDefinition
  | JdbcOutputNodeDefinition
  | JdbcSnapshotSyncOutputNodeDefinition
  | ModelSnapshotSyncOutputNodeDefinition
  | KafkaOutputNodeDefinition
  | FileOutputNodeDefinition;

export type CanvasNodeByType<T extends CanvasNodeType> =
  Extract<CanvasNodeDefinition, { type: T }>;

export type CanvasNodeConfigurationByType<T extends CanvasNodeType> =
  CanvasNodeByType<T>['configuration'];

export type CanvasNodeConfigurationUpdate =
  | Pick<ModelInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<JdbcInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<JdbcIncrementalInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<JdbcQueryInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<FileDatasetInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<HttpApiInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialServiceInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<KafkaInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<TdEngineTmqInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<JoinNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<GeometryConstructNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialTransformNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<GeometryValidateNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<GeometryRepairNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<GeometryBufferNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<GeometryExplodeNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialMeasureNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<GeometrySerializeNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialClipNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialAggregateNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialJoinNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<StreamJoinNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<RenameNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<FilterNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SqlTransformNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SelectColumnsNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<DeriveColumnsNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<TypeCastNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<AggregateNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<UnionNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<DeduplicateNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<NullHandlingNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<ValueMappingNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<MaskFieldsNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<JsonExtractNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<WindowNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<TopNNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<ModelOutputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<JdbcOutputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<JdbcSnapshotSyncOutputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<ModelSnapshotSyncOutputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<KafkaOutputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<FileOutputNodeDefinition, 'id' | 'type' | 'configuration'>;

export type CanvasNodeConfigurationUpdateByType<T extends CanvasNodeType> =
  T extends CanvasNodeType
    ? Pick<CanvasNodeByType<T>, 'id' | 'type' | 'configuration'>
    : never;

export type CanvasNodeConfiguration = CanvasNodeDefinition['configuration'];

export interface CanvasEdgeDefinition {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
}

export interface CanvasDefinition {
  schemaVersion: typeof CANVAS_SCHEMA_VERSION;
  schemaMinorVersion: typeof CANVAS_SCHEMA_MINOR_VERSION;
  nodes: CanvasNodeDefinition[];
  edges: CanvasEdgeDefinition[];
}

export interface CanvasColumnSchema {
  name: string;
  fieldType: PlatformDataType;
  length: number | null;
  precision: number | null;
  scale: number | null;
  nullable: boolean;
  defaultValue: string | null;
  autoIncrement: boolean;
  generated: boolean;
  comment: string | null;
  geometry: GeometryTypeDefinition | null;
}

export type CanvasTableOrigin =
  | {
    kind: 'JDBC';
    dataSourceId: string;
    tableName: string;
    modelId: null;
    modelCode: null;
    modelSchemaVersion: null;
  }
  | {
    kind: 'JDBC_INCREMENTAL';
    dataSourceId: string;
    tableName: string;
    modelId: null;
    modelCode: null;
    modelSchemaVersion: null;
  }
  | {
    kind: 'JDBC_QUERY';
    dataSourceId: string;
    tableName: string;
    modelId: null;
    modelCode: null;
    modelSchemaVersion: null;
  }
  | {
    kind: 'MODEL';
    dataSourceId: null;
    tableName: null;
    modelId: string;
    modelCode: string;
    modelSchemaVersion: number;
  }
  | {
    kind: 'HTTP_API';
    dataSourceId: string;
    tableName: string;
    modelId: null;
    modelCode: null;
    modelSchemaVersion: null;
  }
  | {
    kind: 'KAFKA';
    dataSourceId: string;
    tableName: string;
    modelId: null;
    modelCode: null;
    modelSchemaVersion: null;
  }
  | {
    kind: 'TDENGINE_TMQ';
    dataSourceId: string;
    tableName: string;
    modelId: null;
    modelCode: null;
    modelSchemaVersion: null;
    topicName: string;
    catalogName: string;
    supertableName: string;
  }
  | {
    kind: 'FILE_DATASET';
    dataSourceId: null;
    tableName: null;
    modelId: null;
    modelCode: null;
    modelSchemaVersion: null;
    fileDatasetTableId: string;
  };

export interface CanvasTableSchema {
  name: string;
  origin: CanvasTableOrigin | null;
  columns: CanvasColumnSchema[];
  datasetKind: CanvasDatasetKind;
  eventTimeColumn: string | null;
  watermarkDelay: string | null;
}

export type CanvasValidationSeverity = 'ERROR' | 'WARNING';

export interface CanvasValidationIssue {
  code: string;
  severity: CanvasValidationSeverity;
  message: string;
  nodeId: string | null;
  path: string | null;
}

export interface CanvasNodeValidationResult {
  nodeId: string;
  issues: CanvasValidationIssue[];
  inputTables: CanvasTableSchema[];
  outputTables: CanvasTableSchema[];
}

export interface CanvasValidationResult {
  valid: boolean;
  canvasIssues: CanvasValidationIssue[];
  nodeResults: Map<string, CanvasNodeValidationResult>;
}

export type CanvasNodeValidationStatus = 'UNCHECKED' | 'UNCONFIGURED' | 'VALID' | 'WARNING' | 'ERROR';

export interface CanvasNodeValidationBadge {
  status: CanvasNodeValidationStatus;
  message: string;
}

export interface CanvasNodeRuntimeCompilation {
  inputTables: CanvasTableSchema[];
  outputTables: CanvasTableSchema[];
}

export type CanvasNodeRuntimeSummary =
  | {
    kind: 'JDBC';
    dataSourceName: string;
    dataSourceType: string;
    qualifiedTableName: string;
    primaryKeyColumns?: string[];
    tables?: Array<{
      tableName: string;
      primaryKeyColumns: string[];
    }>;
  }
  | {
    kind: 'MODEL';
    modelName: string;
    modelCode: string;
    modelSchemaVersion: number;
    dataSourceName: string;
    qualifiedTableName: string;
  }
  | {
    kind: 'HTTP_API';
    dataSourceName: string;
    qualifiedTableName: string;
  }
  | {
    kind: 'KAFKA';
    dataSourceName: string;
    qualifiedTableName: string;
    fieldCount: number;
  }
  | {
    kind: 'TDENGINE_TMQ';
    dataSourceName: string;
    qualifiedTableName: string;
    fieldCount: number;
  }
  | {
    kind: 'FILE_DATASET';
    fileDatasetName: string;
    tableName: string;
    tableCode: string;
    datasetType: string;
    status: string;
    geometry: {
      fieldName: string;
      kind: GeometryTypeDefinition['kind'];
      crs: GeometryTypeDefinition['crs'];
      dimension: GeometryTypeDefinition['dimension'];
    } | null;
    tables?: Array<{
      fileDatasetTableId: string;
      tableName: string;
      tableCode: string;
      datasetType: string;
      status: string;
      schema: CanvasTableSchema;
    }>;
  }
  | {
    kind: 'S3';
    dataSourceName: string;
    bucketName: string;
  };

interface CanvasNodeRuntimeBase<T extends CanvasNodeType, C> {
  type: T;
  name: string;
  configuration: C;
  readOnly?: boolean;
  validation?: CanvasNodeValidationBadge;
  summary?: CanvasNodeRuntimeSummary;
  compilation?: CanvasNodeRuntimeCompilation;
}

export type CanvasNodeRuntimeData =
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.ModelInput, ModelInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.JdbcInput, JdbcInputConfiguration>
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.JdbcIncrementalInput,
    JdbcIncrementalInputConfiguration
  >
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.JdbcQueryInput, JdbcQueryInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.FileDatasetInput, FileDatasetInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.HttpApiInput, HttpApiInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.SpatialServiceInput, SpatialServiceInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.KafkaInput, KafkaInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.TdEngineTmqInput, TdEngineTmqInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.Join, JoinConfiguration>
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.GeometryConstruct,
    GeometryConstructConfiguration
  >
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.SpatialTransform, SpatialTransformConfiguration>
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.GeometryValidate,
    GeometryValidateConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.GeometryRepair,
    GeometryRepairConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.GeometryBuffer,
    GeometryBufferConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.GeometryExplode,
    GeometryExplodeConfiguration
  >
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.SpatialMeasure, SpatialMeasureConfiguration>
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.GeometrySerialize,
    GeometrySerializeConfiguration
  >
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.SpatialClip, SpatialClipConfiguration>
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialAggregate,
    SpatialAggregateConfiguration
  >
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.SpatialJoin, SpatialJoinConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.StreamJoin, StreamJoinConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.Rename, RenameConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.Filter, FilterConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.SqlTransform, SqlTransformConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.SelectColumns, SelectColumnsConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.DeriveColumns, DeriveColumnsConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.TypeCast, TypeCastConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.Aggregate, AggregateConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.Union, UnionConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.Deduplicate, DeduplicateConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.NullHandling, NullHandlingConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.ValueMapping, ValueMappingConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.MaskFields, MaskFieldsConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.JsonExtract, JsonExtractConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.Window, WindowConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.TopN, TopNConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.ModelOutput, ModelOutputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.JdbcOutput, JdbcOutputConfiguration>
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.JdbcSnapshotSyncOutput,
    JdbcSnapshotSyncOutputConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.ModelSnapshotSyncOutput,
    ModelSnapshotSyncOutputConfiguration
  >
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.KafkaOutput, KafkaOutputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.FileOutput, FileOutputConfiguration>;

export type CanvasNodeRuntimeDataByType<T extends CanvasNodeType> =
  Extract<CanvasNodeRuntimeData, { type: T }>;

const emptyConfigurationFactories: Record<
  CanvasNodeType,
  () => CanvasNodeConfiguration
> = {
  [CanvasNodeType.ModelInput]: createModelInputConfiguration,
  [CanvasNodeType.JdbcInput]: createJdbcInputConfiguration,
  [CanvasNodeType.JdbcIncrementalInput]: createJdbcIncrementalInputConfiguration,
  [CanvasNodeType.JdbcQueryInput]: createJdbcQueryInputConfiguration,
  [CanvasNodeType.FileDatasetInput]: createFileDatasetInputConfiguration,
  [CanvasNodeType.HttpApiInput]: createHttpApiInputConfiguration,
  [CanvasNodeType.SpatialServiceInput]: createSpatialServiceInputConfiguration,
  [CanvasNodeType.KafkaInput]: createKafkaInputConfiguration,
  [CanvasNodeType.TdEngineTmqInput]: createTdEngineTmqInputConfiguration,
  [CanvasNodeType.Join]: createJoinConfiguration,
  [CanvasNodeType.GeometryConstruct]: createGeometryConstructConfiguration,
  [CanvasNodeType.SpatialTransform]: createSpatialTransformConfiguration,
  [CanvasNodeType.GeometryValidate]: createGeometryValidateConfiguration,
  [CanvasNodeType.GeometryRepair]: createGeometryRepairConfiguration,
  [CanvasNodeType.GeometryBuffer]: createGeometryBufferConfiguration,
  [CanvasNodeType.GeometryExplode]: createGeometryExplodeConfiguration,
  [CanvasNodeType.SpatialMeasure]: createSpatialMeasureConfiguration,
  [CanvasNodeType.GeometrySerialize]: createGeometrySerializeConfiguration,
  [CanvasNodeType.SpatialClip]: createSpatialClipConfiguration,
  [CanvasNodeType.SpatialAggregate]: createSpatialAggregateConfiguration,
  [CanvasNodeType.SpatialJoin]: createSpatialJoinConfiguration,
  [CanvasNodeType.StreamJoin]: createStreamJoinConfiguration,
  [CanvasNodeType.Rename]: createRenameConfiguration,
  [CanvasNodeType.Filter]: createFilterConfiguration,
  [CanvasNodeType.SqlTransform]: createSqlTransformConfiguration,
  [CanvasNodeType.SelectColumns]: createSelectColumnsConfiguration,
  [CanvasNodeType.DeriveColumns]: createDeriveColumnsConfiguration,
  [CanvasNodeType.TypeCast]: createTypeCastConfiguration,
  [CanvasNodeType.Aggregate]: createAggregateConfiguration,
  [CanvasNodeType.Union]: createUnionConfiguration,
  [CanvasNodeType.Deduplicate]: createDeduplicateConfiguration,
  [CanvasNodeType.NullHandling]: createNullHandlingConfiguration,
  [CanvasNodeType.ValueMapping]: createValueMappingConfiguration,
  [CanvasNodeType.MaskFields]: createMaskFieldsConfiguration,
  [CanvasNodeType.JsonExtract]: createJsonExtractConfiguration,
  [CanvasNodeType.Window]: createWindowConfiguration,
  [CanvasNodeType.TopN]: createTopNConfiguration,
  [CanvasNodeType.ModelOutput]: createModelOutputConfiguration,
  [CanvasNodeType.JdbcOutput]: createJdbcOutputConfiguration,
  [CanvasNodeType.JdbcSnapshotSyncOutput]: createJdbcSnapshotSyncOutputConfiguration,
  [CanvasNodeType.ModelSnapshotSyncOutput]: createModelSnapshotSyncOutputConfiguration,
  [CanvasNodeType.KafkaOutput]: createKafkaOutputConfiguration,
  [CanvasNodeType.FileOutput]: createFileOutputConfiguration,
};

export const emptyNodeConfiguration = (type: CanvasNodeType): CanvasNodeConfiguration => (
  emptyConfigurationFactories[type]()
);

export const createsCycle = (
  edges: CanvasEdgeDefinition[],
  sourceNodeId: string,
  targetNodeId: string,
) => {
  const adjacency = new Map<string, string[]>();
  [...edges, {
    id: '__candidate__',
    sourceNodeId,
    targetNodeId,
  }].forEach((edge) => {
    adjacency.set(edge.sourceNodeId, [...(adjacency.get(edge.sourceNodeId) ?? []), edge.targetNodeId]);
  });

  const visited = new Set<string>();
  const visit = (nodeId: string): boolean => {
    if (nodeId === sourceNodeId) return true;
    if (visited.has(nodeId)) return false;
    visited.add(nodeId);
    return (adjacency.get(nodeId) ?? []).some(visit);
  };

  return visit(targetNodeId);
};
