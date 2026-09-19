import type {
  SpatialDurationUnit,
  TrackBoundaryConfiguration,
} from '../canvasTypes';

export { spatialDistanceUnitOptions as trackDistanceUnitOptions } from './spatialUnits';

export const trackDurationUnitOptions: Array<{ value: SpatialDurationUnit; label: string }> = [
  { value: 'MILLISECONDS', label: '毫秒' },
  { value: 'SECONDS', label: '秒' },
  { value: 'MINUTES', label: '分钟' },
  { value: 'HOURS', label: '小时' },
  { value: 'DAYS', label: '天' },
  { value: 'WEEKS', label: '周（固定 7 天）' },
];

export const emptyTrackBoundaries = (): TrackBoundaryConfiguration => ({
  maximumTimeGap: null,
  maximumTimeGapUnit: null,
  maximumDistanceGap: null,
  maximumDistanceGapUnit: null,
});
