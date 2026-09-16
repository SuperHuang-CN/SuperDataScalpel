import type { GeometrySerializeConfiguration } from "../../canvasTypes";

export const createGeometrySerializeConfiguration = (): GeometrySerializeConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  outputColumnName: '',
  format: 'WKT',
});
