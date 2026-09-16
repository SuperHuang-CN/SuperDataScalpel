import type { JdbcQueryInputConfiguration } from "../../canvasTypes";

export const createJdbcQueryInputConfiguration = (): JdbcQueryInputConfiguration => ({
  dataSourceId: '',
  sql: '',
  outputTableName: '',
  analyzedSqlSha256: '',
  outputColumns: [],
});
