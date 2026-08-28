import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { TaskRun } from '../model/task';

const state = vi.hoisted(() => ({
  run: null as TaskRun | null,
  download: { mutateAsync: vi.fn(), isPending: false },
  downloadBlob: vi.fn(),
}));

vi.mock('../hooks/useTasks', () => ({
  useTaskRun: () => ({
    data: state.run,
    isPending: false,
    isError: false,
    isFetching: false,
    error: null,
    refetch: vi.fn(),
  }),
  useTaskRunLineage: () => ({
    data: null,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useTaskRunResultArtifact: () => ({
    data: null,
    isPending: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useDownloadQualityFailureSamples: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    variables: undefined,
  }),
  useDownloadTaskRunArtifact: () => state.download,
}));

vi.mock('../../../shared/browser/downloadBlob', () => ({
  downloadBlob: state.downloadBlob,
}));

import { TaskRunDetailDrawer } from './TaskRunDetailDrawer';

const run: TaskRun = {
  id: '3ad35b15-d35f-49d9-bef1-145788861717',
  taskId: 'a9b60b6c-21cc-486d-b4d6-8cf5b97282aa',
  scheduleId: null,
  streamingDeploymentId: null,
  taskType: 'SPARK_CANVAS',
  externalExecutionId: 'b689fe87-aee2-4188-bb68-0942311b4ddb',
  computeEngineId: '325b0c6f-7905-4755-9130-70886559ae2a',
  backendApplicationId: 'application-001',
  trackingUrl: 'https://spark.example.test/application-001',
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

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('TaskRunDetailDrawer', () => {
  beforeEach(() => {
    state.run = run;
    state.download.mutateAsync.mockReset().mockResolvedValue(new Blob(['result']));
    state.downloadBlob.mockReset();
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

  it('shows Spark execution routing, artifacts and cancellation', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    render(
      <TaskRunDetailDrawer
        open
        runId={run.id}
        canExecute
        cancelLoading={false}
        forceTerminateLoading={false}
        onClose={vi.fn()}
        onCancel={onCancel}
        onForceTerminate={vi.fn()}
      />,
    );

    expect(await screen.findByText('Spark 执行路由')).toBeInTheDocument();
    expect(screen.getByText('application-001')).toBeInTheDocument();
    expect(screen.getByText('真实执行')).toBeInTheDocument();
    expect(screen.getByLabelText('影响行数未知')).toHaveTextContent('—');
    expect(screen.getByRole('link', { name: '打开 Spark 跟踪页面' }))
      .toHaveAttribute('href', 'https://spark.example.test/application-001');

    await user.click(screen.getByRole('button', { name: '取消运行' }));
    expect(onCancel).toHaveBeenCalledWith(run);

    await user.click(screen.getByRole('button', { name: '下载执行结果' }));
    await waitFor(() => expect(state.download.mutateAsync).toHaveBeenCalledWith({
      runId: run.id,
      kind: 'result',
    }));
    expect(state.downloadBlob).toHaveBeenCalledWith(
      expect.any(Blob),
      `task-run-${run.id}-result.json`,
    );
  });

  it('does not render a non-http tracking URL as a link', async () => {
    state.run = { ...run, trackingUrl: 'javascript:alert(1)' };
    render(
      <TaskRunDetailDrawer
        open
        runId={run.id}
        canExecute={false}
        cancelLoading={false}
        forceTerminateLoading={false}
        onClose={vi.fn()}
        onCancel={vi.fn()}
        onForceTerminate={vi.fn()}
      />,
    );

    expect(await screen.findByText('Spark 执行路由')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '打开 Spark 跟踪页面' })).not.toBeInTheDocument();
  });

  it('shows the latest Spark JAR phase and metric snapshot', async () => {
    state.run = {
      ...run,
      taskType: 'SPARK_JAR',
      userJobObservability: {
        status: {
          phase: 'WRITE_OUTPUT',
          message: '正在写入结果',
          updatedAt: '2026-08-14T06:00:00Z',
        },
        metrics: [{
          name: 'orders.rows',
          kind: 'COUNTER',
          counterValue: 12,
          gaugeValue: null,
          count: null,
          lastDurationMillis: null,
          totalDurationMillis: null,
          maxDurationMillis: null,
        }],
      },
    };

    render(
      <TaskRunDetailDrawer
        open
        runId={run.id}
        canExecute={false}
        cancelLoading={false}
        forceTerminateLoading={false}
        onClose={vi.fn()}
        onCancel={vi.fn()}
        onForceTerminate={vi.fn()}
      />,
    );

    expect(await screen.findByText('用户作业观测（当前 Attempt）')).toBeInTheDocument();
    expect(screen.getByText('WRITE_OUTPUT')).toBeInTheDocument();
    expect(screen.getByText('正在写入结果')).toBeInTheDocument();
    expect(screen.getByText('orders.rows')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
  });

  it('shows structured Canvas execution diagnostics without a stack trace', async () => {
    state.run = {
      ...run,
      status: 'FAILED',
      endedAt: '2026-07-21T01:00:04Z',
      message: '数据源用户无权读取表 dev_source.sys_user',
      errorDetail: 'legacy detail must not be preferred',
      executionError: {
        code: 'JDBC_PERMISSION_DENIED',
        message: '数据源用户无权读取表 dev_source.sys_user',
        category: 'PERMISSION',
        retryable: false,
        nodeId: '65b9615d-b72a-42c1-8e4e-f28a660da082',
        nodeType: 'JDBC_INPUT',
        nodeName: '用户输入',
        phase: 'READ',
        sqlState: '42501',
        diagnosticId: 'a655249e-e786-4276-a842-80d2dc0c3cc4',
      },
    };

    render(
      <TaskRunDetailDrawer
        open
        runId={run.id}
        canExecute={false}
        cancelLoading={false}
        forceTerminateLoading={false}
        onClose={vi.fn()}
        onCancel={vi.fn()}
        onForceTerminate={vi.fn()}
      />,
    );

    expect(await screen.findByText('JDBC_PERMISSION_DENIED')).toBeInTheDocument();
    expect(screen.getByText('用户输入')).toBeInTheDocument();
    expect(screen.getByText('42501')).toBeInTheDocument();
    expect(screen.getByText('a655249e-e786-4276-a842-80d2dc0c3cc4')).toBeInTheDocument();
    expect(screen.queryByText('legacy detail must not be preferred')).not.toBeInTheDocument();
  });
});
