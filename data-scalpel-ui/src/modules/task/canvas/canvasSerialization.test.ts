import { describe, expect, it, vi } from 'vitest';
import { exampleCanvasDefinition } from './defaultCanvas';
import { runtimeDataFromDefinition, styleCanvasEdge, toCanvasDefinition } from './canvasSerialization';

vi.mock('./canvasRegistry', () => ({
  canvasNodeTemplate: vi.fn(),
}));

describe('canvas X6 serialization boundary', () => {
  it('serializes graph-shaped runtime data without leaking shape, category or ports', () => {
    const definition = exampleCanvasDefinition();
    const nodes = definition.nodes.map((node) => ({
      id: node.id,
      getPosition: () => ({ x: node.layout.x, y: node.layout.y }),
      getSize: () => ({ width: node.layout.width, height: node.layout.height }),
      getData: <T,>() => runtimeDataFromDefinition(node) as T,
    }));
    const edges = definition.edges.map((edge) => ({
      id: edge.id,
      getSourceCellId: () => edge.sourceNodeId,
      getTargetCellId: () => edge.targetNodeId,
    }));

    const serialized = toCanvasDefinition({ getNodes: () => nodes, getEdges: () => edges });
    const json = JSON.stringify(serialized);

    expect(serialized).toEqual(definition);
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
});
