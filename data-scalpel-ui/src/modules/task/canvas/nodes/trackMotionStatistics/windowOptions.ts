import type { TrackMotionStatistic, TrackMotionStatisticGroup, TrackMotionStatisticsConfiguration, TrackMotionWindowOptions } from '../../canvasTypes';
import { trackDistanceUnitOptions, trackDurationUnitOptions } from '../trackOptions';

export const motionStatisticGroups = {
  "DISTANCE": [
    "DISTANCE",
    "TOT_DISTANCE",
    "MIN_DISTANCE",
    "MAX_DISTANCE",
    "AVG_DISTANCE"
  ],
  "DURATION": [
    "DURATION",
    "TOT_DURATION",
    "MIN_DURATION",
    "MAX_DURATION",
    "AVG_DURATION"
  ],
  "SPEED": [
    "SPEED",
    "MIN_SPEED",
    "MAX_SPEED",
    "AVG_SPEED"
  ],
  "ACCELERATION": [
    "ACCELERATION",
    "MIN_ACCELERATION",
    "MAX_ACCELERATION"
  ],
  "ELEVATION": [
    "ELEVATION",
    "ELEV_CHANGE",
    "TOT_ELEV_CHANGE",
    "MIN_ELEVATION",
    "MAX_ELEVATION",
    "AVG_ELEVATION"
  ],
  "SLOPE": [
    "SLOPE",
    "MIN_SLOPE",
    "MAX_SLOPE",
    "AVG_SLOPE"
  ],
  "IDLE": [
    "IDLING",
    "TOT_IDLE_TIME",
    "PCT_IDLE_TIME"
  ],
  "BEARING": [
    "BEARING"
  ]
} as const satisfies Record<TrackMotionStatisticGroup, readonly TrackMotionStatistic[]>;
export const motionGroupLabels: Record<TrackMotionStatisticGroup, string> = {
  DISTANCE: '距离', DURATION: '时长', SPEED: '速度', ACCELERATION: '加速度',
  ELEVATION: '高程', SLOPE: '坡度', IDLE: '静止', BEARING: '方向',
};
export const speedUnits = [
  { value: 'METERS_PER_SECOND', label: '米/秒' }, { value: 'KILOMETERS_PER_HOUR', label: '千米/小时' },
  { value: 'FEET_PER_SECOND', label: '英尺/秒' }, { value: 'MILES_PER_HOUR', label: '英里/小时' },
  { value: 'KNOTS', label: '节' },
] as const;
export const accelerationUnits = [
  { value: 'METERS_PER_SECOND_SQUARED', label: '米/秒²' }, { value: 'FEET_PER_SECOND_SQUARED', label: '英尺/秒²' },
] as const;
export const createMotionGroup = (group: TrackMotionStatisticGroup) => motionStatisticGroups[group].map(kind => ({
  statisticId: crypto.randomUUID(), kind, outputColumnName: kind.toLowerCase(),
}));
export const createMotionWindowOptions = (): TrackMotionWindowOptions => ({
  observationCount: 3, orderByColumns: [], statistics: [...createMotionGroup('DISTANCE'), ...createMotionGroup('SPEED')],
  distanceUnit: 'METERS', durationUnit: 'SECONDS', speedUnit: 'METERS_PER_SECOND',
  accelerationUnit: 'METERS_PER_SECOND_SQUARED', elevationColumnName: null, inputElevationUnit: null,
  elevationUnit: 'METERS', idleTimeThreshold: null, idleTimeThresholdUnit: 'SECONDS',
});
export const selectedMotionGroups = (value: TrackMotionWindowOptions | null | undefined): TrackMotionStatisticGroup[] =>
  (Object.keys(motionStatisticGroups) as TrackMotionStatisticGroup[]).filter(group =>
    value?.statistics.some(s => (motionStatisticGroups[group] as readonly string[]).includes(s.kind)));

export function parseMotionWindowOptions(raw: Record<string, unknown>, path: string, errors: string[]):
  Pick<TrackMotionStatisticsConfiguration, 'motionSemantics' | 'windowOptions'> {
  const result: Pick<TrackMotionStatisticsConfiguration, 'motionSemantics' | 'windowOptions'> = {};
  if (raw.motionSemantics != null) {
    if (raw.motionSemantics === 'LEGACY_LAG' || raw.motionSemantics === 'OBSERVATION_WINDOW') result.motionSemantics = raw.motionSemantics;
    else errors.push(`${path}.motionSemantics 无效`);
  }
  if (raw.windowOptions == null) return result;
  if (typeof raw.windowOptions !== 'object' || Array.isArray(raw.windowOptions)) {
    errors.push(`${path}.windowOptions 必须是对象`); return result;
  }
  const o = raw.windowOptions as Record<string, unknown>;
  const p = `${path}.windowOptions`;
  const unit = <T extends string>(name: string, options: readonly { value: T }[]): T | null => {
    if (o[name] == null) return null;
    const selected = options.find(option => option.value === o[name]);
    if (!selected) errors.push(`${p}.${name} 无效`);
    return selected?.value ?? null;
  };
  const number = (name: string, integer = false) => {
    const v = o[name]; if (v == null) return null;
    if (typeof v !== 'number' || !Number.isFinite(v) || integer && !Number.isInteger(v)) {
      errors.push(`${p}.${name} 必须是${integer ? '整数' : '有限数值'}`); return null;
    }
    return v;
  };
  const text = (v: unknown, name: string) => {
    if (v != null && typeof v !== 'string') errors.push(`${name} 必须是字符串`);
    return typeof v === 'string' ? v : '';
  };
  const kinds = new Set<string>(Object.values(motionStatisticGroups).flat());
  const statistics: TrackMotionWindowOptions['statistics'] = [];
  if (!Array.isArray(o.statistics)) errors.push(`${p}.statistics 必须是数组`);
  else o.statistics.forEach((s: unknown, i: number) => {
    const at = `${p}.statistics[${i}]`;
    if (s == null || typeof s !== 'object' || Array.isArray(s)) { errors.push(`${at} 必须是对象`); return; }
    const item = s as Record<string, unknown>;
    if (typeof item.kind !== 'string' || !kinds.has(item.kind)) { errors.push(`${at}.kind 无效`); return; }
    statistics.push({ kind: item.kind as TrackMotionStatistic, statisticId: text(item.statisticId, `${at}.statisticId`),
      outputColumnName: text(item.outputColumnName, `${at}.outputColumnName`) });
  });
  const order = o.orderByColumns;
  if (order != null && (!Array.isArray(order) || !order.every(v => typeof v === 'string')))
    errors.push(`${p}.orderByColumns 必须是字符串数组`);
  result.windowOptions = {
    observationCount: number('observationCount', true), orderByColumns: Array.isArray(order) ? order.filter((v): v is string => typeof v === 'string') : [],
    statistics, distanceUnit: unit('distanceUnit', trackDistanceUnitOptions), durationUnit: unit('durationUnit', trackDurationUnitOptions),
    speedUnit: unit('speedUnit', speedUnits), accelerationUnit: unit('accelerationUnit', accelerationUnits),
    elevationColumnName: o.elevationColumnName == null ? null : text(o.elevationColumnName, `${p}.elevationColumnName`),
    inputElevationUnit: unit('inputElevationUnit', trackDistanceUnitOptions), elevationUnit: unit('elevationUnit', trackDistanceUnitOptions),
    idleTimeThreshold: number('idleTimeThreshold'), idleTimeThresholdUnit: unit('idleTimeThresholdUnit', trackDurationUnitOptions),
  };
  return result;
}
