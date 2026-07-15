export type ModelDetailTabKey = 'basic' | 'fields' | 'changes' | 'data' | 'tasks' | 'lineage';

export const normalizeModelDetailTab = (value: string | null): ModelDetailTabKey => {
  if (value === 'fields' || value === 'changes' || value === 'data' || value === 'tasks' || value === 'lineage') return value;
  return 'basic';
};

export interface MockModelPreviewRow {
  id: number;
  orderNo: string;
  customerName: string;
  amount: number;
  status: 'NEW' | 'PAID' | 'DELIVERED' | 'CLOSED';
  createdAt: string;
}

const previewStatuses: MockModelPreviewRow['status'][] = ['NEW', 'PAID', 'DELIVERED', 'CLOSED'];
const previewCustomers = ['政务服务中心', '城市运行中心', '数据资源局', '应急管理局', '公共资源中心'];

export const mockModelPreviewRows: MockModelPreviewRow[] = Array.from({ length: 36 }, (_value, index) => ({
  id: 10001 + index,
  orderNo: `DS${String(202607140001 + index)}`,
  customerName: previewCustomers[index % previewCustomers.length],
  amount: 860 + index * 137.5,
  status: previewStatuses[index % previewStatuses.length],
  createdAt: `2026-07-${String(1 + (index % 14)).padStart(2, '0')} ${String(8 + (index % 10)).padStart(2, '0')}:20:00`,
}));

export const filterMockModelPreviewRows = (
  rows: MockModelPreviewRow[],
  keyword?: string,
  status?: MockModelPreviewRow['status'],
) => {
  const normalizedKeyword = keyword?.trim().toLowerCase();
  return rows.filter((row) => (
    (!normalizedKeyword
      || row.orderNo.toLowerCase().includes(normalizedKeyword)
      || row.customerName.toLowerCase().includes(normalizedKeyword))
    && (!status || row.status === status)
  ));
};

export interface MockRelatedTask {
  id: string;
  code: string;
  name: string;
  relation: 'PRODUCER' | 'CONSUMER';
  taskType: '同步任务' | '转换任务' | '数据服务';
  status: 'RUNNING' | 'ENABLED' | 'DISABLED' | 'FAILED';
  schedule: string;
  lastRunAt: string | null;
}

export const mockRelatedTasks: MockRelatedTask[] = [
  { id: 'task-1', code: 'sync_erp_order', name: 'ERP 订单同步', relation: 'PRODUCER', taskType: '同步任务', status: 'ENABLED', schedule: '每 10 分钟', lastRunAt: '2026-07-14 10:20:00' },
  { id: 'task-2', code: 'merge_crm_customer', name: '客户信息补全', relation: 'PRODUCER', taskType: '转换任务', status: 'RUNNING', schedule: '每小时', lastRunAt: '2026-07-14 10:00:00' },
  { id: 'task-3', code: 'build_order_wide', name: '订单服务宽表构建', relation: 'CONSUMER', taskType: '转换任务', status: 'ENABLED', schedule: '每日 02:00', lastRunAt: '2026-07-14 02:03:12' },
  { id: 'task-4', code: 'publish_order_api', name: '订单查询服务', relation: 'CONSUMER', taskType: '数据服务', status: 'ENABLED', schedule: '实时', lastRunAt: '2026-07-14 10:26:18' },
  { id: 'task-5', code: 'archive_order', name: '历史订单归档', relation: 'CONSUMER', taskType: '同步任务', status: 'DISABLED', schedule: '每月 1 日', lastRunAt: null },
];

export const filterMockRelatedTasks = (
  tasks: MockRelatedTask[],
  keyword?: string,
  relation?: MockRelatedTask['relation'],
  status?: MockRelatedTask['status'],
) => {
  const normalizedKeyword = keyword?.trim().toLowerCase();
  return tasks.filter((task) => (
    (!normalizedKeyword
      || task.name.toLowerCase().includes(normalizedKeyword)
      || task.code.toLowerCase().includes(normalizedKeyword))
    && (!relation || task.relation === relation)
    && (!status || task.status === status)
  ));
};

export type MockLineageDirection = 'UPSTREAM' | 'DOWNSTREAM' | 'BOTH';
export type MockLineageNodeKind = 'MODEL' | 'TASK' | 'SERVICE';

export interface MockLineageNode {
  id: string;
  label: string;
  subtitle: string;
  kind: MockLineageNodeKind;
  side: 'UPSTREAM' | 'CURRENT' | 'DOWNSTREAM';
  depth: 0 | 1 | 2;
  x: number;
  y: number;
}

export interface MockLineageEdge {
  id: string;
  source: string;
  target: string;
}

const mockLineageNodes: MockLineageNode[] = [
  { id: 'crm-customer', label: 'CRM 客户主数据', subtitle: '业务模型', kind: 'MODEL', side: 'UPSTREAM', depth: 2, x: 24, y: 54 },
  { id: 'erp-order', label: 'ERP 订单表', subtitle: '源端模型', kind: 'MODEL', side: 'UPSTREAM', depth: 1, x: 24, y: 220 },
  { id: 'sync-order', label: 'ERP 订单同步', subtitle: '同步任务', kind: 'TASK', side: 'UPSTREAM', depth: 1, x: 270, y: 140 },
  { id: 'current-model', label: '当前模型', subtitle: '订单事实模型', kind: 'MODEL', side: 'CURRENT', depth: 0, x: 520, y: 140 },
  { id: 'wide-task', label: '订单宽表构建', subtitle: '转换任务', kind: 'TASK', side: 'DOWNSTREAM', depth: 1, x: 770, y: 68 },
  { id: 'service-task', label: '订单查询服务', subtitle: '数据服务', kind: 'SERVICE', side: 'DOWNSTREAM', depth: 1, x: 770, y: 226 },
  { id: 'order-wide', label: '订单服务宽表', subtitle: '服务模型', kind: 'MODEL', side: 'DOWNSTREAM', depth: 2, x: 1016, y: 54 },
  { id: 'order-api', label: '订单查询 API', subtitle: '已发布服务', kind: 'SERVICE', side: 'DOWNSTREAM', depth: 2, x: 1016, y: 220 },
];

const mockLineageEdges: MockLineageEdge[] = [
  { id: 'edge-1', source: 'crm-customer', target: 'sync-order' },
  { id: 'edge-2', source: 'erp-order', target: 'sync-order' },
  { id: 'edge-3', source: 'sync-order', target: 'current-model' },
  { id: 'edge-4', source: 'current-model', target: 'wide-task' },
  { id: 'edge-5', source: 'current-model', target: 'service-task' },
  { id: 'edge-6', source: 'wide-task', target: 'order-wide' },
  { id: 'edge-7', source: 'service-task', target: 'order-api' },
];

export const buildMockLineage = (direction: MockLineageDirection, depth: 1 | 2) => {
  const nodes = mockLineageNodes.filter((node) => (
    node.side === 'CURRENT'
    || (node.depth <= depth && (direction === 'BOTH' || node.side === direction))
  ));
  const nodeIds = new Set(nodes.map((node) => node.id));
  return {
    nodes,
    edges: mockLineageEdges.filter((edge) => nodeIds.has(edge.source) && nodeIds.has(edge.target)),
  };
};
