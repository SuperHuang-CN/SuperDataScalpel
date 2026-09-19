import type { JdbcSnapshotSyncOutputConfiguration } from "../../canvasTypes";
import { createSnapshotDeletePolicy } from '../configurationDefaults';

export const createJdbcSnapshotSyncOutputConfiguration = (
): JdbcSnapshotSyncOutputConfiguration => ({
  sourceTableName: '',
  dataSourceId: '',
  targetTableName: '',
  keyColumns: [],
  columnMappings: [],
  deletePolicy: createSnapshotDeletePolicy(),
});
