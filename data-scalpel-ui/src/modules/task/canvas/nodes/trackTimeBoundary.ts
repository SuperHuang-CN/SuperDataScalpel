import type { TrackFixedTimeBoundary, TrackTimeBoundaryUnit } from '../canvasTypes';

export const trackTimeBoundaryOptions: Array<{ value: TrackTimeBoundaryUnit; label: string }> = [
  { value: 'MILLISECONDS', label: '毫秒' }, { value: 'SECONDS', label: '秒' },
  { value: 'MINUTES', label: '分钟' }, { value: 'HOURS', label: '小时' },
  { value: 'DAYS', label: '日历日' }, { value: 'WEEKS', label: '日历周' },
  { value: 'MONTHS', label: '日历月' }, { value: 'YEARS', label: '日历年' },
];

export function parseTrackFixedTimeBoundary(
  value: unknown, path: string, errors: string[],
): TrackFixedTimeBoundary | null | undefined {
  if (value == null) return value;
  if (typeof value !== 'object' || Array.isArray(value)) {
    errors.push(`${path} 必须是对象或 null`);
    return undefined;
  }
  const raw = value as Record<string, unknown>;
  const interval = raw.interval;
  if (interval != null && (typeof interval !== 'number' || !Number.isInteger(interval)
      || interval < -2147483648 || interval > 2147483647)) {
    errors.push(`${path}.interval 必须是 32 位整数或 null`);
  }
  const unit = trackTimeBoundaryOptions.find((item) => item.value === raw.unit)?.value ?? null;
  if (raw.unit != null && unit === null) errors.push(`${path}.unit 不是受支持的边界单位`);
  const nullableText = (key: 'referenceTime' | 'timeZone') => {
    if (raw[key] == null) return null;
    if (typeof raw[key] === 'string') return raw[key];
    errors.push(`${path}.${key} 必须是字符串或 null`);
    return null;
  };
  return { interval: typeof interval === 'number' ? interval : null, unit,
    referenceTime: nullableText('referenceTime'), timeZone: nullableText('timeZone') };
}
