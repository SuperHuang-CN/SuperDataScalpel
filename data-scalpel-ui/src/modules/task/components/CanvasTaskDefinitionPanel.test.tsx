import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useEffect, type ReactNode } from 'react';
import { createMemoryRouter, Link, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { CanvasDefinition } from '../canvas/canvasTypes';
import { emptyCanvasDefinition } from '../canvas/defaultCanvas';
import type { DataTask } from '../model/task';

const savedDefinition = emptyCanvasDefinition();
const changedDefinition: CanvasDefinition = {
  ...savedDefinition,
  nodes: [{
    id: '938705bd-26ed-4894-b882-c0b42503e3cb',
    type: 'JDBC_INPUT',
    name: '订单输入',
    layout: { x: 80, y: 80, width: 240, height: 120 },
    configuration: { dataSourceId: '', tableName: '' },
  }],
};
const saveDefinition = vi.fn();
const saveStreamingConfiguration = vi.fn();
const definitionState = vi.hoisted(() => ({ configured: false }));

vi.mock('../hooks/useTasks', () => ({
  useCanvasTaskDefinition: () => ({
    isLoading: false,
    data: {
      taskId: 'ed92188e-dde3-4503-a8ba-3ddbe79ca516',
      configured: definitionState.configured,
      version: definitionState.configured ? 1 : 0,
      definition: savedDefinition,
      updatedAt: null,
    },
  }),
  useUpdateCanvasTaskDefinition: () => ({
    isPending: false,
    mutateAsync: saveDefinition,
  }),
  useTaskStreamingConfiguration: () => ({
    data: undefined,
    isLoading: false,
  }),
  useUpdateTaskStreamingConfiguration: () => ({
    isPending: false,
    mutateAsync: saveStreamingConfiguration,
  }),
}));

vi.mock('../canvas/CanvasDesigner', () => ({
  CanvasDesigner: ({
    initialDefinition,
    onDefinitionChange,
    onInspectorDirtyChange,
    toolbarLeading,
    toolbarTrailing,
  }: {
    initialDefinition: CanvasDefinition;
    onDefinitionChange?: (definition: CanvasDefinition) => void;
    onInspectorDirtyChange?: (dirty: boolean) => void;
    toolbarLeading?: ReactNode;
    toolbarTrailing?: ReactNode;
  }) => {
    useEffect(() => onDefinitionChange?.(initialDefinition), [initialDefinition, onDefinitionChange]);
    return (
      <div>
        {toolbarLeading}
        <button type="button" onClick={() => onDefinitionChange?.(changedDefinition)}>修改画布</button>
        <button type="button" onClick={() => onInspectorDirtyChange?.(true)}>修改节点草稿</button>
        {toolbarTrailing}
      </div>
    );
  },
}));

import { CanvasTaskDefinitionPanel } from './CanvasTaskDefinitionPanel';

const baseTask: DataTask = {
  id: 'ed92188e-dde3-4503-a8ba-3ddbe79ca516',
  name: '客户编排',
  directoryId: null,
  type: 'SPARK_CANVAS',
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

const renderPanel = (task: DataTask = baseTask, onDirtyChange = vi.fn()) => {
  const router = createMemoryRouter([
    {
      path: '/task/:taskId',
      element: (
        <>
          <CanvasTaskDefinitionPanel task={task} canUpdate onDirtyChange={onDirtyChange} />
          <Link to="/other">离开定义页</Link>
        </>
      ),
    },
    { path: '/other', element: <div>其他页面</div> },
  ], { initialEntries: [`/task/${task.id}?tab=definition`] });
  render(<RouterProvider router={router} />);
};

describe('CanvasTaskDefinitionPanel', () => {
  beforeEach(() => {
    definitionState.configured = false;
    saveDefinition.mockReset().mockResolvedValue({ version: 1 });
    saveStreamingConfiguration.mockReset().mockResolvedValue({ triggerIntervalSeconds: 10 });
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

  it('shows the embedded definition toolbar without duplicating the detail header', async () => {
    renderPanel();

    expect(await screen.findByText('Canvas 定义')).toBeInTheDocument();
    expect(screen.getByText('未配置')).toBeInTheDocument();
    expect(screen.queryByText('客户编排')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /保存定义/ })).toBeDisabled();
  });

  it('saves an invalid draft definition and protects browser unload', async () => {
    const user = userEvent.setup();
    const onDirtyChange = vi.fn();
    renderPanel(baseTask, onDirtyChange);

    await user.click(await screen.findByRole('button', { name: '修改画布' }));
    expect(screen.getByText('有未保存修改')).toBeInTheDocument();
    expect(onDirtyChange).toHaveBeenLastCalledWith(true);
    await user.click(screen.getByRole('button', { name: /保存定义/ }));
    await waitFor(() => expect(saveDefinition).toHaveBeenCalledWith({
      id: baseTask.id,
      definition: changedDefinition,
    }));

    const unloadEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(unloadEvent);
    expect(unloadEvent.defaultPrevented).toBe(true);
  });

  it('does not expose save actions for a published task', async () => {
    definitionState.configured = true;
    renderPanel({ ...baseTask, status: 'PUBLISHED', definitionConfigured: true, definitionVersion: 1 });

    expect(await screen.findByText('任务已发布，当前定义不能保存。停用任务后才能修改定义。')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /保存定义/ })).not.toBeInTheDocument();
  });

  it('shows an explicit error when a published Canvas definition is missing', async () => {
    renderPanel({ ...baseTask, status: 'PUBLISHED' });

    expect(await screen.findByText('任务已发布，但 Canvas 定义缺失')).toBeInTheDocument();
    expect(screen.getByText('当前任务无法运行。请先停用任务，再重新配置 Canvas 定义或删除任务。'))
      .toBeInTheDocument();
  });
});
