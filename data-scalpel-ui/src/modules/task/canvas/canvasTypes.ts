import type { PlatformDataType } from '../../model';

export const CANVAS_SCHEMA_VERSION = 1 as const;
export const CANVAS_SCHEMA_MINOR_VERSION = 5 as const;
export const CANVAS_LEGACY_SCHEMA_MINOR_VERSION = 0 as const;

export const CanvasNodeType = {
  ModelInput: 'MODEL_INPUT',
  JdbcInput: 'JDBC_INPUT',
  FileDatasetInput: 'FILE_DATASET_INPUT',
  HttpApiInput: 'HTTP_API_INPUT',
  KafkaInput: 'KAFKA_INPUT',
  Join: 'JOIN',
  StreamJoin: 'STREAM_JOIN',
  Rename: 'RENAME',
  ModelOutput: 'MODEL_OUTPUT',
  JdbcOutput: 'JDBC_OUTPUT',
  KafkaOutput: 'KAFKA_OUTPUT',
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

export interface JdbcInputConfiguration {
  dataSourceId: string;
  tableName: string;
}

export interface FileDatasetInputConfiguration {
  fileDatasetTableId: string;
}

export interface ModelInputConfiguration {
  modelId: string;
}

export interface HttpApiRuntimeParameter {
  name: string;
  value: string;
}

export interface HttpApiInputConfiguration {
  dataSourceId: string;
  resourceId: string;
  outputTableName: string;
  runtimeParameters: HttpApiRuntimeParameter[];
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

export interface KafkaInputConfiguration {
  dataSourceId: string;
  topic: string;
  valueSchema: KafkaValueSchema;
  outputTableName: string;
  startingOffsets: KafkaStartingOffsets | null;
}

export type JoinType = 'INNER' | 'LEFT' | 'RIGHT' | 'FULL';

export interface JoinCondition {
  leftColumnName: string;
  operator: 'EQUALS';
  rightColumnName: string;
}

export interface JoinConfiguration {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: JoinType | null;
  conditions: JoinCondition[];
}

export type StreamJoinType = 'INNER' | 'LEFT';

export interface StreamJoinConfiguration {
  leftTableName: string;
  rightTableName: string;
  outputTableName: string;
  joinType: StreamJoinType | null;
  conditions: JoinCondition[];
}

export interface RenameConfiguration {
  sourceTableName: string;
  outputTableName: string;
  columnMappings: CanvasColumnMapping[];
}

export type JdbcWriteMode = 'APPEND' | 'OVERWRITE';

export type ColumnMappingMode = 'BY_NAME' | 'EXPLICIT';

export interface CanvasColumnMapping {
  sourceColumnName: string;
  targetColumnName: string;
}

export type JdbcColumnMapping = CanvasColumnMapping;

export interface JdbcOutputConfiguration {
  sourceTableName: string;
  dataSourceId: string;
  targetTableName: string;
  writeMode: JdbcWriteMode | null;
  columnMappingMode: ColumnMappingMode | null;
  columnMappings: CanvasColumnMapping[];
}

export interface ModelOutputConfiguration {
  sourceTableName: string;
  targetModelId: string;
  writeMode: JdbcWriteMode | null;
  columnMappingMode: ColumnMappingMode | null;
  columnMappings: CanvasColumnMapping[];
}

export interface KafkaOutputConfiguration {
  sourceTableName: string;
  dataSourceId: string;
  topic: string;
  valueSchema: KafkaValueSchema;
  keyColumnName: string;
  columnMappingMode: ColumnMappingMode | null;
  columnMappings: CanvasColumnMapping[];
}

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

export type FileDatasetInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.FileDatasetInput,
  FileDatasetInputConfiguration
>;

export type HttpApiInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.HttpApiInput,
  HttpApiInputConfiguration
>;

export type KafkaInputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.KafkaInput,
  KafkaInputConfiguration
>;

export type JoinNodeDefinition = CanvasNodeBase<typeof CanvasNodeType.Join, JoinConfiguration>;

export type StreamJoinNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.StreamJoin,
  StreamJoinConfiguration
>;

export type RenameNodeDefinition = CanvasNodeBase<typeof CanvasNodeType.Rename, RenameConfiguration>;

export type JdbcOutputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.JdbcOutput,
  JdbcOutputConfiguration
>;

export type ModelOutputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.ModelOutput,
  ModelOutputConfiguration
>;

export type KafkaOutputNodeDefinition = CanvasNodeBase<
  typeof CanvasNodeType.KafkaOutput,
  KafkaOutputConfiguration
>;

export type CanvasNodeDefinition =
  | ModelInputNodeDefinition
  | JdbcInputNodeDefinition
  | FileDatasetInputNodeDefinition
  | HttpApiInputNodeDefinition
  | KafkaInputNodeDefinition
  | JoinNodeDefinition
  | StreamJoinNodeDefinition
  | RenameNodeDefinition
  | ModelOutputNodeDefinition
  | JdbcOutputNodeDefinition
  | KafkaOutputNodeDefinition;

export type CanvasNodeConfigurationUpdate =
  | Pick<ModelInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<JdbcInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<FileDatasetInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<HttpApiInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<KafkaInputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<JoinNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<StreamJoinNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<RenameNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<ModelOutputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<JdbcOutputNodeDefinition, 'id' | 'type' | 'configuration'>
  | Pick<KafkaOutputNodeDefinition, 'id' | 'type' | 'configuration'>;

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

export type CanvasNodeRuntimeSummary =
  | {
    kind: 'JDBC';
    dataSourceName: string;
    qualifiedTableName: string;
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
    kind: 'FILE_DATASET';
    fileDatasetName: string;
    tableName: string;
    tableCode: string;
    status: string;
  };

interface CanvasNodeRuntimeBase<T extends CanvasNodeType, C> {
  type: T;
  name: string;
  configuration: C;
  validation?: CanvasNodeValidationBadge;
  summary?: CanvasNodeRuntimeSummary;
}

export type CanvasNodeRuntimeData =
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.ModelInput, ModelInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.JdbcInput, JdbcInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.FileDatasetInput, FileDatasetInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.HttpApiInput, HttpApiInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.KafkaInput, KafkaInputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.Join, JoinConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.StreamJoin, StreamJoinConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.Rename, RenameConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.ModelOutput, ModelOutputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.JdbcOutput, JdbcOutputConfiguration>
  | CanvasNodeRuntimeBase<typeof CanvasNodeType.KafkaOutput, KafkaOutputConfiguration>;

export const emptyNodeConfiguration = (type: CanvasNodeType): CanvasNodeConfiguration => {
  switch (type) {
    case CanvasNodeType.ModelInput:
      return { modelId: '' };
    case CanvasNodeType.JdbcInput:
      return { dataSourceId: '', tableName: '' };
    case CanvasNodeType.FileDatasetInput:
      return { fileDatasetTableId: '' };
    case CanvasNodeType.HttpApiInput:
      return { dataSourceId: '', resourceId: '', outputTableName: '', runtimeParameters: [] };
    case CanvasNodeType.KafkaInput:
      return {
        dataSourceId: '',
        topic: '',
        valueSchema: { columns: [] },
        outputTableName: '',
        startingOffsets: null,
      };
    case CanvasNodeType.Join:
      return {
        leftTableName: '',
        rightTableName: '',
        outputTableName: '',
        joinType: null,
        conditions: [],
      };
    case CanvasNodeType.StreamJoin:
      return {
        leftTableName: '',
        rightTableName: '',
        outputTableName: '',
        joinType: null,
        conditions: [],
      };
    case CanvasNodeType.Rename:
      return {
        sourceTableName: '',
        outputTableName: '',
        columnMappings: [],
      };
    case CanvasNodeType.ModelOutput:
      return {
        sourceTableName: '',
        targetModelId: '',
        writeMode: null,
        columnMappingMode: null,
        columnMappings: [],
      };
    case CanvasNodeType.JdbcOutput:
      return {
        sourceTableName: '',
        dataSourceId: '',
        targetTableName: '',
        writeMode: null,
        columnMappingMode: null,
        columnMappings: [],
      };
    case CanvasNodeType.KafkaOutput:
      return {
        sourceTableName: '',
        dataSourceId: '',
        topic: '',
        valueSchema: { columns: [] },
        keyColumnName: '',
        columnMappingMode: null,
        columnMappings: [],
      };
  }
};

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
