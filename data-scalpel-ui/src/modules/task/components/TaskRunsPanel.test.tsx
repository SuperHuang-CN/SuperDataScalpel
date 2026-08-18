import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useState } from 'react';
import type { DataTask, TaskRun } from '../model/task';

const state = vi.hoisted(() => ({
  cancel: { mutateAsync: vi.fn(), isPending: false },
}));

const run: TaskRun = {
  id: '3ad35b15-d35f-49d9-bef1-145788861717',
  taskId: 'a9b60b6c-21cc-486d-b4d6-8cf5b97282aa',
  scheduleId: null,
  streamingDeploymentId: null,
  taskType: 'SPARK_CANVAS',
  externalExecutionId: 'b689fe87-aee2-4188-bb68-0942311b4ddb',
  computeEngineId: '325b0c6f-7905-4755-9130-70886559ae2a',
  backendApplicationId: null,
  trackingUrl: null,
  attempt: 1,
  definitionVersion: 4,
  triggerType: 'MANUAL',
  executionMode: 'REAL',
  status: 'RUNNING',
  scheduledFireAt: null,
  queuedAt: '2026-07-21T01:00:00Z',
  startedAt: '2026-07-21T01:00:02Z',
  endedAt: null,
  deadlineAt: '2026-07-21T02:00:00Z',
  affectedRows: null,
  userJarFileName: null,
  userJarSha256: null,
  userJarSizeBytes: null,
  message: null,
  errorDetail: null,
  executionError: null,
  createdAt: '2026-07-21T01:00:00Z',
  updatedAt: '2026-07-21T01:00:02Z',
};

const task: DataTask = {
  id: run.taskId,
  name: '订单编排',
  directoryId: null,
  type: 'SPARK_CANVAS',
  status: 'PUBLISHED',
  description: null,
  computeEngineId: run.computeEngineId,
  computeEngineName: '本地 Spark',
  definitionConfigured: true,
  definitionVersion: 4,
  outputModelId: null,
  outputModelName: null,
  createdAt: '2026-07-21T00:00:00Z',
  updatedAt: '2026-07-21T00:00:00Z',
};

vi.mock('../hooks/useTasks', () => ({
  useTaskRuns: () => ({
    data: { content: [run], totalElements: 1, totalPages: 1, number: 0, size: 20 },
    isLoading: false,
    isFetching: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useCancelTaskRun: () => state.cancel,
}));

vi.mock('./TaskRunDetailDrawer', () => ({
  TaskRunDetailDrawer: ({ open }: { open: boolean }) => open ? <div>运行详情抽屉</div> : null,
}));

import { TaskRunsPanel } from './TaskRunsPanel';

const TestPanel = () => {
  const [detailRunId, setDetailRunId] = useState<string | null>(null);
  return (
    <TaskRunsPanel
      task={task}
      canExecute
      detailRunId={detailRunId}
      onDetailRunChange={setDetailRunId}
    />
  );
};

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('TaskRunsPanel', () => {
  beforeEach(() => {
    state.cancel.mutateAsync.mockReset().mockResolvedValue({ ...run, status: 'CANCEL_REQUESTED' });
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation(() => ({
        matches: false,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    });
  });

  afterEach(() => cleanup());

  it('opens run details and confirms cancellation for an active Canvas run', async () => {
    const user = userEvent.setup();
    render(<TestPanel />);

    expect(await screen.findByLabelText('影响行数未知')).toHaveTextContent('—');
    await user.click(await screen.findByLabelText(`查看运行 ${run.id}`));
    expect(screen.getByText('运行详情抽屉')).toBeInTheDocument();

    await user.click(screen.getByLabelText(`取消运行 ${run.id}`));
    const [title] = await screen.findAllByText('取消任务运行');
    const dialog = title.closest('[role="dialog"]');
    expect(dialog).not.toBeNull();
    await user.click(within(dialog as HTMLElement).getByRole('button', { name: '取消运行' }));

    await waitFor(() => expect(state.cancel.mutateAsync).toHaveBeenCalledWith(run.id));
  });
});
