import type { SpatialAggregateConfiguration } from "../../canvasTypes";

export const createSpatialAggregateConfiguration = (): SpatialAggregateConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  groupByColumns: [],
  aggregations: [],
  dissolve: null,
});
