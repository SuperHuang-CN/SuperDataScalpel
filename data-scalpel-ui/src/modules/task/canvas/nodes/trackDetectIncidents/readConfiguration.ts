import type { TrackDetectIncidentsConfiguration, TrackIncidentScalar, TrackIncidentWindow } from '../../canvasTypes';

type LifecycleOptions = Pick<TrackDetectIncidentsConfiguration,
  'incidentSemantics' | 'incidentStatusColumnName' | 'orderByColumns' | 'conditionWindows' | 'conditionScalars'>;

export function parseIncidentLifecycleOptions(
  value: Record<string, unknown>, path: string, errors: string[],
): LifecycleOptions {
  const result: LifecycleOptions = { conditionWindows: [], conditionScalars: [] };
  if (value.conditionWindows != null) {
    if (!Array.isArray(value.conditionWindows)) errors.push(`${path}.conditionWindows 必须是数组`);
    else result.conditionWindows = value.conditionWindows.flatMap((item: unknown, index): TrackIncidentWindow[] => {
      const itemPath = `${path}.conditionWindows[${index}]`;
      if (typeof item !== 'object' || item === null || Array.isArray(item)) {
        errors.push(`${itemPath} 必须是对象`); return [];
      }
      const row = item as Record<string, unknown>;
      const source = row.source == null ? 'FIELD' : row.source;
      if (source !== 'FIELD' && source !== 'TRACK_DISTANCE' && source !== 'TRACK_SPEED'
        && source !== 'TRACK_ACCELERATION') {
        errors.push(`${itemPath}.source 仅支持 FIELD、TRACK_DISTANCE、TRACK_SPEED 或 TRACK_ACCELERATION`);
      }
      if (typeof row.bindingName !== 'string'
        || source === 'FIELD' && typeof row.sourceColumnName !== 'string'
        || (source === 'TRACK_DISTANCE' || source === 'TRACK_SPEED' || source === 'TRACK_ACCELERATION')
          && row.sourceColumnName != null && typeof row.sourceColumnName !== 'string') {
        errors.push(`${itemPath} 的指标名或来源字段类型无效`);
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
        startOffset: offset(row.startOffset, 'startOffset'), endOffset: offset(row.endOffset, 'endOffset'),
        ...(row.source == null ? {} : { source: source === 'TRACK_DISTANCE' ? 'TRACK_DISTANCE' as const
          : source === 'TRACK_SPEED' ? 'TRACK_SPEED' as const
            : source === 'TRACK_ACCELERATION' ? 'TRACK_ACCELERATION' as const : 'FIELD' as const }) }];
    });
  }
  if (value.conditionScalars != null) {
    if (!Array.isArray(value.conditionScalars)) errors.push(`${path}.conditionScalars 必须是数组`);
    else result.conditionScalars = value.conditionScalars.flatMap((item: unknown, index): TrackIncidentScalar[] => {
      const itemPath = `${path}.conditionScalars[${index}]`;
      if (typeof item !== 'object' || item === null || Array.isArray(item)) {
        errors.push(`${itemPath} 必须是对象`); return [];
      }
      const row = item as Record<string, unknown>;
      if (typeof row.bindingName !== 'string') errors.push(`${itemPath}.bindingName 必须是字符串`);
      const validSources = ['TRACK_START_TIME', 'TRACK_DURATION', 'TRACK_CURRENT_TIME', 'TRACK_INDEX',
        'TRACK_POINT_X_AT', 'TRACK_POINT_Y_AT'];
      if (row.source != null && (typeof row.source !== 'string' || !validSources.includes(row.source))) {
        errors.push(`${itemPath}.source 无效`);
      }
      let offset: number | null = null;
      if (row.offset != null) {
        if (typeof row.offset === 'number' && Number.isInteger(row.offset)
          && row.offset >= -2147483648 && row.offset <= 2147483647) offset = row.offset;
        else errors.push(`${itemPath}.offset 必须是 32 位整数或 null`);
      }
      return [{
        bindingName: typeof row.bindingName === 'string' ? row.bindingName : '',
        source: typeof row.source === 'string' && validSources.includes(row.source)
          ? row.source as NonNullable<TrackIncidentScalar['source']> : null,
        ...(row.offset == null ? {} : { offset }),
      }];
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
