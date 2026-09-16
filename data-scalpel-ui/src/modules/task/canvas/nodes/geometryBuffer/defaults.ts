import type { GeometryBufferConfiguration } from "../../canvasTypes";

export const createGeometryBufferConfiguration = (): GeometryBufferConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  outputColumnName: 'buffer_geometry',
  distance: 100,
  mode: 'PLANAR',
  distanceUnit: 'SOURCE_CRS_UNIT',
  distanceSource: 'CONSTANT',
  distanceFieldName: null,
  distanceExpression: null,
});
