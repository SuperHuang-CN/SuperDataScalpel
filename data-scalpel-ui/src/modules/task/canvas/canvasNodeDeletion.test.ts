import type { Graph, Node } from '@antv/x6';
import { Modal } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  confirmCanvasNodeDeletion,
  registerCanvasNodeDeletionRequestHandler,
  requestCanvasNodeDeletion,
} from './canvasNodeDeletion';

vi.mock('antd', () => ({
  Modal: { confirm: vi.fn() },
}));

const node = (id: string, name: string): Node => ({
  id,
  model: null,
  getData: () => ({ name }),
}) as unknown as Node;

describe('canvas node deletion', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('confirms the node count and unique connected edge count before deletion', () => {
    const first = node('node-1', '订单输入');
    const second = node('node-2', '客户输入');
    const model = {};
    Object.defineProperty(first, 'model', { value: model });
    Object.defineProperty(second, 'model', { value: model });
    const removeCells = vi.fn();
    const graph = {
      model,
      getConnectedEdges: vi.fn((current: Node) => current.id === first.id
        ? [{ id: 'edge-1' }]
        : [{ id: 'edge-1' }, { id: 'edge-2' }]),
      removeCells,
    } as unknown as Graph;

    confirmCanvasNodeDeletion(graph, [first, second, first]);

    expect(Modal.confirm).toHaveBeenCalledWith(expect.objectContaining({
      title: '删除选中的 2 个节点？',
      content: expect.stringContaining('同时会删除 2 条关联连接线'),
      okButtonProps: { danger: true },
    }));
    const confirmation = vi.mocked(Modal.confirm).mock.calls[0][0] as { onOk: () => void };
    confirmation.onOk();
    expect(removeCells).toHaveBeenCalledWith([first, second]);
  });

  it('routes node deletion through the registered canvas coordinator', () => {
    const target = node('node-1', '订单输入');
    const model = {};
    Object.defineProperty(target, 'model', { value: model });
    const graph = {
      model,
      getConnectedEdges: vi.fn(() => []),
      removeCells: vi.fn(),
    } as unknown as Graph;
    const handler = vi.fn();
    const unregister = registerCanvasNodeDeletionRequestHandler(graph, handler);

    requestCanvasNodeDeletion(graph, [target]);

    expect(handler).toHaveBeenCalledWith([target]);
    expect(Modal.confirm).not.toHaveBeenCalled();

    unregister();
    requestCanvasNodeDeletion(graph, [target]);
    expect(Modal.confirm).toHaveBeenCalledWith(expect.objectContaining({
      title: '删除节点“订单输入”？',
    }));
  });
});
