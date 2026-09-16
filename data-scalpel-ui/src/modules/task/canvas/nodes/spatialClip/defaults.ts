import type { SpatialClipConfiguration } from "../../canvasTypes";

export const createSpatialClipConfiguration = (): SpatialClipConfiguration => ({
  sourceTableName: '',
  maskTableName: '',
  outputTableName: '',
  sourceGeometryColumnName: '',
  maskGeometryColumnName: '',
  outputColumnName: 'clipped_geometry',
  geometryPolicy: 'SOURCE_FAMILY_2D',
  maskCombination: 'DISSOLVE_ALL',
});
