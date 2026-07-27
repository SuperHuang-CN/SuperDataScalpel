import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DataTask } from '../model/task';
import { TaskDrawer } from './TaskDrawer';

vi.mock('../../computeengine', () => ({
  computeEngineRegistrationStateLabels: { ACTIVE: '已激活' },
  isComputeEngineSelectable: () => true,
  useComputeEngines: () => ({ data: { content: [] }, isFetching: false }),
}));

class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const sparkTask: DataTask = {
  id: '0f14ebbc-7b95-4f9a-b0d6-a15208ca31e6',
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

describe('TaskDrawer', () => {
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
  });

  afterEach(() => cleanup());

  it('creates LOCAL_SQL by default and allows choosing SPARK_CANVAS', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<TaskDrawer open task={null} directories={[]} onClose={vi.fn()} onSubmit={onSubmit} />);

    expect(screen.queryByLabelText('任务编码')).not.toBeInTheDocument();
    await user.type(await screen.findByLabelText('任务名称'), '每日订单');
    await user.click(screen.getByRole('button', { name: /创\s*建/ }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      name: '每日订单',
      type: 'LOCAL_SQL',
    })));

    onSubmit.mockClear();
    await user.click(screen.getByLabelText('任务类型'));
    await user.click(await screen.findByText('Spark 编排'));
    await user.click(screen.getByRole('button', { name: /创\s*建/ }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({ type: 'SPARK_CANVAS' })));
  }, 30_000);

  it('shows the existing type as read-only while editing', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    render(<TaskDrawer open task={sparkTask} directories={[]} onClose={vi.fn()} onSubmit={onSubmit} />);

    expect(await screen.findByLabelText('任务类型')).toBeDisabled();
    expect(screen.getByText('Spark 编排')).toBeInTheDocument();
    expect(screen.queryByLabelText('任务编码')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      name: '客户编排',
      type: 'SPARK_CANVAS',
    })));
  });
});
