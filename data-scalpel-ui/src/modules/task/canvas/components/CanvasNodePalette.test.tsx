import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState, type ComponentProps } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { canvasNodeTemplates } from '../canvasRegistry';
import { CanvasNodeCategory, CanvasNodeType } from '../canvasTypes';
import { CanvasNodePalette } from './CanvasNodePalette';

vi.mock('@antv/x6-react-shape', () => ({ register: vi.fn() }));

interface PaletteHarnessProps extends Partial<ComponentProps<typeof CanvasNodePalette>> {
  initialCategory?: CanvasNodeCategory | null;
}

const PaletteHarness = ({
  initialCategory = null,
  ...overrides
}: PaletteHarnessProps) => {
  const [activeCategory, setActiveCategory] = useState<CanvasNodeCategory | null>(initialCategory);
  return (
    <CanvasNodePalette
      templates={canvasNodeTemplates}
      executionMode="BATCH"
      activeCategory={activeCategory}
      inspectorDirty={false}
      onActiveCategoryChange={setActiveCategory}
      onAddNode={vi.fn()}
      onStartNodeDrag={vi.fn()}
      onBlockedDrag={vi.fn()}
      {...overrides}
    />
  );
};

describe('CanvasNodePalette', () => {
  afterEach(cleanup);

  it('starts collapsed and shows category counts for the current execution mode', () => {
    render(<PaletteHarness />);

    expect(screen.getByRole('button', { name: '输入 5' })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByRole('button', { name: '处理器 26' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '输出 3' })).toBeInTheDocument();
    expect(screen.queryByLabelText('输入节点')).not.toBeInTheDocument();
  });

  it('switches the shared panel between categories and closes on repeated click', async () => {
    const user = userEvent.setup();
    render(<PaletteHarness />);

    await user.click(screen.getByRole('button', { name: '输入 5' }));
    expect(screen.getByLabelText('输入节点')).toBeInTheDocument();
    expect(screen.getByText('JDBC 输入')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '处理器 26' }));
    expect(screen.queryByLabelText('输入节点')).not.toBeInTheDocument();
    expect(screen.getByLabelText('处理器节点')).toBeInTheDocument();
    expect(screen.getByText('Join 处理器')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '处理器 26' }));
    expect(screen.queryByLabelText('处理器节点')).not.toBeInTheDocument();
  });

  it('filters by label, node type, description and keywords', async () => {
    const user = userEvent.setup();
    render(<PaletteHarness initialCategory={CanvasNodeCategory.Input} />);
    const search = screen.getByRole('textbox', { name: '搜索输入节点' });

    await user.type(search, '物理表');
    expect(screen.getByText('JDBC 输入')).toBeInTheDocument();
    expect(screen.queryByText('模型输入')).not.toBeInTheDocument();

    await user.clear(search);
    await user.type(search, 'HTTP_API_INPUT');
    expect(screen.getByText('HTTP API 输入')).toBeInTheDocument();

    await user.clear(search);
    await user.type(search, '不存在');
    expect(screen.getByText('没有匹配的节点')).toBeInTheDocument();
  });

  it('filters by second-level group and searches across all groups', async () => {
    const user = userEvent.setup();
    render(<PaletteHarness initialCategory={CanvasNodeCategory.Processor} />);

    await user.click(screen.getByRole('button', { name: '字段处理' }));
    expect(screen.getByText('选择字段')).toBeInTheDocument();
    expect(screen.queryByText('Join 处理器')).not.toBeInTheDocument();

    await user.type(screen.getByRole('textbox', { name: '搜索处理器节点' }), 'join');
    expect(screen.getByText('Join 处理器')).toBeInTheDocument();
  });

  it('uses streaming labels and filters out batch-only nodes', async () => {
    const user = userEvent.setup();
    render(<PaletteHarness executionMode="STREAMING" />);

    expect(screen.getByRole('button', { name: '输入 3' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '输入 3' }));
    expect(screen.getByText('JDBC 静态维表')).toBeInTheDocument();
    expect(screen.getByText('JDBC 查询输入')).toBeInTheDocument();
    expect(screen.getByText('Kafka 输入')).toBeInTheDocument();
    expect(screen.queryByText('模型输入')).not.toBeInTheDocument();
  });

  it('adds a node from the explicit center-add action', async () => {
    const user = userEvent.setup();
    const onAddNode = vi.fn();
    render(
      <PaletteHarness
        initialCategory={CanvasNodeCategory.Input}
        onAddNode={onAddNode}
      />,
    );

    await user.click(screen.getByRole('button', { name: '添加JDBC 输入到画布中心' }));

    expect(onAddNode).toHaveBeenCalledTimes(1);
    expect(onAddNode.mock.calls[0]?.[0].type).toBe(CanvasNodeType.JdbcInput);
  });

  it('offers keyboard users the same center-add action from a node row', () => {
    const onAddNode = vi.fn();
    render(
      <PaletteHarness
        initialCategory={CanvasNodeCategory.Input}
        onAddNode={onAddNode}
      />,
    );

    fireEvent.keyDown(screen.getByRole('button', { name: '拖拽JDBC 输入到画布' }), { key: 'Enter' });

    expect(onAddNode).toHaveBeenCalledTimes(1);
    expect(onAddNode.mock.calls[0]?.[0].type).toBe(CanvasNodeType.JdbcInput);
  });

  it('starts drag normally and blocks it while the inspector is dirty', () => {
    const onStartNodeDrag = vi.fn();
    const onBlockedDrag = vi.fn();
    const { rerender } = render(
      <PaletteHarness
        initialCategory={CanvasNodeCategory.Processor}
        onStartNodeDrag={onStartNodeDrag}
        onBlockedDrag={onBlockedDrag}
      />,
    );
    const dragArea = screen.getByRole('button', { name: '拖拽Join 处理器到画布' });

    fireEvent.mouseDown(dragArea, { button: 0 });
    expect(onStartNodeDrag).toHaveBeenCalledTimes(1);

    rerender(
      <PaletteHarness
        initialCategory={CanvasNodeCategory.Processor}
        inspectorDirty
        onStartNodeDrag={onStartNodeDrag}
        onBlockedDrag={onBlockedDrag}
      />,
    );
    fireEvent.mouseDown(screen.getByRole('button', { name: '拖拽Join 处理器到画布' }), { button: 0 });

    expect(onStartNodeDrag).toHaveBeenCalledTimes(1);
    expect(onBlockedDrag).toHaveBeenCalledTimes(1);
  });

  it('closes on Escape and returns focus to the active category button', async () => {
    render(<PaletteHarness initialCategory={CanvasNodeCategory.Output} />);

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(screen.queryByLabelText('输出节点')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('button', { name: '输出 3' })).toHaveFocus());
  });
});
