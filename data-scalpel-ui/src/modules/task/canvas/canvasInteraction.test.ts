import { describe, expect, it } from 'vitest';
import { isCanvasZoomWheel } from './canvasInteraction';

describe('canvas wheel gestures', () => {
  it('uses vertical wheel gestures for zooming', () => {
    expect(isCanvasZoomWheel({ deltaX: 0, deltaY: 100 })).toBe(true);
    expect(isCanvasZoomWheel({ deltaX: 20, deltaY: -80 })).toBe(true);
  });

  it('keeps horizontal gestures available for panning', () => {
    expect(isCanvasZoomWheel({ deltaX: 100, deltaY: 0 })).toBe(false);
    expect(isCanvasZoomWheel({ deltaX: -80, deltaY: 20 })).toBe(false);
  });
});
