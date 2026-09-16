import type { SpatialDensityConfiguration } from "../../canvasTypes";

export const createSpatialDensityConfiguration = (): SpatialDensityConfiguration => ({
  sourceTableName: '',
  pointGeometryColumnName: '',
  fields: [],
  weighting: 'UNIFORM',
  binShape: 'SQUARE',
  binSize: 1000,
  binSizeUnit: 'METERS',
  radius: 2000,
  radiusUnit: 'METERS',
  areaUnit: 'SQUARE_KILOMETERS',
  temporalSlicing: null,
  outputTableName: '',
  binIdColumnName: 'bin_id',
  binGeometryColumnName: 'bin_geometry',
  countDensityColumnName: 'point_density',
});
