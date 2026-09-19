import { describe, expect, it, vi } from 'vitest';
import { exampleCanvasDefinition } from './defaultCanvas';
import type { CanvasDefinition } from './canvasTypes';
import {
  canvasDefinitionFingerprint,
  runtimeDataFromDefinition,
  styleCanvasEdge,
  toCanvasDefinition,
} from './canvasSerialization';
import { canvasNodeRegistry } from './nodes/nodeRegistry';

vi.mock('./canvasRegistry', () => ({
  canvasNodeTemplate: vi.fn(),
}));

describe('canvas X6 serialization boundary', () => {
  it('serializes graph-shaped runtime data without leaking shape, category or ports', () => {
    const definition = exampleCanvasDefinition();
    const nodes = definition.nodes.map((node) => ({
      id: node.id,
      getPosition: () => ({ x: node.layout.x, y: node.layout.y }),
      getData: <T,>() => runtimeDataFromDefinition(node) as T,
    }));
    const edges = definition.edges.map((edge) => ({
      id: edge.id,
      getSourceCellId: () => edge.sourceNodeId,
      getTargetCellId: () => edge.targetNodeId,
    }));

    const serialized = toCanvasDefinition({ getNodes: () => nodes, getEdges: () => edges });
    const json = JSON.stringify(serialized);

    expect(canvasDefinitionFingerprint(serialized)).toBe(canvasDefinitionFingerprint(definition));
    serialized.nodes.forEach((node) => {
      const sourceNode = definition.nodes.find((candidate) => candidate.id === node.id);
      const size = canvasNodeRegistry.resolveSize(runtimeDataFromDefinition(node));
      expect(sourceNode).toBeDefined();
      expect(node.layout).toEqual({
        x: sourceNode?.layout.x,
        y: sourceNode?.layout.y,
        ...size,
      });
    });
    expect(json).not.toContain('shape');
    expect(json).not.toContain('category');
    expect(json).not.toContain('portId');
    expect(json).not.toContain('catalogName');
    expect(json).not.toContain('schemaName');
    expect(json).not.toContain('targetTable"');
  });

  it('adds the built-in remove tool at the middle of an edge', () => {
    const attr = vi.fn();
    const addTools = vi.fn();

    styleCanvasEdge({ attr, addTools });

    expect(attr).toHaveBeenCalledWith('line', expect.objectContaining({ stroke: '#1677ff' }));
    expect(addTools).toHaveBeenCalledWith([
      {
        name: 'button-remove',
        args: { distance: '50%' },
      },
    ]);
  });

  it('compares definitions by semantics instead of object and graph insertion order', () => {
    const definition = exampleCanvasDefinition();
    const reordered: CanvasDefinition = {
      edges: [...definition.edges].reverse(),
      nodes: [...definition.nodes].reverse(),
      schemaMinorVersion: definition.schemaMinorVersion,
      schemaVersion: definition.schemaVersion,
    };

    expect(canvasDefinitionFingerprint(reordered)).toBe(canvasDefinitionFingerprint(definition));
  });

  it('ignores stale persisted width and height when calculating the definition fingerprint', () => {
    const definition = exampleCanvasDefinition();
    const staleSizeDefinition = structuredClone(definition);
    staleSizeDefinition.nodes[0].layout.width = 240;
    staleSizeDefinition.nodes[0].layout.height = 120;
    definition.nodes[0].layout.width = 999;
    definition.nodes[0].layout.height = 888;

    expect(canvasDefinitionFingerprint(staleSizeDefinition))
      .toBe(canvasDefinitionFingerprint(definition));
  });
});
