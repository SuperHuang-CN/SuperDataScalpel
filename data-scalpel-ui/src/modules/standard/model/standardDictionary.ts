import type {
  DataModelStatus,
  PhysicalTableMode,
  PlatformDataType,
} from '../../model';

export type StandardDictionaryValueType = Extract<
  PlatformDataType,
  'STRING' | 'INTEGER' | 'LONG' | 'DECIMAL' | 'BOOLEAN'
>;

export interface StandardDictionary {
  id: string;
  code: string;
  name: string;
  valueType: StandardDictionaryValueType;
  enabled: boolean;
  version: number;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface StandardDictionarySummary {
  id: string;
  code: string;
  name: string;
  valueType: StandardDictionaryValueType;
  enabled: boolean;
  version: number;
}

export interface StandardDictionaryDetail {
  dictionary: StandardDictionary;
  itemCount: number;
  fieldReferenceCount: number;
  templateFieldReferenceCount: number;
}

export interface StandardDictionaryItem {
  id: string;
  dictionaryId: string;
  parentId: string | null;
  code: string;
  name: string;
  sortOrder: number;
  enabled: boolean;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface StandardDictionaryTreeNode {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  sortOrder: number;
  enabled: boolean;
  effectiveEnabled: boolean;
  description: string | null;
  children: StandardDictionaryTreeNode[];
  createdAt: string;
  updatedAt: string;
}

export interface StandardDictionaryItemMutation {
  dictionaryVersion: number;
  item: StandardDictionaryItem;
}

export interface CreateStandardDictionaryRequest {
  code: string;
  name: string;
  valueType: StandardDictionaryValueType;
  description?: string;
}

export interface UpdateStandardDictionaryRequest extends CreateStandardDictionaryRequest {
  expectedVersion: number;
}

export interface CreateStandardDictionaryItemRequest {
  expectedVersion: number;
  parentId?: string;
  targetIndex?: number;
  code: string;
  name: string;
  enabled: boolean;
  description?: string;
}

export interface UpdateStandardDictionaryItemRequest {
  expectedVersion: number;
  code: string;
  name: string;
  description?: string;
}

export interface MoveStandardDictionaryItemRequest {
  expectedVersion: number;
  targetParentId?: string;
  targetIndex: number;
}

export interface StandardDictionaryFieldReference {
  modelId: string;
  modelCode: string;
  modelName: string;
  modelStatus: DataModelStatus;
  physicalTableMode: PhysicalTableMode;
  schemaVersion: number;
  fieldId: string;
  fieldCode: string;
  fieldName: string;
  fieldType: PlatformDataType;
}

export type StandardDictionaryImportAction = 'CREATE' | 'UPDATE' | 'UNCHANGED' | 'ERROR';

export interface StandardDictionaryImportItemPreview {
  rowNumber: number;
  code: string;
  name: string;
  parentCode: string | null;
  sortOrder: number;
  enabled: boolean;
  description: string | null;
  action: StandardDictionaryImportAction;
  issues: string[];
}

export interface StandardDictionaryImportDictionaryPreview {
  rowNumber: number;
  existingId: string | null;
  expectedVersion: number | null;
  code: string;
  name: string;
  valueType: StandardDictionaryValueType;
  enabled: boolean;
  description: string | null;
  action: StandardDictionaryImportAction;
  issues: string[];
  items: StandardDictionaryImportItemPreview[];
}

export interface StandardDictionaryImportPreview {
  fileName: string;
  formatVersion: number;
  importable: boolean;
  previewDigest: string;
  issues: string[];
  dictionaries: StandardDictionaryImportDictionaryPreview[];
}

export interface StandardDictionaryImportResult {
  createdDictionaryCount: number;
  updatedDictionaryCount: number;
  createdItemCount: number;
  updatedItemCount: number;
  dictionaryIds: string[];
}

export const standardDictionaryValueTypeLabels: Record<StandardDictionaryValueType, string> = {
  STRING: '字符串',
  INTEGER: '整数',
  LONG: '长整数',
  DECIMAL: '精确小数',
  BOOLEAN: '布尔',
};

export const isStandardDictionaryTypeFamilyCompatible = (
  fieldType: PlatformDataType | null | undefined,
  valueType: StandardDictionaryValueType,
): boolean => {
  if (fieldType === 'STRING') return valueType === 'STRING';
  if (fieldType === 'BOOLEAN') return valueType === 'BOOLEAN';
  if (fieldType && ['BYTE', 'SHORT', 'INTEGER', 'LONG', 'DECIMAL'].includes(fieldType)) {
    return ['INTEGER', 'LONG', 'DECIMAL'].includes(valueType);
  }
  return false;
};

export const standardDictionaryImportActionLabels: Record<StandardDictionaryImportAction, string> = {
  CREATE: '新增',
  UPDATE: '更新',
  UNCHANGED: '无变化',
  ERROR: '存在问题',
};
