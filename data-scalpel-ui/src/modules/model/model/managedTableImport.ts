import type { DataSource, DataSourceTable } from '../../datasource';
import type {
  CreateManagedDataModelDraftRequest,
  ManagedImportPreview,
  GeometryTypeDefinition,
  PhysicalTableMode,
  PlatformDataType,
  TypeMappingQuality,
} from './dataModel';

export type ManagedImportPreviewState = 'idle' | 'loading' | 'ready' | 'error';

export interface ManagedImportFieldDraft {
  key: string;
  sourceName: string;
  nativeType: string;
  code: string;
  name: string;
  fieldType: PlatformDataType | null;
  length?: number;
  precision?: number;
  scale?: number;
  geometry?: GeometryTypeDefinition;
  nullable: boolean;
  primaryKey: boolean;
  sortOrder: number;
  description: string;
  mappingQuality: TypeMappingQuality;
  mappingMessage?: string;
  sourceIssues: string[];
}

export interface ManagedTableModelDraft {
  key: string;
  table: DataSourceTable;
  code: string;
  name: string;
  description: string;
  physicalTableName: string;
  fields: ManagedImportFieldDraft[];
  previewState: ManagedImportPreviewState;
  previewError?: string;
  tableIssues: string[];
  previewIssues: string[];
  warnings: string[];
  modelValuesLocked: boolean;
}

export interface ManagedTableDraftIssue {
  code?: string;
  name?: string;
  description?: string;
  physicalTableName?: string;
  preview?: string;
  fields: Map<string, string[]>;
}

export interface ManagedImportTaskResult<TItem, TResult> {
  item: TItem;
  result?: TResult;
  error?: unknown;
}

const MODEL_CODE = /^[a-z][a-z0-9_]{0,63}$/;
const PHYSICAL_TABLE_NAME = /^[a-z][a-z0-9_]{0,127}$/;
const FIELD_CODE = /^[a-z][a-z0-9_]{0,63}$/;

const lowerIdentifier = (value: string | null | undefined, pattern: RegExp): string => {
  const normalized = value?.trim().toLowerCase() ?? '';
  return pattern.test(normalized) ? normalized : '';
};

export const isModelDataSourceSelectable = (
  source: Pick<DataSource, 'id' | 'purposes' | 'connectionKind' | 'enabled'>,
  physicalTableMode: PhysicalTableMode,
  currentDataSourceId?: string,
): boolean => (
  source.connectionKind === 'JDBC'
  && (source.enabled || source.id === currentDataSourceId)
  && (physicalTableMode === 'EXTERNAL' || source.purposes.includes('STORAGE'))
);

export const isManagedImportSourceSelectable = (
  source: Pick<DataSource, 'connectionKind' | 'enabled'>,
): boolean => source.connectionKind === 'JDBC' && source.enabled;

export const isManagedImportTargetSelectable = (
  source: Pick<DataSource, 'purposes' | 'connectionKind' | 'enabled'>,
): boolean => source.connectionKind === 'JDBC' && source.enabled && source.purposes.includes('STORAGE');

export const managedTableKey = (table: DataSourceTable): string => JSON.stringify([
  table.identifier.catalog,
  table.identifier.schema,
  table.identifier.table,
]);

export const managedTableLocation = (table: DataSourceTable): string => (
  [table.identifier.catalog, table.identifier.schema, table.identifier.table]
    .filter((part): part is string => Boolean(part))
    .join('.')
);

const duplicateValues = (values: string[]): Set<string> => {
  const counts = new Map<string, number>();
  values.filter(Boolean).forEach((value) => counts.set(value, (counts.get(value) ?? 0) + 1));
  return new Set([...counts].filter(([, count]) => count > 1).map(([value]) => value));
};

export const runManagedImportTasks = async <TItem, TResult>(
  items: TItem[],
  execute: (item: TItem) => Promise<TResult>,
): Promise<Array<ManagedImportTaskResult<TItem, TResult>>> => {
  const results = new Array<ManagedImportTaskResult<TItem, TResult>>(items.length);
  let cursor = 0;
  const worker = async () => {
    while (cursor < items.length) {
      const index = cursor;
      cursor += 1;
      const item = items[index];
      try {
        results[index] = { item, result: await execute(item) };
      } catch (error) {
        results[index] = { item, error };
      }
    }
  };
  const concurrency = Math.min(3, items.length);
  await Promise.all(Array.from({ length: concurrency }, () => worker()));
  return results;
};

export const createManagedTableModelDrafts = (tables: DataSourceTable[]): ManagedTableModelDraft[] => {
  const normalizedCodes = tables.map((table) => lowerIdentifier(table.identifier.table, MODEL_CODE));
  const duplicateCodes = duplicateValues(normalizedCodes);
  return tables.map((table, index) => {
    const tableName = table.identifier.table;
    const comment = table.comment?.trim() ?? '';
    const code = normalizedCodes[index];
    return {
      key: managedTableKey(table),
      table,
      code: duplicateCodes.has(code) ? '' : code,
      name: (comment || tableName).slice(0, 100),
      description: comment.length > 100 ? comment.slice(0, 1000) : '',
      physicalTableName: lowerIdentifier(tableName, PHYSICAL_TABLE_NAME),
      fields: [],
      previewState: 'idle',
      tableIssues: [],
      previewIssues: [],
      warnings: [],
      modelValuesLocked: false,
    };
  });
};

export const mergeManagedTableModelDrafts = (
  tables: DataSourceTable[],
  currentDrafts: ManagedTableModelDraft[],
): ManagedTableModelDraft[] => {
  const existing = new Map(currentDrafts.map((draft) => [draft.key, draft]));
  return createManagedTableModelDrafts(tables).map((draft) => existing.get(draft.key) ?? draft);
};

export const applyManagedImportPreview = (
  draft: ManagedTableModelDraft,
  preview: ManagedImportPreview,
): ManagedTableModelDraft => {
  const normalizedCodes = preview.columns.map((column) => lowerIdentifier(column.code ?? column.sourceName, FIELD_CODE));
  const duplicateCodes = duplicateValues(normalizedCodes);
  const fields = preview.columns.map((column, index): ManagedImportFieldDraft => {
    const code = normalizedCodes[index];
    return {
      key: `${index}:${column.sourceName}`,
      sourceName: column.sourceName,
      nativeType: column.nativeType,
      code: duplicateCodes.has(code) ? '' : code,
      name: (column.name?.trim() || column.description?.trim() || column.sourceName).slice(0, 100),
      fieldType: column.fieldType,
      ...(column.length !== null ? { length: column.length } : {}),
      ...(column.precision !== null ? { precision: column.precision } : {}),
      ...(column.scale !== null ? { scale: column.scale } : {}),
      ...(column.geometry != null ? { geometry: column.geometry } : {}),
      nullable: column.fieldType === 'GEOMETRY'
        ? column.nullable
        : column.primaryKey ? false : column.nullable,
      primaryKey: column.fieldType === 'GEOMETRY' ? false : column.primaryKey,
      sortOrder: column.sortOrder,
      description: column.description?.trim().slice(0, 500) ?? '',
      mappingQuality: column.mappingQuality,
      ...(column.mappingMessage ? { mappingMessage: column.mappingMessage } : {}),
      sourceIssues: column.issues,
    };
  });
  return {
    ...draft,
    code: draft.modelValuesLocked || !draft.code
      ? draft.code
      : lowerIdentifier(preview.suggestedCode ?? draft.code, MODEL_CODE),
    name: draft.modelValuesLocked
      ? draft.name
      : (preview.suggestedName?.trim() || draft.name).slice(0, 100),
    physicalTableName: draft.modelValuesLocked
      ? draft.physicalTableName
      : lowerIdentifier(preview.suggestedPhysicalTableName ?? draft.physicalTableName, PHYSICAL_TABLE_NAME),
    fields,
    previewState: 'ready',
    previewError: undefined,
    tableIssues: preview.tableIssues,
    previewIssues: preview.issues,
    warnings: preview.warnings,
    modelValuesLocked: true,
  };
};

const fieldIssues = (
  field: ManagedImportFieldDraft,
  duplicateCodes: Set<string>,
): string[] => {
  const issues: string[] = [];
  if (!field.code.trim()) issues.push('请填写字段编码');
  else if (!FIELD_CODE.test(field.code.trim())) issues.push('字段编码须以小写字母开头，只能包含小写字母、数字和下划线，最多 64 个字符');
  else if (duplicateCodes.has(field.code.trim().toLowerCase())) issues.push('当前模型中字段编码重复');
  if (!field.name.trim()) issues.push('请填写字段名称');
  else if (field.name.trim().length > 100) issues.push('字段名称不能超过 100 个字符');
  if (!field.fieldType) issues.push('请选择目标字段类型');
  if (field.length !== undefined && (!Number.isInteger(field.length) || field.length < 1)) issues.push('字段长度必须是正整数');
  if (field.fieldType === 'DECIMAL') {
    if (!field.precision || !Number.isInteger(field.precision) || field.precision < 1 || field.precision > 38) {
      issues.push('DECIMAL 精度须为 1 至 38');
    }
    if (field.scale === undefined || !Number.isInteger(field.scale) || field.scale < 0 || field.scale > (field.precision ?? 0)) {
      issues.push('DECIMAL 小数位须为 0 至精度值');
    }
  }
  if (field.fieldType === 'GEOMETRY') {
    if (!field.geometry) issues.push('GEOMETRY 必须指定几何类型、EPSG CRS 和 XY 维度');
    if (field.primaryKey) issues.push('GEOMETRY 不能作为主键');
  }
  if (field.description.trim().length > 500) issues.push('字段说明不能超过 500 个字符');
  return issues;
};

export const managedTableDraftIssues = (
  drafts: ManagedTableModelDraft[],
): Map<string, ManagedTableDraftIssue> => {
  const duplicateModelCodes = duplicateValues(drafts.map((draft) => draft.code.trim().toLowerCase()));
  const duplicatePhysicalTableNames = duplicateValues(
    drafts.map((draft) => draft.physicalTableName.trim().toLowerCase()),
  );
  return new Map(drafts.map((draft) => {
    const issues: ManagedTableDraftIssue = { fields: new Map() };
    const code = draft.code.trim();
    if (!code) issues.code = '请填写模型编码';
    else if (!MODEL_CODE.test(code)) issues.code = '编码须以小写字母开头，只能包含小写字母、数字和下划线，最多 64 个字符';
    else if (duplicateModelCodes.has(code.toLowerCase())) issues.code = '本次导入中模型编码重复';
    if (!draft.name.trim()) issues.name = '请填写模型名称';
    else if (draft.name.trim().length > 100) issues.name = '模型名称不能超过 100 个字符';
    if (draft.description.trim().length > 1000) issues.description = '说明不能超过 1000 个字符';
    const physicalTableName = draft.physicalTableName.trim();
    if (!physicalTableName) issues.physicalTableName = '请填写目标物理表名';
    else if (!PHYSICAL_TABLE_NAME.test(physicalTableName)) {
      issues.physicalTableName = '物理表名须以小写字母开头，只能包含小写字母、数字和下划线，最多 128 个字符';
    } else if (duplicatePhysicalTableNames.has(physicalTableName.toLowerCase())) {
      issues.physicalTableName = '本次导入中目标物理表名重复';
    }
    if (draft.previewState === 'idle') issues.preview = '请先读取源表结构';
    else if (draft.previewState === 'loading') issues.preview = '正在读取源表结构';
    else if (draft.previewState === 'error') issues.preview = draft.previewError || '源表结构读取失败';
    else if (draft.tableIssues.length > 0) issues.preview = draft.tableIssues.join('；');
    else if (draft.fields.length === 0) issues.preview = '源表没有可导入字段';
    else if (draft.fields.length > 500) issues.preview = '单个模型最多导入 500 个字段';
    const duplicateFieldCodes = duplicateValues(draft.fields.map((field) => field.code.trim().toLowerCase()));
    draft.fields.forEach((field) => {
      const currentIssues = fieldIssues(field, duplicateFieldCodes);
      if (currentIssues.length) issues.fields.set(field.key, currentIssues);
    });
    return [draft.key, issues];
  }));
};

export const hasManagedTableDraftIssues = (issues: Map<string, ManagedTableDraftIssue>): boolean => (
  [...issues.values()].some((item) => (
    Boolean(item.code || item.name || item.description || item.physicalTableName || item.preview)
    || item.fields.size > 0
  ))
);

export const toManagedDataModelDraftRequest = (
  draft: ManagedTableModelDraft,
  storageDataSourceId: string,
  directoryId?: string,
): CreateManagedDataModelDraftRequest | undefined => {
  if (draft.fields.some((field) => !field.fieldType)) return undefined;
  return {
    code: draft.code.trim(),
    name: draft.name.trim(),
    directoryId,
    storageDataSourceId,
    physicalTableName: draft.physicalTableName.trim(),
    clickHouseOrderByColumns: [],
    description: draft.description.trim() || undefined,
    fields: draft.fields.flatMap((field) => field.fieldType ? [{
      code: field.code.trim(),
      name: field.name.trim(),
      fieldType: field.fieldType,
      ...(field.fieldType === 'STRING' && field.length !== undefined ? { length: field.length } : {}),
      ...(field.fieldType === 'DECIMAL' && field.precision !== undefined ? { precision: field.precision } : {}),
      ...(field.fieldType === 'DECIMAL' && field.scale !== undefined ? { scale: field.scale } : {}),
      ...(field.fieldType === 'GEOMETRY' && field.geometry ? { geometry: field.geometry } : {}),
      nullable: field.primaryKey ? false : field.nullable,
      primaryKey: field.fieldType === 'GEOMETRY' ? false : field.primaryKey,
      sortOrder: field.sortOrder,
      description: field.description.trim() || undefined,
    }] : []),
  };
};
