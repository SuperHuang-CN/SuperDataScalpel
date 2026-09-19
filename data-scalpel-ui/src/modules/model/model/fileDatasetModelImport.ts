import type { FileDataset, FileDatasetTable } from '../../filedataset';
import type { FileDatasetImportPreview, PlatformTypeDefinition } from './dataModel';
import {
  duplicateValues,
  fieldCodeCandidate,
  managedModelDraftIssues,
  modelCodeCandidate,
  physicalTableNameCandidate,
  type ManagedImportFieldDraft,
  type ManagedModelDraftValues,
  type ManagedTableDraftIssue,
} from './managedTableImport';

export interface FileDatasetModelDraft extends ManagedModelDraftValues {
  datasetId: string;
  datasetName: string;
  datasetType: FileDataset['type'];
  table: FileDatasetTable;
  modelCodePrefix?: string;
  codeOverridden: boolean;
  warehouseLayerOverridden: boolean;
  modelValuesLocked: boolean;
  sourceUpdatedAt?: string;
}

const sourceTypeLabel = (type: PlatformTypeDefinition): string => {
  if (type.type === 'STRING' && type.length) return `STRING(${type.length})`;
  if (type.type === 'DECIMAL') return `DECIMAL(${type.precision},${type.scale})`;
  if (type.type === 'GEOMETRY' && type.geometry) {
    return `${type.geometry.kind} · ${type.geometry.crs.authority}:${type.geometry.crs.code}`;
  }
  return type.type;
};

export const createFileDatasetModelDrafts = (
  dataset: FileDataset,
  tables: FileDatasetTable[],
  warehouseLayerId?: string,
  modelCodePrefix?: string,
): FileDatasetModelDraft[] => {
  const normalizedCodes = tables.map((table) => modelCodeCandidate(table.code, modelCodePrefix));
  const duplicateCodes = duplicateValues(normalizedCodes);
  return tables.map((table, index) => {
    const code = normalizedCodes[index];
    return {
      key: table.id,
      datasetId: dataset.id,
      datasetName: dataset.name,
      datasetType: dataset.type,
      table,
      code: duplicateCodes.has(code) ? '' : code,
      modelCodePrefix,
      codeOverridden: false,
      name: table.name.trim().slice(0, 100),
      warehouseLayerId,
      warehouseLayerOverridden: false,
      description: '',
      physicalTableName: physicalTableNameCandidate(table.code),
      fields: [],
      previewState: 'idle',
      tableIssues: [],
      previewIssues: [],
      warnings: [],
      modelValuesLocked: false,
    };
  });
};

export const mergeFileDatasetModelDrafts = (
  dataset: FileDataset,
  tables: FileDatasetTable[],
  currentDrafts: FileDatasetModelDraft[],
  warehouseLayerId?: string,
  modelCodePrefix?: string,
): FileDatasetModelDraft[] => {
  const existing = new Map(currentDrafts.map((draft) => [draft.key, draft]));
  return createFileDatasetModelDrafts(dataset, tables, warehouseLayerId, modelCodePrefix)
    .map((draft) => existing.get(draft.key) ?? draft);
};

export const applyFileDatasetImportPreview = (
  draft: FileDatasetModelDraft,
  preview: FileDatasetImportPreview,
): FileDatasetModelDraft => {
  const normalizedCodes = preview.columns.map((column) => fieldCodeCandidate(column.code ?? column.sourceName));
  const duplicateCodes = duplicateValues(normalizedCodes);
  const fields = preview.columns.map((column, index): ManagedImportFieldDraft => {
    const code = normalizedCodes[index];
    return {
      key: `${index}:${column.sourceName}`,
      sourceName: column.sourceName,
      nativeType: sourceTypeLabel(column.sourceType),
      code: duplicateCodes.has(code) ? '' : code,
      name: (column.name?.trim() || column.sourceName).slice(0, 100),
      fieldType: column.fieldType,
      ...(column.length !== null ? { length: column.length } : {}),
      ...(column.precision !== null ? { precision: column.precision } : {}),
      ...(column.scale !== null ? { scale: column.scale } : {}),
      ...(column.geometry != null ? { geometry: column.geometry } : {}),
      nullable: column.nullable,
      primaryKey: false,
      sortOrder: column.sortOrder,
      description: '',
      mappingQuality: column.mappingQuality,
      ...(column.mappingMessage ? { mappingMessage: column.mappingMessage } : {}),
      sourceIssues: column.issues,
    };
  });
  return {
    ...draft,
    code: draft.codeOverridden || draft.modelValuesLocked || !draft.code
      ? draft.code
      : modelCodeCandidate(preview.suggestedCode ?? draft.table.code, draft.modelCodePrefix),
    name: draft.modelValuesLocked
      ? draft.name
      : (preview.suggestedName?.trim() || draft.name).slice(0, 100),
    physicalTableName: draft.modelValuesLocked
      ? draft.physicalTableName
      : physicalTableNameCandidate(preview.suggestedPhysicalTableName ?? draft.table.code),
    fields,
    previewState: 'ready',
    previewError: undefined,
    tableIssues: preview.tableIssues,
    previewIssues: preview.issues,
    warnings: preview.warnings,
    modelValuesLocked: true,
    sourceUpdatedAt: preview.sourceUpdatedAt,
  };
};

export const applyFileDatasetDraftWarehouseLayer = (
  draft: FileDatasetModelDraft,
  warehouseLayerId: string | undefined,
  modelCodePrefix: string | undefined,
  warehouseLayerOverridden: boolean,
): FileDatasetModelDraft => ({
  ...draft,
  code: draft.codeOverridden
    ? draft.code
    : modelCodeCandidate(draft.table.code, modelCodePrefix),
  modelCodePrefix,
  warehouseLayerId,
  warehouseLayerOverridden,
});

export const clearDuplicateFileDatasetDraftCodeCandidates = (
  drafts: FileDatasetModelDraft[],
): FileDatasetModelDraft[] => {
  const duplicateCodes = duplicateValues(drafts.map((draft) => draft.code.trim().toLowerCase()));
  return drafts.map((draft) => (
    !draft.codeOverridden && duplicateCodes.has(draft.code.trim().toLowerCase())
      ? { ...draft, code: '' }
      : draft
  ));
};

export const fileDatasetDraftIssues = (
  drafts: FileDatasetModelDraft[],
): Map<string, ManagedTableDraftIssue> => managedModelDraftIssues(drafts);
