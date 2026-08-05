import type { NodeMetadata } from '@antv/x6';
import type { CanvasNodeType } from './canvasTypes';
import { canvasNodeRegistry } from './nodes/nodeRegistry';

const groups = {
  in: {
    position: 'left' as const,
    attrs: {
      circle: { r: 5, magnet: true, stroke: '#1677ff', strokeWidth: 2, fill: '#fff' },
    },
  },
  out: {
    position: 'right' as const,
    attrs: {
      circle: { r: 5, magnet: true, stroke: '#1677ff', strokeWidth: 2, fill: '#fff' },
    },
  },
};

export const canvasNodePorts = (type: CanvasNodeType): NonNullable<NodeMetadata['ports']> => {
  const capability = canvasNodeRegistry.require(type).graph;
  const items: Array<{ id: string; group: 'in' | 'out' }> = [];
  if (capability.maxInputs !== 0) items.push({ id: 'in', group: 'in' });
  if (capability.maxOutputs !== 0) items.push({ id: 'out', group: 'out' });
  return { groups, items };
};
