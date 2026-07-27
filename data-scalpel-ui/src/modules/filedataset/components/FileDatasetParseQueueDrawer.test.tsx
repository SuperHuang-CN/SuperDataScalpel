import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const hooks = vi.hoisted(() => ({
  summary: vi.fn(),
  jobs: vi.fn(),
  refetchSummary: vi.fn(),
  refetchJobs: vi.fn(),
}));

vi.mock('../hooks/useFileDatasets', () => ({
  useFileDatasetParseQueueSummary: (enabled: boolean) => hooks.summary(enabled),
  useFileDatasetParseJobs: (request: unknown, enabled: boolean) => hooks.jobs(request, enabled),
}));

import { FileDatasetParseQueueDrawer } from './FileDatasetParseQueueDrawer';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('FileDatasetParseQueueDrawer', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false, media: query, onchange: null,
        addListener: vi.fn(), removeListener: vi.fn(),
        addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
      })),
    });
    hooks.summary.mockReset().mockReturnValue({
      data: {
        queueEnabled: false,
        configuredWorkerConcurrency: 4,
        historyRetentionDays: 30,
        queuedCount: 2,
        runnableQueuedCount: 1,
        retryWaitingCount: 1,
        runningCount: 0,
        succeededCount: 8,
        failedCount: 1,
        cancelledCount: 0,
        oldestQueuedAt: '2026-07-19T00:00:00Z',
        oldestRunningAt: null,
        generatedAt: '2026-07-19T00:01:00Z',
      },
      isError: false,
      isFetching: false,
      refetch: hooks.refetchSummary,
    });
    hooks.jobs.mockReset().mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 },
      isError: false,
      isFetching: false,
      refetch: hooks.refetchJobs,
    });
  });

  afterEach(() => cleanup());

  it('shows the durable queue-disabled semantics and disables monitoring after close', async () => {
    const { rerender } = render(
      <FileDatasetParseQueueDrawer open onClose={vi.fn()} />,
    );

    expect(await screen.findByText('统一解析队列已关闭')).toBeInTheDocument();
    expect(screen.getByText('Worker 将停止领取新任务，但已排队任务不会丢失，正在运行的任务继续完成。'))
      .toBeInTheDocument();
    expect(hooks.summary).toHaveBeenLastCalledWith(true);
    expect(hooks.jobs).toHaveBeenLastCalledWith(expect.objectContaining({ sort: '-queuedAt' }), true);

    rerender(<FileDatasetParseQueueDrawer open={false} onClose={vi.fn()} />);
    expect(hooks.summary).toHaveBeenLastCalledWith(false);
    expect(hooks.jobs).toHaveBeenLastCalledWith(expect.anything(), false);
  });

  it('shows stable job snapshots and table load details', async () => {
    hooks.jobs.mockReturnValue({
      data: {
        content: [{
          id: '00000000-0000-0000-0000-000000000001',
          type: 'TABLE_SOURCE_VALIDATE',
          fileDatasetId: '00000000-0000-0000-0000-000000000002',
          fileDatasetName: '年度道路',
          sourceFileId: '00000000-0000-0000-0000-000000000003',
          sourceFileName: 'roads-2026.csv',
          fileDatasetTableId: '00000000-0000-0000-0000-000000000004',
          tableName: '道路',
          loadMode: 'REPLACE_SOURCE',
          targetSourceId: '00000000-0000-0000-0000-000000000005',
          sourceName: 'roads-2026',
          sourceKey: 'FILE',
          status: 'FAILED',
          attemptCount: 1,
          maxAttempts: 3,
          availableAt: '2026-07-19T00:00:00Z',
          queuedAt: '2026-07-19T00:00:00Z',
          startedAt: '2026-07-19T00:00:01Z',
          completedAt: '2026-07-19T00:00:02Z',
          leaseOwner: null,
          leaseExpiresAt: null,
          lastHeartbeatAt: null,
          errorMessage: 'Schema 不一致',
          createdAt: '2026-07-19T00:00:00Z',
          updatedAt: '2026-07-19T00:00:02Z',
        }],
        totalElements: 1,
        totalPages: 1,
        page: 0,
        size: 20,
      },
      isError: false,
      isFetching: false,
      refetch: hooks.refetchJobs,
    });

    render(<FileDatasetParseQueueDrawer open onClose={vi.fn()} />);

    expect(await screen.findByText('年度道路')).toBeInTheDocument();
    expect(screen.getByText('道路')).toBeInTheDocument();
    expect(screen.getByText('roads-2026.csv')).toBeInTheDocument();
    expect(screen.getByText('替换来源')).toBeInTheDocument();
    expect(screen.getByText('roads-2026')).toBeInTheDocument();
    expect(screen.getByText('目标 00000000')).toBeInTheDocument();
    expect(screen.getByText('Schema 不一致')).toBeInTheDocument();
  });
});
