import type { RectangleLike } from '@antv/x6';

export interface CanvasPoint {
  x: number;
  y: number;
}

export interface CanvasNodeSize {
  width: number;
  height: number;
}

interface CanvasNodePlacementOptions {
  viewportCenter: CanvasPoint;
  nodeSize: CanvasNodeSize;
  gridSize: number;
  occupiedBounds: readonly RectangleLike[];
}

const snapToGrid = (value: number, gridSize: number) => (
  Math.round(value / gridSize) * gridSize
);

const hasSameOrigin = (
  bounds: readonly RectangleLike[],
  position: CanvasPoint,
  tolerance: number,
) => bounds.some((bound) => (
  Math.abs(bound.x - position.x) < tolerance
  && Math.abs(bound.y - position.y) < tolerance
));

export const canvasNodeCenterPlacement = ({
  viewportCenter,
  nodeSize,
  gridSize,
  occupiedBounds,
}: CanvasNodePlacementOptions): CanvasPoint => {
  const safeGridSize = Math.max(1, gridSize);
  const base = {
    x: snapToGrid(viewportCenter.x - nodeSize.width / 2, safeGridSize),
    y: snapToGrid(viewportCenter.y - nodeSize.height / 2, safeGridSize),
  };
  const cascadeStep = safeGridSize * 3;

  for (let offsetIndex = 0; offsetIndex < 20; offsetIndex += 1) {
    const position = {
      x: base.x + cascadeStep * offsetIndex,
      y: base.y + cascadeStep * offsetIndex,
    };
    if (!hasSameOrigin(occupiedBounds, position, safeGridSize / 2)) return position;
  }

  return base;
};
