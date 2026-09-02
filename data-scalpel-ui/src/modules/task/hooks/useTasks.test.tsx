import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { emptyCanvasDefinition } from '../canvas/defaultCanvas';
import type { CanvasTaskDefinition, TaskRun } from '../model/task';

const api = vi.hoisted(() => ({
  cancelTaskRun: vi.fn(),
  forceTerminateTaskRun: vi.fn(),
  updateCanvasTaskDefinition: vi.fn(),
}));

vi.mock('../../directory', () => ({
  invalidateDirectoryTree: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('../api/taskApi', () => ({
  cancelTaskRun: api.cancelTaskRun,
  forceTerminateTaskRun: api.forceTerminateTaskRun,
  createTask: vi.fn(),
  createTaskSchedule: vi.fn(),
  deleteTask: vi.fn(),
  deleteTaskSchedule: vi.fn(),
  executeTaskCommand: vi.fn(),
  executeTaskScheduleCommand: vi.fn(),
  fetchTask: vi.fn(),
  fetchTaskModelRelations: vi.fn(),
  fetchModelRelatedTasks: vi.fn(),
  fetchTaskRun: vi.fn(),
  fetchCanvasTaskDefinition: vi.fn(),
  fetchTaskDefinition: vi.fn(),
  fetchTaskRuns: vi.fn(),
  fetchTaskSchedules: vi.fn(),
  fetchTasks: vi.fn(),
  runTask: vi.fn(),
  updateTask: vi.fn(),
  updateCanvasTaskDefinition: api.updateCanvasTaskDefinition,
  updateTaskDefinition: vi.fn(),
  updateTaskSchedule: vi.fn(),
  validateTaskDefinition: vi.fn(),
}));

import { useCancelTaskRun, useUpdateCanvasTaskDefinition } from './useTasks';

const taskId = 'ba0eef7e-9dd1-49da-aea0-a89b92493179';
const definition = emptyCanvasDefinition();

describe('useUpdateCanvasTaskDefinition', () => {
  beforeEach(() => api.updateCanvasTaskDefinition.mockReset());

  it('refreshes the cached Canvas definition with the persisted version', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const previous: CanvasTaskDefinition = {
      taskId,
      configured: false,
      version: 0,
      loadStatus: 'UNCONFIGURED',
      schemaVersion: definition.schemaVersion,
      schemaMinorVersion: definition.schemaMinorVersion,
      definition,
      message: null,
      updatedAt: null,
    };
    const saved: CanvasTaskDefinition = {
      taskId,
      configured: true,
      version: 1,
      loadStatus: 'LOADED',
      schemaVersion: definition.schemaVersion,
      schemaMinorVersion: definition.schemaMinorVersion,
      definition,
      message: null,
      updatedAt: '2026-07-17T00:00:00Z',
    };
    queryClient.setQueryData(['tasks', taskId, 'canvas-definition'], previous);
    const taskRelationsKey = ['task-model-relations', 'task', taskId];
    const modelRelationsKey = ['task-model-relations', 'model', 'model-1'];
    queryClient.setQueryData(taskRelationsKey, { configured: false, models: [] });
    queryClient.setQueryData(modelRelationsKey, { content: [], totalElements: 0 });
    api.updateCanvasTaskDefinition.mockResolvedValue(saved);
    const wrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useUpdateCanvasTaskDefinition(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ id: taskId, definition });
    });

    expect(api.updateCanvasTaskDefinition).toHaveBeenCalledWith(taskId, definition);
    expect(queryClient.getQueryData(['tasks', taskId, 'canvas-definition'])).toEqual(saved);
    expect(queryClient.getQueryState(taskRelationsKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(modelRelationsKey)?.isInvalidated).toBe(true);
    queryClient.clear();
  });
});

describe('useCancelTaskRun', () => {
  beforeEach(() => api.cancelTaskRun.mockReset());

  it('updates the run detail cache and invalidates its task run list', async () => {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const run: TaskRun = {
      id: '3ad35b15-d35f-49d9-bef1-145788861717',
      taskId,
      scheduleId: null,
      streamingDeploymentId: null,
      taskType: 'SPARK_CANVAS',
      externalExecutionId: 'b689fe87-aee2-4188-bb68-0942311b4ddb',
      computeEngineId: '325b0c6f-7905-4755-9130-70886559ae2a',
      backendApplicationId: null,
      trackingUrl: null,
      attempt: 1,
      definitionVersion: 1,
      triggerType: 'MANUAL',
      executionMode: 'REAL',
      canvasTrialTargetNodeId: null,
      canvasTrialTableName: null,
      canvasTrialSelectedColumnCount: null,
      status: 'CANCEL_REQUESTED',
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
      updatedAt: '2026-07-21T01:01:00Z',
    };
    const listKey = ['tasks', taskId, 'runs', { page: 0, size: 20 }];
    queryClient.setQueryData(listKey, { content: [], totalElements: 0 });
    api.cancelTaskRun.mockResolvedValue(run);
    const wrapper = ({ children }: PropsWithChildren) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useCancelTaskRun(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync(run.id);
    });

    expect(api.cancelTaskRun.mock.calls[0]?.[0]).toBe(run.id);
    expect(queryClient.getQueryData(['task-runs', run.id])).toEqual(run);
    expect(queryClient.getQueryState(listKey)?.isInvalidated).toBe(true);
    queryClient.clear();
  });
});
