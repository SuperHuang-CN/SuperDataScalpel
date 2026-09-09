import type { SpatialCalendarWindowOptions, SpatialTemporalSlicing } from '../canvasTypes';
import { spatialDurationUnitOptions } from './spatialAggregationOptions';

export const calendarWindowUnits = [
  { value: 'MILLISECONDS', label: '毫秒' }, { value: 'SECONDS', label: '秒' },
  { value: 'MINUTES', label: '分钟' }, { value: 'HOURS', label: '小时（实际时长）' },
  { value: 'DAYS', label: '日历日' }, { value: 'WEEKS', label: '周' },
  { value: 'MONTHS', label: '日历月' }, { value: 'YEARS', label: '日历年' },
] satisfies Array<{ value: NonNullable<SpatialCalendarWindowOptions['intervalUnit']>; label: string }>;

export const calendarWindowHelp = '窗口左闭右开；重复间隔小于/等于/大于窗宽时可形成重叠/连续/留空。固定模式的天始终为 24 小时。日历模式的日/周/月/年按 IANA 时区推进，小时及更小单位仍为实际时长。同族周期从原始参考时刻计算起止，避免月末累积偏移；跨族组合先定位起点再加窗宽。无参考时使用 Unix Epoch 对应的本地时刻，不默认为本地午夜。无偏移的参考时间不可落在 DST 空隙/重叠时段；运行生成的边界按 Java 时区规则调整。每条观测最多检查 4096 个候选窗口，超限报错，不截断。NULL 时间或落在空隙内的观测不参与；不会补出无观测时间窗。';

export const usesCalendarWindow = (value: SpatialTemporalSlicing | null | undefined) => value?.calendar?.mode === 'CALENDAR';
export const temporalWindowLabel = (value: SpatialTemporalSlicing) => value.calendar && !value.calendar.mode ? '待选窗口语义' : usesCalendarWindow(value)
  ? `${value.interval} ${calendarWindowUnits.find(item => item.value === value.calendar?.intervalUnit)?.label ?? '待选单位'} · 日历`
  : `${value.interval} ${spatialDurationUnitOptions.find(item => item.value === value.intervalUnit)?.label ?? '待选单位'} · 固定`;

export function parseCalendarWindow(raw: Record<string, unknown>, path: string, errors: string[]): Pick<SpatialTemporalSlicing, 'calendar'> {
  if (!('calendar' in raw)) return {};
  if (raw.calendar == null) return { calendar: null };
  if (typeof raw.calendar !== 'object' || Array.isArray(raw.calendar)) { errors.push(`${path}.calendar 必须是对象或 null`); return {}; }
  const c = raw.calendar as Record<string, unknown>;
  if (c.mode != null && c.mode !== 'FIXED_DURATION' && c.mode !== 'CALENDAR') errors.push(`${path}.calendar.mode 无效`);
  const unit = (v: unknown, key: string): SpatialCalendarWindowOptions['intervalUnit'] => {
    if (v == null) return null;
    const option = calendarWindowUnits.find(item => item.value === v);
    if (!option) errors.push(`${path}.calendar.${key} 不是有效时间单位`);
    return option?.value ?? null;
  };
  return { calendar: { mode: c.mode === 'FIXED_DURATION' || c.mode === 'CALENDAR' ? c.mode : null,
    intervalUnit: unit(c.intervalUnit, 'intervalUnit'), repeatIntervalUnit: unit(c.repeatIntervalUnit, 'repeatIntervalUnit') } };
}
