interface CanvasWheelGesture {
  deltaX: number;
  deltaY: number;
}

export const isCanvasZoomWheel = ({ deltaX, deltaY }: CanvasWheelGesture): boolean => (
  deltaY !== 0 && Math.abs(deltaY) >= Math.abs(deltaX)
);
