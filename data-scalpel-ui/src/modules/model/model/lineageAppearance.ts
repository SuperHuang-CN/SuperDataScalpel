import type { LineageGraphNodeKind } from './dataModel';

// The graph geometry and its HTML content must share the same size.
export const LINEAGE_ASSET_SIZE = { width: 272, height: 92 } as const;

export const lineageNodeKinds: LineageGraphNodeKind[] = ['MODEL', 'JDBC_TABLE', 'EXTERNAL_RESOURCE', 'TASK', 'FIELD', 'DATA_SERVICE'];
export const lineageNodeKindLabels: Record<LineageGraphNodeKind, string> = {
  MODEL: '数据模型', JDBC_TABLE: 'JDBC 物理表', EXTERNAL_RESOURCE: '外部资源',
  TASK: '数据任务', FIELD: '字段', DATA_SERVICE: '数据服务',
};
