import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialSummarizeWithinConfiguration } from './defaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialSummarizeWithin } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialSummarizeWithinCanvasView } from './canvasView';

export const spatialSummarizeWithinSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SpatialSummarizeWithin,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '区域内汇总',
  description: '按区域表或规则格网汇总点、线、面，支持分组和时间切片',
  searchKeywords: ['空间', 'summarize within', 'aggregate points', '区域汇总', '区域统计'],
  iconKey: CanvasNodeIconKey.SpatialSummarizeWithin,
  order: 76,
  canvasView: spatialSummarizeWithinCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 12,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialSummarizeWithinConfiguration,
  summarize: summarizeSpatialSummarizeWithin,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
