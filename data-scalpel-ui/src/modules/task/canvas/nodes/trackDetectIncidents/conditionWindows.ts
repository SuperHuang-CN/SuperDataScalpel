import type { CanvasColumnSchema, TrackIncidentWindow } from '../../canvasTypes';

export const incidentWindowKinds: { value: NonNullable<TrackIncidentWindow['kind']>; label: string }[] = [
  { value: 'MEAN', label: '均值' }, { value: 'SUM', label: '合计' }, { value: 'COUNT', label: '非空值数' },
  { value: 'MIN', label: '最小值' }, { value: 'MAX', label: '最大值' },
  { value: 'FIRST', label: '窗口首值' }, { value: 'LAST', label: '窗口末值' },
  { value: 'STDDEV_POP', label: '总体标准差' }, { value: 'VARIANCE_POP', label: '总体方差' },
];

export function incidentWindowErrors(windows: TrackIncidentWindow[], columns: CanvasColumnSchema[], schemaAvailable = true) {
  const sourceNames = new Set(columns.map(column => column.name));
  const names = columns.map(column => column.name.toLowerCase());
  return windows.map((item, index) => {
    const errors: Partial<Record<keyof TrackIncidentWindow, string>> = {};
    if (!/^[A-Za-z_][A-Za-z0-9_]{0,127}$/.test(item.bindingName)) errors.bindingName = '指标名需为字母或下划线开头的 1～128 位标识符';
    else if (names.includes(item.bindingName.toLowerCase()) || windows.some((other, otherIndex) =>
      otherIndex !== index && other.bindingName.toLowerCase() === item.bindingName.toLowerCase())) errors.bindingName = '指标名称重复或占用了来源字段';
    if (!item.sourceColumnName || schemaAvailable && !sourceNames.has(item.sourceColumnName)) errors.sourceColumnName = '请选择有效的原始字段，不能引用其他指标';
    if (!item.kind) errors.kind = '请选择函数';
    if (item.startOffset == null || !Number.isInteger(item.startOffset) || item.startOffset <= -2147483648 || item.startOffset > 2147483647)
      errors.startOffset = '请输入有效的有界整数偏移';
    if (item.endOffset == null || !Number.isInteger(item.endOffset) || item.endOffset < -2147483648 || item.endOffset > 2147483647)
      errors.endOffset = '请输入有效的有界整数偏移';
    if (item.startOffset != null && item.endOffset != null && item.startOffset >= item.endOffset)
      errors.endOffset = '终点必须大于起点，终点观测不包含在内';
    return errors;
  });
}

/** Local predicate-editor candidates only, not downstream Schema propagation. Native aggregate
 * result types are resolved by the Compiler; users can explicitly choose the literal type. */
export function incidentConditionColumns(columns: CanvasColumnSchema[], windows: TrackIncidentWindow[]): CanvasColumnSchema[] {
  const result = [...columns];
  const names = new Set(columns.map(column => column.name.toLowerCase()));
  for (const item of windows) {
    if (!item.bindingName || names.has(item.bindingName.toLowerCase())) continue;
    const source = columns.find(column => column.name === item.sourceColumnName);
    if (!source || !item.kind) continue;
    const numeric = ['SUM', 'MEAN', 'STDDEV_POP', 'VARIANCE_POP'].includes(item.kind);
    result.push({ ...source, name: item.bindingName, fieldType: item.kind === 'COUNT' ? 'LONG' : numeric ? 'DOUBLE' : source.fieldType,
      geometry: numeric || item.kind === 'COUNT' ? null : source.geometry,
      nullable: true, autoIncrement: false, generated: false, comment: '仅用于事件条件的窗口指标' });
    names.add(item.bindingName.toLowerCase());
  }
  return result;
}
