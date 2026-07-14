export type DataModelStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';

export type DataModelFieldType =
  | 'STRING'
  | 'TEXT'
  | 'INTEGER'
  | 'LONG'
  | 'DECIMAL'
  | 'BOOLEAN'
  | 'DATE'
  | 'DATETIME'
  | 'BINARY';

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
  status: DataModelStatus;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DataModelField {
  id: string;
  modelId: string;
  code: string;
  name: string;
  fieldType: DataModelFieldType;
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
  physicalTableManaged: boolean;
}

export interface CreateDataModelRequest {
  code: string;
  name: string;
  directoryId?: string;
  storageDataSourceId: string;
  catalogName?: string;
  schemaName?: string;
  physicalTableName: string;
  description?: string;
}

export type UpdateDataModelRequest = Omit<CreateDataModelRequest, 'code'>;

export interface DataModelFieldInput {
  id?: string;
  code: string;
  name: string;
  fieldType: DataModelFieldType;
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

export const dataModelFieldTypeLabels: Record<DataModelFieldType, string> = {
  STRING: '字符串',
  TEXT: '长文本',
  INTEGER: '整数',
  LONG: '长整数',
  DECIMAL: '小数',
  BOOLEAN: '布尔',
  DATE: '日期',
  DATETIME: '日期时间',
  BINARY: '二进制',
};

export const physicalLocation = (model: Pick<DataModel, 'catalogName' | 'schemaName' | 'physicalTableName'>) => (
  [model.catalogName, model.schemaName, model.physicalTableName].filter(Boolean).join('.')
);
