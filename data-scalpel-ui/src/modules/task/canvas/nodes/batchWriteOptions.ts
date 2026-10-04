import type { BatchWriteOptions, CanvasColumnSchema, CanvasFilterCondition, JdbcWriteMode } from '../canvasTypes';
import { parseFilterCondition } from '../canvasValueParsers';
import { isRecord } from './configurationParsing';
import { validateFilterConditionDraft } from '../components/processors/filterConditionDraft';

export const parseBatchWrite = (value: unknown, path: string, errors: string[]): BatchWriteOptions | null => {
  if (value == null) return null;
  if (!isRecord(value)) { errors.push(`${path} 必须是原子写入配置`); return null; }
  if (value.allowEmptyOverwrite != null && typeof value.allowEmptyOverwrite !== 'boolean') errors.push(`${path}.allowEmptyOverwrite 必须是布尔值`);
  return {
    overwriteCondition: value.overwriteCondition == null ? null : parseFilterCondition(value.overwriteCondition, `${path}.overwriteCondition`, errors),
    allowEmptyOverwrite: value.allowEmptyOverwrite === true,
  };
};

export const activeBatchWrite = (value: BatchWriteOptions | null | undefined, mode: JdbcWriteMode | null | undefined) => (
  !value ? null : mode === 'OVERWRITE' ? value : { overwriteCondition: null, allowEmptyOverwrite: false }
);

export const batchWriteIssue = (value: BatchWriteOptions | null | undefined, mode: JdbcWriteMode | null | undefined,
  columns: CanvasColumnSchema[], mappedColumns: string[], databaseType: string | undefined): string | null => {
  if (!value) return null;
  if (!databaseType || !['POSTGRESQL', 'MYSQL', 'ORACLE', 'SQL_SERVER', 'OPENGAUSS'].includes(databaseType)) return '当前数据库尚未开放原子批写';
  if (mode !== 'OVERWRITE' || !value.overwriteCondition) return null;
  const draftIssue = validateFilterConditionDraft(value.overwriteCondition);
  if (draftIssue) return draftIssue;
  const inspect = (condition: CanvasFilterCondition): string | null => {
    if (condition.kind === 'GROUP') return condition.children.map(inspect).find(Boolean) ?? null;
    const column = columns.find(c => c.name === condition.columnName);
    if (!column || !mappedColumns.includes(column.name)) return `覆盖条件字段 ${condition.columnName} 不存在或未完成映射`;
    if (['GEOMETRY', 'BINARY'].includes(column.fieldType)) return '空间和二进制字段不支持覆盖条件';
    if (['CONTAINS', 'STARTS_WITH', 'ENDS_WITH'].includes(condition.operator)) return '覆盖条件暂不支持文本匹配运算';
    if (condition.values.some(v => v.dataType !== column.fieldType)) return `条件字段 ${column.name} 的值类型与目标不一致`;
    return null;
  };
  return inspect(value.overwriteCondition);
};
