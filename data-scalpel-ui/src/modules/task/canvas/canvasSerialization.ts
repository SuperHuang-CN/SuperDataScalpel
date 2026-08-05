import type { Edge, Graph } from '@antv/x6';
import { canvasNodePorts } from './canvasPorts';
import { canvasNodeTemplate } from './canvasRegistry';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CANVAS_SCHEMA_VERSION,
  type CanvasDefinition,
  type CanvasEdgeDefinition,
  type CanvasNodeDefinition,
  type CanvasNodeRuntimeData,
} from './canvasTypes';

const clone = <T,>(value: T): T => structuredClone(value);

export const runtimeDataFromDefinition = (node: CanvasNodeDefinition): CanvasNodeRuntimeData => {
  return {
    type: node.type,
    name: node.name,
    configuration: clone(node.configuration),
  } as CanvasNodeRuntimeData;
};

interface SerializableCanvasNode {
  id: string;
  getPosition: () => { x: number; y: number };
  getSize: () => { width: number; height: number };
  getData: <T>() => T;
}

interface SerializableCanvasEdge {
  id: string;
  getSourceCellId: () => string | null;
  getTargetCellId: () => string | null;
}

interface SerializableCanvasGraph {
  getNodes: () => SerializableCanvasNode[];
  getEdges: () => SerializableCanvasEdge[];
}

const nodeDefinition = (node: SerializableCanvasNode): CanvasNodeDefinition => {
  const position = node.getPosition();
  const size = node.getSize();
  const data = node.getData<CanvasNodeRuntimeData>();
  const common = {
    id: node.id,
    name: data.name,
    layout: { x: position.x, y: position.y, width: size.width, height: size.height },
  };

  return {
    ...common,
    type: data.type,
    configuration: clone(data.configuration),
  } as CanvasNodeDefinition;
};

export const toCanvasDefinition = (graph: SerializableCanvasGraph): CanvasDefinition => ({
  schemaVersion: CANVAS_SCHEMA_VERSION,
  schemaMinorVersion: CANVAS_SCHEMA_MINOR_VERSION,
  nodes: graph.getNodes().map(nodeDefinition),
  edges: graph.getEdges().flatMap((edge): CanvasEdgeDefinition[] => {
    const sourceNodeId = edge.getSourceCellId();
    const targetNodeId = edge.getTargetCellId();
    if (!sourceNodeId || !targetNodeId) return [];
    return [{ id: edge.id, sourceNodeId, targetNodeId }];
  }),
});

const edgeAttrs = {
  line: {
    stroke: '#1677ff',
    strokeWidth: 2,
    targetMarker: 'block',
  },
};

export const loadCanvasDefinition = (graph: Graph, definition: CanvasDefinition) => {
  definition.nodes.forEach((node) => {
    const template = canvasNodeTemplate(node.type);
    graph.addNode({
      id: node.id,
      shape: template.shape,
      x: node.layout.x,
      y: node.layout.y,
      width: node.layout.width,
      height: node.layout.height,
      data: runtimeDataFromDefinition(node),
      ports: canvasNodePorts(node.type),
    });
  });

  definition.edges.forEach((edge) => {
    graph.addEdge({
      id: edge.id,
      source: { cell: edge.sourceNodeId, port: 'out' },
      target: { cell: edge.targetNodeId, port: 'in' },
      attrs: edgeAttrs,
    });
  });
};

export const replaceCanvasDefinition = (graph: Graph, definition: CanvasDefinition) => {
  graph.clearCells();
  loadCanvasDefinition(graph, definition);
};

export const styleCanvasEdge = (edge: Pick<Edge, 'attr' | 'addTools'>) => {
  edge.attr('line', edgeAttrs.line);
  edge.addTools([
    {
      name: 'button-remove',
      args: { distance: '50%' },
    },
  ]);
};
