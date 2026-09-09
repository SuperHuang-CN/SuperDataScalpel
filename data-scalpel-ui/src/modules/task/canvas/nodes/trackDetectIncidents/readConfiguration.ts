import type { TrackDetectIncidentsConfiguration, TrackIncidentWindow } from '../../canvasTypes';

type LifecycleOptions = Pick<TrackDetectIncidentsConfiguration,
  'incidentSemantics' | 'incidentStatusColumnName' | 'orderByColumns' | 'conditionWindows'>;

export function parseIncidentLifecycleOptions(
  value: Record<string, unknown>, path: string, errors: string[],
): LifecycleOptions {
  const result: LifecycleOptions = { conditionWindows: [] };
  if (value.conditionWindows != null) {
    if (!Array.isArray(value.conditionWindows)) errors.push(`${path}.conditionWindows 必须是数组`);
    else result.conditionWindows = value.conditionWindows.flatMap((item: unknown, index): TrackIncidentWindow[] => {
      const itemPath = `${path}.conditionWindows[${index}]`;
      if (typeof item !== 'object' || item === null || Array.isArray(item)) {
        errors.push(`${itemPath} 必须是对象`); return [];
      }
      const row = item as Record<string, unknown>;
      if (typeof row.bindingName !== 'string' || typeof row.sourceColumnName !== 'string') {
        errors.push(`${itemPath} 的指标名及来源字段必须是字符串`);
      }
      let kind: TrackIncidentWindow['kind'] = null;
      switch (row.kind) {
        case null: case undefined: break;
        case 'COUNT': case 'SUM': case 'MEAN': case 'MIN': case 'MAX': case 'FIRST': case 'LAST': case 'STDDEV_POP': case 'VARIANCE_POP':
          kind = row.kind; break;
        default: errors.push(`${itemPath}.kind 无效`);
      }
      const offset = (value: unknown, name: string): number | null => {
        if (value == null) return null;
        if (typeof value === 'number' && Number.isInteger(value) && value >= -2147483648 && value <= 2147483647) return value;
        errors.push(`${itemPath}.${name} 必须是 32 位整数或 null`); return null;
      };
      return [{ bindingName: typeof row.bindingName === 'string' ? row.bindingName : '',
        sourceColumnName: typeof row.sourceColumnName === 'string' ? row.sourceColumnName : '', kind,
        startOffset: offset(row.startOffset, 'startOffset'), endOffset: offset(row.endOffset, 'endOffset') }];
    });
  }
  if (value.incidentSemantics != null) {
    if (value.incidentSemantics === 'LEGACY' || value.incidentSemantics === 'CONDITION_LIFECYCLE') {
      result.incidentSemantics = value.incidentSemantics;
    } else errors.push(`${path}.incidentSemantics 无效`);
  }
  if (value.incidentStatusColumnName != null) {
    if (typeof value.incidentStatusColumnName === 'string') {
      result.incidentStatusColumnName = value.incidentStatusColumnName;
    } else errors.push(`${path}.incidentStatusColumnName 必须是字符串或 null`);
  }
  if (value.orderByColumns != null) {
    if (Array.isArray(value.orderByColumns) && value.orderByColumns.every((item) => typeof item === 'string')) {
      result.orderByColumns = value.orderByColumns;
    } else errors.push(`${path}.orderByColumns 必须是字符串数组`);
  }
  return result;
}
