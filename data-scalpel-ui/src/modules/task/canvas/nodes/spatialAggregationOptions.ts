import type {
  SpatialDurationUnit,
  SpatialGroupSummary,
  SpatialTemporalSlicing,
} from '../canvasTypes';

export const spatialDurationUnitOptions: Array<{
  value: SpatialDurationUnit;
  label: string;
}> = [
  { value: 'MILLISECONDS', label: '毫秒' },
  { value: 'SECONDS', label: '秒' },
  { value: 'MINUTES', label: '分钟' },
  { value: 'HOURS', label: '小时' },
  { value: 'DAYS', label: '天' },
  { value: 'WEEKS', label: '周（固定 7 天）' },
];

export const createSpatialGroupSummary = (): SpatialGroupSummary => ({
  groupByColumnName: '',
  includeMinorityMajority: false,
  includeGroupPercentage: false,
  minorityFlagColumnName: 'is_minority',
  majorityFlagColumnName: 'is_majority',
  groupPercentageColumnName: 'group_percentage',
});

export const createSpatialTemporalSlicing = (): SpatialTemporalSlicing => ({
  timeColumnName: '',
  interval: 1,
  intervalUnit: 'DAYS',
  repeatInterval: null,
  repeatIntervalUnit: null,
  referenceTime: null,
  timeZone: 'UTC',
  windowStartColumnName: 'window_start',
  windowEndColumnName: 'window_end',
});
