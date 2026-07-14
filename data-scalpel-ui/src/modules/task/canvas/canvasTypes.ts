export const CanvasNodeCategory = {
  Input: 'INPUT',
  Processor: 'PROCESSOR',
  Output: 'OUTPUT',
} as const;

export type CanvasNodeCategory = typeof CanvasNodeCategory[keyof typeof CanvasNodeCategory];

export interface CanvasNodeData {
  label: string;
  category: CanvasNodeCategory;
  configuration: Record<string, unknown>;
}

export interface CanvasNodeDefinition extends CanvasNodeData {
  id: string;
  shape: string;
  position: {
    x: number;
    y: number;
  };
  size: {
    width: number;
    height: number;
  };
}

export interface CanvasEdgeDefinition {
  id: string;
  source: {
    nodeId: string;
    portId?: string;
  };
  target: {
    nodeId: string;
    portId?: string;
  };
}

export interface CanvasDefinition {
  nodes: CanvasNodeDefinition[];
  edges: CanvasEdgeDefinition[];
}

export const canConnectCategories = (
  source: CanvasNodeCategory | undefined,
  target: CanvasNodeCategory | undefined,
) => {
  if (source === CanvasNodeCategory.Input) {
    return target === CanvasNodeCategory.Processor || target === CanvasNodeCategory.Output;
  }
  return source === CanvasNodeCategory.Processor
    && (target === CanvasNodeCategory.Processor || target === CanvasNodeCategory.Output);
};

export const createsCycle = (
  edges: CanvasEdgeDefinition[],
  sourceNodeId: string,
  targetNodeId: string,
) => {
  const adjacency = new Map<string, string[]>();
  [...edges, {
    id: '__candidate__',
    source: { nodeId: sourceNodeId },
    target: { nodeId: targetNodeId },
  }].forEach((edge) => {
    adjacency.set(edge.source.nodeId, [...(adjacency.get(edge.source.nodeId) ?? []), edge.target.nodeId]);
  });

  const visited = new Set<string>();
  const visit = (nodeId: string): boolean => {
    if (nodeId === sourceNodeId) return true;
    if (visited.has(nodeId)) return false;
    visited.add(nodeId);
    return (adjacency.get(nodeId) ?? []).some(visit);
  };

  return visit(targetNodeId);
};
