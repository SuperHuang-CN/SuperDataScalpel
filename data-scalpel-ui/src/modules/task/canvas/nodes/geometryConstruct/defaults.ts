import type { GeometryConstructConfiguration } from "../../canvasTypes";

export const createGeometryConstructConfiguration = (): GeometryConstructConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  outputColumnName: '',
  source: { kind: 'WKT', columnName: '' },
  targetGeometry: null,
});
