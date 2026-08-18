import type {
  DataModelDataQueryRequest,
  DataModelDataQueryResponse,
  DataModelField,
  PlatformDataType,
} from '../../model';
import type { StandardDictionarySummary } from '../../standard';

export type DataEntryFormStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED';
export type DataEntryOptionStatus = 'ACTIVE' | 'DISABLED' | 'MISSING' | 'SOURCE_UNAVAILABLE';

export interface DataEntryHealthIssue {
  code: string;
  message: string;
  affectedOperations: string[];
  fieldId: string | null;
  sourceModelId: string | null;
}

export interface DataEntryHealth {
  canPublish: boolean;
  canSubmit: boolean;
  canDeleteEntries: boolean;
  canQueryEntries: boolean;
  issues: DataEntryHealthIssue[];
}

export interface DataEntryLookup {
  id: string;
  targetFieldId: string;
  sourceModelId: string;
  sourceModelCode: string | null;
  sourceModelName: string | null;
  sourceValueFieldId: string | null;
  sourceValueFieldCode: string | null;
  sourceLabelFieldId: string;
  sourceLabelFieldCode: string | null;
  sourceLabelFieldName: string | null;
}

export interface DataEntryField extends Omit<DataModelField, 'modelId' | 'physicalColumnRole' | 'createdAt' | 'updatedAt'> {
  inputSource: 'DEFAULT' | 'DICTIONARY' | 'MODEL_LOOKUP';
  standardDictionary: StandardDictionarySummary | null;
  lookup: DataEntryLookup | null;
}

export interface DataEntryForm {
  id: string;
  modelId: string;
  modelCode: string | null;
  modelName: string | null;
  modelDescription: string | null;
  modelStatus: string | null;
  modelSchemaVersion: number | null;
  status: DataEntryFormStatus;
  publishedModelSchemaVersion: number | null;
  healthSummary: 'HEALTHY' | 'CHECK_REQUIRED' | 'DETAIL_CHECK_REQUIRED';
  issues: DataEntryHealthIssue[];
  createdAt: string;
  updatedAt: string;
}

export interface DataEntryFormDetail {
  form: DataEntryForm;
  fields: DataEntryField[];
  lookups: DataEntryLookup[];
  health: DataEntryHealth;
}

export interface DataEntryModelCandidate {
  modelId: string;
  modelCode: string;
  modelName: string;
  modelStatus: string;
  schemaVersion: number;
  knownEligible: boolean;
  issues: DataEntryHealthIssue[];
}

export interface DataEntryOption {
  value: unknown;
  label: string;
  displayLabel: string;
  status: DataEntryOptionStatus;
}

export interface DataEntryOptionResponse {
  content: DataEntryOption[];
  pageNo: number;
  pageSize: number;
  hasNext: boolean;
}

export interface DataEntryMutationResponse {
  operationLogId: string;
  requestedCount: number;
  affectedCount: number;
  status: 'SUCCEEDED' | 'PARTIALLY_SUCCEEDED';
  manualVerificationRequired: boolean;
  warningMessage: string | null;
}

export type DataEntryOperationType = 'INSERT' | 'IMPORT' | 'DELETE';
export type DataEntryOperationStatus = 'PROCESSING' | 'SUCCEEDED' | 'PARTIALLY_SUCCEEDED' | 'FAILED';

export interface DataEntryOperationLog {
  id: string;
  formId: string;
  modelId: string | null;
  modelSchemaVersion: number | null;
  operationType: DataEntryOperationType;
  status: DataEntryOperationStatus;
  operatorUsername: string;
  requestedCount: number;
  affectedCount: number | null;
  payloadSnapshot: string | null;
  completedAt: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  manualVerificationRequired: boolean;
  createdAt: string;
  updatedAt: string;
}

export type DataEntryImportFormat = 'XLSX' | 'CSV';

export interface DataEntryImportField {
  id: string;
  code: string;
  name: string;
  fieldType: PlatformDataType;
  nullable: boolean;
  primaryKey: boolean;
  inputSource: 'DEFAULT' | 'DICTIONARY' | 'MODEL_LOOKUP';
}

export interface DataEntryImportPreviewRow {
  rowNumber: number;
  values: Record<string, unknown>;
  displayValues: Record<string, string>;
}

export interface DataEntryImportIssue {
  code: string;
  rowNumber: number;
  fieldCode: string | null;
  message: string;
}

export interface DataEntryImportPreview {
  fileName: string;
  format: DataEntryImportFormat;
  fileSize: number;
  totalRowCount: number;
  validRowCount: number;
  errorRowCount: number;
  issueCount: number;
  importable: boolean;
  issuesTruncated: boolean;
  previewDigest: string;
  fields: DataEntryImportField[];
  previewRows: DataEntryImportPreviewRow[];
  issues: DataEntryImportIssue[];
}

export interface DataEntryLookupInput {
  targetFieldId: string;
  sourceModelId: string;
  sourceLabelFieldId: string;
}

export type { DataModelDataQueryRequest, DataModelDataQueryResponse, PlatformDataType };

export const dataEntryStatusLabels: Record<DataEntryFormStatus, string> = {
  DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用',
};

export const dataEntryOperationStatusLabels: Record<DataEntryOperationStatus, string> = {
  PROCESSING: '处理中', SUCCEEDED: '成功', PARTIALLY_SUCCEEDED: '部分成功', FAILED: '失败',
};
