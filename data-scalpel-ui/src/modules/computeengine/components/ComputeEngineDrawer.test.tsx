import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../shared/api/http';
import { computeBackendTypeLabels, defaultSparkExecutionResourcePolicy, type ComputeEngine } from '../model/computeEngine';

const mutations = vi.hoisted(() => ({
  create: vi.fn(),
  update: vi.fn(),
  reconfigure: vi.fn(),
}));

vi.mock('../hooks/useComputeEngines', () => ({
  useCreateComputeEngine: () => ({ isPending: false, mutateAsync: mutations.create }),
  useUpdateComputeEngine: () => ({ isPending: false, mutateAsync: mutations.update }),
  useReconfigureComputeEngine: () => ({ isPending: false, mutateAsync: mutations.reconfigure }),
}));

import { ComputeEngineDrawer } from './ComputeEngineDrawer';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const engine = (registrationState: ComputeEngine['registrationState']): ComputeEngine => ({
  id: '3f1f4e86-f468-4764-b8f7-865b0fbe3e29',
  name: '本地 Docker 计算引擎',
  description: '本地开发',
  dispatcherBaseUrl: 'http://127.0.0.1:18092',
  accessTokenConfigured: true,
  expectedBackendType: 'LOCAL_DOCKER',
  reportedBackendType: 'LOCAL_DOCKER',
  registrationState,
  healthState: 'UP',
  commandTopic: 'datascalpel.execution.command.local',
  runnerEventTopic: 'datascalpel.runner.event.local',
  adminEventTopic: 'datascalpel.execution.event',
  maxQueuedExecutions: 20,
  maxConcurrentSubmissions: 2,
  maxInFlightApplications: 2,
  resourcePolicy: defaultSparkExecutionResourcePolicy('LOCAL_DOCKER'),
  dispatcherInstanceId: 'dispatcher-local',
  lastCheckAt: '2026-07-19T00:00:00Z',
  lastError: null,
  detachedAt: null,
  detachReason: null,
  createdAt: '2026-07-19T00:00:00Z',
  updatedAt: '2026-07-19T00:00:00Z',
});

const renderDrawer = (value: ComputeEngine | null, canUpdate = true, canManage = true) => render(
  <ComputeEngineDrawer
    open
    engine={value}
    canUpdate={canUpdate}
    canManage={canManage}
    onClose={vi.fn()}
  />,
);

describe('ComputeEngineDrawer', () => {
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
    mutations.reconfigure.mockReset().mockResolvedValue(undefined);
  });

  afterEach(() => cleanup());

  it.each(['LOCAL_DOCKER', 'YARN', 'KUBERNETES'] as const)('creates %s with a complete resource policy and edited Driver values', async (backend) => {
    const user = userEvent.setup();
    renderDrawer(null);
    fireEvent.change(await screen.findByLabelText('名称'), { target: { value: 'test-engine' } });
    fireEvent.change(screen.getByLabelText('Dispatcher 地址'), { target: { value: 'http://127.0.0.1:18092' } });
    await user.click(screen.getByRole('button', { name: '配置 Token' }));
    fireEvent.change(await screen.findByLabelText('访问 Token'), { target: { value: 'test-token' } });
    if (backend !== 'LOCAL_DOCKER') {
      await user.click(screen.getByRole('combobox', { name: '计算后端' }));
      await user.click(await screen.findByText(computeBackendTypeLabels[backend], { selector: '.ant-select-item-option-content' }));
    }
    if (backend === 'LOCAL_DOCKER') expect(screen.queryByLabelText('执行器数量')).not.toBeInTheDocument();
    const driverCores = screen.getAllByRole('spinbutton', { name: /驱动 CPU/ })[0]!;
    fireEvent.change(driverCores, { target: { value: '3' } });
    fireEvent.blur(driverCores);
    await user.click(screen.getByRole('button', { name: '创建引擎' }));

    const policy = defaultSparkExecutionResourcePolicy(backend);
    policy.defaults.driverCores = 3;
    await waitFor(() => expect(mutations.create).toHaveBeenCalledWith(expect.objectContaining({
      expectedBackendType: backend,
      resourcePolicy: policy,
    })));
  });

  it.each(['LOCAL_DOCKER', 'YARN', 'KUBERNETES'] as const)('preserves saved %s resource fields during ordinary editing', async (backend) => {
    const user = userEvent.setup();
    const value = engine('CREATED');
    value.expectedBackendType = backend;
    value.resourcePolicy = {
      defaults: { driverCores: 3, driverMemoryMiB: 6144, executorInstances: 3, executorCores: 2, executorMemoryMiB: 3072 },
      maximums: { driverCores: 10, driverMemoryMiB: 20480, executorInstances: 12, executorCores: 4, executorMemoryMiB: 8192 },
    };
    renderDrawer(value);
    await user.click(await screen.findByRole('button', { name: '保存修改' }));

    await waitFor(() => expect(mutations.update).toHaveBeenCalledWith({
      id: value.id,
      request: expect.objectContaining({ resourcePolicy: value.resourcePolicy }),
    }));
  });

  it('confirms and reconfigures an active engine instead of using ordinary update', async () => {
    const user = userEvent.setup();
    renderDrawer(engine('ACTIVE'));

    await user.click(await screen.findByRole('button', { name: '应用并重新注册' }));
    expect(await screen.findByText('将暂停“本地 Docker 计算引擎”的新任务准入，安全排空后自动反注册并重新注册。')).toBeInTheDocument();
    await user.click(screen.getAllByRole('button', { name: '应用并重新注册' }).at(-1)!);

    await waitFor(() => expect(mutations.reconfigure).toHaveBeenCalledWith(expect.objectContaining({
      id: '3f1f4e86-f468-4764-b8f7-865b0fbe3e29',
      request: expect.objectContaining({ resourcePolicy: defaultSparkExecutionResourcePolicy('LOCAL_DOCKER') }),
    })));
    expect(mutations.update).not.toHaveBeenCalled();
  });

  it('keeps the draft open and exposes retry after active executions cause conflict', async () => {
    const user = userEvent.setup();
    mutations.reconfigure.mockRejectedValue(new ApiError(
      '计算引擎仍有排队或活动任务，已进入排空状态；任务结束后请再次应用配置',
      409,
      { status: 409, code: 'BUSINESS_CONFLICT' },
    ));
    renderDrawer(engine('ACTIVE'));

    await user.click(await screen.findByRole('button', { name: '应用并重新注册' }));
    await screen.findByText('将暂停“本地 Docker 计算引擎”的新任务准入，安全排空后自动反注册并重新注册。');
    await user.click(screen.getAllByRole('button', { name: '应用并重新注册' }).at(-1)!);

    expect(await screen.findByText('计算引擎正在排空')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '再次应用并重新注册' })).toBeInTheDocument();
    expect(screen.getByLabelText('名称')).toHaveValue('本地 Docker 计算引擎');
  });

  it('shows registering configuration as read-only', async () => {
    renderDrawer(engine('REGISTERING'));

    expect(await screen.findByText('计算引擎正在注册，当前只能查看配置。')).toBeInTheDocument();
    expect(screen.getByLabelText('名称')).toBeDisabled();
    expect(screen.queryByRole('button', { name: '保存' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /关\s*闭/ })).toBeInTheDocument();
  });
});
