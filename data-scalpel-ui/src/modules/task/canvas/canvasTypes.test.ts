import { describe, expect, it } from 'vitest';
import { CanvasNodeCategory, canConnectCategories, createsCycle } from './canvasTypes';

describe('task canvas connection rules', () => {
  it('allows only input-to-processor/output and processor-to-processor/output flows', () => {
    expect(canConnectCategories(CanvasNodeCategory.Input, CanvasNodeCategory.Processor)).toBe(true);
    expect(canConnectCategories(CanvasNodeCategory.Processor, CanvasNodeCategory.Output)).toBe(true);
    expect(canConnectCategories(CanvasNodeCategory.Output, CanvasNodeCategory.Input)).toBe(false);
  });

  it('detects a directed cycle', () => {
    const edges = [{
      id: 'input-to-filter',
      source: { nodeId: 'input' },
      target: { nodeId: 'filter' },
    }];

    expect(createsCycle(edges, 'filter', 'input')).toBe(true);
    expect(createsCycle(edges, 'filter', 'output')).toBe(false);
  });
});
