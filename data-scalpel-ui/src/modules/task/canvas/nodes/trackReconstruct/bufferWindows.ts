import type { CanvasColumnSchema, TrackBufferWindowBinding, TrackSummaryStatisticKind } from '../../canvasTypes';

export const bufferWindowStatistics: { value: TrackSummaryStatisticKind; label: string }[] = [
  { value:'MEAN',label:'均值' },{ value:'SUM',label:'合计' },{ value:'MIN',label:'最小值' },{ value:'MAX',label:'最大值' },
  { value:'RANGE',label:'极差' },{ value:'COUNT_FIELD',label:'非空数' },{ value:'STDDEV',label:'样本标准差' },
  { value:'VARIANCE',label:'样本方差' },{ value:'FIRST',label:'首值' },{ value:'LAST',label:'末值' },
];
const allStatistics = new Set<TrackSummaryStatisticKind>(['COUNT','SUM','MEAN','MIN','MAX','RANGE','STDDEV','VARIANCE','FIRST','LAST','COUNT_FIELD','ANY']);

export function parseBufferWindows(raw: unknown, path: string, errors: string[]): TrackBufferWindowBinding[] {
  if (raw == null) return [];
  if (!Array.isArray(raw)) { errors.push(`${path} 必须是数组`); return []; }
  return raw.map((value: unknown,index) => {
    const p = `${path}[${index}]`;
    if (value == null || typeof value !== 'object' || Array.isArray(value)) errors.push(`${p} 必须是对象`);
    const o = value != null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string,unknown> : {};
    for (const field of ['name','sourceColumnName']) if (typeof o[field] !== 'string') errors.push(`${p}.${field} 必须是字符串`);
    for (const field of ['startOffset','endOffset']) if (o[field] != null && (typeof o[field] !== 'number' || !Number.isInteger(o[field]))) errors.push(`${p}.${field} 必须是整数`);
    const statistic = [...allStatistics].find(kind => kind === o.statistic);
    if (o.statistic != null && !statistic) errors.push(`${p}.statistic 无效`);
    return { name:typeof o.name === 'string' ? o.name : '',sourceColumnName:typeof o.sourceColumnName === 'string' ? o.sourceColumnName : '',
      startOffset:typeof o.startOffset === 'number' ? o.startOffset : null,endOffset:typeof o.endOffset === 'number' ? o.endOffset : null,
      statistic:statistic ?? null };
  });
}

export function bufferWindowProblems(bindings: TrackBufferWindowBinding[], columns: CanvasColumnSchema[], validationAvailable: boolean): string[][] {
  return bindings.map((b,index) => {
    const errors: string[] = [];
    if (!/^[A-Za-z_][A-Za-z0-9_]{0,127}$/.test(b.name) || b.name.toLowerCase().startsWith('__datascalpel_')) errors.push('绑定名格式错误或使用平台内部前缀');
    if (columns.some(c => c.name.toLowerCase() === b.name.toLowerCase())
      || bindings.some((other,i) => i !== index && other.name.toLowerCase() === b.name.toLowerCase())) errors.push('绑定名重复');
    const field = columns.find(c => c.name === b.sourceColumnName);
    if (!b.sourceColumnName.trim()) errors.push('请选择原始来源字段');
    else if (validationAvailable && !field) errors.push('来源字段已失效；不能引用其他绑定');
    else if (field && !['BYTE','SHORT','INTEGER','LONG','FLOAT','DOUBLE','DECIMAL'].includes(field.fieldType)) errors.push('需要数值字段');
    if (b.startOffset == null || b.endOffset == null || !Number.isInteger(b.startOffset) || !Number.isInteger(b.endOffset)
      || b.startOffset < -1000 || b.startOffset > 1000 || b.endOffset < -1000 || b.endOffset > 1000 || b.startOffset > b.endOffset) errors.push('起止偏移须为 -1000～1000 的整数且起点不大于终点');
    if (!bufferWindowStatistics.some(s => s.value === b.statistic)) errors.push('请选择支持的数值统计');
    return errors;
  });
}
