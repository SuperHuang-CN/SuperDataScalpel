import type { SpatialJoinConfiguration } from "../../canvasTypes";

export const createSpatialJoinConfiguration = (): SpatialJoinConfiguration => ({
  leftTableName: '',
  rightTableName: '',
  outputTableName: '',
  joinType: 'INNER',
  conditions: [],
  attributeConditions: [],
  outputColumns: [],
  joinOperation: 'JOIN_ONE_TO_MANY',
  oneToOne: null,
  temporalCondition: null,
  spatialNear: null,
  distanceOutput: null,
});
