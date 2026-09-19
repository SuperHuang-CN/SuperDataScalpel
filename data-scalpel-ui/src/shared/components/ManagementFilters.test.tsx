import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ConfigProvider, Form } from 'antd';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  ManagementFilterActions,
  ManagementAdaptiveMoreFilters,
  ManagementMoreFilters,
  ManagementSearchInput,
} from './ManagementFilters';

interface Filters {
  keyword?: string;
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

const FilterHarness = ({ onFinish, onReset }: {
  onFinish: (values: Filters) => void;
  onReset: () => void;
}) => {
  const [form] = Form.useForm<Filters>();
  return (
    <ConfigProvider componentSize="small">
      <Form form={form} onFinish={onFinish}>
        <Form.Item name="keyword">
          <ManagementSearchInput placeholder="搜索资源" />
        </Form.Item>
        <ManagementFilterActions
          form={form}
          appliedFilters={{}}
          onReset={onReset}
        />
      </Form>
    </ConfigProvider>
  );
};

describe('ManagementFilters sizing contract', () => {
  it('keeps page-level search actions at middle size under the global small provider', async () => {
    const onFinish = vi.fn();
    const onReset = vi.fn();
    render(<FilterHarness onFinish={onFinish} onReset={onReset} />);

    const input = screen.getByPlaceholderText('搜索资源');
    const inputWrapper = input.closest('.ant-input-affix-wrapper');
    const queryButton = screen.getByRole('button', { name: /查\s*询/ });

    expect(inputWrapper).toHaveClass('management-search-input');
    expect(inputWrapper).not.toHaveClass('ant-input-affix-wrapper-sm');
    expect(queryButton).not.toHaveClass('ant-btn-sm');

    fireEvent.change(input, { target: { value: '订单' } });
    const resetButton = await screen.findByRole('button', { name: /重\s*置/ });
    expect(resetButton).not.toHaveClass('ant-btn-sm');

    fireEvent.click(queryButton);
    await waitFor(() => expect(onFinish).toHaveBeenCalledWith({ keyword: '订单' }));
    fireEvent.click(resetButton);
    expect(onReset).toHaveBeenCalledOnce();
  });

  it('keeps the more-filter trigger and footer actions at middle size', () => {
    const MoreFilterHarness = () => {
      const [open, setOpen] = useState(false);
      return (
        <ConfigProvider componentSize="small">
          <ManagementMoreFilters
            count={2}
            open={open}
            onOpenChange={setOpen}
            onClear={() => undefined}
            onCancel={() => setOpen(false)}
            onConfirm={() => setOpen(false)}
          >
            高级条件
          </ManagementMoreFilters>
        </ConfigProvider>
      );
    };

    render(<MoreFilterHarness />);
    const trigger = screen.getByRole('button', { name: /更多 · 2/ });
    expect(trigger).not.toHaveClass('ant-btn-sm');
    fireEvent.click(trigger);

    [/清空高级条件/, /取\s*消/, /确\s*定/].forEach((name) => {
      expect(screen.getByRole('button', { name })).not.toHaveClass('ant-btn-sm');
    });
  });

  it('shows advanced conditions inline when the filter strip has enough room', async () => {
    class MockResizeObserver {
      constructor(private readonly callback: ResizeObserverCallback) {}
      observe() { this.callback([], this as unknown as ResizeObserver); }
      disconnect() {}
      unobserve() {}
    }
    vi.stubGlobal('ResizeObserver', MockResizeObserver);
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(1_200);
    vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockReturnValue(900);

    const Harness = () => {
      const [open, setOpen] = useState(false);
      return (
        <div className="management-filter-strip">
          <div className="management-filter-form">常用条件</div>
          <ManagementAdaptiveMoreFilters
            count={0}
            open={open}
            onOpenChange={setOpen}
            onClear={() => undefined}
            onCancel={() => setOpen(false)}
            onConfirm={() => setOpen(false)}
          >
            <div>高级条件</div>
          </ManagementAdaptiveMoreFilters>
          <div className="management-filter-actions">查询</div>
        </div>
      );
    };

    render(<Harness />);
    expect(await screen.findByText('高级条件')).toBeVisible();
    expect(screen.queryByRole('button', { name: /更多/ })).not.toBeInTheDocument();
  });

  it('does not show more merely because the filter strip is narrower than a fixed breakpoint', async () => {
    class MockResizeObserver {
      constructor(private readonly callback: ResizeObserverCallback) {}
      observe() { this.callback([], this as unknown as ResizeObserver); }
      disconnect() {}
      unobserve() {}
    }
    vi.stubGlobal('ResizeObserver', MockResizeObserver);
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(700);
    vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockReturnValue(650);

    const Harness = () => {
      const [open, setOpen] = useState(false);
      return (
        <div className="management-filter-strip">
          <div className="management-filter-form">常用条件</div>
          <ManagementAdaptiveMoreFilters
            count={0}
            open={open}
            onOpenChange={setOpen}
            onClear={() => undefined}
            onCancel={() => setOpen(false)}
            onConfirm={() => setOpen(false)}
          >
            <div>仍可容纳的低频条件</div>
          </ManagementAdaptiveMoreFilters>
          <div className="management-filter-actions">查询</div>
        </div>
      );
    };

    render(<Harness />);
    expect(await screen.findByText('仍可容纳的低频条件')).toBeVisible();
    expect(screen.queryByRole('button', { name: /更多/ })).not.toBeInTheDocument();
  });

  it('uses the more trigger only when the filter strip cannot contain every condition', async () => {
    class MockResizeObserver {
      constructor(private readonly callback: ResizeObserverCallback) {}
      observe() { this.callback([], this as unknown as ResizeObserver); }
      disconnect() {}
      unobserve() {}
    }
    vi.stubGlobal('ResizeObserver', MockResizeObserver);
    vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(700);
    vi.spyOn(HTMLElement.prototype, 'scrollWidth', 'get').mockReturnValue(900);

    const Harness = () => {
      const [open, setOpen] = useState(false);
      return (
        <div className="management-filter-strip">
          <div className="management-filter-form">常用条件</div>
          <ManagementAdaptiveMoreFilters
            count={1}
            open={open}
            onOpenChange={setOpen}
            onClear={() => undefined}
            onCancel={() => setOpen(false)}
            onConfirm={() => setOpen(false)}
          >
            <div>高级条件</div>
          </ManagementAdaptiveMoreFilters>
          <div className="management-filter-actions">查询</div>
        </div>
      );
    };

    render(<Harness />);
    expect(await screen.findByRole('button', { name: /更多 · 1/ })).toBeVisible();
    expect(screen.queryByText('高级条件')).not.toBeInTheDocument();
  });
});
