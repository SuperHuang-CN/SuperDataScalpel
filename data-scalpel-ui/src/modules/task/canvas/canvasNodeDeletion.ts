import type { Graph, Node } from '@antv/x6';
import { Modal } from 'antd';
import type { CanvasNodeRuntimeData } from './canvasTypes';

type CanvasNodeDeletionRequestHandler = (nodes: readonly Node[]) => void;

const deletionRequestHandlers = new WeakMap<Graph, CanvasNodeDeletionRequestHandler>();

const uniqueNodes = (nodes: readonly Node[]): Node[] => [
  ...new Map(nodes.map((node) => [node.id, node])).values(),
];

export const confirmCanvasNodeDeletion = (graph: Graph, nodes: readonly Node[]) => {
  const targets = uniqueNodes(nodes).filter((node) => node.model === graph.model);
  if (targets.length === 0) return;

  const edgeCount = new Set(
    targets.flatMap((node) => graph.getConnectedEdges(node).map((edge) => edge.id)),
  ).size;
  const nodeName = targets[0].getData<CanvasNodeRuntimeData>().name;
  const title = targets.length === 1
    ? `删除节点“${nodeName}”？`
    : `删除选中的 ${targets.length} 个节点？`;
  const edgeNotice = edgeCount > 0
    ? `同时会删除 ${edgeCount} 条关联连接线。`
    : '';

  Modal.confirm({
    title,
    content: `节点配置将被删除。${edgeNotice}删除后仍可通过撤销恢复。`,
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: () => graph.removeCells(targets),
  });
};

export const registerCanvasNodeDeletionRequestHandler = (
  graph: Graph,
  handler: CanvasNodeDeletionRequestHandler,
): (() => void) => {
  deletionRequestHandlers.set(graph, handler);
  return () => {
    if (deletionRequestHandlers.get(graph) === handler) {
      deletionRequestHandlers.delete(graph);
    }
  };
};

export const requestCanvasNodeDeletion = (graph: Graph, nodes: readonly Node[]) => {
  const handler = deletionRequestHandlers.get(graph);
  if (handler) {
    handler(nodes);
    return;
  }
  confirmCanvasNodeDeletion(graph, nodes);
};
