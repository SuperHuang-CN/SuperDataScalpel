import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createAggregateConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeAggregate } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const aggregateSpec = createCanvasNodeSpec({
  type: CanvasNodeType.Aggregate,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorAggregate,
  label: '聚合',
  description: '按字段分组并计算统计指标',
  searchKeywords: ['aggregate', 'group by', '聚合', '分组', '统计', 'count', 'sum', 'avg'],
  iconKey: CanvasNodeIconKey.Aggregate,
  order: 10,
  defaultSize: { width: 240, height: 120 },
  supportedModes: ['BATCH'],
  introducedInMinor: 11,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createAggregateConfiguration,
  summarize: summarizeAggregate,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
