import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { fetchAlerts } from '../api/operationsApi';
import { AlertCenterPage } from './AlertCenterPage';

vi.mock('../api/operationsApi', () => ({ fetchAlerts: vi.fn(async () => ({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 })) }));
vi.mock('../components/AlertIncidentDrawer', () => ({ AlertIncidentDrawer: () => null }));
const clients: QueryClient[] = [];
afterEach(() => { cleanup(); clients.forEach(c => c.clear()); clients.length = 0; vi.clearAllMocks(); });
const setup = (url = '/operations/alerts') => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } }); clients.push(client);
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[url]}><AlertCenterPage /></MemoryRouter></QueryClientProvider>);
  return client;
};

describe('alert center filters', () => {
  it('keeps unsubmitted drafts out of automatic refresh and resets source filters', async () => {
    const user = userEvent.setup(); const client = setup('/operations/alerts?ruleType=RUN_FAILED');
    await waitFor(() => expect(fetchAlerts).toHaveBeenCalledTimes(1));
    fireEvent.change(screen.getByPlaceholderText('搜索对象或告警摘要'), { target: { value: '政务' } });
    await client.invalidateQueries({ queryKey: ['operations', 'alerts'] });
    expect(vi.mocked(fetchAlerts).mock.calls.at(-1)?.[0].search).not.toContain('政务');
    await waitFor(() => expect(screen.getByRole('button', { name: /查\s*询/ })).not.toHaveClass('ant-btn-loading'), { timeout: 15000 });
    await user.click(screen.getByRole('button', { name: /查\s*询/ }));
    await waitFor(() => expect(vi.mocked(fetchAlerts).mock.calls.at(-1)?.[0].search).toContain('政务'));
    await user.click(screen.getByRole('button', { name: /重\s*置/ }));
    await waitFor(() => expect(vi.mocked(fetchAlerts).mock.calls.at(-1)?.[0].search).toBe('status!"CLOSED"'));
    expect(screen.getByPlaceholderText('搜索对象或告警摘要')).toHaveValue('');
  });

  it('submits occurrence time as a half-open UTC interval', async () => {
    const user = userEvent.setup(); setup();
    fireEvent.change(screen.getByLabelText('告警发生开始时间'), { target: { value: '2026-09-07T08:00' } });
    fireEvent.change(screen.getByLabelText('告警发生结束时间'), { target: { value: '2026-09-07T09:00' } });
    await waitFor(() => expect(screen.getByRole('button', { name: /查\s*询/ })).not.toHaveClass('ant-btn-loading'), { timeout: 15000 });
    await user.click(screen.getByRole('button', { name: /查\s*询/ }));
    await waitFor(() => expect(vi.mocked(fetchAlerts).mock.calls.at(-1)?.[0].search).toContain(`occurredAt>="${new Date('2026-09-07T08:00').toISOString()}"`));
    expect(vi.mocked(fetchAlerts).mock.calls.at(-1)?.[0].search).toContain(`occurredAt<"${new Date('2026-09-07T09:00').toISOString()}"`);
  });
});
