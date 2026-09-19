import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createAggregateConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeAggregate } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { aggregateCanvasView } from './canvasView';

export const aggregateSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.Aggregate,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorAggregate,
  label: '聚合',
  description: '按字段分组并计算统计指标',
  searchKeywords: ['aggregate', 'group by', '聚合', '分组', '统计', 'count', 'sum', 'avg'],
  iconKey: CanvasNodeIconKey.Aggregate,
  order: 10,
  canvasView: aggregateCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createAggregateConfiguration,
  summarize: summarizeAggregate,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
