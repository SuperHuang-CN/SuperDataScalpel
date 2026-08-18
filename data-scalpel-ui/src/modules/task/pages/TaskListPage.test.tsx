import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DataTask } from '../model/task';

const hooks = vi.hoisted(() => ({
  useTasks: vi.fn(),
  mutation: { mutateAsync: vi.fn(), isPending: false },
}));

vi.mock('../../directory', () => ({
  DirectoryTreePanel: () => null,
  findDirectoryDescendantIds: () => [],
  useDirectoryTree: () => ({ data: [], isLoading: false }),
}));

vi.mock('../../system', () => ({
  useCurrentUser: () => ({ data: { permissions: ['task.view', 'task.publish', 'task.execute'] } }),
}));

vi.mock('../components/TaskDrawer', () => ({
  TaskDrawer: () => null,
}));

vi.mock('../hooks/useTasks', () => ({
  useTasks: hooks.useTasks,
  useCreateTask: () => hooks.mutation,
  useDeleteTask: () => hooks.mutation,
  useRunTask: () => hooks.mutation,
  useTaskCommand: () => hooks.mutation,
  useUpdateTask: () => hooks.mutation,
}));

import { TaskListPage } from './TaskListPage';

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const LocationProbe = () => {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}{location.search}</div>;
};

const sparkTask: DataTask = {
  id: '8cbd3775-735a-476d-ae64-af90336df2cc',
  name: '客户编排',
  directoryId: null,
  type: 'SPARK_CANVAS',
  status: 'DRAFT',
  description: null,
  computeEngineId: null,
  computeEngineName: null,
  definitionConfigured: true,
  definitionVersion: 3,
  outputModelId: null,
  outputModelName: null,
  createdAt: '2026-07-17T00:00:00Z',
  updatedAt: '2026-07-17T00:00:00Z',
};

describe('TaskListPage', () => {
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
    hooks.useTasks.mockReset().mockReturnValue({
      isLoading: false,
      data: { content: [sparkTask], totalElements: 1, totalPages: 1, page: 0, size: 20 },
    });
  });

  afterEach(() => cleanup());

  it('shows the Spark Canvas type icon, status and definition summary', async () => {
    render(<MemoryRouter><TaskListPage /></MemoryRouter>);

    expect(await screen.findByText('客户编排')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '任务类型：Spark 编排' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: '状态' })).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: '类型 / 状态' })).not.toBeInTheDocument();
    expect(screen.getByText('v3 · Canvas 定义')).toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: '编码' })).not.toBeInTheDocument();
  });

  it('offers lifecycle actions for Spark Canvas tasks', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><TaskListPage /></MemoryRouter>);

    await user.click(await screen.findByLabelText('更多任务操作：客户编排'));
    expect(await screen.findByText('发布')).toBeInTheDocument();
  });

  it('opens the common task detail page from the task name', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/task']}>
        <TaskListPage />
        <LocationProbe />
      </MemoryRouter>,
    );

    await user.click(await screen.findByRole('button', { name: '客户编排' }));
    expect(screen.getByTestId('location')).toHaveTextContent(`/task/${sparkTask.id}`);
  });

  it('adds the selected task type to the server-side search request', async () => {
    const user = userEvent.setup();
    render(<MemoryRouter><TaskListPage /></MemoryRouter>);

    await user.click((await screen.findAllByRole('combobox'))[1]);
    const sparkOptions = await screen.findAllByText('Spark 编排');
    await user.click(sparkOptions[sparkOptions.length - 1]);
    await user.click(screen.getByRole('button', { name: /查\s*询/ }));

    await waitFor(() => expect(hooks.useTasks).toHaveBeenLastCalledWith(expect.objectContaining({
      search: 'type:"SPARK_CANVAS"',
    })));
  }, 15_000);
});
