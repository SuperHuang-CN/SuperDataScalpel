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
  createGeometryDeriveConfiguration,
  createGeometrySimplifyConfiguration,
  createSpatialNearestConfiguration,
  createSpatialSummarizeWithinConfiguration,
  createSpatialOverlayConfiguration,
  createTrackReconstructConfiguration,
  createTrackMotionStatisticsConfiguration,
  createTrackFindDwellConfiguration,
  createTrackDetectIncidentsConfiguration,
  createSpatialBinAggregateConfiguration,
  createSpatialPointClusterConfiguration,
  createSpatialCenterDispersionConfiguration,
  createSpatialDensityConfiguration,
  createSpatialHotSpotsConfiguration,
  createSpatialMultiVariableGridConfiguration,
  createSpatialSimilarLocationsConfiguration,
  createSpatialDescribeDatasetConfiguration,
  createSpatialEnrichFromGridConfiguration,
  createSpatialGroupByProximityConfiguration,
  createTraceProximityEventsConfiguration,
  createSnapTracksConfiguration,
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
export const CANVAS_SCHEMA_MINOR_VERSION = 77 as const;
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
export const CANVAS_SPATIAL_AGGREGATE_MAX_SUMMARY_STATISTICS = 32 as const;
export const CANVAS_SPATIAL_JOIN_MAX_SUMMARY_STATISTICS = 32 as const;
export const CANVAS_GEOMETRY_DERIVE_MAX_DERIVATIONS = 32 as const;
export const CANVAS_SPATIAL_WITHIN_MAX_STATISTICS = 32 as const;
export const CANVAS_SPATIAL_BIN_MAX_STATISTICS = 32 as const;
export const CANVAS_SPATIAL_CENTER_MAX_GROUP_COLUMNS = 8 as const;
export const CANVAS_SPATIAL_CENTER_MAX_ANALYSES = 16 as const;
export const CANVAS_SPATIAL_DENSITY_MAX_FIELDS = 32 as const;
export const CANVAS_SPATIAL_MULTI_VARIABLE_GRID_MAX_VARIABLES = 32 as const;
export const CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_ANALYSIS_FIELDS = 32 as const;
export const CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_APPEND_FIELDS = 64 as const;
export const CANVAS_SPATIAL_SIMILAR_LOCATIONS_MAX_RESULTS = 10_000 as const;
export const CANVAS_SPATIAL_DESCRIBE_DATASET_MAX_SAMPLE_SIZE = 10_000 as const;
export const CANVAS_TRACE_PROXIMITY_MAX_INTERESTS = 256 as const;
export const CANVAS_TRACE_PROXIMITY_MAX_ATTRIBUTE_COLUMNS = 8 as const;
export const CANVAS_TRACE_PROXIMITY_MAX_DEPTH = 32 as const;
export const CANVAS_SNAP_TRACKS_MAX_LINE_FIELDS = 32 as const;

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
  GeometryDerive: 'GEOMETRY_DERIVE',
  GeometrySimplify: 'GEOMETRY_SIMPLIFY',
  SpatialNearest: 'SPATIAL_NEAREST',
  SpatialSummarizeWithin: 'SPATIAL_SUMMARIZE_WITHIN',
  SpatialOverlay: 'SPATIAL_OVERLAY',
  TrackReconstruct: 'TRACK_RECONSTRUCT',
  TrackMotionStatistics: 'TRACK_MOTION_STATISTICS',
  TrackFindDwell: 'TRACK_FIND_DWELL',
  TrackDetectIncidents: 'TRACK_DETECT_INCIDENTS',
  SpatialBinAggregate: 'SPATIAL_BIN_AGGREGATE',
  SpatialPointCluster: 'SPATIAL_POINT_CLUSTER',
  SpatialCenterDispersion: 'SPATIAL_CENTER_DISPERSION',
  SpatialDensity: 'SPATIAL_DENSITY',
  SpatialHotSpots: 'SPATIAL_HOT_SPOTS',
  SpatialMultiVariableGrid: 'SPATIAL_MULTI_VARIABLE_GRID',
  SpatialSimilarLocations: 'SPATIAL_SIMILAR_LOCATIONS',
  SpatialDescribeDataset: 'SPATIAL_DESCRIBE_DATASET',
  SpatialEnrichFromGrid: 'SPATIAL_ENRICH_FROM_GRID',
  SpatialGroupByProximity: 'SPATIAL_GROUP_BY_PROXIMITY',
  TraceProximityEvents: 'TRACE_PROXIMITY_EVENTS',
  SnapTracks: 'SNAP_TRACKS',
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

export type GeometryDeriveKind =
  | 'CENTROID'
  | 'POINT_ON_SURFACE'
  | 'ENVELOPE'
  | 'CONVEX_HULL'
  | 'BOUNDARY';

export interface GeometryDerivation {
  derivationId: string;
  kind: GeometryDeriveKind | null;
  sourceColumnName: string;
  outputColumnName: string;
  geometryPolicy?: GeometryUnaryPolicy | null;
}

export type GeometryUnaryPolicy = 'PRESERVE_DIMENSION' | 'OUTPUT_XY' | 'LEGACY';

export interface GeometryDeriveConfiguration {
  sourceTableName: string;
  outputTableName: string;
  derivations: GeometryDerivation[];
}

export type SpatialDistanceUnit =
  | 'SOURCE_CRS_UNIT'
  | 'METERS'
  | 'KILOMETERS'
  | 'FEET'
  | 'MILES'
  | 'NAUTICAL_MILES'
  | 'YARDS' | 'FEET_US' | 'YARDS_US' | 'MILES_US' | 'NAUTICAL_MILES_US';

export type SpatialDistanceMethod = 'PLANAR' | 'GEODESIC';

export type SpatialAreaUnit =
  | 'SQUARE_METERS'
  | 'SQUARE_KILOMETERS'
  | 'HECTARES'
  | 'ACRES'
  | 'SQUARE_FEET'
  | 'SQUARE_MILES'
  | 'SQUARE_YARDS' | 'SQUARE_FEET_US' | 'SQUARE_YARDS_US' | 'SQUARE_MILES_US' | 'ACRES_US';

export type SpatialDurationUnit = 'MILLISECONDS' | 'SECONDS' | 'MINUTES' | 'HOURS' | 'DAYS' | 'WEEKS';

export interface SpatialTemporalSlicing {
  calendar?: SpatialCalendarWindowOptions | null;
  timeColumnName: string;
  interval: number;
  intervalUnit: SpatialDurationUnit;
  repeatInterval: number | null;
  repeatIntervalUnit: SpatialDurationUnit | null;
  referenceTime: string | null;
  timeZone: string;
  windowStartColumnName: string;
  windowEndColumnName: string;
}

export interface SpatialCalendarWindowOptions {
  mode: 'FIXED_DURATION' | 'CALENDAR' | null;
  intervalUnit: SpatialDurationUnit | 'WEEKS' | 'MONTHS' | 'YEARS' | null;
  repeatIntervalUnit: SpatialDurationUnit | 'WEEKS' | 'MONTHS' | 'YEARS' | null;
}

export interface SpatialGroupSummary {
  groupByColumnName: string;
  includeMinorityMajority: boolean;
  includeGroupPercentage: boolean;
  minorityFlagColumnName: string | null;
  majorityFlagColumnName: string | null;
  groupPercentageColumnName: string | null;
}

export type GeometrySimplifyAlgorithm = 'DOUGLAS_PEUCKER' | 'TOPOLOGY_PRESERVING';

export interface GeometrySimplifyConfiguration {
  sourceTableName: string;
  geometryColumnName: string;
  outputTableName: string;
  outputColumnName: string;
  algorithm: GeometrySimplifyAlgorithm | null;
  tolerance: number | null;
  toleranceUnit: SpatialDistanceUnit | null;
  geometryPolicy?: GeometryUnaryPolicy | null;
}

export type SpatialNearestMatchSemantics = 'EXACT_DISTANCE' | 'LEGACY_KNN';
export type SpatialNearestGeodesicGeometryMode = 'POINT_ONLY' | 'GEOMETRY';

export interface SpatialNearestConnectionLines {
  enabled: boolean | null;
  outputTableName: string;
  geometryColumnName: string;
  maximumGeodesicSegmentLength: number | null;
  maximumGeodesicSegmentLengthUnit: SpatialDistanceUnit | null;
}

export interface SpatialNearestMatching {
  semantics: SpatialNearestMatchSemantics | null;
  sourceIdColumnName: string;
  connectionLines: SpatialNearestConnectionLines | null;
  geodesicGeometryMode?: SpatialNearestGeodesicGeometryMode | null;
}

export interface SpatialNearestConfiguration {
  sourceTableName: string;
  sourceGeometryColumnName: string;
  candidateTableName: string;
  candidateGeometryColumnName: string;
  candidateIdColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  nearestCount: number;
  maximumDistance: number | null;
  maximumDistanceUnit: SpatialDistanceUnit | null;
  includeUnmatched: boolean;
  outputTableName: string;
  distanceColumnName: string;
  distanceOutputUnit: SpatialDistanceUnit;
  rankColumnName: string | null;
  outputColumns: JoinOutputColumn[];
  matching?: SpatialNearestMatching | null;
}

export type SpatialWithinStatisticKind =
  | 'COUNT'
  | 'COUNT_FIELD'
  | 'ANY'
  | 'SUM'
  | 'MEAN'
  | 'MIN'
  | 'MAX'
  | 'RANGE'
  | 'STDDEV'
  | 'VARIANCE'
  | 'LENGTH_WITHIN'
  | 'AREA_WITHIN';

export interface SpatialWithinStatistic {
  statisticId: string;
  kind: SpatialWithinStatisticKind;
  sourceColumnName: string | null;
  outputColumnName: string;
  valueTreatment?: 'ORIGINAL_VALUE' | 'APPORTION_TOTAL' | null;
  weighting?: 'NONE' | 'INTERSECTION_FRACTION' | null;
}

export interface SpatialWithinGroupResult {
  mode?: 'LINKED_TABLES' | 'LEGACY_FLAT' | null;
  areaKeyColumnName: string;
  areaKeyOutputColumnName: string;
  outputTableName: string;
  groupValueColumnName: string;
  minorityValueColumnName: string | null;
  majorityValueColumnName: string | null;
  minorityPercentageColumnName: string | null;
  majorityPercentageColumnName: string | null;
}

export interface SpatialWithinRegions {
  mode: 'AREA_TABLE' | 'PLANAR_GRID' | null;
  binShape: 'SQUARE' | 'HEXAGON' | null;
  binSize: number | null;
  binSizeUnit: SpatialDistanceUnit | null;
  planarGrid: SpatialPlanarGridOptions | null;
  binIdColumnName: string;
  binGeometryColumnName: string;
}

export interface SpatialSummarizeWithinConfiguration {
  regions?: SpatialWithinRegions | null;
  areaTableName: string;
  areaGeometryColumnName: string;
  summaryTableName: string;
  summaryGeometryColumnName: string;
  includeEmptyAreas: boolean;
  distanceMethod: SpatialDistanceMethod | null;
  lengthUnit: SpatialDistanceUnit;
  areaUnit: SpatialAreaUnit;
  areaOutputColumns: JoinOutputColumn[];
  statistics: SpatialWithinStatistic[];
  groupSummary: SpatialGroupSummary | null;
  groupResult?: SpatialWithinGroupResult | null;
  temporalSlicing: SpatialTemporalSlicing | null;
  outputTableName: string;
}

export type SpatialOverlayOperation = 'INTERSECTION' | 'ERASE' | 'UNION' | 'IDENTITY' | 'SYMMETRICAL_DIFFERENCE';
export type SpatialOverlayGeometryPolicy = 'FAMILY_2D' | 'LEGACY_GEOMETRY';

export interface SpatialOverlayConfiguration {
  leftTableName: string;
  leftGeometryColumnName: string;
  rightTableName: string;
  rightGeometryColumnName: string;
  operation: SpatialOverlayOperation | null;
  outputTableName: string;
  outputGeometryColumnName: string;
  outputColumns: JoinOutputColumn[];
  geometryPolicy?: SpatialOverlayGeometryPolicy | null;
}

export type SpatialSpeedUnit =
  | 'METERS_PER_SECOND'
  | 'KILOMETERS_PER_HOUR'
  | 'FEET_PER_SECOND'
  | 'MILES_PER_HOUR'
  | 'KNOTS';

export type SpatialAccelerationUnit =
  | 'METERS_PER_SECOND_SQUARED'
  | 'FEET_PER_SECOND_SQUARED';

export interface TrackBoundaryConfiguration {
  fixedTimeBoundary?: TrackFixedTimeBoundary | null;
  maximumTimeGap: number | null;
  maximumTimeGapUnit: SpatialDurationUnit | null;
  maximumDistanceGap: number | null;
  maximumDistanceGapUnit: SpatialDistanceUnit | null;
}

export type TrackTimeBoundaryUnit = 'MILLISECONDS' | 'SECONDS' | 'MINUTES' | 'HOURS'
  | 'DAYS' | 'WEEKS' | 'MONTHS' | 'YEARS';

export interface TrackFixedTimeBoundary {
  interval: number | null;
  unit: TrackTimeBoundaryUnit | null;
  referenceTime: string | null;
  timeZone: string | null;
}

export type TrackSummaryStatisticKind =
  | 'COUNT' | 'SUM' | 'MEAN' | 'MIN' | 'MAX' | 'RANGE'
  | 'STDDEV' | 'VARIANCE' | 'FIRST' | 'LAST' | 'COUNT_FIELD' | 'ANY';

export interface TrackSummaryStatistic {
  statisticId: string;
  kind: TrackSummaryStatisticKind;
  sourceColumnName: string | null;
  outputColumnName: string;
}

export type TrackSplitBoundaryOption = 'GAP' | 'FINISH_LAST' | 'START_NEXT';
export interface TrackFieldWindowBinding { name: string; sourceColumnName: string; offset: number | null }
export interface TrackSplitExpression { expression: string; bindings: TrackFieldWindowBinding[]; enabled?: boolean | null }
export interface TrackPathGeometryOptions {
  mode: 'METHOD_PATH' | 'LEGACY_VERTEX_LINE' | null;
  maximumGeodesicSegmentLength: number | null;
  maximumGeodesicSegmentLengthUnit: SpatialDistanceUnit | null;
}
export interface TrackReconstructOptions {
  semantics: 'ORDERED_SEGMENTS' | 'LEGACY_POINTS' | null;
  orderByColumns: string[];
  splitBoundaryOption: TrackSplitBoundaryOption | null;
  splitExpression: TrackSplitExpression | null;
  pathGeometry?: TrackPathGeometryOptions | null;
  areaGeometry?: TrackAreaGeometryOptions | null;
}
export interface TrackAreaGeometryOptions {
  enabled?: boolean | null;
  bufferMode: 'NONE' | 'FIELD' | 'EXPRESSION' | null;
  bufferField: string | null;
  bufferExpression: string | null;
  bufferUnit: SpatialDistanceUnit | null;
  windowBindings?: TrackBufferWindowBinding[] | null;
  geodesicBoundary?: TrackGeodesicAreaOptions | null;
}
export interface TrackGeodesicAreaOptions {
  maximumSegmentLength: number | null;
  maximumSegmentLengthUnit: SpatialDistanceUnit | null;
}
export interface TrackBufferWindowBinding {
  name: string;
  sourceColumnName: string;
  startOffset: number | null;
  endOffset: number | null;
  statistic: TrackSummaryStatisticKind | null;
}
export interface TrackReconstructConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  trackIdColumns: string[];
  timeColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  boundaries: TrackBoundaryConfiguration;
  summaryStatistics: TrackSummaryStatistic[];
  outputTableName: string;
  outputGeometryColumnName: string;
  startTimeColumnName: string;
  endTimeColumnName: string;
  pointCountColumnName: string;
  reconstruction?: TrackReconstructOptions | null;
}

export type TrackMotionMetric =
  | { kind: 'DISTANCE' | 'ELEVATION_CHANGE'; metricId: string; outputColumnName: string; outputUnit: SpatialDistanceUnit }
  | { kind: 'DURATION'; metricId: string; outputColumnName: string; outputUnit: SpatialDurationUnit }
  | { kind: 'SPEED'; metricId: string; outputColumnName: string; outputUnit: SpatialSpeedUnit }
  | { kind: 'ACCELERATION'; metricId: string; outputColumnName: string; outputUnit: SpatialAccelerationUnit }
  | { kind: 'BEARING'; metricId: string; outputColumnName: string; outputUnit: 'DEGREES' }
  | { kind: 'SLOPE'; metricId: string; outputColumnName: string; outputUnit: 'PERCENT' }
  | { kind: 'IDLE'; metricId: string; outputColumnName: string; outputUnit: null };

export interface TrackMotionStatisticsConfiguration {
  motionSemantics?: 'LEGACY_LAG' | 'OBSERVATION_WINDOW' | null;
  windowOptions?: TrackMotionWindowOptions | null;
  sourceTableName: string;
  pointGeometryColumnName: string;
  trackIdColumns: string[];
  timeColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  boundaries: TrackBoundaryConfiguration;
  historyPoints: number;
  idleDistanceThreshold: number | null;
  idleDistanceThresholdUnit: SpatialDistanceUnit | null;
  metrics: TrackMotionMetric[];
  outputTableName: string;
}

export type DwellGeometryKind = 'CENTROID' | 'CONVEX_HULL';

export type TrackMotionStatisticGroup = 'DISTANCE' | 'DURATION' | 'SPEED' | 'ACCELERATION' | 'ELEVATION' | 'SLOPE' | 'IDLE' | 'BEARING';
export type TrackMotionStatistic = 'DISTANCE' | 'TOT_DISTANCE' | 'MIN_DISTANCE' | 'MAX_DISTANCE' | 'AVG_DISTANCE' | 'DURATION' | 'TOT_DURATION' | 'MIN_DURATION' | 'MAX_DURATION' | 'AVG_DURATION' | 'SPEED' | 'MIN_SPEED' | 'MAX_SPEED' | 'AVG_SPEED' | 'ACCELERATION' | 'MIN_ACCELERATION' | 'MAX_ACCELERATION' | 'ELEVATION' | 'ELEV_CHANGE' | 'TOT_ELEV_CHANGE' | 'MIN_ELEVATION' | 'MAX_ELEVATION' | 'AVG_ELEVATION' | 'SLOPE' | 'MIN_SLOPE' | 'MAX_SLOPE' | 'AVG_SLOPE' | 'IDLING' | 'TOT_IDLE_TIME' | 'PCT_IDLE_TIME' | 'BEARING';
export interface TrackMotionWindowStatistic { statisticId: string; kind: TrackMotionStatistic; outputColumnName: string }
export interface TrackMotionWindowOptions {
  observationCount: number | null;
  orderByColumns: string[];
  statistics: TrackMotionWindowStatistic[];
  distanceUnit: SpatialDistanceUnit | null;
  durationUnit: SpatialDurationUnit | null;
  speedUnit: SpatialSpeedUnit | null;
  accelerationUnit: SpatialAccelerationUnit | null;
  elevationColumnName: string | null;
  inputElevationUnit: SpatialDistanceUnit | null;
  elevationUnit: SpatialDistanceUnit | null;
  idleTimeThreshold: number | null;
  idleTimeThresholdUnit: SpatialDurationUnit | null;
}

export interface TrackFindDwellConfiguration {
  dwellSemantics?: 'LEGACY_ADJACENT' | 'REFERENCE_CENTER' | null;
  rangeOptions?: TrackDwellRangeOptions | null;
  sourceTableName: string;
  pointGeometryColumnName: string;
  trackIdColumns: string[];
  timeColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  distanceThreshold: number;
  distanceThresholdUnit: SpatialDistanceUnit;
  minimumDuration: number;
  minimumDurationUnit: SpatialDurationUnit;
  boundaries: TrackBoundaryConfiguration;
  summaryStatistics: TrackSummaryStatistic[];
  outputGeometryKind: DwellGeometryKind | null;
  outputTableName: string;
  dwellIdColumnName: string;
  startTimeColumnName: string;
  endTimeColumnName: string;
  durationColumnName: string;
  pointCountColumnName: string;
  outputGeometryColumnName: string;
}

export type TrackDwellResultMode = 'MEAN_CENTERS' | 'CONVEX_HULLS' | 'DWELL_FEATURES' | 'ALL_FEATURES';
export interface TrackDwellRangeOptions {
  resultMode: TrackDwellResultMode | null;
  orderByColumns: string[];
  durationUnit: SpatialDurationUnit | null;
  meanDistanceColumnName: string;
  meanDistanceUnit: SpatialDistanceUnit | null;
  dwellFlagColumnName: string;
}

export type TrackIncidentResultMode = 'INCIDENTS_ONLY' | 'ALL_EVENTS';
export type TrackIncidentSemantics = 'LEGACY' | 'CONDITION_LIFECYCLE';

export interface TrackIncidentWindow {
  bindingName: string;
  sourceColumnName: string;
  kind: 'COUNT' | 'SUM' | 'MEAN' | 'MIN' | 'MAX' | 'FIRST' | 'LAST' | 'STDDEV_POP' | 'VARIANCE_POP' | null;
  startOffset: number | null;
  endOffset: number | null;
  source?: 'FIELD' | 'TRACK_DISTANCE' | 'TRACK_SPEED' | 'TRACK_ACCELERATION' | null;
}

export interface TrackIncidentScalar {
  bindingName: string;
  source: 'TRACK_START_TIME' | 'TRACK_DURATION' | 'TRACK_CURRENT_TIME' | 'TRACK_INDEX'
    | 'TRACK_POINT_X_AT' | 'TRACK_POINT_Y_AT' | null;
  /** Only point-coordinate sources use this relative observation offset. */
  offset?: number | null;
}

export interface TrackDetectIncidentsConfiguration {
  conditionWindows?: TrackIncidentWindow[];
  conditionScalars?: TrackIncidentScalar[];
  incidentSemantics?: TrackIncidentSemantics;
  incidentStatusColumnName?: string | null;
  orderByColumns?: string[];
  sourceTableName: string;
  pointGeometryColumnName: string | null;
  trackIdColumns: string[];
  timeColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  boundaries: TrackBoundaryConfiguration;
  startCondition: CanvasFilterCondition;
  endCondition: CanvasFilterCondition | null;
  resultMode: TrackIncidentResultMode | null;
  outputTableName: string;
  incidentIdColumnName: string;
  incidentFlagColumnName: string;
  incidentStartTimeColumnName: string;
  incidentEndTimeColumnName: string;
  incidentDurationColumnName: string;
  incidentDurationUnit: SpatialDurationUnit;
}

export type SpatialBinShape = 'SQUARE' | 'HEXAGON' | 'H3';

export interface SpatialH3Options {
  mode: 'RESOLUTION' | 'APPROXIMATE_SIZE' | null;
  resolution: number | null;
}

export type SpatialBinStatisticKind =
  | 'COUNT' | 'COUNT_FIELD' | 'ANY' | 'SUM' | 'MEAN' | 'MIN' | 'MAX' | 'RANGE' | 'STDDEV' | 'VARIANCE';

export interface SpatialBinStatistic {
  statisticId: string;
  kind: SpatialBinStatisticKind;
  sourceColumnName: string | null;
  outputColumnName: string;
}

export interface SpatialPlanarGridOptions {
  originX: number | null;
  originY: number | null;
  extent: {
    mode: 'DATA_BOUNDS' | 'EXPLICIT_BOUNDS' | null;
    minX: number | null;
    minY: number | null;
    maxX: number | null;
    maxY: number | null;
  } | null;
}

export interface SpatialBinAggregateConfiguration {
  planarGrid?: SpatialPlanarGridOptions | null;
  h3?: SpatialH3Options | null;
  binSizeSemantics?: SpatialBinSizeSemantics | null;
  sourceTableName: string;
  pointGeometryColumnName: string;
  binShape: SpatialBinShape | null;
  binSize: number;
  binSizeUnit: SpatialDistanceUnit;
  includeEmptyBins: boolean;
  statistics: SpatialBinStatistic[];
  groupSummary: SpatialGroupSummary | null;
  temporalSlicing: SpatialTemporalSlicing | null;
  outputTableName: string;
  binIdColumnName: string;
  binGeometryColumnName: string;
}

export type SpatialBinSizeSemantics = 'LEGACY_SIDE_LENGTH' | 'HEXAGON_FLAT_TO_FLAT';

export type SpatialPointClusterParameters =
  | { algorithm: 'DBSCAN'; searchDistance: number; searchDistanceUnit: SpatialDistanceUnit; minimumFeatures: number }
  | { algorithm: 'HDBSCAN'; minimumFeatures: number }
  | { algorithm: 'MULTI_SCALE'; minimumFeatures: number; sensitivity: number };

export interface SpatialPointClusterConfiguration {
  dbscan?: SpatialDbscanOptions | null;
  hdbscan?: SpatialHdbscanOptions | null;
  sourceTableName: string;
  pointGeometryColumnName: string;
  featureIdColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  parameters: SpatialPointClusterParameters;
  outputTableName: string;
  clusterIdColumnName: string;
  noiseColumnName: string;
}

export interface SpatialHdbscanOptions {
  probabilityColumnName: string;
  outlierColumnName: string;
  exemplarColumnName: string;
  stabilityColumnName: string;
}

export interface SpatialDbscanOptions {
  mode: 'LEGACY_SPATIAL' | 'SPATIAL' | 'LINEAR' | null;
  timeColumnName: string;
  searchDuration: number | null;
  searchDurationUnit: SpatialDurationUnit | null;
}

export type SpatialCenterDispersionKind =
  | 'MEAN_CENTER'
  | 'MEDIAN_CENTER'
  | 'CENTRAL_FEATURE'
  | 'STANDARD_DISTANCE'
  | 'DIRECTIONAL_ELLIPSE';

export interface SpatialCenterFeatureColumn {
  sourceColumnName: string;
  outputColumnName: string;
  included: boolean;
}

export interface SpatialCenterDispersionAnalysis {
  analysisId: string;
  kind: SpatialCenterDispersionKind;
  outputColumnName: string;
  standardDeviations: number | null;
  outputTableName?: string | null;
  centralFeatureColumns?: SpatialCenterFeatureColumn[] | null;
}

export type SpatialCenterResultMode = 'ANALYSIS_TABLES' | 'LEGACY_WIDE';

export interface SpatialCenterDispersionConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  featureIdColumnName: string | null;
  groupByColumns: string[];
  weightColumnName: string | null;
  analyses: SpatialCenterDispersionAnalysis[];
  outputTableName: string;
  resultMode?: SpatialCenterResultMode | null;
}

export type SpatialDensityWeighting = 'UNIFORM' | 'KERNEL';
export type SpatialDensityBinShape = 'SQUARE' | 'HEXAGON';

export interface SpatialDensityField {
  fieldId: string;
  sourceColumnName: string;
  outputColumnName: string;
}

export interface SpatialDensityConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  fields: SpatialDensityField[];
  weighting: SpatialDensityWeighting | null;
  binShape: SpatialDensityBinShape | null;
  binSize: number;
  binSizeUnit: SpatialDistanceUnit;
  radius: number;
  radiusUnit: SpatialDistanceUnit;
  areaUnit: SpatialAreaUnit;
  temporalSlicing: SpatialTemporalSlicing | null;
  outputTableName: string;
  binIdColumnName: string;
  binGeometryColumnName: string;
  countDensityColumnName: string;
}

export type SpatialHotSpotAnalysisSource = 'POINT_COUNT' | 'FIELD_SUM';
export type SpatialHotSpotMultipleTesting = 'NONE' | 'FDR_BH';

export interface SpatialHotSpotsConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  analysisSource: SpatialHotSpotAnalysisSource | null;
  analysisColumnName: string | null;
  binSize: number;
  binSizeUnit: SpatialDistanceUnit;
  neighborhoodDistance: number;
  neighborhoodDistanceUnit: SpatialDistanceUnit;
  temporalSlicing: SpatialTemporalSlicing | null;
  multipleTesting: SpatialHotSpotMultipleTesting | null;
  outputTableName: string;
  binIdColumnName: string;
  binGeometryColumnName: string;
  pointCountColumnName: string;
  analysisValueColumnName: string;
  zScoreColumnName: string;
  pValueColumnName: string;
  adjustedPValueColumnName: string;
  confidenceBinColumnName: string;
}

export type SpatialMultiVariableGridVariableKind =
  | 'DISTANCE_TO_NEAREST'
  | 'ATTRIBUTE_OF_NEAREST'
  | 'ATTRIBUTE_SUMMARY_OF_RELATED';

export type SpatialMultiVariableGridStatisticKind =
  | 'COUNT'
  | 'SUM'
  | 'MEAN'
  | 'MIN'
  | 'MAX'
  | 'RANGE'
  | 'STDDEV'
  | 'VARIANCE'
  | 'ANY';

export interface SpatialMultiVariableGridVariable {
  variableId: string;
  sourceTableName: string;
  geometryColumnName: string;
  kind: SpatialMultiVariableGridVariableKind | null;
  attributeColumnName: string | null;
  statisticKind: SpatialMultiVariableGridStatisticKind | null;
  statisticColumnName: string | null;
  searchDistance: number | null;
  searchDistanceUnit: SpatialDistanceUnit | null;
  filter: CanvasFilterCondition | null;
  outputColumnName: string;
}

export interface SpatialMultiVariableGridConfiguration {
  variables: SpatialMultiVariableGridVariable[];
  binShape: SpatialDensityBinShape | null;
  binSize: number;
  binSizeUnit: SpatialDistanceUnit;
  outputTableName: string;
  binIdColumnName: string;
  binGeometryColumnName: string;
}

export type SpatialSimilarLocationsMatchMethod = 'ATTRIBUTE_VALUES' | 'ATTRIBUTE_PROFILES';
export type SpatialSimilarLocationsResultMode = 'MOST_SIMILAR' | 'LEAST_SIMILAR' | 'BOTH';

export interface SpatialSimilarLocationsAnalysisField {
  columnName: string;
  outputColumnName: string;
}

export interface SpatialSimilarLocationsAppendField {
  sourceColumnName: string;
  outputColumnName: string;
}

export interface SpatialSimilarLocationsConfiguration {
  referenceTableName: string;
  referenceIdColumnName: string;
  referenceGeometryColumnName: string;
  referenceFilter: CanvasFilterCondition | null;
  candidateTableName: string;
  candidateIdColumnName: string;
  candidateGeometryColumnName: string;
  candidateFilter: CanvasFilterCondition | null;
  analysisFields: SpatialSimilarLocationsAnalysisField[];
  appendFields: SpatialSimilarLocationsAppendField[];
  matchMethod: SpatialSimilarLocationsMatchMethod | null;
  resultMode: SpatialSimilarLocationsResultMode | null;
  numberOfResults: number;
  outputTableName: string;
  outputGeometryColumnName: string;
  locationTypeColumnName: string;
  similarityRankColumnName: string;
  dissimilarityRankColumnName: string;
  similarityIndexColumnName: string;
  cosineIndexColumnName: string;
  labelRankColumnName: string;
  referenceIdOutputColumnName: string;
  searchIdOutputColumnName: string;
}

export interface SpatialDescribeDatasetConfiguration {
  sourceTableName: string;
  geometryColumnName: string;
  statisticsTableName: string;
  descriptionTableName: string;
  sampleSize: number;
  sampleTableName: string;
  extentOutput: boolean;
  extentTableName: string;
}

export interface SpatialEnrichFromGridField {
  sourceColumnName: string;
  outputColumnName: string;
}

export interface SpatialEnrichFromGridConfiguration {
  pointTableName: string;
  pointGeometryColumnName: string;
  gridTableName: string;
  gridGeometryColumnName: string;
  gridIdColumnName: string;
  enrichFields: SpatialEnrichFromGridField[];
  outputTableName: string;
}

export type SpatialGroupByProximitySpatialRelationship =
  | 'INTERSECTS'
  | 'TOUCHES'
  | 'NEAR_PLANAR'
  | 'NEAR_GEODESIC';

export type SpatialGroupByProximityTemporalRelationship = 'INTERSECTS' | 'NEAR';

export type SpatialGroupByProximityTemporalUnit =
  | 'MILLISECONDS'
  | 'SECONDS'
  | 'MINUTES'
  | 'HOURS'
  | 'DAYS'
  | 'WEEKS'
  | 'MONTHS'
  | 'YEARS';

export interface SpatialGroupByProximityTemporalCondition {
  relationship: SpatialGroupByProximityTemporalRelationship | null;
  startColumnName: string;
  endColumnName: string | null;
  nearDistance: number | null;
  nearDistanceUnit: SpatialGroupByProximityTemporalUnit | null;
}

export type SpatialGroupByProximityAttributeRelationship =
  | 'EQUALS'
  | 'ABSOLUTE_DIFFERENCE_AT_MOST';

export interface SpatialGroupByProximityAttributeCondition {
  columnName: string;
  relationship: SpatialGroupByProximityAttributeRelationship | null;
  maximumDifference: number | null;
}

export interface SpatialGroupByProximityConfiguration {
  sourceTableName: string;
  geometryColumnName: string;
  spatialRelationship: SpatialGroupByProximitySpatialRelationship | null;
  spatialNearDistance: number | null;
  spatialNearDistanceUnit: SpatialDistanceUnit | null;
  temporalCondition: SpatialGroupByProximityTemporalCondition | null;
  attributeConditions: SpatialGroupByProximityAttributeCondition[];
  groupIdColumnName: string;
  outputTableName: string;
}

export type TraceProximityInterestSource = 'ENTITY_IDS' | 'TABLE';

export interface TraceProximityEntityOfInterest {
  entityId: string;
  startEpochMillis: number | null;
}

export interface TraceProximityEventsConfiguration {
  sourceTableName: string;
  pointGeometryColumnName: string;
  entityIdColumnName: string;
  timeColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  spatialSearchDistance: number | null;
  spatialSearchDistanceUnit: SpatialDistanceUnit | null;
  temporalSearchDistance: number | null;
  temporalSearchDistanceUnit: SpatialGroupByProximityTemporalUnit | null;
  interestSource: TraceProximityInterestSource | null;
  entitiesOfInterest: TraceProximityEntityOfInterest[];
  entitiesOfInterestTableName: string;
  interestEntityIdColumnName: string;
  interestStartTimeColumnName: string | null;
  maxTraceDepth: number | null;
  attributeMatchColumns: string[];
  includeTracks: boolean;
  outputTableName: string;
  tracksOutputTableName: string;
  fromEntityIdColumnName: string;
  toEntityIdColumnName: string;
  depthColumnName: string;
  durationMinutesColumnName: string;
  eventTimeColumnName: string;
}

export type SnapTracksOutputMode = 'ALL_FEATURES' | 'MATCHED_FEATURES';

export interface SnapTracksDirectionMatching {
  directionColumnName: string;
  forwardValue: string;
  backwardValue: string;
  bothValue: string;
  noneValue: string;
}

export interface SnapTracksLineField {
  sourceColumnName: string;
  outputColumnName: string;
}

export interface SnapTracksConfiguration {
  pointTableName: string;
  pointGeometryColumnName: string;
  trackIdColumns: string[];
  timeColumnName: string;
  orderByColumns: string[];
  lineTableName: string;
  lineGeometryColumnName: string;
  lineIdColumnName: string;
  fromNodeColumnName: string;
  toNodeColumnName: string;
  searchDistance: number | null;
  searchDistanceUnit: SpatialDistanceUnit | null;
  distanceMethod: SpatialDistanceMethod | null;
  boundaries: TrackBoundaryConfiguration;
  directionMatching: SnapTracksDirectionMatching | null;
  lineFields: SnapTracksLineField[];
  outputMode: SnapTracksOutputMode | null;
  outputTableName: string;
  snappedGeometryColumnName: string;
  matchedLineIdColumnName: string;
  matchStatusColumnName: string;
  originalXColumnName: string;
  originalYColumnName: string;
  matchXColumnName: string;
  matchYColumnName: string;
  matchDistanceColumnName: string;
}

export interface GeometryBufferConfiguration {
  sourceTableName: string;
  outputTableName: string;
  geometryColumnName: string;
  outputColumnName: string;
  distance: number;
  mode: SpatialMeasureMode;
  distanceUnit?: SpatialDistanceUnit | null;
  distanceSource?: GeometryBufferDistanceSource | null;
  distanceFieldName?: string | null;
  distanceExpression?: string | null;
}

export type GeometryBufferDistanceSource = 'CONSTANT' | 'FIELD' | 'EXPRESSION';

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
    outputUnit?: SpatialAreaUnit | null;
  }
  | {
    kind: 'LENGTH';
    geometryColumnName: string;
    mode: SpatialMeasureMode;
    outputColumnName: string;
    outputUnit?: SpatialDistanceUnit | null;
  }
  | {
    kind: 'PERIMETER';
    geometryColumnName: string;
    mode: SpatialMeasureMode;
    outputColumnName: string;
    outputUnit?: SpatialDistanceUnit | null;
  }
  | {
    kind: 'DISTANCE';
    leftGeometryColumnName: string;
    rightGeometryColumnName: string;
    mode: SpatialMeasureMode;
    outputColumnName: string;
    outputUnit?: SpatialDistanceUnit | null;
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

export type SpatialClipGeometryPolicy = 'SOURCE_FAMILY_2D' | 'LEGACY_ANY_DIMENSION';
export type SpatialClipMaskCombination = 'DISSOLVE_ALL' | 'PAIRWISE';

export interface SpatialClipConfiguration {
  sourceTableName: string;
  maskTableName: string;
  outputTableName: string;
  sourceGeometryColumnName: string;
  maskGeometryColumnName: string;
  outputColumnName: string;
  geometryPolicy?: SpatialClipGeometryPolicy | null;
  maskCombination?: SpatialClipMaskCombination | null;
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

export type SpatialAggregateStatisticKind =
  | 'COUNT_FIELD'
  | 'SUM'
  | 'MEAN'
  | 'MIN'
  | 'MAX'
  | 'RANGE'
  | 'STDDEV'
  | 'VARIANCE'
  | 'ANY';

export interface SpatialAggregateStatistic {
  statisticId: string;
  kind: SpatialAggregateStatisticKind;
  sourceColumnName: string;
  outputColumnName: string;
}

export interface SpatialAggregateDissolveOptions {
  enabled: boolean;
  multipart: boolean;
  countOutputColumnName: string;
  summaryStatistics: SpatialAggregateStatistic[];
  groupingMode?: SpatialAggregateDissolveGroupingMode | null;
}

export type SpatialAggregateDissolveGroupingMode = 'ALL_OR_FIELDS' | 'CONNECTED_COMPONENTS';

export interface SpatialAggregateConfiguration {
  sourceTableName: string;
  outputTableName: string;
  groupByColumns: string[];
  aggregations: SpatialAggregation[];
  dissolve?: SpatialAggregateDissolveOptions | null;
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

export type SpatialJoinType = 'INNER' | 'LEFT';
export type SpatialJoinOperation = 'JOIN_ONE_TO_MANY' | 'JOIN_ONE_TO_ONE';
export type SpatialJoinOneToOneMode = 'SUMMARIZE_MATCHES' | 'KEEP_ONE';
export type SpatialJoinKeepStrategy = 'FIRST' | 'LARGEST' | 'SMALLEST' | 'NEWEST' | 'OLDEST';
export type SpatialJoinSummaryStatisticKind = 'SUM' | 'MIN' | 'MAX' | 'MEAN' | 'STDDEV';
export type SpatialJoinTemporalRelationship =
  | 'EQUALS'
  | 'INTERSECTS'
  | 'DURING'
  | 'CONTAINS'
  | 'FINISHES'
  | 'FINISHED_BY'
  | 'MEETS'
  | 'MET_BY'
  | 'OVERLAPS'
  | 'OVERLAPPED_BY'
  | 'STARTS'
  | 'STARTED_BY'
  | 'NEAR'
  | 'NEAR_BEFORE'
  | 'NEAR_AFTER';

export interface SpatialJoinSummaryStatistic {
  statisticId: string;
  kind: SpatialJoinSummaryStatisticKind | null;
  sourceColumnName: string;
  outputColumnName: string;
}

export interface SpatialJoinKeepRule {
  strategy: SpatialJoinKeepStrategy | null;
  orderByColumnName: string | null;
  stableOrder: SortField[];
}

export interface SpatialJoinOneToOneOptions {
  mode: SpatialJoinOneToOneMode | null;
  joinCountColumnName: string;
  summaryStatistics: SpatialJoinSummaryStatistic[];
  keepRule: SpatialJoinKeepRule | null;
}

export interface SpatialJoinTemporalCondition {
  relationship: SpatialJoinTemporalRelationship | null;
  leftStartColumnName: string;
  leftEndColumnName: string | null;
  rightStartColumnName: string;
  rightEndColumnName: string | null;
  nearDistance: number | null;
  nearDistanceUnit: SpatialDurationUnit | null;
}

export interface SpatialJoinSpatialNearCondition {
  leftGeometryColumnName: string;
  rightGeometryColumnName: string;
  distanceMethod: SpatialDistanceMethod | null;
  distance: number | null;
  distanceUnit: SpatialDistanceUnit | null;
}

export interface SpatialJoinDistanceOutput {
  enabled: boolean;
  spatialDistanceColumnName: string;
  spatialDistanceUnit: SpatialDistanceUnit | null;
  temporalDifferenceColumnName: string;
  temporalDifferenceUnit: SpatialDurationUnit | null;
}

export interface SpatialJoinConfiguration {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: SpatialJoinType;
  conditions: SpatialJoinCondition[];
  attributeConditions?: JoinCondition[] | null;
  outputColumns?: JoinOutputColumn[] | null;
  joinOperation?: SpatialJoinOperation | null;
  oneToOne?: SpatialJoinOneToOneOptions | null;
  temporalCondition?: SpatialJoinTemporalCondition | null;
  spatialNear?: SpatialJoinSpatialNearCondition | null;
  distanceOutput?: SpatialJoinDistanceOutput | null;
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

export type UnionMergeFieldAction = 'MATCH' | 'RENAME' | 'REMOVE';

export interface UnionMergeFieldRule {
  sourceColumnName: string;
  action: UnionMergeFieldAction | null;
  targetColumnName: string | null;
}

export interface UnionMergeTable {
  tableName: string;
  fieldRules: UnionMergeFieldRule[];
}

export interface UnionConfiguration {
  inputTableNames: string[];
  outputTableName: string;
  mode: UnionMode | null;
  mergingTables: UnionMergeTable[] | null;
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

export type GeometryDeriveNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.GeometryDerive,
  GeometryDeriveConfiguration
>;

export type GeometrySimplifyNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.GeometrySimplify,
  GeometrySimplifyConfiguration
>;

export type SpatialNearestNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialNearest,
  SpatialNearestConfiguration
>;

export type SpatialSummarizeWithinNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialSummarizeWithin,
  SpatialSummarizeWithinConfiguration
>;

export type SpatialOverlayNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialOverlay,
  SpatialOverlayConfiguration
>;

export type TrackReconstructNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.TrackReconstruct,
  TrackReconstructConfiguration
>;

export type TrackMotionStatisticsNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.TrackMotionStatistics,
  TrackMotionStatisticsConfiguration
>;

export type TrackFindDwellNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.TrackFindDwell,
  TrackFindDwellConfiguration
>;

export type TrackDetectIncidentsNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.TrackDetectIncidents,
  TrackDetectIncidentsConfiguration
>;

export type SpatialBinAggregateNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialBinAggregate,
  SpatialBinAggregateConfiguration
>;

export type SpatialPointClusterNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialPointCluster,
  SpatialPointClusterConfiguration
>;

export type SpatialCenterDispersionNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialCenterDispersion,
  SpatialCenterDispersionConfiguration
>;

export type SpatialDensityNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialDensity,
  SpatialDensityConfiguration
>;

export type SpatialHotSpotsNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialHotSpots,
  SpatialHotSpotsConfiguration
>;

export type SpatialMultiVariableGridNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialMultiVariableGrid,
  SpatialMultiVariableGridConfiguration
>;

export type SpatialSimilarLocationsNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialSimilarLocations,
  SpatialSimilarLocationsConfiguration
>;

export type SpatialDescribeDatasetNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialDescribeDataset,
  SpatialDescribeDatasetConfiguration
>;

export type SpatialEnrichFromGridNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialEnrichFromGrid,
  SpatialEnrichFromGridConfiguration
>;

export type SpatialGroupByProximityNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SpatialGroupByProximity,
  SpatialGroupByProximityConfiguration
>;

export type TraceProximityEventsNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.TraceProximityEvents,
  TraceProximityEventsConfiguration
>;

export type SnapTracksNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.SnapTracks,
  SnapTracksConfiguration
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
  | GeometryDeriveNodeDefinition
  | GeometrySimplifyNodeDefinition
  | SpatialNearestNodeDefinition
  | SpatialSummarizeWithinNodeDefinition
  | SpatialOverlayNodeDefinition
  | TrackReconstructNodeDefinition
  | TrackMotionStatisticsNodeDefinition
  | TrackFindDwellNodeDefinition
  | TrackDetectIncidentsNodeDefinition
  | SpatialBinAggregateNodeDefinition
  | SpatialPointClusterNodeDefinition
  | SpatialCenterDispersionNodeDefinition
  | SpatialDensityNodeDefinition
  | SpatialHotSpotsNodeDefinition
  | SpatialMultiVariableGridNodeDefinition
  | SpatialSimilarLocationsNodeDefinition
  | SpatialDescribeDatasetNodeDefinition
  | SpatialEnrichFromGridNodeDefinition
  | SpatialGroupByProximityNodeDefinition
  | TraceProximityEventsNodeDefinition
  | SnapTracksNodeDefinition
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
  | Pick<GeometryDeriveNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<GeometrySimplifyNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialNearestNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialSummarizeWithinNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialOverlayNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<TrackReconstructNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<TrackMotionStatisticsNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<TrackFindDwellNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<TrackDetectIncidentsNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialBinAggregateNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialPointClusterNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialCenterDispersionNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialDensityNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialHotSpotsNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialMultiVariableGridNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialSimilarLocationsNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialDescribeDatasetNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialEnrichFromGridNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SpatialGroupByProximityNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<TraceProximityEventsNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<SnapTracksNodeDefinition, 'id' | 'type' | 'configuration'>
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
    typeof CanvasNodeType.GeometryDerive,
    GeometryDeriveConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.GeometrySimplify,
    GeometrySimplifyConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialNearest,
    SpatialNearestConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialSummarizeWithin,
    SpatialSummarizeWithinConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialOverlay,
    SpatialOverlayConfiguration
  >
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.TrackReconstruct, TrackReconstructConfiguration>
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.TrackMotionStatistics,
    TrackMotionStatisticsConfiguration
  >
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.TrackFindDwell, TrackFindDwellConfiguration>
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.TrackDetectIncidents,
    TrackDetectIncidentsConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialBinAggregate,
    SpatialBinAggregateConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialPointCluster,
    SpatialPointClusterConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialCenterDispersion,
    SpatialCenterDispersionConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialDensity,
    SpatialDensityConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialHotSpots,
    SpatialHotSpotsConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialMultiVariableGrid,
    SpatialMultiVariableGridConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialSimilarLocations,
    SpatialSimilarLocationsConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialDescribeDataset,
    SpatialDescribeDatasetConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialEnrichFromGrid,
    SpatialEnrichFromGridConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.SpatialGroupByProximity,
    SpatialGroupByProximityConfiguration
  >
  | CanvasNodeRuntimeBase<
    typeof CanvasNodeType.TraceProximityEvents,
    TraceProximityEventsConfiguration
  >
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.SnapTracks, SnapTracksConfiguration>
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
  [CanvasNodeType.GeometryDerive]: createGeometryDeriveConfiguration,
  [CanvasNodeType.GeometrySimplify]: createGeometrySimplifyConfiguration,
  [CanvasNodeType.SpatialNearest]: createSpatialNearestConfiguration,
  [CanvasNodeType.SpatialSummarizeWithin]: createSpatialSummarizeWithinConfiguration,
  [CanvasNodeType.SpatialOverlay]: createSpatialOverlayConfiguration,
  [CanvasNodeType.TrackReconstruct]: createTrackReconstructConfiguration,
  [CanvasNodeType.TrackMotionStatistics]: createTrackMotionStatisticsConfiguration,
  [CanvasNodeType.TrackFindDwell]: createTrackFindDwellConfiguration,
  [CanvasNodeType.TrackDetectIncidents]: createTrackDetectIncidentsConfiguration,
  [CanvasNodeType.SpatialBinAggregate]: createSpatialBinAggregateConfiguration,
  [CanvasNodeType.SpatialPointCluster]: createSpatialPointClusterConfiguration,
  [CanvasNodeType.SpatialCenterDispersion]: createSpatialCenterDispersionConfiguration,
  [CanvasNodeType.SpatialDensity]: createSpatialDensityConfiguration,
  [CanvasNodeType.SpatialHotSpots]: createSpatialHotSpotsConfiguration,
  [CanvasNodeType.SpatialMultiVariableGrid]: createSpatialMultiVariableGridConfiguration,
  [CanvasNodeType.SpatialSimilarLocations]: createSpatialSimilarLocationsConfiguration,
  [CanvasNodeType.SpatialDescribeDataset]: createSpatialDescribeDatasetConfiguration,
  [CanvasNodeType.SpatialEnrichFromGrid]: createSpatialEnrichFromGridConfiguration,
  [CanvasNodeType.SpatialGroupByProximity]: createSpatialGroupByProximityConfiguration,
  [CanvasNodeType.TraceProximityEvents]: createTraceProximityEventsConfiguration,
  [CanvasNodeType.SnapTracks]: createSnapTracksConfiguration,
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
