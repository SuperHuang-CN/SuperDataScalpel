import type { NodeMetadata } from '@antv/x6';

export const canvasNodePorts: NonNullable<NodeMetadata['ports']> = {
  groups: {
    in: {
      position: 'left',
      attrs: {
        circle: { r: 5, magnet: true, stroke: '#1677ff', strokeWidth: 2, fill: '#fff' },
      },
    },
    out: {
      position: 'right',
      attrs: {
        circle: { r: 5, magnet: true, stroke: '#1677ff', strokeWidth: 2, fill: '#fff' },
      },
    },
  },
  items: [
    { id: 'in', group: 'in' },
    { id: 'out', group: 'out' },
  ],
};
