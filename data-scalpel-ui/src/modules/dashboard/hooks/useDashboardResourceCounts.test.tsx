import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useDashboardResourceCounts } from './useDashboardResourceCounts';

vi.mock('../../datasource', async () => import('../../datasource/hooks/useDataSources'));
vi.mock('../../filedataset', async () => import('../../filedataset/hooks/useFileDatasets'));
vi.mock('../../dataentry', async () => import('../../dataentry/hooks/useDataEntry'));
vi.mock('../../metric', async () => import('../../metric/hooks/useMetrics'));
vi.mock('../../panorama', async () => import('../../panorama/hooks/usePanoramas'));

const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })}>{children}</QueryClientProvider>;
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('resource shortcut counts', () => {
  it('never requests unpermitted resources, including manual refresh', async () => {
    const fetcher = vi.fn();
    vi.stubGlobal('fetch', fetcher);
    const { result } = renderHook(() => useDashboardResourceCounts([]), { wrapper });
    await act(() => result.current.refresh());
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('uses the real paginated total and refreshes only the permitted resource', async () => {
    const paths: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      paths.push(url);
      return new Response(JSON.stringify({ content: [], totalElements: 42, totalPages: 42, page: 0, size: 1 }));
    }));
    const { result } = renderHook(() => useDashboardResourceCounts(['metric.view']), { wrapper });
    await waitFor(() => expect(result.current.counts.metrics.data?.totalElements).toBe(42));
    await act(() => result.current.refresh());
    expect(paths).toHaveLength(2);
    for (const path of paths) {
      const url = new URL(path, 'http://localhost');
      expect(url.pathname).toContain('/metrics');
      expect(url.searchParams.get('size')).toBe('1');
    }
  });
});
