import type { SpatialOverlayConfiguration } from "../../canvasTypes";

export const createSpatialOverlayConfiguration = (): SpatialOverlayConfiguration => ({
  leftTableName: '',
  leftGeometryColumnName: '',
  rightTableName: '',
  rightGeometryColumnName: '',
  operation: 'INTERSECTION',
  geometryPolicy: 'FAMILY_2D',
  outputTableName: '',
  outputGeometryColumnName: 'overlay_geometry',
  outputColumns: [],
});
