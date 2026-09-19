import type { SpatialMultiVariableGridConfiguration } from "../../canvasTypes";

export const createSpatialMultiVariableGridConfiguration = (
): SpatialMultiVariableGridConfiguration => ({
  variables: [],
  binShape: 'SQUARE',
  binSize: 1000,
  binSizeUnit: 'METERS',
  outputTableName: '',
  binIdColumnName: 'bin_id',
  binGeometryColumnName: 'bin_geometry',
});
