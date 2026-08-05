import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createSpatialAggregateConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialAggregate } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const spatialAggregateSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialAggregate,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '空间聚合',
  description: '按字段分组并汇总 Geometry',
  searchKeywords: [
    '空间', 'geometry', 'aggregate', 'union', 'intersection', 'collect', 'envelope',
    '聚合', '合并', '包络',
  ],
  iconKey: CanvasNodeIconKey.SpatialAggregate,
  order: 100,
  defaultSize: { width: 250, height: 120 },
  supportedModes: ['BATCH'],
  introducedInMinor: 23,
  graph: { minInputs: 1, maxInputs: 1, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createSpatialAggregateConfiguration,
  summarize: summarizeSpatialAggregate,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
