import type { TrackFindDwellConfiguration } from "../../canvasTypes";
import { createTrackBoundaryConfiguration } from '../configurationDefaults';

export const createTrackFindDwellConfiguration = (): TrackFindDwellConfiguration => ({
  dwellSemantics: 'REFERENCE_CENTER',
  rangeOptions: { resultMode: 'MEAN_CENTERS', orderByColumns: [], durationUnit: 'MILLISECONDS',
    meanDistanceColumnName: 'mean_distance', meanDistanceUnit: 'METERS', dwellFlagColumnName: 'is_dwell' },
  sourceTableName: '',
  pointGeometryColumnName: '',
  trackIdColumns: [],
  timeColumnName: '',
  distanceMethod: 'PLANAR',
  distanceThreshold: 100,
  distanceThresholdUnit: 'METERS',
  minimumDuration: 20,
  minimumDurationUnit: 'MINUTES',
  boundaries: createTrackBoundaryConfiguration(),
  summaryStatistics: [],
  outputGeometryKind: 'CENTROID',
  outputTableName: '',
  dwellIdColumnName: 'dwell_id',
  startTimeColumnName: 'start_time',
  endTimeColumnName: 'end_time',
  durationColumnName: 'duration',
  pointCountColumnName: 'point_count',
  outputGeometryColumnName: 'dwell_geometry',
});
