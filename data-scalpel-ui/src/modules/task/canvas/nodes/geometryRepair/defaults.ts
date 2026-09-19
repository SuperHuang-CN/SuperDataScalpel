import type { GeometryRepairConfiguration } from "../../canvasTypes";

export const createGeometryRepairConfiguration = (): GeometryRepairConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  outputColumnName: 'repaired_geometry',
});
