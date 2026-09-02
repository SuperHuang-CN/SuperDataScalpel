import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { defaultSparkExecutionResourcePolicy, type ComputeEngine } from '../model/computeEngine';

const hooks = vi.hoisted(() => ({
  useComputeEngines: vi.fn(),
  test: { mutateAsync: vi.fn(), isPending: false, variables: undefined },
  command: { mutateAsync: vi.fn(), isPending: false },
  deactivate: { mutateAsync: vi.fn(), isPending: false },
  detach: { mutateAsync: vi.fn(), isPending: false },
  remove: { mutateAsync: vi.fn(), isPending: false },
}));

vi.mock('../hooks/useComputeEngines', () => ({
  useComputeEngines: hooks.useComputeEngines,
  useTestComputeEngine: () => hooks.test,
  useComputeEngineCommand: () => hooks.command,
  useDeactivateComputeEngine: () => hooks.deactivate,
  useDetachComputeEngine: () => hooks.detach,
  useDeleteComputeEngine: () => hooks.remove,
}));

vi.mock('./ComputeEngineDrawer', () => ({
  ComputeEngineDrawer: () => null,
}));

import { ComputeEngineManagementPanel } from './ComputeEngineManagementPanel';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const engine = (
  registrationState: ComputeEngine['registrationState'] = 'ACTIVE',
  healthState: ComputeEngine['healthState'] = 'DOWN',
): ComputeEngine => ({
  id: '3f1f4e86-f468-4764-b8f7-865b0fbe3e29',
  name: '本地 Docker 计算引擎',
  description: '本地开发',
  dispatcherBaseUrl: 'http://127.0.0.1:18092',
  accessTokenConfigured: true,
  expectedBackendType: 'LOCAL_DOCKER',
  reportedBackendType: registrationState === 'DETACHED' ? null : 'LOCAL_DOCKER',
  registrationState,
  healthState,
  commandTopic: 'datascalpel.execution.command.local',
  runnerEventTopic: 'datascalpel.runner.event.local',
  adminEventTopic: 'datascalpel.execution.event',
  maxQueuedExecutions: 20,
  maxConcurrentSubmissions: 2,
  maxInFlightApplications: 2,
  resourcePolicy: defaultSparkExecutionResourcePolicy('LOCAL_DOCKER'),
  dispatcherInstanceId: registrationState === 'DETACHED' ? null : 'dispatcher-local',
  lastCheckAt: '2026-07-24T00:00:00Z',
  lastError: healthState === 'DOWN' ? '连接失败' : null,
  detachedAt: registrationState === 'DETACHED' ? '2026-07-24T00:00:00Z' : null,
  detachReason: registrationState === 'DETACHED' ? '原主机永久下线' : null,
  createdAt: '2026-07-19T00:00:00Z',
  updatedAt: '2026-07-24T00:00:00Z',
});

const renderPanel = (value: ComputeEngine) => {
  hooks.useComputeEngines.mockReturnValue({
    data: { content: [value], totalElements: 1, totalPages: 1, page: 0, size: 20 },
    isFetching: false,
    refetch: vi.fn(),
  });
  return render(
    <MemoryRouter>
      <ComputeEngineManagementPanel
        canCreate={false}
        canUpdate
        canDelete
        canTest={false}
        canManage
      />
    </MemoryRouter>,
  );
};

describe('ComputeEngineManagementPanel', () => {
  beforeEach(() => {
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
    hooks.useComputeEngines.mockReset();
    hooks.command.mutateAsync.mockReset().mockResolvedValue(undefined);
    hooks.deactivate.mutateAsync.mockReset().mockResolvedValue(undefined);
    hooks.detach.mutateAsync.mockReset().mockResolvedValue(undefined);
    hooks.remove.mutateAsync.mockReset().mockResolvedValue(undefined);
  });

  afterEach(() => cleanup());

  it('exposes safe, remote-force and offline detach operations for an unreachable active engine', async () => {
    const user = userEvent.setup();
    renderPanel(engine());

    expect(screen.queryByText('配置版本')).not.toBeInTheDocument();
    await user.click(await screen.findByLabelText('本地 Docker 计算引擎的更多操作'));

    expect(await screen.findByText('安全反注册')).toBeInTheDocument();
    expect(screen.getByText('强制反注册并取消任务')).toBeInTheDocument();
    expect(screen.getByText('离线解除绑定')).toBeInTheDocument();
  });

  it('uses the explicit remote force flag instead of detaching locally', async () => {
    const user = userEvent.setup();
    renderPanel(engine());

    await user.click(await screen.findByLabelText('本地 Docker 计算引擎的更多操作'));
    await user.click(await screen.findByText('强制反注册并取消任务'));
    await user.click((await screen.findAllByRole('button', { name: '强制反注册并取消任务' })).at(-1)!);

    await waitFor(() => expect(hooks.deactivate.mutateAsync).toHaveBeenCalledWith({
      id: '3f1f4e86-f468-4764-b8f7-865b0fbe3e29',
      force: true,
    }));
    expect(hooks.detach.mutateAsync).not.toHaveBeenCalled();
  });

  it('does not expose remote lifecycle actions for an error that never registered', async () => {
    const user = userEvent.setup();
    renderPanel({
      ...engine('ERROR'),
      dispatcherInstanceId: null,
      reportedBackendType: null,
    });

    await user.click(await screen.findByLabelText('本地 Docker 计算引擎的更多操作'));

    expect(await screen.findByText('注册并激活')).toBeInTheDocument();
    expect(screen.queryByText('安全反注册')).not.toBeInTheDocument();
    expect(screen.queryByText('强制反注册并取消任务')).not.toBeInTheDocument();
    expect(screen.queryByText('离线解除绑定')).not.toBeInTheDocument();
  });

  it('requires a reason and exact engine name before offline detach', async () => {
    const user = userEvent.setup();
    renderPanel(engine());

    await user.click(await screen.findByLabelText('本地 Docker 计算引擎的更多操作'));
    await user.click(await screen.findByText('离线解除绑定'));
    expect(await screen.findByText('这是 Dispatcher 不可达时的灾难恢复操作')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '确认离线解除绑定' }));
    expect(await screen.findByText('请输入解除绑定原因')).toBeInTheDocument();

    await user.type(screen.getByLabelText('解除绑定原因'), '原 Dispatcher 主机已永久下线');
    await user.type(screen.getByLabelText(/输入计算引擎名称/), '错误名称');
    await user.click(screen.getByRole('button', { name: '确认离线解除绑定' }));
    expect(await screen.findByText('输入内容与计算引擎名称不一致')).toBeInTheDocument();
    expect(hooks.detach.mutateAsync).not.toHaveBeenCalled();

    await user.clear(screen.getByLabelText(/输入计算引擎名称/));
    await user.type(screen.getByLabelText(/输入计算引擎名称/), '本地 Docker 计算引擎');
    await user.click(screen.getByRole('button', { name: '确认离线解除绑定' }));

    await waitFor(() => expect(hooks.detach.mutateAsync).toHaveBeenCalledWith({
      id: '3f1f4e86-f468-4764-b8f7-865b0fbe3e29',
      request: {
        confirmationName: '本地 Docker 计算引擎',
        reason: '原 Dispatcher 主机已永久下线',
      },
    }));
  });

  it('allows a detached engine to be registered again with a split-brain warning', async () => {
    const user = userEvent.setup();
    renderPanel(engine('DETACHED'));

    await user.click(await screen.findByLabelText('本地 Docker 计算引擎的更多操作'));
    await user.click(await screen.findByText('注册并激活'));

    expect(await screen.findByText(/请先确认原 Dispatcher 进程已经永久停止/)).toBeInTheDocument();
  });
});
