import { CanvasNodeCategory, type CanvasDefinition } from './canvasTypes';

export const defaultCanvasDefinition: CanvasDefinition = {
  nodes: [
    {
      id: 'input-1',
      shape: 'datascalpel-jdbc-input',
      label: '数据源输入',
      category: CanvasNodeCategory.Input,
      position: { x: 80, y: 160 },
      size: { width: 240, height: 120 },
      configuration: { datasourceName: '示例数据源' },
    },
    {
      id: 'filter-1',
      shape: 'datascalpel-data-filter',
      label: '数据过滤',
      category: CanvasNodeCategory.Processor,
      position: { x: 410, y: 160 },
      size: { width: 240, height: 120 },
      configuration: { condition: 'status = ACTIVE' },
    },
    {
      id: 'output-1',
      shape: 'datascalpel-model-output',
      label: '模型输出',
      category: CanvasNodeCategory.Output,
      position: { x: 740, y: 160 },
      size: { width: 240, height: 120 },
      configuration: { modelName: '示例模型' },
    },
  ],
  edges: [
    {
      id: 'edge-input-filter',
      source: { nodeId: 'input-1', portId: 'out' },
      target: { nodeId: 'filter-1', portId: 'in' },
    },
    {
      id: 'edge-filter-output',
      source: { nodeId: 'filter-1', portId: 'out' },
      target: { nodeId: 'output-1', portId: 'in' },
    },
  ],
};
