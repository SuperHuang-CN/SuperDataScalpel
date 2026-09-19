export type ModelDetailTabKey = 'basic' | 'fields' | 'quality' | 'changes' | 'data' | 'tasks' | 'lineage';

export const normalizeModelDetailTab = (value: string | null): ModelDetailTabKey => {
  if (value === 'fields' || value === 'quality' || value === 'changes' || value === 'data' || value === 'tasks' || value === 'lineage') return value;
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
