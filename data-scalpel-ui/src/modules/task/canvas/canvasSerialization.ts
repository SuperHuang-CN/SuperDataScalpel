import type { Graph } from '@antv/x6';
import { canvasNodePorts } from './canvasPorts';
import type { CanvasDefinition, CanvasEdgeDefinition, CanvasNodeData, CanvasNodeDefinition } from './canvasTypes';

export const toCanvasDefinition = (graph: Graph): CanvasDefinition => ({
  nodes: graph.getNodes().map((node): CanvasNodeDefinition => {
    const position = node.getPosition();
    const size = node.getSize();
    const data = node.getData<CanvasNodeData>();

    return {
      id: node.id,
      shape: node.shape,
      position,
      size,
      label: data.label,
      category: data.category,
      configuration: data.configuration,
    };
  }),
  edges: graph.getEdges().flatMap((edge): CanvasEdgeDefinition[] => {
    const sourceNodeId = edge.getSourceCellId();
    const targetNodeId = edge.getTargetCellId();
    if (!sourceNodeId || !targetNodeId) return [];

    return [{
      id: edge.id,
      source: {
        nodeId: sourceNodeId,
        portId: edge.getSourcePortId() ?? undefined,
      },
      target: {
        nodeId: targetNodeId,
        portId: edge.getTargetPortId() ?? undefined,
      },
    }];
  }),
});

export const loadCanvasDefinition = (graph: Graph, definition: CanvasDefinition) => {
  definition.nodes.forEach((node) => {
    graph.addNode({
      id: node.id,
      shape: node.shape,
      x: node.position.x,
      y: node.position.y,
      width: node.size.width,
      height: node.size.height,
      data: {
        label: node.label,
        category: node.category,
        configuration: node.configuration,
      },
      ports: canvasNodePorts,
    });
  });

  definition.edges.forEach((edge) => {
    graph.addEdge({
      id: edge.id,
      source: { cell: edge.source.nodeId, port: edge.source.portId },
      target: { cell: edge.target.nodeId, port: edge.target.portId },
      attrs: {
        line: {
          stroke: '#1677ff',
          strokeWidth: 2,
          targetMarker: 'block',
        },
      },
    });
  });
};
