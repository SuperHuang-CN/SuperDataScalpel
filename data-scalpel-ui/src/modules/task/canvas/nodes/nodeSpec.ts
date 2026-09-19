import type { ComponentType, Ref } from 'react';
import type {
  CanvasExecutionMode,
  CanvasNodeByType,
  CanvasNodeCategory,
  CanvasNodeConfigurationByType,
  CanvasNodeConfigurationUpdateByType,
  CanvasNodeRuntimeDataByType,
  CanvasNodeType,
  CanvasNodeValidationResult,
} from '../canvasTypes';
import type { CanvasMetadataReference } from './metadataReferences';
import type { CanvasNodeGroup } from './nodeGroups';

export const CANVAS_RUNTIME_NODE_SHAPE = 'datascalpel-canvas-node';

export const CanvasNodeIconKey = {
  Database: 'DATABASE',
  DatabaseQuery: 'DATABASE_QUERY',
  Model: 'MODEL',
  ModelOutput: 'MODEL_OUTPUT',
  File: 'FILE',
  FileOutput: 'FILE_OUTPUT',
  Api: 'API',
  Stream: 'STREAM',
  StreamOutput: 'STREAM_OUTPUT',
  Join: 'JOIN',
  StreamJoin: 'STREAM_JOIN',
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
  Rename: 'RENAME',
  Filter: 'FILTER',
  SqlTransform: 'SQL_TRANSFORM',
  Columns: 'COLUMNS',
  Derive: 'DERIVE',
  Cast: 'CAST',
  Aggregate: 'AGGREGATE',
  Union: 'UNION',
  Deduplicate: 'DEDUPLICATE',
  NullHandling: 'NULL_HANDLING',
  ValueMapping: 'VALUE_MAPPING',
  Masking: 'MASKING',
  Json: 'JSON',
  Window: 'WINDOW',
  TopN: 'TOP_N',
  JdbcOutput: 'JDBC_OUTPUT',
  SnapshotSync: 'SNAPSHOT_SYNC',
} as const;

export type CanvasNodeIconKey = typeof CanvasNodeIconKey[keyof typeof CanvasNodeIconKey];

export interface CanvasNodeGraphCapability {
  minInputs: number;
  maxInputs: number | null;
  minOutputs: number;
  maxOutputs: number | null;
}

export interface CanvasNodeSize {
  width: number;
  height: number;
}

export interface CanvasNodeBodyProps<T extends CanvasNodeType> {
  data: CanvasNodeRuntimeDataByType<T>;
}

export interface CanvasNodeCanvasView<T extends CanvasNodeType> {
  resolveSize(configuration: CanvasNodeConfigurationByType<T>): CanvasNodeSize;
  Body: ComponentType<CanvasNodeBodyProps<T>>;
}

export type CanvasParseResult<T> =
  | { success: true; value: T }
  | { success: false; errors: string[] };

export interface CanvasNodeInspectorHandle {
  apply: () => Promise<boolean>;
}

export interface CanvasNodeInspectorComponentProps<T extends CanvasNodeType> {
  node: CanvasNodeByType<T>;
  executionMode: CanvasExecutionMode;
  validation: CanvasNodeValidationResult | undefined;
  validationUnavailableMessage: string | null;
  onApply: (update: CanvasNodeConfigurationUpdateByType<T>) => void;
  onDirtyChange: (dirty: boolean) => void;
  inspectorRef: Ref<CanvasNodeInspectorHandle>;
}

export type CanvasNodeInspectorComponent<T extends CanvasNodeType> =
  ComponentType<CanvasNodeInspectorComponentProps<T>>;

export interface CanvasNodeSpec<T extends CanvasNodeType> {
  type: T;
  category: CanvasNodeCategory;
  group: CanvasNodeGroup;
  label: string;
  description: string;
  searchKeywords: readonly string[];
  iconKey: CanvasNodeIconKey;
  order: number;
  canvasView: CanvasNodeCanvasView<T>;
  supportedModes: readonly CanvasExecutionMode[];
  introducedInMinor: number;
  graph: CanvasNodeGraphCapability;
  createDefaultConfiguration(): CanvasNodeConfigurationByType<T>;
  parseConfiguration(
    value: unknown,
    path: string,
  ): CanvasParseResult<CanvasNodeConfigurationByType<T>>;
  summarize(data: CanvasNodeRuntimeDataByType<T>): string;
  collectMetadataReferences(node: CanvasNodeByType<T>): readonly CanvasMetadataReference[];
  loadInspector(): Promise<{ default: CanvasNodeInspectorComponent<T> }>;
}

export const defineCanvasNodeSpec = <T extends CanvasNodeType>(
  spec: CanvasNodeSpec<T>,
): CanvasNodeSpec<T> => spec;
