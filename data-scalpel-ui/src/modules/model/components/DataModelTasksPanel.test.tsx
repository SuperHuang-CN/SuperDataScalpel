import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ModelRelatedTask } from '../../task';

const query = vi.hoisted(() => ({
  refetch: vi.fn(),
  request: undefined as unknown,
}));

vi.mock('../../task', () => ({
  buildTaskSearch: (filters: Record<string, string | undefined>) => (
    Object.entries(filters)
      .filter((entry) => entry[1])
      .map(([key, value]) => `${key}:${value}`)
      .join(' AND ') || undefined
  ),
  taskStatusColors: { DRAFT: 'default', PUBLISHED: 'success', DISABLED: 'warning' },
  taskStatusLabels: { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' },
  taskTypeLabels: {
    LOCAL_SQL: '本地 SQL',
    SPARK_CANVAS: 'Spark 编排',
    SPARK_STREAMING_CANVAS: 'Spark 实时编排',
  },
  useModelRelatedTasks: (
    _modelId: string,
    _role: string | undefined,
    request: unknown,
  ) => {
    query.request = request;
    const task: ModelRelatedTask = {
      taskId: 'task-1',
      taskName: '订单汇总任务',
      taskType: 'SPARK_CANVAS',
      taskStatus: 'PUBLISHED',
      definitionVersion: 3,
      roles: ['INPUT', 'OUTPUT'],
      locations: [
        {
          role: 'INPUT',
          referenceType: 'CANVAS_NODE',
          ordinal: null,
          nodeId: '11111111-1111-1111-1111-111111111111',
          nodeName: '订单输入',
        },
        {
          role: 'OUTPUT',
          referenceType: 'CANVAS_NODE',
          ordinal: null,
          nodeId: '22222222-2222-2222-2222-222222222222',
          nodeName: '订单输出',
        },
      ],
      updatedAt: '2026-07-25T00:00:00Z',
    };
    return {
      isPending: false,
      isFetching: false,
      error: null,
      refetch: query.refetch,
      data: {
        content: [task],
        totalElements: 1,
        totalPages: 1,
        page: 0,
        size: 20,
      },
    };
  },
}));

import { DataModelTasksPanel } from './DataModelTasksPanel';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('DataModelTasksPanel', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((media: string) => ({
        matches: false,
        media,
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

  it('renders aggregated roles and Canvas locations and navigates to the task', async () => {
    const user = userEvent.setup();
    const router = createMemoryRouter([
      { path: '/model/:id', element: <DataModelTasksPanel modelId="model-1" /> },
      { path: '/task/:id', element: <div>任务详情已打开</div> },
    ], { initialEntries: ['/model/model-1'] });
    render(<RouterProvider router={router} />);

    expect(await screen.findByText('订单汇总任务')).toBeInTheDocument();
    expect(screen.getByText('任务输入')).toBeInTheDocument();
    expect(screen.getByText('任务输出')).toBeInTheDocument();
    expect(screen.getByText('订单输入')).toBeInTheDocument();
    expect(screen.getByText('订单输出')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '订单汇总任务' }));
    expect(await screen.findByText('任务详情已打开')).toBeInTheDocument();
  });

  it('submits filters as a server-side search request', async () => {
    const user = userEvent.setup();
    const router = createMemoryRouter([
      { path: '/model/:id', element: <DataModelTasksPanel modelId="model-1" /> },
    ], { initialEntries: ['/model/model-1'] });
    render(<RouterProvider router={router} />);

    await user.type(screen.getByPlaceholderText('筛选任务'), '订单');
    await user.click(screen.getByRole('button', { name: /查\s*询/ }));
    expect(query.request).toMatchObject({ search: 'keyword:订单', page: 0, size: 20 });

    await user.click(screen.getByRole('columnheader', { name: /任务名称/ }));
    expect(query.request).toMatchObject({ sort: 'name', page: 0, size: 20 });
  });
});
