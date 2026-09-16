import type { JoinConfiguration } from "../../canvasTypes";

export const createJoinConfiguration = (): JoinConfiguration => ({
  leftTableName: '',
  rightTableName: '',
  outputTableName: '',
  joinType: null,
  conditions: [],
  outputColumns: [],
});
