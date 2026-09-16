import type { StandardDictionarySummary } from '../../standard';
import type { FileDatasetParseStatus, FileDatasetType } from '../../filedataset';

export type DataModelStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';

export interface DataModelReferenceTask {
  id: string;
  name: string;
  type: 'LOCAL_SQL' | 'SPARK_CANVAS' | 'SPARK_STREAMING_CANVAS' | 'SPARK_MODEL_QUALITY' | 'SPARK_JAR' | 'SPARK_STREAMING_JAR';
  status: 'DRAFT' | 'PUBLISHED' | 'DISABLED';
  role: 'INPUT' | 'OUTPUT';
  referenceType: 'LOCAL_SQL_INPUT' | 'LOCAL_SQL_OUTPUT' | 'CANVAS_NODE' | 'MODEL_QUALITY_TARGET' | 'SPARK_JAR_RESOURCE_BINDING' | 'CURRENT_LINEAGE';
  nodeId: string | null;
  nodeName: string | null;
}

export interface DataModelReferenceService {
  id: string;
  name: string;
  type: 'STANDARD_TABLE' | 'SQL_QUERY' | 'SCRIPT_API';
  status: 'DRAFT' | 'ENABLED' | 'DISABLED';
  role: 'PRIMARY' | 'REFERENCE';
  ordinal: number | null;
}

export interface DataModelReferences {
  metrics?: { id: string; name: string; code: string }[];
  businessObjectTypes?: { id: string; name: string; code: string }[];
  modelId: string;
  deletable: boolean;
  tasks: DataModelReferenceTask[];
  services: DataModelReferenceService[];
}

export type PhysicalTableMode = 'MANAGED' | 'EXTERNAL';

export type PhysicalStatisticQuality = 'EXACT' | 'ESTIMATED' | 'UNAVAILABLE';

export type PhysicalStatisticsRefreshStatus =
  | 'SUCCESS'
  | 'PARTIAL'
  | 'FAILED'
  | 'NOT_FOUND'
  | 'UNSUPPORTED';

export interface DataModelPhysicalStatistics {
  modelId: string;
  rowCount: number | null;
  rowCountQuality: PhysicalStatisticQuality;
  storageBytes: number | null;
  storageQuality: PhysicalStatisticQuality;
  collectedAt: string | null;
  lastRefreshAt: string;
  lastRefreshStatus: PhysicalStatisticsRefreshStatus;
  message: string | null;
}

export type ModelWarehouseLayerInputPolicy = 'UNRESTRICTED' | 'ALLOW_LIST';

export interface ModelWarehouseLayerSummary {
  id: string;
  code: string;
  name: string;
  color: string | null;
  enabled: boolean;
  modelCodePrefix: string | null;
}

export interface ModelWarehouseLayer extends ModelWarehouseLayerSummary {
  description: string | null;
  sortOrder: number;
  inputLayerPolicy: ModelWarehouseLayerInputPolicy;
  allowedInputLayers: ModelWarehouseLayerSummary[];
  referencedModelCount: number;
  referencedAsInputByLayerCount: number;
  deletable: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateModelWarehouseLayerRequest {
  code: string;
  name: string;
  description?: string;
  color?: string;
  sortOrder: number;
  modelCodePrefix?: string;
  inputLayerPolicy?: ModelWarehouseLayerInputPolicy;
  allowedInputLayerIds?: string[];
}

export type UpdateModelWarehouseLayerRequest = CreateModelWarehouseLayerRequest;

export interface ModelFieldTemplateField {
  id: string;
  code: string;
  name: string;
  fieldType: PlatformDataType;
  length: number | null;
  precision: number | null;
  scale: number | null;
  geometry?: GeometryTypeDefinition | null;
  nullable: boolean;
  primaryKey: boolean;
  sortOrder: number;
  description: string | null;
  standardDictionary?: StandardDictionarySummary | null;
}

export interface ModelFieldTemplate {
  id: string;
  code: string;
  name: string;
  category: string | null;
  description: string | null;
  sortOrder: number;
  enabled: boolean;
  version: number;
  fieldCount: number;
  fields: ModelFieldTemplateField[];
  createdAt: string;
  updatedAt: string;
}

export interface ModelFieldTemplateFieldInput extends DataModelFieldInput {
  id?: string;
}

export interface CreateModelFieldTemplateRequest {
  code: string;
  name: string;
  category?: string;
  description?: string;
  sortOrder: number;
  fields: ModelFieldTemplateFieldInput[];
}

export interface UpdateModelFieldTemplateRequest extends CreateModelFieldTemplateRequest {
  expectedVersion: number;
}

export type PhysicalTableState = 'NOT_FOUND' | 'MATCHED' | 'DRIFTED' | 'UNREACHABLE' | 'UNSUPPORTED';

export type PhysicalTableDifferenceType =
  | 'MISSING_COLUMN'
  | 'EXTRA_COLUMN'
  | 'TYPE_MISMATCH'
  | 'LENGTH_MISMATCH'
  | 'PRECISION_MISMATCH'
  | 'NULLABILITY_MISMATCH'
  | 'PRIMARY_KEY_MISMATCH'
  | 'STORAGE_CONFIGURATION_MISMATCH';

export type PhysicalTableChangeStatus =
  | 'PLANNED'
  | 'APPLYING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'PARTIAL'
  | 'CANCELLED'
  | 'SUPERSEDED';

export type TableChangeStrategy =
  | 'METADATA_ONLY'
  | 'IN_PLACE'
  | 'REBUILD_RECOMMENDED'
  | 'REBUILD_REQUIRED'
  | 'UNSUPPORTED';

export type TableChangeRisk = 'SAFE' | 'CAUTION' | 'DESTRUCTIVE';

export type TableDdlAtomicity =
  | 'NOT_APPLICABLE'
  | 'TRANSACTIONAL_BATCH'
  | 'ATOMIC_SINGLE_STATEMENT'
  | 'NON_TRANSACTIONAL_SEQUENCE';

export type TableChangeExecutionMode = 'IN_PLACE' | 'REBUILD';

export type TableChangeOperationType =
  | 'ADD_COLUMN'
  | 'DROP_COLUMN'
  | 'RENAME_COLUMN'
  | 'ALTER_COLUMN_TYPE'
  | 'ALTER_COLUMN_LENGTH'
  | 'ALTER_COLUMN_PRECISION'
  | 'ALTER_COLUMN_NULLABILITY'
  | 'ALTER_STORAGE_LAYOUT'
  | 'ADD_PRIMARY_KEY'
  | 'DROP_PRIMARY_KEY'
  | 'REPLACE_PRIMARY_KEY';

export type TableChangeCheckType =
  | 'STRUCTURE_FINGERPRINT_MATCH'
  | 'TABLE_EMPTY'
  | 'COLUMNS_HAVE_NO_NULLS'
  | 'COLUMNS_ARE_UNIQUE'
  | 'MAX_STRING_LENGTH'
  | 'DECIMAL_VALUES_FIT'
  | 'NO_EXTERNAL_DEPENDENCIES'
  | 'NO_REBUILD_DEPENDENCIES'
  | 'DATABASE_RUNTIME_SUPPORTED';

export type TableChangeReasonCode =
  | 'DATA_PRECHECK_REQUIRED'
  | 'POSSIBLE_DATA_LOSS'
  | 'DATA_CONVERSION_REQUIRED'
  | 'KEY_CONSTRAINT_CHANGE'
  | 'STORAGE_LAYOUT_CHANGE'
  | 'DDL_TRANSACTION_LIMITATION'
  | 'RUNTIME_CONFIGURATION_UNSUPPORTED'
  | 'OPERATION_UNSUPPORTED'
  | 'PHYSICAL_TYPE_UNSUPPORTED'
  | 'EXTERNAL_DEPENDENCY';

export type PlatformDataType =
  | 'BOOLEAN'
  | 'BYTE'
  | 'SHORT'
  | 'INTEGER'
  | 'LONG'
  | 'FLOAT'
  | 'DOUBLE'
  | 'DECIMAL'
  | 'STRING'
  | 'BINARY'
  | 'DATE'
  | 'TIMESTAMP'
  | 'TIMESTAMP_NTZ'
  | 'GEOMETRY';

export type GeometryKind =
  | 'GEOMETRY'
  | 'POINT'
  | 'LINESTRING'
  | 'POLYGON'
  | 'MULTIPOINT'
  | 'MULTILINESTRING'
  | 'MULTIPOLYGON'
  | 'GEOMETRYCOLLECTION';

export type CoordinateDimension = 'XY' | 'XYZ' | 'XYM' | 'XYZM';

export interface CrsReference {
  authority: string;
  code: number;
}

export interface GeometryTypeDefinition {
  kind: GeometryKind;
  crs: CrsReference;
  dimension: CoordinateDimension;
}

export interface PlatformTypeDefinition {
  type: PlatformDataType;
  length: number | null;
  precision: number | null;
  scale: number | null;
  geometry?: GeometryTypeDefinition | null;
}

export type TypeMappingQuality = 'EXACT' | 'NORMALIZED' | 'LOSSY' | 'UNSUPPORTED';

export type PhysicalTableColumnType =
  | 'BYTE'
  | 'SHORT'
  | 'INTEGER'
  | 'LONG'
  | 'FLOAT'
  | 'DOUBLE'
  | 'DECIMAL'
  | 'STRING'
  | 'TEXT'
  | 'BOOLEAN'
  | 'DATE'
  | 'TIMESTAMP'
  | 'TIMESTAMP_NTZ'
  | 'DATETIME'
  | 'BINARY'
  | 'GEOMETRY';

export interface ExternalTableImportColumn {
  name: string;
  nativeType: string;
  platformType: PlatformDataType | null;
  length: number | null;
  precision: number | null;
  scale: number | null;
  geometry?: GeometryTypeDefinition | null;
  nullable: boolean;
  primaryKey: boolean;
  mappingQuality: TypeMappingQuality;
  message: string | null;
  importable: boolean;
  comment: string | null;
  physicalColumnRole?: 'REGULAR' | 'TIME_KEY' | 'TAG';
}

export interface ExternalTableImportPreview {
  table: {
    catalog: string | null;
    schema: string | null;
    table: string;
  };
  importable: boolean;
  columns: ExternalTableImportColumn[];
  issues: string[];
}

export interface ManagedImportPreviewRequest {
  sourceDataSourceId: string;
  sourceTable: {
    catalog: string | null;
    schema: string | null;
    table: string;
  };
  targetStorageDataSourceId: string;
}

export interface ManagedImportColumnPreview {
  sourceName: string;
  nativeType: string;
  code: string | null;
  name: string | null;
  fieldType: PlatformDataType | null;
  length: number | null;
  precision: number | null;
  scale: number | null;
  geometry?: GeometryTypeDefinition | null;
  nullable: boolean;
  primaryKey: boolean;
  sortOrder: number;
  description: string | null;
  mappingQuality: TypeMappingQuality;
  mappingMessage: string | null;
  importable: boolean;
  issues: string[];
}

export interface ManagedImportPreview {
  sourceTable: {
    catalog: string | null;
    schema: string | null;
    table: string;
  };
  suggestedCode: string | null;
  suggestedName: string | null;
  suggestedPhysicalTableName: string | null;
  tableImportable: boolean;
  importable: boolean;
  columns: ManagedImportColumnPreview[];
  tableIssues: string[];
  issues: string[];
  warnings: string[];
}

export interface FileDatasetImportPreviewRequest {
  fileDatasetId: string;
  fileDatasetTableId: string;
  targetStorageDataSourceId: string;
}

export interface FileDatasetImportColumnPreview {
  sourceName: string;
  sourceType: PlatformTypeDefinition;
  code: string | null;
  name: string | null;
  fieldType: PlatformDataType | null;
  length: number | null;
  precision: number | null;
  scale: number | null;
  geometry?: GeometryTypeDefinition | null;
  nullable: boolean;
  primaryKey: boolean;
  sortOrder: number;
  description: string | null;
  mappingQuality: TypeMappingQuality;
  mappingMessage: string | null;
  importable: boolean;
  issues: string[];
}

export interface FileDatasetImportPreview {
  fileDatasetId: string;
  fileDatasetName: string;
  fileDatasetType: FileDatasetType;
  fileDatasetTableId: string;
  sourceTableCode: string;
  sourceTableName: string;
  parseStatus: FileDatasetParseStatus;
  sourceUpdatedAt: string;
  suggestedCode: string | null;
  suggestedName: string | null;
  suggestedPhysicalTableName: string | null;
  tableImportable: boolean;
  importable: boolean;
  unresolvedCount: number;
  columns: FileDatasetImportColumnPreview[];
  tableIssues: string[];
  issues: string[];
  warnings: string[];
}

export interface ModelMetadataImportFieldPreview {
  key: string;
  rowNumber: number;
  code: string;
  name: string;
  fieldType: PlatformDataType | null;
  length: number | null;
  precision: number | null;
  scale: number | null;
  geometry?: GeometryTypeDefinition | null;
  nullable: boolean | null;
  primaryKey: boolean | null;
  sortOrder: number | null;
  description: string;
  standardDictionaryCode?: string;
  standardDictionary?: StandardDictionarySummary | null;
  importable: boolean;
  issues: string[];
}

export interface ModelMetadataImportModelPreview {
  key: string;
  rowNumber: number;
  code: string;
  name: string;
  directoryPath: string;
  warehouseLayerCode?: string;
  warehouseLayer?: ModelWarehouseLayerSummary | null;
  physicalTableName: string;
  clickHouseOrderByColumns: string[];
  description: string;
  importable: boolean;
  issues: string[];
  warnings: string[];
  fields: ModelMetadataImportFieldPreview[];
}

export interface ModelMetadataImportPreview {
  fileName: string;
  formatVersion: number;
  importable: boolean;
  issues: string[];
  models: ModelMetadataImportModelPreview[];
}

export interface PlatformTypeCapability {
  type: PlatformDataType;
  supported: boolean;
  message: string | null;
  lengthParameterSupported: boolean;
  unboundedStringSupported: boolean;
  geometryKinds?: GeometryKind[];
  coordinateDimensions?: CoordinateDimension[];
  crsAuthorities?: string[];
}

export interface DataModel {
  id: string;
  code: string;
  name: string;
  directoryId: string | null;
  warehouseLayer?: ModelWarehouseLayerSummary | null;
  storageDataSourceId: string;
  storageDataSourceName: string;
  catalogName: string | null;
  schemaName: string | null;
  physicalTableName: string;
  physicalTableMode: PhysicalTableMode;
  clickHouseOrderByColumns: string[];
  status: DataModelStatus;
  schemaVersion: number;
  physicalStatistics?: DataModelPhysicalStatistics | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DataModelField {
  id: string;
  modelId: string;
  code: string;
  name: string;
  fieldType: PlatformDataType;
  length: number | null;
  precision: number | null;
  scale: number | null;
  geometry?: GeometryTypeDefinition | null;
  nullable: boolean;
  primaryKey: boolean;
  sortOrder: number;
  description: string | null;
  physicalColumnRole?: 'REGULAR' | 'TIME_KEY' | 'TAG';
  standardDictionary?: StandardDictionarySummary | null;
  createdAt: string;
  updatedAt: string;
}

export interface DataModelDetail {
  model: DataModel;
  fields: DataModelField[];
}

export type LineageDirection = 'UPSTREAM' | 'DOWNSTREAM' | 'BOTH';
export type LineageGranularity = 'TABLE' | 'FIELD';
export type LineageCoverage = 'MODEL_ONLY' | 'FIELD_PARTIAL' | 'FIELD_COMPLETE';
export type LineageGraphNodeKind = 'MODEL' | 'JDBC_TABLE' | 'EXTERNAL_RESOURCE' | 'TASK' | 'FIELD' | 'DATA_SERVICE';
export type LineageGraphNodeSide = 'UPSTREAM' | 'CURRENT' | 'DOWNSTREAM';
export type LineageGraphEdgeType = 'READS' | 'WRITES' | 'DERIVES' | 'FIELD_EFFECT' | 'EXPOSES';
export type LineageWriteMode = 'APPEND' | 'FULL_OVERWRITE' | 'UPSERT' | 'PARTITION_OVERWRITE' | 'SNAPSHOT_SYNC' | 'CREATE_NEW';
export type LineageExternalResourceType =
  | 'KAFKA_TOPIC'
  | 'FILE_DATASET_TABLE'
  | 'HTTP_API_RESOURCE'
  | 'SPATIAL_SERVICE_RESOURCE'
  | 'OBJECT_STORAGE_PATH'
  | 'JDBC_QUERY_RESULT';
export type LineageOutputFieldEffect =
  | 'DERIVED'
  | 'WRITTEN_UNKNOWN_SOURCE'
  | 'CONSTANT'
  | 'DEFAULT_VALUE'
  | 'NULL_FILLED'
  | 'PRESERVED'
  | 'NOT_WRITTEN';
export type LineageFieldDerivationType = 'DIRECT' | 'CALCULATED' | 'AGGREGATED';
export type LineageFieldUsageType = 'JOIN_KEY' | 'FILTER_CONDITION' | 'GROUP_KEY' | 'SORT_KEY' | 'PARTITION_KEY';

export interface LineageGraphNode {
  id: string;
  kind: LineageGraphNodeKind;
  side: LineageGraphNodeSide;
  depth: number;
  label: string;
  subtitle: string;
  modelId: string | null;
  modelFieldId: string | null;
  taskId: string | null;
  dataSourceId: string | null;
  taskStatus: DataModelStatus | null;
  definitionVersion: number | null;
  writeMode: LineageWriteMode | null;
  stale: boolean;
  externalResourceType: LineageExternalResourceType | null;
  resourceId: string | null;
  dataServiceId: string | null;
  dataServiceType: 'STANDARD_TABLE' | 'SQL_QUERY' | 'SCRIPT_API' | null;
  dataServiceStatus: 'DRAFT' | 'ENABLED' | 'DISABLED' | null;
  routePath: string | null;
  fieldOwner: {
    key: string;
    kind: Exclude<LineageGraphNodeKind, 'FIELD' | 'TASK' | 'DATA_SERVICE'>;
    label: string;
    subtitle: string | null;
    fieldOrder: number;
  } | null;
  focusRoot: boolean;
  focusFieldKeys: string[];
}

export interface LineageGraphEdge {
  id: string;
  source: string;
  target: string;
  type: LineageGraphEdgeType;
  derivationType: LineageFieldDerivationType | null;
  outputEffect: LineageOutputFieldEffect | null;
  usages: LineageFieldUsageType[];
  focusFieldKeys: string[];
}

export interface LineageGraph {
  rootNodeId: string | null;
  granularity: LineageGranularity;
  coverage: LineageCoverage | null;
  truncated: boolean;
  warnings: string[];
  nodes: LineageGraphNode[];
  edges: LineageGraphEdge[];
}

export interface LineageFocusField {
  fieldKey: string;
  modelFieldId: string | null;
  code: string;
  name: string;
  sortOrder: number;
  coverage: LineageCoverage | null;
  hasLineage: boolean;
  truncated: boolean;
  warnings: string[];
}

export interface LineageFieldGraph {
  graph: LineageGraph;
  focusFields: LineageFocusField[];
}

export interface CreateDataModelRequest {
  code: string;
  name: string;
  directoryId?: string;
  warehouseLayerId?: string;
  storageDataSourceId: string;
  physicalTableName: string;
  physicalTableMode?: PhysicalTableMode;
  clickHouseOrderByColumns?: string[];
  description?: string;
}

export interface CreateManagedDataModelDraftRequest {
  code: string;
  name: string;
  directoryId?: string;
  warehouseLayerId?: string;
  storageDataSourceId: string;
  physicalTableName: string;
  clickHouseOrderByColumns: string[];
  description?: string;
  fields: DataModelFieldInput[];
}

export interface ImportModelMetadataModelRequest {
  code: string;
  name: string;
  directoryPath?: string;
  warehouseLayerId?: string;
  physicalTableName: string;
  clickHouseOrderByColumns: string[];
  description?: string;
  fields: DataModelFieldInput[];
}

export interface ImportModelMetadataRequest {
  targetStorageDataSourceId: string;
  models: ImportModelMetadataModelRequest[];
}

export interface ImportedModelMetadata {
  id: string;
  code: string;
  name: string;
  directoryId?: string | null;
}

export interface ModelMetadataImportResult {
  modelCount: number;
  fieldCount: number;
  models: ImportedModelMetadata[];
}

export type UpdateDataModelRequest = Omit<CreateDataModelRequest, 'code'>;

export interface DataModelFieldInput {
  id?: string;
  code: string;
  name: string;
  fieldType: PlatformDataType;
  length?: number;
  precision?: number;
  scale?: number;
  geometry?: GeometryTypeDefinition;
  nullable: boolean;
  primaryKey: boolean;
  sortOrder: number;
  description?: string;
  standardDictionaryId?: string;
}

export interface UpdateDataModelFieldsRequest {
  fields: DataModelFieldInput[];
}

export interface DataModelFilters {
  keyword?: string;
  status?: DataModelStatus;
  physicalTableModes?: PhysicalTableMode[];
  storageDataSourceId?: string;
  warehouseLayerId?: string;
  directoryIds?: string[];
  uncategorized?: boolean;
}

export const dataModelStatusLabels: Record<DataModelStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  DISABLED: '已停用',
};

export const physicalTableModeLabels: Record<PhysicalTableMode, string> = {
  MANAGED: '新建物理表',
  EXTERNAL: '绑定已有表',
};

export const physicalTableStateLabels: Record<PhysicalTableState, string> = {
  NOT_FOUND: '未创建',
  MATCHED: '结构正常',
  DRIFTED: '结构不一致',
  UNREACHABLE: '无法访问',
  UNSUPPORTED: '暂不支持',
};

export const physicalTableStateColors: Record<PhysicalTableState, string> = {
  NOT_FOUND: 'default',
  MATCHED: 'success',
  DRIFTED: 'warning',
  UNREACHABLE: 'error',
  UNSUPPORTED: 'default',
};

export const physicalTableChangeStatusLabels: Record<PhysicalTableChangeStatus, string> = {
  PLANNED: '待执行',
  APPLYING: '执行中',
  SUCCEEDED: '已完成',
  FAILED: '执行失败',
  PARTIAL: '部分完成',
  CANCELLED: '已取消',
  SUPERSEDED: '已替代',
};

export const physicalTableChangeStatusColors: Record<PhysicalTableChangeStatus, string> = {
  PLANNED: 'processing',
  APPLYING: 'processing',
  SUCCEEDED: 'success',
  FAILED: 'error',
  PARTIAL: 'warning',
  CANCELLED: 'default',
  SUPERSEDED: 'default',
};

export const tableChangeStrategyLabels: Record<TableChangeStrategy, string> = {
  METADATA_ONLY: '仅元数据',
  IN_PLACE: '原表修改',
  REBUILD_RECOMMENDED: '建议重建表',
  REBUILD_REQUIRED: '必须重建表',
  UNSUPPORTED: '暂不支持',
};

export const tableChangeRiskLabels: Record<TableChangeRisk, string> = {
  SAFE: '低风险',
  CAUTION: '需注意',
  DESTRUCTIVE: '破坏性',
};

export const tableChangeRiskColors: Record<TableChangeRisk, string> = {
  SAFE: 'success',
  CAUTION: 'warning',
  DESTRUCTIVE: 'error',
};

export const tableDdlAtomicityLabels: Record<TableDdlAtomicity, string> = {
  NOT_APPLICABLE: '不适用',
  TRANSACTIONAL_BATCH: '事务批次',
  ATOMIC_SINGLE_STATEMENT: '单语句原子',
  NON_TRANSACTIONAL_SEQUENCE: '非事务顺序执行',
};

export const tableChangeExecutionModeLabels: Record<TableChangeExecutionMode, string> = {
  IN_PLACE: '原表修改',
  REBUILD: '重建物理表',
};

export const tableChangeOperationLabels: Record<TableChangeOperationType, string> = {
  ADD_COLUMN: '新增字段',
  DROP_COLUMN: '删除字段',
  RENAME_COLUMN: '重命名字段',
  ALTER_COLUMN_TYPE: '修改字段类型',
  ALTER_COLUMN_LENGTH: '修改字段长度',
  ALTER_COLUMN_PRECISION: '修改字段精度',
  ALTER_COLUMN_NULLABILITY: '修改空值约束',
  ALTER_STORAGE_LAYOUT: '修改存储布局',
  ADD_PRIMARY_KEY: '新增主键',
  DROP_PRIMARY_KEY: '删除主键',
  REPLACE_PRIMARY_KEY: '修改主键',
};

export const tableChangeCheckLabels: Record<TableChangeCheckType, string> = {
  STRUCTURE_FINGERPRINT_MATCH: '结构指纹一致',
  TABLE_EMPTY: '物理表为空',
  COLUMNS_HAVE_NO_NULLS: '字段不存在空值',
  COLUMNS_ARE_UNIQUE: '字段值唯一',
  MAX_STRING_LENGTH: '字符串长度满足限制',
  DECIMAL_VALUES_FIT: '小数精度满足限制',
  NO_EXTERNAL_DEPENDENCIES: '不存在外部依赖',
  NO_REBUILD_DEPENDENCIES: '不存在重建依赖',
  DATABASE_RUNTIME_SUPPORTED: '数据库运行参数满足要求',
};

export interface PhysicalTableDifference {
  column: string | null;
  type: PhysicalTableDifferenceType;
  expected: string;
  actual: string;
}

export interface PhysicalTableInspection {
  mode: PhysicalTableMode;
  state: PhysicalTableState;
  catalogName: string | null;
  schemaName: string | null;
  tableName: string;
  exists: boolean;
  compatible: boolean;
  createSupported: boolean;
  message: string;
  differences: PhysicalTableDifference[];
}

export interface PhysicalTableDdlPlan {
  mode: PhysicalTableMode;
  supported: boolean;
  message: string;
  statements: string[];
}

export interface PhysicalTableChangeColumn {
  columnId: string;
  name: string;
  type: PhysicalTableColumnType;
  length: number | null;
  precision: number | null;
  scale: number | null;
  geometry?: GeometryTypeDefinition | null;
  nullable: boolean;
}

export interface PhysicalTableChangeReason {
  code: TableChangeReasonCode;
  message: string;
}

export interface PhysicalTableChangeCheck {
  type: TableChangeCheckType;
  columnNames: string[];
  lengthLimit: number | null;
  precisionLimit: number | null;
  scaleLimit: number | null;
  expectedFingerprint: string | null;
  description: string;
}

export interface PhysicalTableChangeOperation {
  type: TableChangeOperationType;
  beforeColumn: PhysicalTableChangeColumn | null;
  afterColumn: PhysicalTableChangeColumn | null;
  beforePrimaryKeyColumns: string[];
  afterPrimaryKeyColumns: string[];
  strategy: TableChangeStrategy;
  risk: TableChangeRisk;
  reasons: PhysicalTableChangeReason[];
  checks: PhysicalTableChangeCheck[];
}

export interface PhysicalTableChangeExecutionOption {
  mode: TableChangeExecutionMode;
  atomicity: TableDdlAtomicity;
  statements: string[];
}

export interface PhysicalTableChangePlan {
  strategy: TableChangeStrategy;
  risk: TableChangeRisk;
  atomicity: TableDdlAtomicity;
  beforeFingerprint: string;
  targetFingerprint: string;
  allowsInPlaceExecution: boolean;
  allowsRebuildExecution: boolean;
  executionOptions: PhysicalTableChangeExecutionOption[];
  operations: PhysicalTableChangeOperation[];
  checks: PhysicalTableChangeCheck[];
  reasons: PhysicalTableChangeReason[];
}

export interface DataModelPhysicalChange {
  id: string;
  modelId: string;
  baseSchemaVersion: number;
  targetSchemaVersion: number;
  status: PhysicalTableChangeStatus;
  plan: PhysicalTableChangePlan;
  executionMode: TableChangeExecutionMode | null;
  executionStartedAt: string | null;
  completedAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreatePhysicalTableChangePlanRequest {
  fields: DataModelFieldInput[];
}

export interface ExecutePhysicalTableChangePlanRequest {
  executionMode: TableChangeExecutionMode;
}

export const shouldCreatePhysicalTableChangePlan = (
  mode: PhysicalTableMode,
  state: PhysicalTableState | undefined,
) => mode === 'MANAGED' && state === 'MATCHED';

export const canSaveFieldsDirectly = (
  mode: PhysicalTableMode,
  state: PhysicalTableState | undefined,
) => mode !== 'MANAGED' || state === 'NOT_FOUND';

const sameGeometryDefinition = (
  current: GeometryTypeDefinition | undefined,
  requested: GeometryTypeDefinition | undefined,
) => current === requested || (
  current !== undefined
  && requested !== undefined
  && current.kind === requested.kind
  && current.crs.authority === requested.crs.authority
  && current.crs.code === requested.crs.code
  && current.dimension === requested.dimension
);

export const isMetadataOnlyFieldUpdate = (
  currentFields: DataModelFieldInput[],
  requestedFields: DataModelFieldInput[],
  ignorePrimaryKey = false,
) => {
  if (currentFields.length !== requestedFields.length) return false;
  const currentById = new Map(
    currentFields
      .filter((field): field is DataModelFieldInput & { id: string } => field.id !== undefined)
      .map((field) => [field.id, field]),
  );
  if (currentById.size !== currentFields.length) return false;
  return requestedFields.every((requested) => {
    if (requested.id === undefined) return false;
    const current = currentById.get(requested.id);
    return current !== undefined
      && current.code === requested.code
      && current.fieldType === requested.fieldType
      && current.length === requested.length
      && current.precision === requested.precision
      && current.scale === requested.scale
      && sameGeometryDefinition(current.geometry, requested.geometry)
      && current.nullable === requested.nullable
      && (ignorePrimaryKey || current.primaryKey === requested.primaryKey);
  });
};

export interface DataModelPreviewColumn {
  code: string;
  name: string;
  fieldType: PlatformDataType;
}

export interface DataModelPreview {
  catalogName: string | null;
  schemaName: string | null;
  tableName: string;
  columns: DataModelPreviewColumn[];
  rows: Record<string, unknown>[];
  limit: number;
  truncated: boolean;
}

export interface DataModelSpatialPreviewGeometryField {
  code: string;
  name: string;
  kind: GeometryKind;
  sourceCrs: CrsReference;
  spatialIndexAvailable: boolean;
  estimatedRowCount: number | null;
  physicalStatisticsRefreshRequired: boolean;
  previewAllowed: boolean;
  message: string | null;
}

export interface DataModelSpatialPreview {
  supported: boolean;
  message: string | null;
  geometryFields: DataModelSpatialPreviewGeometryField[];
  displayCrs: 'EPSG:3857';
  initialBounds: [number, number, number, number];
  limits: {
    minimumWidth: number;
    maximumWidth: number;
    minimumHeight: number;
    maximumHeight: number;
    maximumFeatures: number;
    maximumCoordinates: number;
    maximumWkbBytes: number;
  };
}

export interface DataModelSpatialPreviewMap {
  blob: Blob;
  featureCount: number;
  skippedCount: number;
  truncated: boolean;
}

export type DataModelDataQueryConditionType = 'AND' | 'OR';

export type DataModelDataQuerySortDirection = 'ASC' | 'DESC';

export type DataModelDataQueryFilterOperator =
  | 'EQ'
  | 'NE'
  | 'GT'
  | 'GE'
  | 'LT'
  | 'LE'
  | 'LIKE'
  | 'NOT_LIKE'
  | 'IN'
  | 'NOT_IN'
  | 'BETWEEN'
  | 'NOT_BETWEEN'
  | 'IS_NULL'
  | 'IS_NOT_NULL'
  | 'IS_EMPTY'
  | 'IS_NOT_EMPTY';

export interface DataModelDataQueryFilterInput {
  field: string;
  operator: DataModelDataQueryFilterOperator;
  value?: unknown;
  secondValue?: unknown;
  values?: unknown[];
}

export interface DataModelDataQueryOrderInput {
  field: string;
  direction: DataModelDataQuerySortDirection;
}

export interface DataModelDataQueryRequest {
  pageNo?: number;
  pageSize?: number;
  conditionType?: DataModelDataQueryConditionType;
  columns?: string[];
  filters?: DataModelDataQueryFilterInput[];
  orders?: DataModelDataQueryOrderInput[];
  returnCount?: boolean;
}

export interface DataModelDataQueryColumn {
  code: string;
  name: string;
  fieldType: PlatformDataType;
}

export interface DataModelDataQueryResponse {
  columns: DataModelDataQueryColumn[];
  rows: Record<string, unknown>[];
  pageNo: number;
  pageSize: number;
  hasNext: boolean;
  totalCount: number | null;
  stableOrder: boolean;
}

export const dataModelFieldTypeLabels: Record<PlatformDataType, string> = {
  BOOLEAN: '布尔',
  BYTE: '字节整数',
  SHORT: '短整数',
  INTEGER: '整数',
  LONG: '长整数',
  FLOAT: '单精度浮点',
  DOUBLE: '双精度浮点',
  DECIMAL: '小数',
  STRING: '字符串',
  BINARY: '二进制',
  DATE: '日期',
  TIMESTAMP: '时间戳（时间线）',
  TIMESTAMP_NTZ: '时间戳（无时区）',
  GEOMETRY: '空间几何',
};

export const geometryKindLabels: Record<GeometryKind, string> = {
  GEOMETRY: 'Geometry',
  POINT: 'Point',
  LINESTRING: 'LineString',
  POLYGON: 'Polygon',
  MULTIPOINT: 'MultiPoint',
  MULTILINESTRING: 'MultiLineString',
  MULTIPOLYGON: 'MultiPolygon',
  GEOMETRYCOLLECTION: 'GeometryCollection',
};
