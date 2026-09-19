import type { GeometrySimplifyConfiguration } from "../../canvasTypes";

export const createGeometrySimplifyConfiguration = (): GeometrySimplifyConfiguration => ({
  sourceTableName: '',
  geometryColumnName: '',
  outputTableName: '',
  outputColumnName: 'simplified_geometry',
  algorithm: 'TOPOLOGY_PRESERVING',
  tolerance: null,
  toleranceUnit: 'SOURCE_CRS_UNIT',
  geometryPolicy: 'PRESERVE_DIMENSION',
});
