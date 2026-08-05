import { FilterOutlined, SearchOutlined } from '@ant-design/icons';
import type { FormInstance, InputProps } from 'antd';
import { Button, Form, Input, Popover } from 'antd';
import type { ReactNode } from 'react';

const hasFilterValue = (value: unknown): boolean => {
  if (value === undefined || value === null || value === '') return false;
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === 'object') return Object.values(value).some(hasFilterValue);
  return true;
};

interface ManagementFilterActionsProps<T extends object> {
  form: FormInstance<T>;
  appliedFilters?: T;
  additionalActive?: boolean;
  loading?: boolean;
  onReset: () => void;
}

export const ManagementFilterActions = <T extends object>({
  form,
  appliedFilters,
  additionalActive = false,
  loading = false,
  onReset,
}: ManagementFilterActionsProps<T>) => {
  const draftFilters = Form.useWatch((values: T) => values, form);
  const showReset = additionalActive || hasFilterValue(draftFilters) || hasFilterValue(appliedFilters);

  return (
    <div className="management-filter-actions">
      <Button type="primary" loading={loading} onClick={() => form.submit()}>查询</Button>
      {showReset && <Button type="text" onClick={onReset}>重置</Button>}
    </div>
  );
};

export const ManagementSearchInput = (props: InputProps) => (
  <Input
    {...props}
    prefix={<SearchOutlined className="management-search-input-icon" aria-hidden />}
  />
);

interface ManagementMoreFiltersProps {
  count: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onClear: () => void;
  onCancel: () => void;
  onConfirm: () => void;
  children: ReactNode;
}

export const ManagementMoreFilters = ({
  count,
  open,
  onOpenChange,
  onClear,
  onCancel,
  onConfirm,
  children,
}: ManagementMoreFiltersProps) => (
  <Popover
    trigger="click"
    placement="bottomLeft"
    rootClassName="management-more-filter-overlay"
    open={open}
    onOpenChange={onOpenChange}
    content={(
      <div className="management-more-filter-popover">
        <div className="management-more-filter-title">更多筛选</div>
        <div className="management-more-filter-body">{children}</div>
        <div className="management-more-filter-footer">
          <Button type="link" size="small" onClick={onClear}>清空高级条件</Button>
          <div>
            <Button size="small" onClick={onCancel}>取消</Button>
            <Button type="primary" size="small" onClick={onConfirm}>确定</Button>
          </div>
        </div>
      </div>
    )}
  >
    <Button
      className={count ? 'management-more-filter-trigger is-active' : 'management-more-filter-trigger'}
      icon={<FilterOutlined />}
    >
      {count ? `更多 · ${count}` : '更多'}
    </Button>
  </Popover>
);
