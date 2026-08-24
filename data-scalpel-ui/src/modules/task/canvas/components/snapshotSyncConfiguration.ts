import type { CanvasColumnSchema, SnapshotSyncConfiguration } from '../canvasTypes';
import { orderOutputFieldMappings } from './outputFieldMappings';

export type SnapshotSyncFormValues = SnapshotSyncConfiguration;

export const normalizeSnapshotSyncConfiguration = (
  values: SnapshotSyncFormValues,
  targetColumns: readonly CanvasColumnSchema[],
): SnapshotSyncConfiguration => {
  const action = values.deletePolicy?.action === 'DELETE' ? 'DELETE' : 'KEEP';
  return {
    sourceTableName: values.sourceTableName ?? '',
    keyColumns: values.keyColumns ?? [],
    columnMappings: orderOutputFieldMappings(
      targetColumns,
      (values.columnMappings ?? []).map((mapping) => ({
        sourceColumnName: mapping.sourceColumnName ?? '',
        targetColumnName: mapping.targetColumnName ?? '',
      })),
    ),
    deletePolicy: action === 'DELETE'
      ? {
        action,
        maxDeleteRows: values.deletePolicy?.maxDeleteRows ?? null,
        maxDeleteRatio: values.deletePolicy?.maxDeleteRatio ?? null,
      }
      : { action: 'KEEP', maxDeleteRows: null, maxDeleteRatio: null },
  };
};
