import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { CategoryBarChart, type CategoryBarDatum } from './CategoryBarChart';

afterEach(cleanup);
const initial: CategoryBarDatum[] = Array.from({ length: 6 }, (_, index) => ({
  key: `layer-${index}`, label: `自定义分层 ${index}`, count: index, href: `/model?layer=layer-${index}`,
}));
const wrapper = ({ children }: { children: React.ReactNode }) => <MemoryRouter>{children}</MemoryRouter>;

describe('dynamic dashboard categories', () => {
  it('keeps a newly added seventh layer and the unassigned category reachable', () => {
    const { rerender } = render(<CategoryBarChart label="分层分布" data={initial} />, { wrapper });
    expect(screen.queryByRole('button', { name: '分层分布下一页' })).not.toBeInTheDocument();
    const expanded = [...initial,
      { key: 'custom', label: '用户新增的超长中文分层名称 SSS', count: 0, href: '/model?layer=custom' },
      { key: 'unassigned', label: '未分层', count: 3, href: '/model?layer=unassigned' },
    ];
    rerender(<CategoryBarChart label="分层分布" data={expanded} />);
    fireEvent.click(screen.getByRole('button', { name: '分层分布下一页' }));
    expect(screen.getByRole('link', { name: '用户新增的超长中文分层名称 SSS：0' })).toHaveAttribute('href', '/model?layer=custom');
    expect(screen.getByRole('link', { name: '未分层：3' })).toHaveAttribute('href', '/model?layer=unassigned');
    expect(screen.getByText('7–8 / 共 8 类')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '分层分布下一页' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '分层分布上一页' }));
    expect(screen.getByRole('link', { name: '自定义分层 0：0' })).toBeInTheDocument();
  });

  it('returns to a valid page when categories are deleted during refresh', () => {
    const data = [...initial, { key: 'extra', label: '临时分层', count: 1, href: '/model?layer=extra' }];
    const { rerender } = render(<CategoryBarChart label="分层分布" data={data} />, { wrapper });
    fireEvent.click(screen.getByRole('button', { name: '分层分布下一页' }));
    rerender(<CategoryBarChart label="分层分布" data={initial} />);
    expect(screen.getAllByRole('link')).toHaveLength(6);
    expect(screen.queryByRole('link', { name: '临时分层：1' })).not.toBeInTheDocument();
  });
});
