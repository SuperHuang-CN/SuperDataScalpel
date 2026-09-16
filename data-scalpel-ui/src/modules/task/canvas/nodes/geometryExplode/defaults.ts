import type { GeometryExplodeConfiguration } from "../../canvasTypes";

export const createGeometryExplodeConfiguration = (): GeometryExplodeConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  outputColumnName: 'geometry_part',
  partIndexColumnName: null,
});
