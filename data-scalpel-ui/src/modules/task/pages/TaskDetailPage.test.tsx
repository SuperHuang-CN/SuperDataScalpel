import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DataTask } from '../model/task';

const state = vi.hoisted(() => ({
  taskType: 'SPARK_CANVAS' as DataTask['type'],
  taskStatus: 'DRAFT' as DataTask['status'],
  definitionConfigured: true,
  updateMutation: { mutateAsync: vi.fn(), isPending: false },
  deleteMutation: { mutateAsync: vi.fn(), isPending: false },
  publishMutation: { mutateAsync: vi.fn(), isPending: false },
  disableMutation: { mutateAsync: vi.fn(), isPending: false },
  enableMutation: { mutateAsync: vi.fn(), isPending: false },
  runMutation: { mutateAsync: vi.fn(), isPending: false },
  startStreamingMutation: { mutateAsync: vi.fn(), isPending: false },
  stopStreamingMutation: { mutateAsync: vi.fn(), isPending: false },
  streamingState: null as 'STARTING' | 'RUNNING' | 'STOPPING' | 'STOPPED' | 'FAILED' | null,
}));

vi.mock('../../directory', () => ({
  useDirectoryTree: () => ({
    data: [{
      id: '4fb0f08a-f11e-4cb0-9a2f-b9c4e56d1795',
      name: '每日同步',
      parentId: null,
      scope: 'TASK',
      sortOrder: 0,
      children: [],
    }],
  }),
}));

vi.mock('../../system', () => ({
  useCurrentUser: () => ({
    data: { permissions: ['directory.view', 'task.update', 'task.publish', 'task.execute', 'task.delete'] },
  }),
}));

vi.mock('../hooks/useTasks', () => ({
  useTask: () => ({
    isPending: false,
    isFetching: false,
    error: null,
    refetch: vi.fn(),
    data: {
      id: '67cc5990-074c-4724-9420-6ddcc331c1c0',
      name: state.taskType === 'LOCAL_SQL' ? '客户本地任务' : '客户编排',
      directoryId: '4fb0f08a-f11e-4cb0-9a2f-b9c4e56d1795',
      type: state.taskType,
      status: state.taskStatus,
      description: '同步客户订单数据',
      computeEngineId: state.taskType === 'LOCAL_SQL' ? null : 'f0222350-17e5-413b-b333-6674dc1f60d7',
      computeEngineName: state.taskType === 'LOCAL_SQL' ? null : '测试 Spark',
      definitionConfigured: state.definitionConfigured,
      definitionVersion: state.definitionConfigured ? 3 : null,
      outputModelId: state.taskType === 'LOCAL_SQL' ? '2fc7261c-a53a-46af-bc12-2c84aff91211' : null,
      outputModelName: state.taskType === 'LOCAL_SQL' ? '订单宽表' : null,
      createdAt: '2026-07-17T00:00:00Z',
      updatedAt: '2026-07-18T00:00:00Z',
    } satisfies DataTask,
  }),
  useUpdateTask: () => state.updateMutation,
  useDeleteTask: () => state.deleteMutation,
  useTaskCommand: (command: 'publish' | 'disable' | 'enable') => ({
    publish: state.publishMutation,
    disable: state.disableMutation,
    enable: state.enableMutation,
  })[command],
  useRunTask: () => state.runMutation,
  useTaskStreamingCommand: (command: 'start' | 'stop') => ({
    start: state.startStreamingMutation,
    stop: state.stopStreamingMutation,
  })[command],
  useTaskStreamingStatus: () => ({
    isPending: false,
    isFetching: false,
    isError: false,
    error: null,
    refetch: vi.fn(),
    data: {
      taskId: '67cc5990-074c-4724-9420-6ddcc331c1c0',
      deployment: state.streamingState === null ? null : {
        id: '03e0477c-fe97-40f5-b387-755364b471aa',
        definitionVersion: 3,
        computeEngineId: 'f0222350-17e5-413b-b333-6674dc1f60d7',
        currentRunId: '1b20ce62-6b2a-4b5f-8b09-3fe5f7aa3d80',
        checkpointKeyPrefix: 'tasks/streaming/deployment',
        desiredState: state.streamingState === 'STOPPED' ? 'STOPPED' : 'RUNNING',
        actualState: state.streamingState,
        applicationId: 'application-20260723-0001',
        trackingUrl: null,
        attempt: 1,
        startedAt: '2026-07-23T10:00:00Z',
        stopRequestedAt: null,
        stoppedAt: state.streamingState === 'STOPPED' ? '2026-07-23T10:10:00Z' : null,
        lastProgressAt: '2026-07-23T10:00:10Z',
        lastErrorAt: null,
        lastError: null,
        queries: [],
      },
    },
  }),
}));

vi.mock('../components/TaskDrawer', () => ({
  TaskDrawer: () => null,
}));

vi.mock('../components/LocalSqlTaskDefinitionPanel', () => ({
  LocalSqlTaskDefinitionPanel: ({ onDirtyChange }: { onDirtyChange: (dirty: boolean) => void }) => (
    <div>
      本地 SQL 定义面板
      <button type="button" onClick={() => onDirtyChange(true)}>标记定义已修改</button>
    </div>
  ),
}));

vi.mock('../components/CanvasTaskDefinitionPanel', () => ({
  CanvasTaskDefinitionPanel: ({ onDirtyChange }: { onDirtyChange: (dirty: boolean) => void }) => (
    <div>
      Canvas 定义面板
      <button type="button" onClick={() => onDirtyChange(true)}>标记定义已修改</button>
    </div>
  ),
}));

vi.mock('../components/TaskSchedulesPanel', () => ({
  TaskSchedulesPanel: () => <div>定时计划配置面板</div>,
}));

vi.mock('../components/TaskRunsPanel', () => ({
  TaskRunsPanel: () => <div>任务运行记录面板</div>,
}));

vi.mock('../components/TaskModelsPanel', () => ({
  TaskModelsPanel: () => <div>任务关联模型面板</div>,
}));

import { TaskDetailPage } from './TaskDetailPage';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const renderPage = (entry = '/task/67cc5990-074c-4724-9420-6ddcc331c1c0') => {
  const router = createMemoryRouter([
    { path: '/task/:taskId', element: <TaskDetailPage /> },
    { path: '/task', element: <div>任务列表</div> },
  ], { initialEntries: [entry] });
  render(<RouterProvider router={router} />);
};

describe('TaskDetailPage', () => {
  beforeEach(() => {
    state.taskType = 'SPARK_CANVAS';
    state.taskStatus = 'DRAFT';
    state.definitionConfigured = true;
    state.streamingState = null;
    [
      state.updateMutation,
      state.deleteMutation,
      state.publishMutation,
      state.disableMutation,
      state.enableMutation,
      state.runMutation,
      state.startStreamingMutation,
      state.stopStreamingMutation,
    ].forEach((mutation) => mutation.mutateAsync.mockReset().mockResolvedValue(undefined));
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

  it('opens on the common basic information tab', async () => {
    renderPage();

    expect((await screen.findAllByText('客户编排')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Spark 编排').length).toBeGreaterThan(0);
    expect(screen.getAllByText('每日同步').length).toBeGreaterThan(0);
    expect(screen.getAllByText('测试 Spark').length).toBeGreaterThan(0);
    expect(screen.getByText('同步客户订单数据')).toBeInTheDocument();
    expect(screen.queryByText('Canvas 定义面板')).not.toBeInTheDocument();
  });

  it('deep-links to the type-specific definition tab', async () => {
    renderPage('/task/67cc5990-074c-4724-9420-6ddcc331c1c0?tab=definition');

    expect(await screen.findByText('Canvas 定义面板')).toBeInTheDocument();
    expect(screen.queryByText('本地 SQL 定义面板')).not.toBeInTheDocument();
  });

  it('shows the real schedule panel for batch Spark Canvas tasks', async () => {
    renderPage('/task/67cc5990-074c-4724-9420-6ddcc331c1c0?tab=schedules');

    expect(await screen.findByText('定时计划配置面板')).toBeInTheDocument();
  });

  it('shows the real schedule panel for local SQL tasks', async () => {
    state.taskType = 'LOCAL_SQL';
    renderPage('/task/67cc5990-074c-4724-9420-6ddcc331c1c0?tab=schedules');

    expect(await screen.findByText('定时计划配置面板')).toBeInTheDocument();
  });

  it('switches to the task run records tab', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole('tab', { name: '运行记录' }));
    expect(await screen.findByText('任务运行记录面板')).toBeInTheDocument();
  });

  it('deep-links to the related models tab', async () => {
    renderPage('/task/67cc5990-074c-4724-9420-6ddcc331c1c0?tab=models');

    expect(await screen.findByText('任务关联模型面板')).toBeInTheDocument();
  });

  it('confirms before leaving a dirty definition tab', async () => {
    const user = userEvent.setup();
    renderPage('/task/67cc5990-074c-4724-9420-6ddcc331c1c0?tab=definition');

    await user.click(await screen.findByRole('button', { name: '标记定义已修改' }));
    await user.click(screen.getByRole('tab', { name: /关联模型/ }));
    let dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('放弃未保存的任务定义修改？');
    await user.click(within(dialog).getByRole('button', { name: '继续编辑' }));
    expect(await screen.findByText('Canvas 定义面板')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: /关联模型/ }));
    dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: '放弃修改' }));
    expect(await screen.findByText('任务关联模型面板')).toBeInTheDocument();
  });

  it('confirms a real Canvas run and opens the run records tab after submission', async () => {
    const user = userEvent.setup();
    state.taskStatus = 'PUBLISHED';
    renderPage();

    await user.click(await screen.findByRole('button', { name: /立即运行/ }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('运行 Spark Canvas 任务');
    expect(within(dialog).getByText(/OVERWRITE 会清空目标表后写入/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: '确认运行' }));

    await waitFor(() => expect(state.runMutation.mutateAsync).toHaveBeenCalledWith(
      '67cc5990-074c-4724-9420-6ddcc331c1c0',
    ));
    expect(await screen.findByText('任务运行记录面板')).toBeInTheDocument();
  });

  it('shows a published task definition failure, keeps disable available, and blocks running', async () => {
    const user = userEvent.setup();
    state.taskStatus = 'PUBLISHED';
    state.definitionConfigured = false;
    renderPage();

    expect(await screen.findByText('已发布任务的定义缺失')).toBeInTheDocument();
    expect(screen.getByText(/当前任务无法运行。请先停用任务/)).toBeInTheDocument();
    const disableButton = screen.getByRole('button', { name: /停用/ });
    const runButton = screen.getByRole('button', { name: /立即运行/ });
    expect(disableButton).toBeEnabled();
    expect(runButton).toBeDisabled();

    await user.click(disableButton);
    await waitFor(() => expect(state.disableMutation.mutateAsync).toHaveBeenCalledWith(
      '67cc5990-074c-4724-9420-6ddcc331c1c0',
    ));
    expect(state.runMutation.mutateAsync).not.toHaveBeenCalled();
  });

  it('allows a published task to be disabled while its definition has unsaved changes', async () => {
    const user = userEvent.setup();
    state.taskStatus = 'PUBLISHED';
    renderPage('/task/67cc5990-074c-4724-9420-6ddcc331c1c0?tab=definition');

    await user.click(await screen.findByRole('button', { name: '标记定义已修改' }));
    const disableButton = screen.getByRole('button', { name: /停用/ });
    expect(disableButton).toBeEnabled();
    expect(screen.getByRole('button', { name: /立即运行/ })).toBeDisabled();

    await user.click(disableButton);
    await waitFor(() => expect(state.disableMutation.mutateAsync).toHaveBeenCalled());
  });

  it('still blocks publishing a dirty draft definition', async () => {
    const user = userEvent.setup();
    renderPage('/task/67cc5990-074c-4724-9420-6ddcc331c1c0?tab=definition');

    await user.click(await screen.findByRole('button', { name: '标记定义已修改' }));
    expect(screen.getByRole('button', { name: /发布/ })).toBeDisabled();
  });

  it('starts a published streaming task and opens the realtime tab', async () => {
    const user = userEvent.setup();
    state.taskType = 'SPARK_STREAMING_CANVAS';
    state.taskStatus = 'PUBLISHED';
    renderPage();

    expect((await screen.findAllByText('Spark 实时编排')).length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: /立即运行/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /启动/ }));

    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveAccessibleName('启动 Spark 实时任务');
    expect(within(dialog).getByText(/多输出使用独立 Checkpoint/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: '确认启动' }));

    await waitFor(() => expect(state.startStreamingMutation.mutateAsync).toHaveBeenCalledWith(
      '67cc5990-074c-4724-9420-6ddcc331c1c0',
    ));
    expect(await screen.findByText('实时任务持续消费数据，不使用 Cron 定时计划')).toBeInTheDocument();
  });
});
