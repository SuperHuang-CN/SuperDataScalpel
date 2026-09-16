import type { AggregateConfiguration } from "../../canvasTypes";

export const createAggregateConfiguration = (): AggregateConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  groupByColumns: [],
  aggregations: [],
});
