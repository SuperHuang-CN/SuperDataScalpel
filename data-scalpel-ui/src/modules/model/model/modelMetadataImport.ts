import type {
  GeometryTypeDefinition,
  ImportModelMetadataModelRequest,
  ModelMetadataImportPreview,
  PlatformDataType,
} from './dataModel';
import type { StandardDictionarySummary } from '../../standard';

export interface ModelMetadataFieldDraft {
  key: string;
  rowNumber: number;
  code: string;
  name: string;
  fieldType: PlatformDataType | null;
  length?: number;
  precision?: number;
  scale?: number;
  geometry?: GeometryTypeDefinition;
  nullable: boolean | null;
  primaryKey: boolean | null;
  sortOrder: number | null;
  description: string;
  standardDictionaryCode: string;
  standardDictionaryId?: string;
  standardDictionary?: StandardDictionarySummary | null;
  standardDictionaryIssue?: string;
  serverIssues: string[];
}

export interface ModelMetadataDraft {
  key: string;
  rowNumber: number;
  code: string;
  name: string;
  directoryPath: string;
  directoryIssue?: string;
  warehouseLayerCode: string;
  warehouseLayerId?: string;
  warehouseLayerIssue?: string;
  physicalTableName: string;
  clickHouseOrderByColumns: string[];
  description: string;
  fields: ModelMetadataFieldDraft[];
  serverIssues: string[];
  warnings: string[];
}

export interface ModelMetadataDraftIssue {
  code?: string;
  name?: string;
  directoryPath?: string;
  warehouseLayer?: string;
  physicalTableName?: string;
  description?: string;
  clickHouseOrderByColumns?: string;
  fields?: string;
  server?: string;
  fieldIssues: Map<string, string[]>;
}

const MODEL_CODE = /^[a-z][a-z0-9_]{0,63}$/;
const TABLE_NAME = /^[a-z][a-z0-9_]{0,127}$/;
const FIELD_CODE = /^[a-z][a-z0-9_]{0,63}$/;

const duplicates = (values: string[]): Set<string> => {
  const counts = new Map<string, number>();
  values.filter(Boolean).forEach((value) => counts.set(value, (counts.get(value) ?? 0) + 1));
  return new Set([...counts].filter(([, count]) => count > 1).map(([value]) => value));
};

const isWarehouseLayerIssue = (issue: string): boolean => issue.startsWith('数仓分层');
const isDirectoryIssue = (issue: string): boolean => issue.startsWith('模型目录');
const isStandardDictionaryIssue = (issue: string): boolean => issue.includes('码表');

export const modelMetadataDrafts = (preview: ModelMetadataImportPreview): ModelMetadataDraft[] => (
  preview.models.map((model) => {
    const warehouseLayerIssues = model.issues.filter(isWarehouseLayerIssue);
    const directoryIssues = model.issues.filter(isDirectoryIssue);
    const resolvedLayer = model.warehouseLayer?.enabled ? model.warehouseLayer : null;
    return {
      key: model.key,
      rowNumber: model.rowNumber,
      code: model.code,
      name: model.name,
      directoryPath: model.directoryPath ?? '',
      ...(directoryIssues.length ? { directoryIssue: directoryIssues.join('；') } : {}),
      warehouseLayerCode: model.warehouseLayerCode ?? '',
      ...(resolvedLayer ? { warehouseLayerId: resolvedLayer.id } : {}),
      ...(warehouseLayerIssues.length ? { warehouseLayerIssue: warehouseLayerIssues.join('；') } : {}),
      physicalTableName: model.physicalTableName,
      clickHouseOrderByColumns: model.clickHouseOrderByColumns,
      description: model.description,
      serverIssues: model.issues.filter((issue) => (
        !isWarehouseLayerIssue(issue) && !isDirectoryIssue(issue)
      )),
      warnings: model.warnings,
      fields: model.fields.map((field) => {
        const dictionaryIssues = field.issues.filter(isStandardDictionaryIssue);
        return {
          key: field.key,
          rowNumber: field.rowNumber,
          code: field.code,
          name: field.name,
          fieldType: field.fieldType,
          ...(field.length !== null ? { length: field.length } : {}),
          ...(field.precision !== null ? { precision: field.precision } : {}),
          ...(field.scale !== null ? { scale: field.scale } : {}),
          ...(field.geometry != null ? { geometry: field.geometry } : {}),
          nullable: field.nullable,
          primaryKey: field.fieldType === 'GEOMETRY' ? false : field.primaryKey,
          sortOrder: field.sortOrder,
          description: field.description,
          standardDictionaryCode: field.standardDictionaryCode ?? '',
          ...(field.standardDictionary ? {
            standardDictionaryId: field.standardDictionary.id,
            standardDictionary: field.standardDictionary,
          } : {}),
          ...(dictionaryIssues.length ? { standardDictionaryIssue: dictionaryIssues.join('；') } : {}),
          serverIssues: field.issues.filter((issue) => !isStandardDictionaryIssue(issue)),
        };
      }),
    };
  })
);

const fieldIssues = (field: ModelMetadataFieldDraft, duplicateCodes: Set<string>): string[] => {
  const issues: string[] = [...field.serverIssues];
  const code = field.code.trim();
  if (!code) issues.push('请填写字段编码');
  else if (!FIELD_CODE.test(code)) issues.push('字段编码须以小写字母开头，只能包含小写字母、数字和下划线，最多 64 个字符');
  else if (duplicateCodes.has(code.toLowerCase())) issues.push('当前模型中字段编码重复');
  if (!field.name.trim()) issues.push('请填写字段名称');
  else if (field.name.trim().length > 100) issues.push('字段名称不能超过 100 个字符');
  if (!field.fieldType) issues.push('请选择字段类型');
  if (field.nullable === null) issues.push('请选择是否可空');
  if (field.primaryKey === null) issues.push('请选择是否主键');
  if (field.primaryKey && field.nullable) issues.push('主键字段不能允许为空');
  if (field.sortOrder === null || !Number.isInteger(field.sortOrder) || field.sortOrder < 0) {
    issues.push('排序值必须是大于等于 0 的整数');
  }
  if (field.fieldType === 'STRING' && field.length !== undefined
    && (!Number.isInteger(field.length) || field.length < 1)) issues.push('STRING 长度必须是正整数');
  if (field.fieldType === 'DECIMAL') {
    if (!field.precision || !Number.isInteger(field.precision) || field.precision < 1 || field.precision > 38) {
      issues.push('DECIMAL 精度须为 1 至 38');
    }
    if (field.scale === undefined || !Number.isInteger(field.scale)
      || field.scale < 0 || field.scale > (field.precision ?? 0)) {
      issues.push('DECIMAL 小数位须为 0 至精度值');
    }
  }
  if (field.fieldType === 'GEOMETRY') {
    if (!field.geometry) issues.push('GEOMETRY 必须指定几何类型、EPSG CRS 和 XY 维度');
    if (field.primaryKey) issues.push('GEOMETRY 不能作为主键');
  }
  if (field.standardDictionaryIssue) issues.push(field.standardDictionaryIssue);
  if (field.description.trim().length > 500) issues.push('字段说明不能超过 500 个字符');
  return [...new Set(issues)];
};

export const modelMetadataDraftIssues = (
  drafts: ModelMetadataDraft[],
  targetIsClickHouse: boolean,
): Map<string, ModelMetadataDraftIssue> => {
  const duplicateCodes = duplicates(drafts.map((draft) => draft.code.trim().toLowerCase()));
  const duplicateTables = duplicates(drafts.map((draft) => draft.physicalTableName.trim().toLowerCase()));
  return new Map(drafts.map((draft) => {
    const issue: ModelMetadataDraftIssue = { fieldIssues: new Map() };
    const code = draft.code.trim();
    if (!code) issue.code = '请填写模型编码';
    else if (!MODEL_CODE.test(code)) issue.code = '编码须以小写字母开头，只能包含小写字母、数字和下划线，最多 64 个字符';
    else if (duplicateCodes.has(code.toLowerCase())) issue.code = '本次导入中模型编码重复';
    if (!draft.name.trim()) issue.name = '请填写模型名称';
    else if (draft.name.trim().length > 100) issue.name = '模型名称不能超过 100 个字符';
    if (draft.directoryIssue) issue.directoryPath = draft.directoryIssue;
    if (draft.warehouseLayerIssue) issue.warehouseLayer = draft.warehouseLayerIssue;
    const tableName = draft.physicalTableName.trim();
    if (!tableName) issue.physicalTableName = '请填写目标物理表名';
    else if (!TABLE_NAME.test(tableName)) issue.physicalTableName = '物理表名须以小写字母开头，只能包含小写字母、数字和下划线，最多 128 个字符';
    else if (duplicateTables.has(tableName.toLowerCase())) issue.physicalTableName = '本次导入中目标物理表名重复';
    if (draft.description.trim().length > 1000) issue.description = '模型说明不能超过 1000 个字符';
    if (draft.fields.length === 0) issue.fields = '模型至少需要一个字段';
    else if (draft.fields.length > 500) issue.fields = '单个模型最多包含 500 个字段';
    if (draft.serverIssues.length) issue.server = draft.serverIssues.join('；');
    if (draft.clickHouseOrderByColumns.length) {
      if (!targetIsClickHouse) {
        issue.clickHouseOrderByColumns = '非 ClickHouse 目标不能设置排序键';
      } else {
        const fieldCodes = new Set(draft.fields.map((field) => field.code.trim()));
        const duplicateOrderBy = duplicates(draft.clickHouseOrderByColumns);
        const invalid = draft.clickHouseOrderByColumns.find((item) => (
          !FIELD_CODE.test(item) || duplicateOrderBy.has(item) || !fieldCodes.has(item)
        ));
        if (invalid) issue.clickHouseOrderByColumns = `排序键字段无效、重复或不存在：${invalid}`;
        else {
          const geometryOrderBy = draft.clickHouseOrderByColumns.find((item) => (
            draft.fields.find((field) => field.code.trim() === item)?.fieldType === 'GEOMETRY'
          ));
          if (geometryOrderBy) {
            issue.clickHouseOrderByColumns = `ClickHouse Geometry 字段不能作为排序键：${geometryOrderBy}`;
          }
        }
      }
    }
    const duplicateFieldCodes = duplicates(draft.fields.map((field) => field.code.trim().toLowerCase()));
    draft.fields.forEach((field) => {
      const current = fieldIssues(field, duplicateFieldCodes);
      if (current.length) issue.fieldIssues.set(field.key, current);
    });
    return [draft.key, issue];
  }));
};

export const hasModelMetadataDraftIssues = (issues: Map<string, ModelMetadataDraftIssue>): boolean => (
  [...issues.values()].some((issue) => (
    Boolean(issue.code || issue.name || issue.directoryPath || issue.physicalTableName || issue.description
      || issue.warehouseLayer || issue.clickHouseOrderByColumns || issue.fields || issue.server)
    || issue.fieldIssues.size > 0
  ))
);

export const toModelMetadataImportRequest = (
  draft: ModelMetadataDraft,
): ImportModelMetadataModelRequest | undefined => {
  if (draft.fields.some((field) => (
    !field.fieldType || field.nullable === null || field.primaryKey === null || field.sortOrder === null
  ))) return undefined;
  return {
    code: draft.code.trim(),
    name: draft.name.trim(),
    directoryPath: draft.directoryPath.trim() || undefined,
    warehouseLayerId: draft.warehouseLayerId,
    physicalTableName: draft.physicalTableName.trim(),
    clickHouseOrderByColumns: draft.clickHouseOrderByColumns,
    description: draft.description.trim() || undefined,
    fields: draft.fields.flatMap((field) => field.fieldType && field.nullable !== null
      && field.primaryKey !== null && field.sortOrder !== null ? [{
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
        standardDictionaryId: field.standardDictionaryId,
      }] : []),
  };
};
