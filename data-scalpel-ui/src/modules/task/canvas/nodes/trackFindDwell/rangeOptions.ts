import type { TrackDwellRangeOptions, TrackDwellResultMode, TrackFindDwellConfiguration } from '../../canvasTypes';
import { trackDistanceUnitOptions, trackDurationUnitOptions } from '../trackOptions';

export const dwellResultOptions: Array<{ value: TrackDwellResultMode; label: string }> = [
  { value: 'MEAN_CENTERS', label: '均值中心' }, { value: 'CONVEX_HULLS', label: '凸包' },
  { value: 'DWELL_FEATURES', label: '驻留点' }, { value: 'ALL_FEATURES', label: '全部点' },
];
export const createDwellRangeOptions = (): TrackDwellRangeOptions => ({
  resultMode: 'MEAN_CENTERS', orderByColumns: [], durationUnit: 'MILLISECONDS',
  meanDistanceColumnName: 'mean_distance', meanDistanceUnit: 'METERS', dwellFlagColumnName: 'is_dwell',
});
export const dwellFeatureMode = (mode: TrackDwellResultMode | null | undefined) => mode === 'DWELL_FEATURES' || mode === 'ALL_FEATURES';
export const dwellOutputLabel = (configuration: TrackFindDwellConfiguration) =>
  configuration.dwellSemantics === 'REFERENCE_CENTER'
    ? dwellResultOptions.find((item) => item.value === configuration.rangeOptions?.resultMode)?.label ?? '待选择输出'
    : configuration.outputGeometryKind === 'CONVEX_HULL' ? '凸包（旧版）' : '中心点（旧版）';

export function parseDwellOptions(raw: Record<string, unknown>, path: string, errors: string[]):
  Pick<TrackFindDwellConfiguration, 'dwellSemantics' | 'rangeOptions'> {
  const result: Pick<TrackFindDwellConfiguration, 'dwellSemantics' | 'rangeOptions'> = {};
  if (raw.dwellSemantics != null) {
    if (raw.dwellSemantics === 'LEGACY_ADJACENT' || raw.dwellSemantics === 'REFERENCE_CENTER') result.dwellSemantics = raw.dwellSemantics;
    else errors.push(`${path}.dwellSemantics 无效`);
  }
  if (raw.rangeOptions != null) {
    if (typeof raw.rangeOptions !== 'object' || Array.isArray(raw.rangeOptions)) {
      errors.push(`${path}.rangeOptions 必须是对象或 null`); return result;
    }
    const value = raw.rangeOptions as Record<string, unknown>;
    const mode = dwellResultOptions.find((item) => item.value === value.resultMode)?.value ?? null;
    if (value.resultMode != null && !mode) errors.push(`${path}.rangeOptions.resultMode 无效`);
    const duration = trackDurationUnitOptions.find((item) => item.value === value.durationUnit)?.value ?? null;
    const distance = trackDistanceUnitOptions.find((item) => item.value === value.meanDistanceUnit)?.value ?? null;
    if (value.durationUnit != null && !duration) errors.push(`${path}.rangeOptions.durationUnit 无效`);
    if (value.meanDistanceUnit != null && !distance) errors.push(`${path}.rangeOptions.meanDistanceUnit 无效`);
    const order = value.orderByColumns;
    const validOrder = Array.isArray(order) && order.every((item): item is string => typeof item === 'string');
    if (order != null && !validOrder) errors.push(`${path}.rangeOptions.orderByColumns 必须是字符串数组`);
    for (const name of ['meanDistanceColumnName', 'dwellFlagColumnName']) {
      if (value[name] != null && typeof value[name] !== 'string') errors.push(`${path}.rangeOptions.${name} 必须是字符串`);
    }
    result.rangeOptions = { resultMode: mode, orderByColumns: validOrder ? order : [], durationUnit: duration,
      meanDistanceUnit: distance, meanDistanceColumnName: typeof value.meanDistanceColumnName === 'string' ? value.meanDistanceColumnName : '',
      dwellFlagColumnName: typeof value.dwellFlagColumnName === 'string' ? value.dwellFlagColumnName : '' };
  }
  return result;
}
