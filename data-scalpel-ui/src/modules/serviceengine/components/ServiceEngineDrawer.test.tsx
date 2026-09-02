import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ServiceEngine } from '../model/serviceEngine';

const mutations = vi.hoisted(() => ({
  create: vi.fn(),
  update: vi.fn(),
  testNew: vi.fn(),
  testStored: vi.fn(),
}));

vi.mock('../hooks/useServiceEngines', () => ({
  useCreateServiceEngine: () => ({ isPending: false, mutateAsync: mutations.create }),
  useUpdateServiceEngine: () => ({ isPending: false, mutateAsync: mutations.update }),
  useTestNewServiceEngine: () => ({ isPending: false, mutateAsync: mutations.testNew }),
  useTestServiceEngine: () => ({ isPending: false, mutateAsync: mutations.testStored }),
}));

import { ServiceEngineDrawer } from './ServiceEngineDrawer';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const storedEngine: ServiceEngine = {
  id: '95c0d668-7763-48e9-9f9c-782116d95d71',
  code: 'engine_a',
  name: 'Engine A',
  adminUrl: 'http://engine-a.test:8081',
  runtimeUrl: 'http://engine-a.test:8081',
  managementTokenConfigured: true,
  enabled: true,
  description: '测试 Engine',
  createdAt: '2026-07-27T00:00:00Z',
  updatedAt: '2026-07-27T00:00:00Z',
};

const renderDrawer = (engine: ServiceEngine | null) => render(
  <ServiceEngineDrawer
    open
    engine={engine}
    canTest
    onClose={vi.fn()}
  />,
);

describe('ServiceEngineDrawer', () => {
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
    mutations.create.mockReset().mockResolvedValue(undefined);
    mutations.update.mockReset().mockResolvedValue(undefined);
    mutations.testNew.mockReset().mockResolvedValue({
      code: 'engine_a',
      databaseTypes: ['POSTGRESQL'],
      elapsedMs: 12,
    });
    mutations.testStored.mockReset().mockResolvedValue({
      code: 'engine_a',
      databaseTypes: ['POSTGRESQL'],
      elapsedMs: 8,
    });
  });

  afterEach(() => cleanup());

  it('tests the current unsaved create form with its management token', async () => {
    const user = userEvent.setup();
    renderDrawer(null);

    expect(screen.queryByLabelText('Engine 编码')).not.toBeInTheDocument();
    await user.type(screen.getByLabelText('管理地址'), 'http://candidate.test:8081');
    await user.type(screen.getByLabelText('Management Token'), 'candidate-token');
    await user.click(screen.getByRole('button', { name: '测试连接' }));

    await waitFor(() => expect(mutations.testNew).toHaveBeenCalledWith({
      adminUrl: 'http://candidate.test:8081',
      managementToken: 'candidate-token',
    }));
    const testFeedback = await screen.findByLabelText('Service Engine 连接测试详情');
    expect(screen.getByText('连接测试成功')).toBeInTheDocument();
    await user.hover(testFeedback);
    expect(await screen.findByText('engine_a')).toBeInTheDocument();
    expect(screen.getByText('响应时间：12 ms')).toBeInTheDocument();
    expect(screen.getByText('支持数据库：POSTGRESQL')).toBeInTheDocument();
  });

  it('creates an engine without a client-supplied code', async () => {
    const user = userEvent.setup();
    renderDrawer(null);

    await user.type(screen.getByLabelText('名称'), 'Engine A');
    await user.type(screen.getByLabelText('管理地址'), 'http://candidate.test:8081');
    await user.type(screen.getByLabelText('运行地址'), 'http://public.test:8081');
    await user.type(screen.getByLabelText('Management Token'), 'candidate-token');
    await user.click(screen.getByRole('button', { name: /创\s*建/ }));

    await waitFor(() => expect(mutations.create).toHaveBeenCalledWith({
      name: 'Engine A',
      adminUrl: 'http://candidate.test:8081',
      runtimeUrl: 'http://public.test:8081',
      managementToken: 'candidate-token',
      enabled: true,
      description: undefined,
    }));
  });

  it('does not refill the stored token and lets the backend reuse it for testing', async () => {
    const user = userEvent.setup();
    renderDrawer(storedEngine);

    const tokenInput = await screen.findByLabelText('Management Token（留空保持不变）');
    expect(tokenInput).toHaveValue('');
    expect(screen.getByText('当前已配置 Management Token。')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '测试连接' }));
    await waitFor(() => expect(mutations.testStored).toHaveBeenCalledWith({
      id: storedEngine.id,
      request: {
        adminUrl: storedEngine.adminUrl,
        managementToken: undefined,
      },
    }));
    expect(await screen.findByText('连接测试成功')).toBeInTheDocument();

    await user.type(tokenInput, 'rotated-token');
    expect(screen.queryByText('连接测试成功')).not.toBeInTheDocument();
  });

  it('keeps an empty token out of the update request', async () => {
    const user = userEvent.setup();
    renderDrawer(storedEngine);

    await user.click(await screen.findByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(mutations.update).toHaveBeenCalledWith({
      id: storedEngine.id,
      request: {
        name: storedEngine.name,
        adminUrl: storedEngine.adminUrl,
        runtimeUrl: storedEngine.runtimeUrl,
        managementToken: undefined,
        enabled: true,
        description: storedEngine.description,
      },
    }));
  });
});
