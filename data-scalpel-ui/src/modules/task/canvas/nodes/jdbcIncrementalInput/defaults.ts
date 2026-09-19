import type { JdbcIncrementalInputConfiguration } from "../../canvasTypes";

export const createJdbcIncrementalInputConfiguration = (): JdbcIncrementalInputConfiguration => ({
  dataSourceId: '',
  tableName: '',
  outputTableName: '',
  incrementalTimeColumn: '',
  startPosition: 'LATEST',
  startTime: null,
  cursorTimeZone: 'UTC',
  visibilityDelaySeconds: 30,
  triggerIntervalSeconds: 60,
});
