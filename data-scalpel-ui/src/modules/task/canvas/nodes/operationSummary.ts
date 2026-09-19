
export const summarizeProcessorOperations = (
  operations: Array<{ sourceTableName: string; output: { outputTableName: string | null } }>,
  empty: string,
) => {
  if (operations.length === 0) return empty;
  const preview = operations.slice(0, 2)
    .map((operation) => `${operation.sourceTableName} → ${operation.output.outputTableName ?? operation.sourceTableName}`)
    .join('、');
  return operations.length > 2 ? `${preview} 等 ${operations.length} 张表` : preview;
};

export const snapshotDeleteSummary = (action: 'KEEP' | 'DELETE') => (
  action === 'DELETE' ? '删除目标独有行' : '保留目标独有行'
);
