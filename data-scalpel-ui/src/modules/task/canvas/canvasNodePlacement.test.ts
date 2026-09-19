import { describe, expect, it } from 'vitest';
import { canvasNodeCenterPlacement } from './canvasNodePlacement';

describe('canvasNodeCenterPlacement', () => {
  it('centers and snaps a node to the graph grid', () => {
    expect(canvasNodeCenterPlacement({
      viewportCenter: { x: 503, y: 347 },
      nodeSize: { width: 240, height: 120 },
      gridSize: 10,
      occupiedBounds: [],
    })).toEqual({ x: 380, y: 290 });
  });

  it('cascades nodes instead of placing them at the same origin', () => {
    expect(canvasNodeCenterPlacement({
      viewportCenter: { x: 500, y: 350 },
      nodeSize: { width: 240, height: 120 },
      gridSize: 10,
      occupiedBounds: [
        { x: 380, y: 290, width: 240, height: 120 },
        { x: 410, y: 320, width: 240, height: 120 },
      ],
    })).toEqual({ x: 440, y: 350 });
  });

  it('uses a safe grid size when the graph reports zero', () => {
    expect(canvasNodeCenterPlacement({
      viewportCenter: { x: 100, y: 100 },
      nodeSize: { width: 20, height: 20 },
      gridSize: 0,
      occupiedBounds: [],
    })).toEqual({ x: 90, y: 90 });
  });
});
