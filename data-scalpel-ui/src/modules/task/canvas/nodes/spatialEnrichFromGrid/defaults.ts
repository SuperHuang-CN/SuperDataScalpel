import type { SpatialEnrichFromGridConfiguration } from "../../canvasTypes";

export const createSpatialEnrichFromGridConfiguration = (
): SpatialEnrichFromGridConfiguration => ({
  pointTableName: '',
  pointGeometryColumnName: '',
  gridTableName: '',
  gridGeometryColumnName: '',
  gridIdColumnName: '',
  enrichFields: [],
  outputTableName: '',
});
