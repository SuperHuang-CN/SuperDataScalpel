import { createNearestMatching } from "./matching";
import type { SpatialNearestConfiguration } from "../../canvasTypes";

export const createSpatialNearestConfiguration = (): SpatialNearestConfiguration => ({
  sourceTableName: '',
  sourceGeometryColumnName: '',
  candidateTableName: '',
  candidateGeometryColumnName: '',
  candidateIdColumnName: '',
  distanceMethod: null,
  nearestCount: 1,
  maximumDistance: null,
  maximumDistanceUnit: null,
  includeUnmatched: false,
  outputTableName: '',
  distanceColumnName: 'distance',
  distanceOutputUnit: 'METERS',
  rankColumnName: 'nearest_rank',
  outputColumns: [],
  matching: createNearestMatching(),
});
