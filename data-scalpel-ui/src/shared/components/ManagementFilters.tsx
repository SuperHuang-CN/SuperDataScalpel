import { FilterOutlined, SearchOutlined } from '@ant-design/icons';
import type { FormInstance, InputProps } from 'antd';
import { Button, Form, Input, Popover } from 'antd';
import type { ReactNode } from 'react';
import { useLayoutEffect, useRef, useState } from 'react';

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
      <Button type="primary" size="middle" loading={loading} onClick={() => form.submit()}>查询</Button>
      {showReset && <Button type="text" size="middle" onClick={onReset}>重置</Button>}
    </div>
  );
};

export const ManagementSearchInput = (props: InputProps) => (
  <Input
    {...props}
    className={['management-search-input', props.className].filter(Boolean).join(' ')}
    size={props.size ?? 'middle'}
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

type ManagementAdaptiveMoreFiltersProps = ManagementMoreFiltersProps;

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
          <Button type="link" size="middle" onClick={onClear}>清空高级条件</Button>
          <div>
            <Button size="middle" onClick={onCancel}>取消</Button>
            <Button type="primary" size="middle" onClick={onConfirm}>确定</Button>
          </div>
        </div>
      </div>
    )}
  >
    <Button
      size="middle"
      className={count ? 'management-more-filter-trigger is-active' : 'management-more-filter-trigger'}
      icon={<FilterOutlined />}
    >
      {count ? `更多 · ${count}` : '更多'}
    </Button>
  </Popover>
);

export const ManagementAdaptiveMoreFilters = ({
  count,
  open,
  onOpenChange,
  onClear,
  onCancel,
  onConfirm,
  children,
}: ManagementAdaptiveMoreFiltersProps) => {
  const anchorRef = useRef<HTMLDivElement>(null);
  const requiredWidthRef = useRef(0);
  const measuredActionWidthRef = useRef(0);
  const [collapsed, setCollapsed] = useState(false);

  useLayoutEffect(() => {
    const anchor = anchorRef.current;
    const strip = anchor?.closest<HTMLElement>('.management-filter-strip');
    if (!anchor || !strip || typeof ResizeObserver === 'undefined') return undefined;

    let animationFrame: number | undefined;
    const evaluate = () => {
      const availableWidth = strip.clientWidth;
      const actions = strip.querySelector<HTMLElement>(':scope > .management-filter-actions');
      const actionWidth = actions?.getBoundingClientRect().width || actions?.scrollWidth || 0;
      if (!collapsed) {
        const mustCollapse = strip.scrollWidth > availableWidth + 1;
        if (mustCollapse) {
          requiredWidthRef.current = Math.max(requiredWidthRef.current, strip.scrollWidth);
          measuredActionWidthRef.current = actionWidth;
          setCollapsed(true);
        }
        return;
      }

      const adjustedRequiredWidth = requiredWidthRef.current
        + actionWidth - measuredActionWidthRef.current;
      if (availableWidth >= adjustedRequiredWidth + 8) {
        onOpenChange(false);
        setCollapsed(false);
      }
    };
    const scheduleEvaluation = () => {
      if (animationFrame !== undefined) cancelAnimationFrame(animationFrame);
      animationFrame = requestAnimationFrame(evaluate);
    };
    const observer = new ResizeObserver(scheduleEvaluation);
    observer.observe(strip);
    observer.observe(anchor);
    const actions = strip.querySelector<HTMLElement>(':scope > .management-filter-actions');
    if (actions) observer.observe(actions);
    scheduleEvaluation();

    return () => {
      observer.disconnect();
      if (animationFrame !== undefined) cancelAnimationFrame(animationFrame);
    };
  }, [collapsed, onOpenChange]);

  return (
    <div
      ref={anchorRef}
      className={collapsed
        ? 'management-adaptive-more-filters is-collapsed'
        : 'management-adaptive-more-filters is-inline'}
    >
      {collapsed ? (
        <ManagementMoreFilters
          count={count}
          open={open}
          onOpenChange={onOpenChange}
          onClear={onClear}
          onCancel={onCancel}
          onConfirm={onConfirm}
        >
          {children}
        </ManagementMoreFilters>
      ) : <div className="management-inline-more-filters">{children}</div>}
    </div>
  );
};
