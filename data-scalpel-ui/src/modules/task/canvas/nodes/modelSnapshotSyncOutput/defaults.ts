import type { ModelSnapshotSyncOutputConfiguration } from "../../canvasTypes";
import { createSnapshotDeletePolicy } from '../configurationDefaults';

export const createModelSnapshotSyncOutputConfiguration = (
): ModelSnapshotSyncOutputConfiguration => ({
  sourceTableName: '',
  targetModelId: '',
  keyColumns: [],
  columnMappings: [],
  deletePolicy: createSnapshotDeletePolicy(),
});
