import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApiConsumer } from '../model/apiConsumer';

const hooks = vi.hoisted(() => ({
  useApiConsumers: vi.fn(),
  sync: vi.fn(),
  reconcile: vi.fn(),
  remove: vi.fn(),
  refetch: vi.fn(),
}));

vi.mock('../hooks/useApiConsumers', () => ({
  useApiConsumers: hooks.useApiConsumers,
  useSyncApiConsumer: () => ({ isPending: false, variables: undefined, mutateAsync: hooks.sync }),
  useReconcileApiConsumerGateway: () => ({
    isPending: false,
    variables: undefined,
    mutateAsync: hooks.reconcile,
  }),
  useDeleteApiConsumer: () => ({ isPending: false, variables: undefined, mutateAsync: hooks.remove }),
}));

vi.mock('./ApiConsumerDrawer', () => ({
  ApiConsumerDrawer: () => null,
}));

vi.mock('./ApiConsumerAccessDrawer', () => ({
  ApiConsumerAccessDrawer: () => null,
}));

import { ApiConsumerManagementPanel } from './ApiConsumerManagementPanel';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const failedConsumer: ApiConsumer = {
  id: 'consumer-1',
  code: 'customer.api',
  name: '客户系统',
  description: null,
  revision: 2,
  gatewayBindings: [{
    id: 'binding-1',
    provider: 'KONG',
    externalId: 'kong-1',
    syncedRevision: 1,
    syncStatus: 'SYNC_FAILED',
    lastError: 'Kong 暂时不可用',
    operationStartedAt: null,
    lastSyncedAt: '2026-07-25T02:00:00Z',
    reconciliationStatus: 'DRIFTED',
    reconciliationReason: 'REMOTE_MISSING',
    reconciliationMessage: 'Kong Consumer 不存在',
    reconciliationOperationId: null,
    reconciliationStartedAt: null,
    lastReconciledAt: '2026-07-25T02:30:00Z',
    createdAt: '2026-07-25T02:00:00Z',
    updatedAt: '2026-07-25T02:00:00Z',
  }],
  createdAt: '2026-07-25T02:00:00Z',
  updatedAt: '2026-07-25T03:00:00Z',
};

describe('ApiConsumerManagementPanel', () => {
  beforeEach(() => {
    hooks.sync.mockReset().mockResolvedValue({
      ...failedConsumer,
      gatewayBindings: [{
        ...failedConsumer.gatewayBindings[0],
        syncedRevision: 2,
        syncStatus: 'SYNCED',
        lastError: null,
      }],
    });
    hooks.reconcile.mockReset().mockResolvedValue(failedConsumer);
    hooks.remove.mockReset().mockResolvedValue(undefined);
    hooks.refetch.mockReset();
    hooks.useApiConsumers.mockReturnValue({
      data: { content: [failedConsumer], totalElements: 1 },
      isFetching: false,
      isError: false,
      error: null,
      refetch: hooks.refetch,
    });
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });

  afterEach(() => cleanup());

  it('shows failed gateway status and supports row-level sync and delete', async () => {
    const user = userEvent.setup();
    render(<ApiConsumerManagementPanel canManage canConfigureAccess />);

    expect(screen.getByText('Kong · 同步失败')).toBeInTheDocument();
    expect(screen.getByText('对账 · 有漂移')).toBeInTheDocument();
    expect(screen.getByText('v1 / v2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '更多客户系统操作' }));
    await user.click(await screen.findByRole('menuitem', { name: /对账网关状态/ }));
    await waitFor(() => expect(hooks.reconcile).toHaveBeenCalledWith('consumer-1'));

    await user.click(screen.getByRole('button', { name: '更多客户系统操作' }));
    await user.click(await screen.findByRole('menuitem', { name: /同步到网关/ }));
    await waitFor(() => expect(hooks.sync).toHaveBeenCalledWith('consumer-1'));

    await user.click(screen.getByRole('button', { name: '更多客户系统操作' }));
    await user.click(await screen.findByRole('menuitem', { name: /删除/ }));
    await screen.findByText('确认从网关和 DataScalpel 删除“客户系统”吗？');
    const deleteButtons = screen.getAllByRole('button', { name: /删\s*除/ });
    await user.click(deleteButtons.at(-1) as HTMLButtonElement);
    await waitFor(() => expect(hooks.remove).toHaveBeenCalledWith('consumer-1'));
  });

  it('keeps a visible retryable alert when loading fails', async () => {
    const user = userEvent.setup();
    hooks.useApiConsumers.mockReturnValue({
      data: undefined,
      isFetching: false,
      isError: true,
      error: new Error('network unavailable'),
      refetch: hooks.refetch,
    });

    render(<ApiConsumerManagementPanel canManage={false} canConfigureAccess={false} />);

    expect(screen.getByText('消费者列表加载失败')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /重\s*试/ }));
    expect(hooks.refetch).toHaveBeenCalled();
  });
});
