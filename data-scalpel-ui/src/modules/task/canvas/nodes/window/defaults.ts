import type { WindowConfiguration } from "../../canvasTypes";

export const createWindowConfiguration = (): WindowConfiguration => ({
  sourceTableName: '',
  outputTableName: '',
  partitionByColumns: [],
  orderBy: [],
  functions: [],
});
