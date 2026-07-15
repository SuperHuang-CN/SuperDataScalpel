export type DataModelStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';

export type PhysicalTableMode = 'MANAGED' | 'EXTERNAL';

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
  | 'TIMESTAMP_NTZ';

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
  | 'BINARY';

export interface ExternalTableImportColumn {
  name: string;
  nativeType: string;
  platformType: PlatformDataType | null;
  length: number | null;
  precision: number | null;
  scale: number | null;
  nullable: boolean;
  primaryKey: boolean;
  mappingQuality: TypeMappingQuality;
  message: string | null;
  importable: boolean;
  comment: string | null;
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

export interface PlatformTypeCapability {
  type: PlatformDataType;
  supported: boolean;
  message: string | null;
  lengthParameterSupported: boolean;
  unboundedStringSupported: boolean;
}

export interface DataModel {
  id: string;
  code: string;
  name: string;
  directoryId: string | null;
  storageDataSourceId: string;
  storageDataSourceName: string;
  catalogName: string | null;
  schemaName: string | null;
  physicalTableName: string;
  physicalTableMode: PhysicalTableMode;
  clickHouseOrderByColumns: string[];
  status: DataModelStatus;
  schemaVersion: number;
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
  nullable: boolean;
  primaryKey: boolean;
  sortOrder: number;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DataModelDetail {
  model: DataModel;
  fields: DataModelField[];
}

export interface CreateDataModelRequest {
  code: string;
  name: string;
  directoryId?: string;
  storageDataSourceId: string;
  physicalTableName: string;
  physicalTableMode?: PhysicalTableMode;
  clickHouseOrderByColumns?: string[];
  description?: string;
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
  nullable: boolean;
  primaryKey: boolean;
  sortOrder: number;
  description?: string;
}

export interface UpdateDataModelFieldsRequest {
  fields: DataModelFieldInput[];
}

export interface DataModelFilters {
  keyword?: string;
  status?: DataModelStatus;
  storageDataSourceId?: string;
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
};

export const physicalLocation = (model: Pick<DataModel, 'catalogName' | 'schemaName' | 'physicalTableName'>) => (
  [model.catalogName, model.schemaName, model.physicalTableName].filter(Boolean).join('.')
);
