import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { ServiceDistributionChart, TaskDistributionChart } from './OverviewDistributionCharts';

afterEach(cleanup);
const tasks = [
  { key: 'batch', label: '批处理任务', count: 7, href: '/task?type=batch', detail: '运行 2 · 排队 1' },
  { key: 'stream', label: '实时任务', count: 0, href: '/task?type=stream', detail: '运行部署 0 · 启停中 0' },
];
const Location = () => <output aria-label="当前路径">{useLocation().search}</output>;
const wrapper = ({ children }: { children: React.ReactNode }) => <MemoryRouter>{children}<Location /></MemoryRouter>;

describe('overview distribution charts', () => {
  it('shows a full single-category ring, retains zero categories and supports keyboard navigation', () => {
    render(<TaskDistributionChart data={tasks} />, { wrapper });
    const segment = screen.getAllByRole('link', { name: '批处理任务：7 个，占比 100.0%；运行 2 · 排队 1' })[0];
    expect(segment).toHaveAttribute('stroke-dasharray', '100 100');
    expect(screen.getByRole('link', { name: '实时任务：0 个，占比 0.0%；运行部署 0 · 启停中 0' })).toBeInTheDocument();
    fireEvent.focus(segment);
    expect(screen.getByText('100.0%')).toBeInTheDocument();
    fireEvent.keyDown(segment, { key: 'Enter' });
    expect(screen.getByLabelText('当前路径')).toHaveTextContent('?type=batch');
  });

  it('does not fabricate percentages or segments when no tasks exist', () => {
    const { container } = render(<TaskDistributionChart data={tasks.map(item => ({ ...item, count: 0 }))} />, { wrapper });
    expect(container.querySelectorAll('.home-task-segment')).toHaveLength(0);
    expect(screen.getByText('暂无任务')).toBeInTheDocument();
    expect(screen.queryByText('100.0%')).not.toBeInTheDocument();
    expect(screen.getAllByRole('link')).toHaveLength(2);
  });

  it('compares enabled counts on the same scale and marks failures as a subset', () => {
    const { container } = render(<ServiceDistributionChart data={[
      { key: 'sql', label: 'SQL 查询', count: 4, errorCount: 1, href: '/dataservice?type=SQL' },
      { key: 'script', label: 'Groovy 脚本', count: 2, errorCount: 0, href: '/dataservice?type=SCRIPT' },
      { key: 'spatial', label: '空间服务', count: 0, errorCount: 0, href: '/dataservice?type=SPATIAL' },
    ]} />, { wrapper });
    const bars = container.querySelectorAll('.home-service-column-bar');
    expect(bars[0]).toHaveStyle({ height: '100%' });
    expect(bars[0].querySelector('i')).toHaveStyle({ height: '25%' });
    expect(bars[1]).toHaveStyle({ height: '50%' });
    expect(bars[2]).toHaveStyle({ height: '0%' });
    expect(screen.getByRole('link', { name: '空间服务：已启用 0，部署异常 0' })).toHaveAttribute('href', '/dataservice?type=SPATIAL');
  });
});
