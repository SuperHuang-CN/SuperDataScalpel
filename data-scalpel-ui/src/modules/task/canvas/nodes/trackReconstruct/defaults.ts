import { createReconstructionOptions } from "./reconstruction";
import type { TrackReconstructConfiguration } from "../../canvasTypes";
import { createTrackBoundaryConfiguration } from '../configurationDefaults';

export const createTrackReconstructConfiguration = (): TrackReconstructConfiguration => ({
  reconstruction: createReconstructionOptions(),
  sourceTableName: '',
  pointGeometryColumnName: '',
  trackIdColumns: [],
  timeColumnName: '',
  distanceMethod: 'PLANAR',
  boundaries: createTrackBoundaryConfiguration(),
  summaryStatistics: [],
  outputTableName: '',
  outputGeometryColumnName: 'track_geometry',
  startTimeColumnName: 'start_time',
  endTimeColumnName: 'end_time',
  pointCountColumnName: 'point_count',
});
