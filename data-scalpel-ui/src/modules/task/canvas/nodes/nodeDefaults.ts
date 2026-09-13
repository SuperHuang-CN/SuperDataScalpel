import { createNearestMatching } from './spatialNearest/matching';
import { createMotionWindowOptions } from './trackMotionStatistics/windowOptions';
import { createWithinGroupResult } from './spatialSummarizeWithin/groupResult';
import { createReconstructionOptions } from './trackReconstruct/reconstruction';
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
  GeometryDeriveConfiguration,
  GeometrySimplifyConfiguration,
  SpatialNearestConfiguration,
  SpatialSummarizeWithinConfiguration,
  SpatialOverlayConfiguration,
  TrackReconstructConfiguration,
  TrackMotionStatisticsConfiguration,
  TrackFindDwellConfiguration,
  TrackDetectIncidentsConfiguration,
  SpatialBinAggregateConfiguration,
  SpatialPointClusterConfiguration,
  SpatialCenterDispersionConfiguration,
  SpatialDensityConfiguration,
  SpatialHotSpotsConfiguration,
  SpatialMultiVariableGridConfiguration,
  SpatialSimilarLocationsConfiguration,
  SpatialDescribeDatasetConfiguration,
  SpatialEnrichFromGridConfiguration,
  SpatialGroupByProximityConfiguration,
  TraceProximityEventsConfiguration,
  SnapTracksConfiguration,
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
  valueFormat: 'JSON',
  metadataFields: ['KEY', 'TOPIC', 'PARTITION', 'OFFSET', 'TIMESTAMP'],
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
  eventTimeColumn: null,
  watermarkDelaySeconds: null,
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

export const createGeometryDeriveConfiguration = (): GeometryDeriveConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  derivations: [],
});

export const createGeometrySimplifyConfiguration = (): GeometrySimplifyConfiguration => ({
  sourceTableName: '',
  geometryColumnName: '',
  outputTableName: '',
  outputColumnName: 'simplified_geometry',
  algorithm: 'TOPOLOGY_PRESERVING',
  tolerance: null,
  toleranceUnit: 'SOURCE_CRS_UNIT',
  geometryPolicy: 'PRESERVE_DIMENSION',
});

export const createSpatialNearestConfiguration = (): SpatialNearestConfiguration => ({
  sourceTableName: '',
  sourceGeometryColumnName: '',
  candidateTableName: '',
  candidateGeometryColumnName: '',
  candidateIdColumnName: '',
  distanceMethod: null,
  nearestCount: 1,
  maximumDistance: null,
  maximumDistanceUnit: null,
  includeUnmatched: false,
  outputTableName: '',
  distanceColumnName: 'distance',
  distanceOutputUnit: 'METERS',
  rankColumnName: 'nearest_rank',
  outputColumns: [],
  matching: createNearestMatching(),
});

export const createSpatialSummarizeWithinConfiguration = (): SpatialSummarizeWithinConfiguration => ({
  areaTableName: '',
  areaGeometryColumnName: '',
  summaryTableName: '',
  summaryGeometryColumnName: '',
  includeEmptyAreas: true,
  distanceMethod: 'PLANAR',
  lengthUnit: 'SOURCE_CRS_UNIT',
  areaUnit: 'SQUARE_METERS',
  areaOutputColumns: [],
  statistics: [],
  groupSummary: null,
  groupResult: createWithinGroupResult(),
  temporalSlicing: null,
  outputTableName: '',
});

export const createSpatialOverlayConfiguration = (): SpatialOverlayConfiguration => ({
  leftTableName: '',
  leftGeometryColumnName: '',
  rightTableName: '',
  rightGeometryColumnName: '',
  operation: 'INTERSECTION',
  geometryPolicy: 'FAMILY_2D',
  outputTableName: '',
  outputGeometryColumnName: 'overlay_geometry',
  outputColumns: [],
});

export const createTrackBoundaryConfiguration = () => ({
  maximumTimeGap: null,
  maximumTimeGapUnit: null,
  maximumDistanceGap: null,
  maximumDistanceGapUnit: null,
});

export const createTrackReconstructConfiguration = (): TrackReconstructConfiguration => ({
  reconstruction: createReconstructionOptions(),
  sourceTableName: '',
  pointGeometryColumnName: '',
  trackIdColumns: [],
  timeColumnName: '',
  distanceMethod: 'PLANAR',
  boundaries: createTrackBoundaryConfiguration(),
  summaryStatistics: [],
  outputTableName: '',
  outputGeometryColumnName: 'track_geometry',
  startTimeColumnName: 'start_time',
  endTimeColumnName: 'end_time',
  pointCountColumnName: 'point_count',
});

export const createTrackMotionStatisticsConfiguration = (): TrackMotionStatisticsConfiguration => ({
  motionSemantics: 'OBSERVATION_WINDOW',
  windowOptions: createMotionWindowOptions(),
  sourceTableName: '',
  pointGeometryColumnName: '',
  trackIdColumns: [],
  timeColumnName: '',
  distanceMethod: 'GEODESIC',
  boundaries: createTrackBoundaryConfiguration(),
  historyPoints: 1,
  idleDistanceThreshold: null,
  idleDistanceThresholdUnit: null,
  metrics: [],
  outputTableName: '',
});

export const createTrackFindDwellConfiguration = (): TrackFindDwellConfiguration => ({
  dwellSemantics: 'REFERENCE_CENTER',
  rangeOptions: { resultMode: 'MEAN_CENTERS', orderByColumns: [], durationUnit: 'MILLISECONDS',
    meanDistanceColumnName: 'mean_distance', meanDistanceUnit: 'METERS', dwellFlagColumnName: 'is_dwell' },
  sourceTableName: '',
  pointGeometryColumnName: '',
  trackIdColumns: [],
  timeColumnName: '',
  distanceMethod: 'PLANAR',
  distanceThreshold: 100,
  distanceThresholdUnit: 'METERS',
  minimumDuration: 20,
  minimumDurationUnit: 'MINUTES',
  boundaries: createTrackBoundaryConfiguration(),
  summaryStatistics: [],
  outputGeometryKind: 'CENTROID',
  outputTableName: '',
  dwellIdColumnName: 'dwell_id',
  startTimeColumnName: 'start_time',
  endTimeColumnName: 'end_time',
  durationColumnName: 'duration',
  pointCountColumnName: 'point_count',
  outputGeometryColumnName: 'dwell_geometry',
});

export const createTrackDetectIncidentsConfiguration = (): TrackDetectIncidentsConfiguration => ({
  conditionWindows: [],
  conditionScalars: [],
  incidentSemantics: 'CONDITION_LIFECYCLE',
  incidentStatusColumnName: 'incident_status',
  orderByColumns: [],
  sourceTableName: '',
  pointGeometryColumnName: null,
  trackIdColumns: [],
  timeColumnName: '',
  distanceMethod: 'PLANAR',
  boundaries: createTrackBoundaryConfiguration(),
  startCondition: { kind: 'GROUP', operator: 'AND', children: [] },
  endCondition: null,
  resultMode: 'INCIDENTS_ONLY',
  outputTableName: '',
  incidentIdColumnName: 'incident_id',
  incidentFlagColumnName: 'is_incident',
  incidentStartTimeColumnName: 'incident_start_time',
  incidentEndTimeColumnName: 'incident_end_time',
  incidentDurationColumnName: 'incident_duration',
  incidentDurationUnit: 'MILLISECONDS',
});

export const createSpatialBinAggregateConfiguration = (): SpatialBinAggregateConfiguration => ({
  binSizeSemantics: 'HEXAGON_FLAT_TO_FLAT',
  sourceTableName: '',
  pointGeometryColumnName: '',
  binShape: 'SQUARE',
  binSize: 1000,
  binSizeUnit: 'METERS',
  includeEmptyBins: false,
  statistics: [{
    statisticId: crypto.randomUUID(),
    kind: 'COUNT',
    sourceColumnName: null,
    outputColumnName: 'point_count',
  }],
  groupSummary: null,
  temporalSlicing: null,
  outputTableName: '',
  binIdColumnName: 'bin_id',
  binGeometryColumnName: 'bin_geometry',
});

export const createSpatialPointClusterConfiguration = (): SpatialPointClusterConfiguration => ({
  dbscan: { mode: 'SPATIAL', timeColumnName: '', searchDuration: null, searchDurationUnit: 'MINUTES' },
  sourceTableName: '',
  pointGeometryColumnName: '',
  featureIdColumnName: '',
  distanceMethod: 'PLANAR',
  parameters: {
    algorithm: 'DBSCAN',
    searchDistance: 200,
    searchDistanceUnit: 'METERS',
    minimumFeatures: 5,
  },
  outputTableName: '',
  clusterIdColumnName: 'cluster_id',
  noiseColumnName: 'is_noise',
});

export const createSpatialCenterDispersionConfiguration = (
): SpatialCenterDispersionConfiguration => ({
  sourceTableName: '',
  pointGeometryColumnName: '',
  featureIdColumnName: null,
  groupByColumns: [],
  weightColumnName: null,
  analyses: [{ analysisId: crypto.randomUUID(), kind: 'MEAN_CENTER', outputColumnName: 'mean_center', standardDeviations: null, outputTableName: '' }],
  outputTableName: '',
  resultMode: 'ANALYSIS_TABLES',
});

export const createSpatialDensityConfiguration = (): SpatialDensityConfiguration => ({
  sourceTableName: '',
  pointGeometryColumnName: '',
  fields: [],
  weighting: 'UNIFORM',
  binShape: 'SQUARE',
  binSize: 1000,
  binSizeUnit: 'METERS',
  radius: 2000,
  radiusUnit: 'METERS',
  areaUnit: 'SQUARE_KILOMETERS',
  temporalSlicing: null,
  outputTableName: '',
  binIdColumnName: 'bin_id',
  binGeometryColumnName: 'bin_geometry',
  countDensityColumnName: 'point_density',
});

export const createSpatialHotSpotsConfiguration = (): SpatialHotSpotsConfiguration => ({
  sourceTableName: '',
  pointGeometryColumnName: '',
  analysisSource: 'POINT_COUNT',
  analysisColumnName: null,
  binSize: 1000,
  binSizeUnit: 'METERS',
  neighborhoodDistance: 2000,
  neighborhoodDistanceUnit: 'METERS',
  temporalSlicing: null,
  multipleTesting: 'FDR_BH',
  outputTableName: '',
  binIdColumnName: 'bin_id',
  binGeometryColumnName: 'bin_geometry',
  pointCountColumnName: 'point_count',
  analysisValueColumnName: 'analysis_value',
  zScoreColumnName: 'gi_z_score',
  pValueColumnName: 'gi_p_value',
  adjustedPValueColumnName: 'gi_adjusted_p_value',
  confidenceBinColumnName: 'gi_bin',
});

export const createSpatialMultiVariableGridConfiguration = (
): SpatialMultiVariableGridConfiguration => ({
  variables: [],
  binShape: 'SQUARE',
  binSize: 1000,
  binSizeUnit: 'METERS',
  outputTableName: '',
  binIdColumnName: 'bin_id',
  binGeometryColumnName: 'bin_geometry',
});

export const createSpatialSimilarLocationsConfiguration = (
): SpatialSimilarLocationsConfiguration => ({
  referenceTableName: '',
  referenceIdColumnName: '',
  referenceGeometryColumnName: '',
  referenceFilter: null,
  candidateTableName: '',
  candidateIdColumnName: '',
  candidateGeometryColumnName: '',
  candidateFilter: null,
  analysisFields: [],
  appendFields: [],
  matchMethod: 'ATTRIBUTE_VALUES',
  resultMode: 'MOST_SIMILAR',
  numberOfResults: 10,
  outputTableName: '',
  outputGeometryColumnName: 'geometry',
  locationTypeColumnName: 'location_type',
  similarityRankColumnName: 'simrank',
  dissimilarityRankColumnName: 'dsimrank',
  similarityIndexColumnName: 'simindex',
  cosineIndexColumnName: 'cosimindex',
  labelRankColumnName: 'labelrank',
  referenceIdOutputColumnName: 'referenceid',
  searchIdOutputColumnName: 'searchid',
});

export const createSpatialDescribeDatasetConfiguration = (
): SpatialDescribeDatasetConfiguration => ({
  sourceTableName: '',
  geometryColumnName: '',
  statisticsTableName: '',
  descriptionTableName: '',
  sampleSize: 0,
  sampleTableName: '',
  extentOutput: false,
  extentTableName: '',
});

export const createSpatialEnrichFromGridConfiguration = (
): SpatialEnrichFromGridConfiguration => ({
  pointTableName: '',
  pointGeometryColumnName: '',
  gridTableName: '',
  gridGeometryColumnName: '',
  gridIdColumnName: '',
  enrichFields: [],
  outputTableName: '',
});

export const createSpatialGroupByProximityConfiguration = (
): SpatialGroupByProximityConfiguration => ({
  sourceTableName: '',
  geometryColumnName: '',
  spatialRelationship: 'INTERSECTS',
  spatialNearDistance: 100,
  spatialNearDistanceUnit: 'METERS',
  temporalCondition: null,
  attributeConditions: [],
  groupIdColumnName: 'group_id',
  outputTableName: '',
});

export const createTraceProximityEventsConfiguration = (
): TraceProximityEventsConfiguration => ({
  sourceTableName: '',
  pointGeometryColumnName: '',
  entityIdColumnName: '',
  timeColumnName: '',
  distanceMethod: 'PLANAR',
  spatialSearchDistance: 10,
  spatialSearchDistanceUnit: 'METERS',
  temporalSearchDistance: 5,
  temporalSearchDistanceUnit: 'MINUTES',
  interestSource: 'ENTITY_IDS',
  entitiesOfInterest: [],
  entitiesOfInterestTableName: '',
  interestEntityIdColumnName: '',
  interestStartTimeColumnName: null,
  maxTraceDepth: 3,
  attributeMatchColumns: [],
  includeTracks: false,
  outputTableName: '',
  tracksOutputTableName: '',
  fromEntityIdColumnName: 'trace_from_id',
  toEntityIdColumnName: 'trace_to_id',
  depthColumnName: 'trace_depth',
  durationMinutesColumnName: 'trace_duration_minutes',
  eventTimeColumnName: 'trace_event_time',
});

export const createSnapTracksConfiguration = (): SnapTracksConfiguration => ({
  pointTableName: '',
  pointGeometryColumnName: '',
  trackIdColumns: [],
  timeColumnName: '',
  orderByColumns: [],
  lineTableName: '',
  lineGeometryColumnName: '',
  lineIdColumnName: '',
  fromNodeColumnName: '',
  toNodeColumnName: '',
  searchDistance: 30,
  searchDistanceUnit: 'METERS',
  distanceMethod: 'PLANAR',
  boundaries: createTrackBoundaryConfiguration(),
  directionMatching: null,
  lineFields: [],
  outputMode: 'ALL_FEATURES',
  outputTableName: '',
  snappedGeometryColumnName: 'snapped_geometry',
  matchedLineIdColumnName: 'matched_line_id',
  matchStatusColumnName: 'match_status',
  originalXColumnName: 'original_x',
  originalYColumnName: 'original_y',
  matchXColumnName: 'match_x',
  matchYColumnName: 'match_y',
  matchDistanceColumnName: 'match_distance',
});

export const createGeometryBufferConfiguration = (): GeometryBufferConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  outputColumnName: 'buffer_geometry',
  distance: 100,
  mode: 'PLANAR',
  distanceUnit: 'SOURCE_CRS_UNIT',
  distanceSource: 'CONSTANT',
  distanceFieldName: null,
  distanceExpression: null,
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
  geometryPolicy: 'SOURCE_FAMILY_2D',
  maskCombination: 'DISSOLVE_ALL',
});

export const createSpatialAggregateConfiguration = (): SpatialAggregateConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  groupByColumns: [],
  aggregations: [],
  dissolve: null,
});

export const createSpatialJoinConfiguration = (): SpatialJoinConfiguration => ({
  leftTableName: '',
  rightTableName: '',
  outputTableName: '',
  joinType: 'INNER',
  conditions: [],
  attributeConditions: [],
  outputColumns: [],
  joinOperation: 'JOIN_ONE_TO_MANY',
  oneToOne: null,
  temporalCondition: null,
  spatialNear: null,
  distanceOutput: null,
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
  mergingTables: [],
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
