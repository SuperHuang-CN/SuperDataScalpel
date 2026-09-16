import type { StreamJoinConfiguration } from "../../canvasTypes";

export const createStreamJoinConfiguration = (): StreamJoinConfiguration => ({
  leftTableName: '',
  rightTableName: '',
  outputTableName: '',
  joinType: null,
  conditions: [],
  outputColumns: [],
});
