import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { defaultSparkExecutionResourcePolicy, type ComputeTargetDiscovery } from '../model/computeEngine';
import { ComputeEngineDiscoveryDrawer } from './ComputeEngineDiscoveryDrawer';

const api = vi.hoisted(() => ({ discover: vi.fn(), register: vi.fn() }));
vi.mock('../api/computeEngineApi', () => ({ discoverComputeTargets: api.discover, registerComputeTargets: api.register }));
const directory = (): ComputeTargetDiscovery => ({
  dispatcherInstanceId: 'instance-102', controlPlaneVersion: 3,
  messaging: { commandTopic: 'command.instance102', runnerEventTopic: 'runner.instance102', adminEventTopic: 'admin.events', runnerControlTopic: 'runner.instance102.control' },
  targets: ['local', 'kube', 'offline'].map((key) => ({ engine: null, target: {
    resourcePolicy: defaultSparkExecutionResourcePolicy(key === 'local' ? 'LOCAL_DOCKER' : 'KUBERNETES'),
    targetKey: key, name: key, backendType: key === 'local' ? 'LOCAL_DOCKER' : 'KUBERNETES',
    targetFingerprint: key.repeat(16), ready: key !== 'offline', issues: key === 'offline' ? ['无法连接'] : [],
    registeredEngineId: null, registrationState: 'UNREGISTERED',
    capabilities: { cancellation: true, logCollection: true, restartReconciliation: true, streaming: false, durableCheckpoint: false },
  } })),
});
beforeEach(() => { vi.clearAllMocks(); api.discover.mockResolvedValue(directory()); });
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
async function connect() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter><ComputeEngineDiscoveryDrawer onClose={() => {}} /></MemoryRouter>
  </QueryClientProvider>);
  fireEvent.change(screen.getByLabelText('Dispatcher 地址'), { target: { value: 'http://dispatcher.test' } });
  fireEvent.change(screen.getByLabelText('访问 Token'), { target: { value: 'test-token' } });
  fireEvent.click(screen.getByRole('button', { name: /连接并发现/ }));
  await screen.findByLabelText('local的引擎名称');
}
it('discovers without registering and invalidates stale targets when connection changes', async () => {
  await connect();
  expect(api.register).not.toHaveBeenCalled();
  expect(screen.getAllByRole('checkbox').filter((box) => box.hasAttribute('disabled'))).toHaveLength(1);
  fireEvent.change(screen.getByLabelText('访问 Token'), { target: { value: 'changed-token' } });
  await waitFor(() => expect(screen.queryByLabelText('local的引擎名称')).not.toBeInTheDocument());
  expect(screen.getByRole('button', { name: '注册所选目标' })).toBeDisabled();
});
it('registers selected targets with discovered identity and preserves partial failure feedback', async () => {
  api.register.mockResolvedValue({ items: [
    { targetKey: 'local', success: true, engine: null, error: null },
    { targetKey: 'kube', success: false, engine: null, error: '目标暂不可用' },
  ] });
  await connect();
  fireEvent.click(screen.getAllByRole('checkbox')[0]);
  fireEvent.click(screen.getByRole('button', { name: '注册所选目标' }));
  await waitFor(() => expect(api.register).toHaveBeenCalledTimes(1));
  const request = api.register.mock.calls[0][0];
  expect(request.dispatcherInstanceId).toBe('instance-102');
  expect(request.targets.map((target: { targetKey: string }) => target.targetKey)).toEqual(['local', 'kube']);
  await screen.findByText('本次成功 1 个，失败 1 个');
  await screen.findByText('目标暂不可用');
  await waitFor(() => expect(api.discover).toHaveBeenCalledTimes(2));
});

it('locks expanded resource fields while registration is pending', async () => {
  api.register.mockImplementation(() => new Promise(() => {}));
  await connect();
  fireEvent.click(screen.getAllByRole('button', { name: 'Expand row' })[0]);
  expect(screen.getByText('command.instance102')).toBeInTheDocument();
  expect(screen.getByText('runner.instance102')).toBeInTheDocument();
  expect(screen.getByText('admin.events')).toBeInTheDocument();
  expect(screen.getByLabelText('最大排队数')).not.toBeDisabled();
  fireEvent.click(screen.getAllByRole('checkbox')[1]);
  fireEvent.click(screen.getByRole('button', { name: '注册所选目标' }));
  await waitFor(() => expect(api.register).toHaveBeenCalledTimes(1));
  expect(screen.getByLabelText('最大排队数')).toBeDisabled();
  expect(screen.queryByRole('spinbutton', { name: 'Driver CPU（核）' })).not.toBeInTheDocument();
  expect(screen.getByText('任务默认资源（Dispatcher 只读）')).toBeInTheDocument();
});

it('preserves an unregistered target draft after a failed registration and refresh', async () => {
  api.register.mockResolvedValue({ items: [
    { targetKey: 'local', success: false, engine: null, error: '引擎名称已存在，请修改名称' },
  ] });
  await connect();
  fireEvent.change(screen.getByLabelText('local的引擎名称'), { target: { value: 'my-docker-engine' } });
  fireEvent.click(screen.getAllByRole('button', { name: 'Expand row' })[0]);
  fireEvent.change(screen.getByLabelText('最大排队数'), { target: { value: '37' } });
  fireEvent.click(screen.getAllByRole('checkbox')[1]);
  fireEvent.click(screen.getByRole('button', { name: '注册所选目标' }));
  await screen.findByText('本次成功 0 个，失败 1 个');
  await waitFor(() => expect(api.discover).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(screen.getByRole('button', { name: '注册所选目标' })).toBeEnabled());
  expect(screen.getByLabelText('local的引擎名称')).toHaveValue('my-docker-engine');
  expect(screen.getByLabelText('最大排队数')).toHaveValue('37');
  fireEvent.click(screen.getByRole('button', { name: '注册所选目标' }));
  await waitFor(() => expect(api.register).toHaveBeenCalledTimes(2));
  expect(api.register.mock.calls[1][0].targets[0]).toMatchObject({ name: 'my-docker-engine', maxQueuedExecutions: 37 });
});

it.each(['instance', 'fingerprint'])('does not copy drafts after the discovered %s changes', async (change) => {
  await connect();
  fireEvent.change(screen.getByLabelText('local的引擎名称'), { target: { value: 'old-target-name' } });
  const changed = directory();
  if (change === 'instance') changed.dispatcherInstanceId = 'replacement-instance';
  else changed.targets[0].target.targetFingerprint = 'c'.repeat(64);
  api.discover.mockResolvedValue(changed);
  fireEvent.click(screen.getByRole('button', { name: /连接并发现/ }));
  await waitFor(() => expect(api.discover).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(screen.getByLabelText('local的引擎名称')).toHaveValue('local'));
});

const checkingDirectory = (legacy = false): ComputeTargetDiscovery => {
  const result = directory();
  result.targets[1].target = { ...result.targets[1].target, ready: false,
    checking: legacy ? undefined : true, issues: ['目标就绪状态检查中，请稍后刷新'] };
  return result;
};

it.each([false, true])('automatically finishes checking and preserves edits (legacy=%s)', async (legacy) => {
  api.discover.mockResolvedValueOnce(checkingDirectory(legacy)).mockResolvedValue(directory());
  await connect();
  expect(screen.getByText('检查中')).toBeInTheDocument();
  expect(screen.queryByText('目标就绪状态检查中，请稍后刷新')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('local的引擎名称'), { target: { value: 'edited-local' } });
  fireEvent.click(screen.getAllByRole('checkbox')[1]);
  await waitFor(() => expect(screen.queryByText('检查中')).not.toBeInTheDocument(), { timeout: 5000 });
  expect(screen.getByLabelText('local的引擎名称')).toHaveValue('edited-local');
  expect(screen.getAllByRole('checkbox')[1]).toBeChecked();
  expect(screen.getAllByRole('checkbox').filter((box) => box.hasAttribute('disabled'))).toHaveLength(1);
  await act(async () => { await new Promise((resolve) => setTimeout(resolve, 2200)); });
  expect(api.discover).toHaveBeenCalledTimes(2);
}, 12000);

it('does not poll genuine failures', async () => {
  await connect();
  expect(screen.getByText('无法连接')).toBeInTheDocument();
  await act(async () => { await new Promise((resolve) => setTimeout(resolve, 2200)); });
  expect(api.discover).toHaveBeenCalledTimes(1);
});

it.each(['credentials', 'unmount'])('cancels background discovery and ignores late results after %s', async (mode) => {
  let finish: (value: ComputeTargetDiscovery) => void = () => {};
  api.discover.mockResolvedValueOnce(checkingDirectory()).mockImplementationOnce(() => new Promise<ComputeTargetDiscovery>((resolve) => { finish = resolve; }));
  await connect();
  await waitFor(() => expect(api.discover).toHaveBeenCalledTimes(2), { timeout: 5000 });
  const signal = api.discover.mock.calls[1][1] as AbortSignal;
  if (mode === 'credentials') fireEvent.change(screen.getByLabelText('访问 Token'), { target: { value: 'replacement-token' } });
  else cleanup();
  await waitFor(() => expect(signal.aborted).toBe(true));
  await act(async () => { finish(directory()); });
  expect(screen.queryByLabelText('local的引擎名称')).not.toBeInTheDocument();
}, 10000);

it('stops after the deadline and allows a fresh manual retry', async () => {
  let expire: (() => void) | undefined;
  const original = window.setTimeout.bind(window);
  vi.spyOn(window, 'setTimeout').mockImplementation(((callback: TimerHandler, delay?: number, ...args: unknown[]) => {
    if (delay && delay > 55_000 && delay <= 60_000 && typeof callback === 'function') expire = () => callback(...args);
    return original(callback, delay, ...args);
  }) as typeof window.setTimeout);
  api.discover.mockResolvedValue(checkingDirectory());
  await connect();
  expect(expire).toBeDefined();
  act(() => expire?.());
  expect(screen.getByText('检查超时')).toBeInTheDocument();
  await act(async () => { await new Promise((resolve) => setTimeout(resolve, 2200)); });
  expect(api.discover).toHaveBeenCalledTimes(1);
  api.discover.mockResolvedValue(directory());
  fireEvent.click(screen.getByRole('button', { name: /连接并发现/ }));
  await waitFor(() => expect(api.discover).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(screen.queryByText('检查超时')).not.toBeInTheDocument());
});
