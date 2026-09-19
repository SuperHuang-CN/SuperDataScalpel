import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../hooks/useTasks', () => ({
  useTaskModelRelations: () => ({
    isPending: false,
    isFetching: false,
    error: null,
    refetch: vi.fn(),
    data: {
      taskId: 'task-1',
      configured: true,
      definitionVersion: 2,
      models: [{
        modelId: 'model-1',
        modelCode: 'order_fact',
        modelName: '订单事实模型',
        modelStatus: 'DISABLED',
        physicalTableMode: 'MANAGED',
        schemaVersion: 4,
        roles: ['INPUT', 'OUTPUT'],
        locations: [
          {
            role: 'INPUT',
            referenceType: 'LOCAL_SQL_INPUT',
            ordinal: 1,
            nodeId: null,
            nodeName: null,
          },
          {
            role: 'OUTPUT',
            referenceType: 'CANVAS_NODE',
            ordinal: null,
            nodeId: '11111111-1111-1111-1111-111111111111',
            nodeName: '订单输出',
          },
        ],
      }],
    },
  }),
}));

import { TaskModelsPanel } from './TaskModelsPanel';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

describe('TaskModelsPanel', () => {
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

  it('renders saved relation metadata and opens an authorized model', async () => {
    const user = userEvent.setup();
    const router = createMemoryRouter([
      { path: '/task/:id', element: <TaskModelsPanel taskId="task-1" canViewModels /> },
      { path: '/model/:id', element: <div>模型详情已打开</div> },
    ], { initialEntries: ['/task/task-1'] });
    render(<RouterProvider router={router} />);

    expect(await screen.findByText('订单事实模型')).toBeInTheDocument();
    expect(screen.getByText('order_fact')).toBeInTheDocument();
    expect(screen.getByText('已停用')).toBeInTheDocument();
    expect(screen.getByText('输入 #1')).toBeInTheDocument();
    expect(screen.getByText('订单输出')).toBeInTheDocument();
    expect(screen.getByText('v4')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '订单事实模型' }));
    expect(await screen.findByText('模型详情已打开')).toBeInTheDocument();
  });

  it('does not expose navigation without model.view', async () => {
    const router = createMemoryRouter([
      { path: '/task/:id', element: <TaskModelsPanel taskId="task-1" canViewModels={false} /> },
    ], { initialEntries: ['/task/task-1'] });
    render(<RouterProvider router={router} />);

    expect(await screen.findByText('订单事实模型')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '订单事实模型' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '查看模型订单事实模型' })).not.toBeInTheDocument();
  });
});
