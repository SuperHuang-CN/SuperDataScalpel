import type { SpatialTransformConfiguration } from "../../canvasTypes";

export const createSpatialTransformConfiguration = (): SpatialTransformConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  geometryColumnName: '',
  targetCrs: null,
});
