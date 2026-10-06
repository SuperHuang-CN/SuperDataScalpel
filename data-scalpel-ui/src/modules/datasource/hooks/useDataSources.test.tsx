import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchDataSources } from '../api/dataSourceApi';
import { useDataSources } from './useDataSources';

vi.mock('../api/dataSourceApi', () => ({ fetchDataSources: vi.fn() }));

const clients: QueryClient[] = [];
afterEach(() => {
  clients.forEach(client => client.clear());
  clients.length = 0;
  vi.clearAllMocks();
});

const setup = () => {
  const client = new QueryClient({ defaultOptions: { queries: { staleTime: 30_000, retry: false } } });
  clients.push(client);
  vi.mocked(fetchDataSources).mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 });
  const wrapper = ({ children }: PropsWithChildren) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  return { client, wrapper };
};

describe('data source list cache on page navigation', () => {
  it('reuses the global fresh cache after leaving and returning to the list', async () => {
    const { wrapper } = setup();
    const first = renderHook(() => useDataSources({ page: 0, size: 20 }), { wrapper });
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));
    first.unmount();
    const second = renderHook(() => useDataSources({ page: 0, size: 20 }), { wrapper });
    await act(async () => { await Promise.resolve(); });
    expect(second.result.current.isSuccess).toBe(true);
    expect(second.result.current.isFetching).toBe(false);
    expect(fetchDataSources).toHaveBeenCalledTimes(1);
    second.unmount();
  });

  it('refreshes an expired ordinary list when returning', async () => {
    const { client, wrapper } = setup();
    const request = { page: 0, size: 20 };
    const first = renderHook(() => useDataSources(request), { wrapper });
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));
    first.unmount();
    client.setQueryData(['data-sources', request], first.result.current.data, { updatedAt: Date.now() - 31_000 });
    const second = renderHook(() => useDataSources(request), { wrapper });
    await waitFor(() => expect(fetchDataSources).toHaveBeenCalledTimes(2));
    second.unmount();
  });

  it('still refreshes published-model source choices on every mount', async () => {
    const { wrapper } = setup();
    const request = { page: 0, size: 20, hasPublishedModels: true };
    const first = renderHook(() => useDataSources(request), { wrapper });
    await waitFor(() => expect(first.result.current.isSuccess).toBe(true));
    first.unmount();
    const second = renderHook(() => useDataSources(request), { wrapper });
    await waitFor(() => expect(fetchDataSources).toHaveBeenCalledTimes(2));
    second.unmount();
  });
});
