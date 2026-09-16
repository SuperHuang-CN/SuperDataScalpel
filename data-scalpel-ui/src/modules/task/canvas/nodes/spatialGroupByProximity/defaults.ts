import type { SpatialGroupByProximityConfiguration } from "../../canvasTypes";

export const createSpatialGroupByProximityConfiguration = (
): SpatialGroupByProximityConfiguration => ({
  sourceTableName: '',
  geometryColumnName: '',
  spatialRelationship: 'INTERSECTS',
  spatialNearDistance: 100,
  spatialNearDistanceUnit: 'METERS',
  temporalCondition: null,
  attributeConditions: [],
  groupIdColumnName: 'group_id',
  outputTableName: '',
});
