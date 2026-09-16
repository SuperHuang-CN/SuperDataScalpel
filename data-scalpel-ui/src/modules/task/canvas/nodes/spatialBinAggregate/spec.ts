import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialBinAggregateConfiguration } from './defaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialBinAggregate } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialBinAggregateCanvasView } from './canvasView';

export const spatialBinAggregateSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SpatialBinAggregate,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '空间格网聚合',
  description: '将点分配到方格、平面六边形或 H3 格网中，进行分组、时间切片和统计',
  searchKeywords: ['空间', '格网', 'bin', 'hexagon', 'H3', 'aggregate points', '聚合点'],
  iconKey: CanvasNodeIconKey.SpatialBinAggregate,
  order: 82,
  canvasView: spatialBinAggregateCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 18,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialBinAggregateConfiguration,
  summarize: summarizeSpatialBinAggregate,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
