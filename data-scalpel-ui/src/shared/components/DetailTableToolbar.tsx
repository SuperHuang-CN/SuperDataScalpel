import { ReloadOutlined } from '@ant-design/icons';
import { Button, Pagination, Select, Tooltip } from 'antd';
import type { ReactNode } from 'react';

const pageSizeOptions = [20, 50, 100].map((value) => ({
  value,
  label: `${value} 条/页`,
}));

interface DetailTableToolbarProps {
  title: ReactNode;
  total: number;
  current?: number;
  pageSize?: number;
  onChange?: (page: number, pageSize: number) => void;
  showPagination?: boolean;
  onRefresh?: () => void;
  refreshing?: boolean;
  refreshLabel?: string;
  itemUnit?: string;
  extra?: ReactNode;
}

export const DetailTableToolbar = ({
  title,
  total,
  current = 1,
  pageSize = 20,
  onChange,
  showPagination = true,
  onRefresh,
  refreshing = false,
  refreshLabel = '刷新列表',
  itemUnit = '项',
  extra,
}: DetailTableToolbarProps) => {
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const safeCurrent = Math.min(Math.max(current, 1), totalPages);

  return (
    <div className="detail-table-result-toolbar">
      <div className="detail-table-result-title">
        <span>{title}</span>
        <span className="detail-table-result-count">共 {total} {itemUnit}</span>
      </div>
      <div className="detail-table-result-actions">
        {extra}
        {extra && (showPagination || onRefresh) && <span className="detail-table-refresh-divider" aria-hidden />}
        {showPagination && (
          <>
            <Select
              className="detail-table-page-size"
              size="small"
              aria-label="每页条数"
              value={pageSize}
              options={pageSizeOptions}
              popupMatchSelectWidth={false}
              onChange={(nextPageSize) => onChange?.(1, nextPageSize)}
            />
            <Pagination
              className="detail-table-pagination"
              current={safeCurrent}
              pageSize={pageSize}
              total={total}
              size="small"
              simple={{ readOnly: true }}
              showSizeChanger={false}
              hideOnSinglePage={false}
              onChange={(nextPage) => onChange?.(nextPage, pageSize)}
            />
          </>
        )}
        {onRefresh && (
          <>
            {showPagination && <span className="detail-table-refresh-divider" aria-hidden />}
            <Tooltip title={refreshLabel}>
              <Button
                type="text"
                icon={<ReloadOutlined />}
                loading={refreshing}
                aria-label={refreshLabel}
                onClick={onRefresh}
              />
            </Tooltip>
          </>
        )}
      </div>
    </div>
  );
};
