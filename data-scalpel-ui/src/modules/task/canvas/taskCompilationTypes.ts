import type { DataSourcePurpose } from '../../datasource';
import type {
  CanvasColumnSchema,
  CanvasDefinition,
  CanvasExecutionMode,
  CanvasTableSchema,
  CanvasValidationIssue,
  CanvasValidationResult,
} from './canvasTypes';

export interface TaskCompilationMetadataTable {
  tableName: string;
  objectType: 'TABLE' | 'VIEW' | 'API_RESOURCE' | 'SPATIAL_FEATURE_RESOURCE';
  columns: CanvasColumnSchema[];
  uniqueKeys: TaskCompilationMetadataUniqueKey[];
}

export interface TaskCompilationMetadataUniqueKey {
  name: string | null;
  type: 'PRIMARY_KEY' | 'UNIQUE_INDEX';
  columns: string[];
}

export interface TaskCompilationMetadataDataSource {
  id: string;
  enabled: boolean;
  connectionKind: 'JDBC' | 'HTTP_API' | 'KAFKA' | 'S3';
  jdbcDatabaseType: 'POSTGRESQL' | 'MYSQL' | null;
  purposes: DataSourcePurpose[];
  tables: TaskCompilationMetadataTable[];
}

export interface TaskCompilationMetadataModel {
  id: string;
  code: string;
  name: string;
  schemaVersion: number;
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED';
  physicalTableMode: 'MANAGED' | 'EXTERNAL';
  dataSourceId: string;
  catalogName: string | null;
  schemaName: string | null;
  physicalTableName: string;
  columns: CanvasColumnSchema[];
}

export interface TaskCompilationMetadataFileDatasetTable {
  id: string;
  code: string;
  name: string;
  datasetType: 'CSV' | 'TSV' | 'TXT' | 'JSON' | 'JSONL' | 'PARQUET' | 'AVRO' | 'EXCEL' | 'GDB' | 'SHP';
  parseStatus: 'QUEUED' | 'PARSING' | 'SCHEMA_READY' | 'READY';
  fileStatus: 'PREPARING' | 'READY';
  columns: CanvasColumnSchema[];
}

export interface TaskCompilationMetadataSnapshot {
  dataSources: TaskCompilationMetadataDataSource[];
  models: TaskCompilationMetadataModel[];
  fileDatasetTables: TaskCompilationMetadataFileDatasetTable[];
}

export interface TaskCompilationRequest {
  requestId: string;
  task: {
    type: 'CANVAS';
    definition: CanvasDefinition;
    executionMode?: CanvasExecutionMode;
  };
  metadataSnapshot: TaskCompilationMetadataSnapshot;
}

export type TaskCompilationNodeState = 'OK' | 'WARNING' | 'ERROR';

export interface TaskCompilationNodeResult {
  nodeId: string;
  state: TaskCompilationNodeState;
  inputTables: CanvasTableSchema[];
  outputTables: CanvasTableSchema[];
  issues: CanvasValidationIssue[];
}

export interface TaskCompilationResponse {
  requestId: string;
  taskType: 'CANVAS';
  valid: boolean;
  durationMs: number;
  sparkApplicationId: string;
  canvasIssues: CanvasValidationIssue[];
  nodeResults: TaskCompilationNodeResult[];
}

export interface TaskCompilationCancellationResponse {
  requestId: string;
  state: 'CANCEL_REQUESTED';
}

export type CanvasTaskCompilationStatus =
  | 'IDLE'
  | 'WAITING_METADATA'
  | 'METADATA_ERROR'
  | 'WAITING'
  | 'COMPILING'
  | 'SUCCESS'
  | 'UNAVAILABLE';

export const taskCompilationValidation = (
  response: TaskCompilationResponse,
): CanvasValidationResult => ({
  valid: response.valid,
  canvasIssues: response.canvasIssues,
  nodeResults: new Map(response.nodeResults.map((result) => [result.nodeId, {
    nodeId: result.nodeId,
    issues: result.issues,
    inputTables: result.inputTables,
    outputTables: result.outputTables,
  }])),
});
