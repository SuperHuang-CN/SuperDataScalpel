import { createMotionWindowOptions } from "./windowOptions";
import type { TrackMotionStatisticsConfiguration } from "../../canvasTypes";
import { createTrackBoundaryConfiguration } from '../configurationDefaults';

export const createTrackMotionStatisticsConfiguration = (): TrackMotionStatisticsConfiguration => ({
  motionSemantics: 'OBSERVATION_WINDOW',
  windowOptions: createMotionWindowOptions(),
  sourceTableName: '',
  pointGeometryColumnName: '',
  trackIdColumns: [],
  timeColumnName: '',
  distanceMethod: 'GEODESIC',
  boundaries: createTrackBoundaryConfiguration(),
  historyPoints: 1,
  idleDistanceThreshold: null,
  idleDistanceThresholdUnit: null,
  metrics: [],
  outputTableName: '',
});
