import type { NodeMetadata } from '@antv/x6';
import { CanvasNodeType } from './canvasTypes';

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
  switch (type) {
    case CanvasNodeType.ModelInput:
    case CanvasNodeType.JdbcInput:
    case CanvasNodeType.FileDatasetInput:
    case CanvasNodeType.HttpApiInput:
    case CanvasNodeType.KafkaInput:
      return { groups, items: [{ id: 'out', group: 'out' }] };
    case CanvasNodeType.Join:
    case CanvasNodeType.StreamJoin:
    case CanvasNodeType.Rename:
      return { groups, items: [{ id: 'in', group: 'in' }, { id: 'out', group: 'out' }] };
    case CanvasNodeType.ModelOutput:
    case CanvasNodeType.JdbcOutput:
    case CanvasNodeType.KafkaOutput:
      return { groups, items: [{ id: 'in', group: 'in' }] };
  }
};
