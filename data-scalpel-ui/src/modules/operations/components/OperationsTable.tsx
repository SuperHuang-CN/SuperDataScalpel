import { ReloadOutlined } from '@ant-design/icons';
import { Button, Empty, Pagination, Space, Table, Tooltip } from 'antd';
import type { TableProps } from 'antd';
import type { ReactNode } from 'react';
import type { PageResponse } from '../../../shared/api/pageResponse';
import { InlineFeedback } from '../../../shared/components/ContextualFeedback';
import { formatManagementDateTime } from '../../../shared/format/managementDateTime';

interface OperationsTableProps<T> {
  title: string; query: { data?: PageResponse<T>; error: Error | null; isPending: boolean; isFetching: boolean; dataUpdatedAt: number; refetch: () => unknown };
  columns: TableProps<T>['columns']; page: number; size: number; onPage: (page: number, size: number) => void;
  extra?: ReactNode; expandable?: TableProps<T>['expandable'];
}
export const OperationsTable = <T extends { id: string }>({ title, query, columns, page, size, onPage, extra, expandable }: OperationsTableProps<T>) => (
  <div className="management-results-surface ops-results">
    <div className="management-result-toolbar">
      <div className="management-result-title">{title} <span className="management-result-count">共 {query.data?.totalElements ?? '—'} 项</span></div>
      <Space className="management-result-actions">
        <Tooltip title={query.dataUpdatedAt ? `最后成功刷新：${formatManagementDateTime(new Date(query.dataUpdatedAt).toISOString())}` : '刷新列表'}>
          <Button type="text" icon={<ReloadOutlined spin={query.isFetching} />} aria-label={`刷新${title}`} onClick={() => void query.refetch()} />
        </Tooltip>{extra}
      </Space>
    </div>
    {query.error && <InlineFeedback className="ops-feedback" tone="error" label="加载失败" detail={query.error.message} action={<Button type="link" size="small" onClick={() => void query.refetch()}>重试</Button>} />}
    <Table<T> size="small" className="management-table" rowKey="id" columns={columns} dataSource={query.data?.content ?? []}
      loading={query.isPending} scroll={{ y: '100%' }} expandable={expandable} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={query.error ? '当前数据不可用' : '暂无记录'} /> }}
      pagination={{ current: page + 1, pageSize: size, total: query.data?.totalElements ?? 0, showSizeChanger: true,
        showTotal: total => `共 ${total} 项`, hideOnSinglePage: false, position: ['bottomRight'] }}
      onChange={p => onPage(p.pageSize !== size ? 0 : (p.current ?? 1) - 1, p.pageSize ?? size)} />
    {query.data?.totalElements === 0 && <Pagination className="ops-empty-pagination" size="small" current={1} pageSize={size} total={0}
      showSizeChanger hideOnSinglePage={false} showTotal={total => `共 ${total} 项`} onChange={(_, nextSize) => onPage(0, nextSize)} />}
  </div>
);
