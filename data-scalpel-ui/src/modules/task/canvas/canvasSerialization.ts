import type { Edge, Graph } from '@antv/x6';
import { canvasNodePorts } from './canvasPorts';
import { canvasNodeTemplate } from './canvasRegistry';
import { canvasNodeRegistry } from './nodes/nodeRegistry';
import {
  CANVAS_SCHEMA_MINOR_VERSION,
  CANVAS_SCHEMA_VERSION,
  type CanvasDefinition,
  type CanvasEdgeDefinition,
  type CanvasNodeDefinition,
  type CanvasNodeRuntimeData,
} from './canvasTypes';

const clone = <T,>(value: T): T => structuredClone(value);

interface CanvasDefinitionLoadOptions {
  readOnly?: boolean;
}

export const runtimeDataFromDefinition = (
  node: CanvasNodeDefinition,
  options: CanvasDefinitionLoadOptions = {},
): CanvasNodeRuntimeData => {
  return {
    type: node.type,
    name: node.name,
    configuration: clone(node.configuration),
    readOnly: options.readOnly,
  } as CanvasNodeRuntimeData;
};

interface SerializableCanvasNode {
  id: string;
  getPosition: () => { x: number; y: number };
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
  const data = node.getData<CanvasNodeRuntimeData>();
  const size = canvasNodeRegistry.resolveSize(data);
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

const canonicalJsonValue = (value: unknown): unknown => {
  if (Array.isArray(value)) return value.map(canonicalJsonValue);
  if (value === null || typeof value !== 'object') return value;
  return Object.fromEntries(
    Object.entries(value)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, child]) => [key, canonicalJsonValue(child)]),
  );
};

export const canvasDefinitionFingerprint = (
  definition: CanvasDefinition | null | undefined,
): string | null => {
  if (!definition) return null;
  const canonicalDefinition: CanvasDefinition = {
    ...definition,
    nodes: definition.nodes.map((node) => {
      const size = canvasNodeRegistry.resolveSize(runtimeDataFromDefinition(node));
      return { ...node, layout: { ...node.layout, width: size.width, height: size.height } };
    }).sort((left, right) => left.id.localeCompare(right.id)),
    edges: [...definition.edges].sort((left, right) => left.id.localeCompare(right.id)),
  };
  return JSON.stringify(canonicalJsonValue(canonicalDefinition));
};

const edgeAttrs = {
  line: {
    stroke: '#1677ff',
    strokeWidth: 2,
    targetMarker: 'block',
  },
};

export const canvasEdgeRouter = {
  name: 'manhattan',
  args: {
    padding: 24,
    step: 10,
  },
};

export const canvasEdgeConnector = {
  name: 'rounded',
  args: {
    radius: 8,
  },
};

export const loadCanvasDefinition = (
  graph: Graph,
  definition: CanvasDefinition,
  options: CanvasDefinitionLoadOptions = {},
) => {
  definition.nodes.forEach((node) => {
    const template = canvasNodeTemplate(node.type);
    const data = runtimeDataFromDefinition(node, options);
    const size = canvasNodeRegistry.resolveSize(data);
    graph.addNode({
      id: node.id,
      shape: template.shape,
      x: node.layout.x,
      y: node.layout.y,
      width: size.width,
      height: size.height,
      data,
      ports: canvasNodePorts(node.type),
    });
  });

  definition.edges.forEach((edge) => {
    graph.addEdge({
      id: edge.id,
      source: { cell: edge.sourceNodeId, port: 'out' },
      target: { cell: edge.targetNodeId, port: 'in' },
      router: canvasEdgeRouter,
      connector: canvasEdgeConnector,
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
