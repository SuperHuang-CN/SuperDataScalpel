import type { UnionConfiguration } from "../../canvasTypes";

export const createUnionConfiguration = (): UnionConfiguration => ({
  inputTableNames: [],
  outputTableName: '',
  mode: 'ALL',
  mergingTables: [],
});
