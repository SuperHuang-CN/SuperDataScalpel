import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DataTask } from '../model/task';

const mutation = { mutateAsync: vi.fn(), isPending: false };

vi.mock('../../model', () => ({
  useDataModel: () => ({ data: undefined }),
  useDataModels: () => ({ data: { content: [] } }),
}));

vi.mock('../../../shared/components/MonacoSqlEditor', () => ({
  MonacoSqlEditor: ({ onChange }: { onChange: (value: string) => void }) => (
    <button type="button" onClick={() => onChange('SELECT 1')}>修改 SQL</button>
  ),
}));

vi.mock('../hooks/useTasks', () => ({
  useTaskDefinition: () => ({
    isLoading: false,
    data: {
      taskId: '67cc5990-074c-4724-9420-6ddcc331c1c0',
      configured: false,
      version: 0,
      sql: null,
      inputs: [],
      output: null,
      resolvedDataSource: null,
      writeMode: 'APPEND',
      timeoutSeconds: 300,
      updatedAt: null,
    },
  }),
  useUpdateTaskDefinition: () => mutation,
  useValidateTaskDefinition: () => mutation,
}));

import { LocalSqlTaskDefinitionPanel } from './LocalSqlTaskDefinitionPanel';

const task: DataTask = {
  id: '67cc5990-074c-4724-9420-6ddcc331c1c0',
  name: '客户本地任务',
  directoryId: null,
  type: 'LOCAL_SQL',
  status: 'DRAFT',
  description: null,
  computeEngineId: null,
  computeEngineName: null,
  definitionConfigured: false,
  definitionVersion: null,
  outputModelId: null,
  outputModelName: null,
  createdAt: '2026-07-17T00:00:00Z',
  updatedAt: '2026-07-17T00:00:00Z',
};

describe('LocalSqlTaskDefinitionPanel', () => {
  beforeEach(() => {
    mutation.mutateAsync.mockReset();
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

  it('starts clean for an unconfigured definition and reports real edits to the detail page', async () => {
    const user = userEvent.setup();
    const onDirtyChange = vi.fn();
    const router = createMemoryRouter([{
      path: '/task/:taskId',
      element: (
        <LocalSqlTaskDefinitionPanel
          task={task}
          canUpdate
          canValidate
          onDirtyChange={onDirtyChange}
        />
      ),
    }], { initialEntries: [`/task/${task.id}?tab=definition`] });
    render(<RouterProvider router={router} />);

    expect(await screen.findByText('SQL 定义')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /保存定义/ })).toBeDisabled();
    expect(onDirtyChange).toHaveBeenLastCalledWith(false);

    await user.click(screen.getByRole('button', { name: '修改 SQL' }));
    expect(screen.getByText('有未保存修改')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /保存定义/ })).toBeEnabled();
    expect(onDirtyChange).toHaveBeenLastCalledWith(true);
  });

  it('shows an explicit error when a published local SQL definition is missing', async () => {
    const publishedTask: DataTask = { ...task, status: 'PUBLISHED' };
    const router = createMemoryRouter([{
      path: '/task/:taskId',
      element: (
        <LocalSqlTaskDefinitionPanel
          task={publishedTask}
          canUpdate
          canValidate
          onDirtyChange={vi.fn()}
        />
      ),
    }], { initialEntries: [`/task/${task.id}?tab=definition`] });
    render(<RouterProvider router={router} />);

    expect(await screen.findByText('任务已发布，但本地 SQL 定义缺失')).toBeInTheDocument();
    expect(screen.getByText('当前任务无法运行。请先停用任务，再重新配置本地 SQL 定义或删除任务。'))
      .toBeInTheDocument();
  });
});
