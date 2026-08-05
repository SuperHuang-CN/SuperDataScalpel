import { CanvasNodeCategory, type CanvasNodeCategory as CanvasNodeCategoryValue } from '../canvasTypes';

export const CanvasNodeGroup = {
  InputDatabase: 'input.database',
  InputFile: 'input.file',
  InputStream: 'input.stream',
  InputApi: 'input.api',
  InputModel: 'input.model',
  ProcessorRow: 'processor.row',
  ProcessorColumn: 'processor.column',
  ProcessorRelational: 'processor.relational',
  ProcessorSpatial: 'processor.spatial',
  ProcessorAggregate: 'processor.aggregate',
  ProcessorQuality: 'processor.quality',
  ProcessorStream: 'processor.stream',
  OutputDatabase: 'output.database',
  OutputFile: 'output.file',
  OutputStream: 'output.stream',
  OutputApi: 'output.api',
  OutputModel: 'output.model',
} as const;

export type CanvasNodeGroup = typeof CanvasNodeGroup[keyof typeof CanvasNodeGroup];

export interface CanvasNodeGroupDefinition {
  id: CanvasNodeGroup;
  category: CanvasNodeCategoryValue;
  label: string;
  order: number;
}

export const canvasNodeGroups: readonly CanvasNodeGroupDefinition[] = [
  { id: CanvasNodeGroup.InputDatabase, category: CanvasNodeCategory.Input, label: '数据库', order: 10 },
  { id: CanvasNodeGroup.InputFile, category: CanvasNodeCategory.Input, label: '文件与对象存储', order: 20 },
  { id: CanvasNodeGroup.InputStream, category: CanvasNodeCategory.Input, label: '消息流', order: 30 },
  { id: CanvasNodeGroup.InputApi, category: CanvasNodeCategory.Input, label: 'API', order: 40 },
  { id: CanvasNodeGroup.InputModel, category: CanvasNodeCategory.Input, label: '模型', order: 50 },
  { id: CanvasNodeGroup.ProcessorRow, category: CanvasNodeCategory.Processor, label: '行处理', order: 10 },
  { id: CanvasNodeGroup.ProcessorColumn, category: CanvasNodeCategory.Processor, label: '字段处理', order: 20 },
  { id: CanvasNodeGroup.ProcessorRelational, category: CanvasNodeCategory.Processor, label: '关联与集合', order: 30 },
  { id: CanvasNodeGroup.ProcessorSpatial, category: CanvasNodeCategory.Processor, label: '空间处理', order: 40 },
  { id: CanvasNodeGroup.ProcessorAggregate, category: CanvasNodeCategory.Processor, label: '聚合分析', order: 50 },
  { id: CanvasNodeGroup.ProcessorQuality, category: CanvasNodeCategory.Processor, label: '数据质量', order: 60 },
  { id: CanvasNodeGroup.ProcessorStream, category: CanvasNodeCategory.Processor, label: '流式处理', order: 70 },
  { id: CanvasNodeGroup.OutputDatabase, category: CanvasNodeCategory.Output, label: '数据库', order: 10 },
  { id: CanvasNodeGroup.OutputFile, category: CanvasNodeCategory.Output, label: '文件与对象存储', order: 20 },
  { id: CanvasNodeGroup.OutputStream, category: CanvasNodeCategory.Output, label: '消息流', order: 30 },
  { id: CanvasNodeGroup.OutputApi, category: CanvasNodeCategory.Output, label: 'API', order: 40 },
  { id: CanvasNodeGroup.OutputModel, category: CanvasNodeCategory.Output, label: '模型', order: 50 },
];

const groupById = new Map(canvasNodeGroups.map((group) => [group.id, group]));

export const canvasNodeGroup = (id: CanvasNodeGroup): CanvasNodeGroupDefinition => {
  const group = groupById.get(id);
  if (!group) throw new Error(`未知 Canvas 节点分组：${id}`);
  return group;
};
