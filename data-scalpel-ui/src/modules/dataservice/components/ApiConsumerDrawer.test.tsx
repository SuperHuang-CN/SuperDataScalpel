import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApiConsumer } from '../model/apiConsumer';

const mutations = vi.hoisted(() => ({
  create: vi.fn(),
  update: vi.fn(),
}));

vi.mock('../hooks/useApiConsumers', () => ({
  useCreateApiConsumer: () => ({ isPending: false, mutateAsync: mutations.create }),
  useUpdateApiConsumer: () => ({ isPending: false, mutateAsync: mutations.update }),
}));

import { ApiConsumerDrawer } from './ApiConsumerDrawer';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const consumer: ApiConsumer = {
  id: 'consumer-1',
  code: 'customer.api',
  name: '客户系统',
  description: '初始说明',
  revision: 1,
  gatewayBindings: [{
    id: 'binding-1',
    provider: 'KONG',
    externalId: 'kong-1',
    syncedRevision: 1,
    syncStatus: 'SYNCED',
    lastError: null,
    operationStartedAt: null,
    lastSyncedAt: '2026-07-25T02:00:00Z',
    reconciliationStatus: 'NOT_CHECKED',
    reconciliationReason: null,
    reconciliationMessage: null,
    reconciliationOperationId: null,
    reconciliationStartedAt: null,
    lastReconciledAt: null,
    createdAt: '2026-07-25T02:00:00Z',
    updatedAt: '2026-07-25T02:00:00Z',
  }],
  createdAt: '2026-07-25T02:00:00Z',
  updatedAt: '2026-07-25T02:00:00Z',
};

describe('ApiConsumerDrawer', () => {
  beforeEach(() => {
    mutations.create.mockReset().mockResolvedValue(consumer);
    mutations.update.mockReset().mockResolvedValue({ ...consumer, name: '新客户系统', revision: 2 });
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

  it('creates a consumer with normalized code', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<ApiConsumerDrawer open consumer={null} onClose={onClose} />);

    await user.type(screen.getByLabelText('消费者编码'), 'customer.api');
    await user.type(screen.getByLabelText('名称'), ' 客户系统 ');
    await user.type(screen.getByLabelText('说明'), ' API 调用方 ');
    await user.click(screen.getByRole('button', { name: /创\s*建/ }));

    await waitFor(() => expect(mutations.create).toHaveBeenCalledWith({
      code: 'customer.api',
      name: '客户系统',
      description: 'API 调用方',
    }));
    expect(onClose).toHaveBeenCalled();
  });

  it('keeps code disabled and excludes it from updates', async () => {
    const user = userEvent.setup();
    render(<ApiConsumerDrawer open consumer={consumer} onClose={vi.fn()} />);

    expect(screen.getByLabelText('消费者编码')).toBeDisabled();
    const name = screen.getByLabelText('名称');
    await user.clear(name);
    await user.type(name, '新客户系统');
    await user.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(mutations.update).toHaveBeenCalledWith({
      id: 'consumer-1',
      request: {
        name: '新客户系统',
        description: '初始说明',
      },
    }));
    expect(mutations.update.mock.calls[0][0].request).not.toHaveProperty('code');
  });
});
