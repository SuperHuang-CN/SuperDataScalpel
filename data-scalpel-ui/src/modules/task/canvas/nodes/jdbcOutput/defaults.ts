import type { JdbcOutputConfiguration } from "../../canvasTypes";

export const createJdbcOutputConfiguration = (): JdbcOutputConfiguration => ({
  dataSourceId: '',
  writes: [],
  sourceTableName: '', targetTableName: '', writeMode: 'OVERWRITE', columnMappings: [], upsertKeyColumns: [],
});
