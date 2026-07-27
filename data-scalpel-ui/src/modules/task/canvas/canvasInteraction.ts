interface CanvasWheelGesture {
  deltaX: number;
  deltaY: number;
}

export const canvasResizeOptions = {
  enabled: true,
  orthogonal: false,
  minWidth: 180,
  minHeight: 96,
} as const;

export const isCanvasZoomWheel = ({ deltaX, deltaY }: CanvasWheelGesture): boolean => (
  deltaY !== 0 && Math.abs(deltaY) >= Math.abs(deltaX)
);
