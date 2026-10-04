import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor, cleanup } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useDashboardQueries } from './useDashboardQueries';

// Exercise real HTTP hooks without loading unrelated Canvas/Monaco components from public barrels.
vi.mock('../../model', async () => ({
  ...await import('../../model/hooks/useModelStatistics'), ...await import('../../model/hooks/useQualityStatistics'),
}));
vi.mock('../../task', async () => import('../../task/hooks/useTaskStatistics'));
vi.mock('../../dataservice', async () => ({
  ...await import('../../dataservice/hooks/useServiceStatistics'), ...await import('../../dataservice/hooks/useGatewayUsage'),
  ...await import('../../dataservice/hooks/useGatewayAccess'),
}));
vi.mock('../../asset', async () => import('../../asset/hooks/useAssetStatistics'));
vi.mock('../../operations', async () => ({
  ...await import('../../operations/hooks/useOperations'), ...await import('../../operations/model/operationsSearch'),
}));

const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })}>{children}</QueryClientProvider>;
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
describe('homepage permissions and independent failures', () => {
  it('does not request business statistics without permissions', () => {
    const fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher);
    const { result } = renderHook(() => useDashboardQueries([], 1), { wrapper });
    expect(fetcher).not.toHaveBeenCalled();
    expect(result.current.canObserve).toBe(false);
  });
  it('requests only model statistics and keeps quality failure separate', async () => {
    const paths: string[] = [];
    vi.stubGlobal('fetch', vi.fn(async (url: string) => {
      paths.push(url);
      return url.includes('model-quality')
        ? new Response(JSON.stringify({ status: 503, detail: '质量查询暂不可用', code: 'UNAVAILABLE' }), { status: 503 })
        : new Response(JSON.stringify({ published: 0, draft: 0, managed: 0, external: 0, layers: [], collectedAt: '2026-09-30T00:00:00Z' }));
    }));
    const { result } = renderHook(() => useDashboardQueries(['model.view'], 1), { wrapper });
    await waitFor(() => expect(result.current.quality.isError).toBe(true));
    expect(result.current.models.data?.published).toBe(0);
    expect(result.current.quality.data).toBeUndefined();
    expect(paths).toHaveLength(2);
    expect(paths.every(path => path.includes('/models/statistics') || path.includes('/model-quality/statistics'))).toBe(true);
  });
  it('does not load the asset subsummary when its parent model panel is hidden', () => {
    const fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher);
    renderHook(() => useDashboardQueries(['asset.view'], 1), { wrapper });
    expect(fetcher).not.toHaveBeenCalled();
  });
});
