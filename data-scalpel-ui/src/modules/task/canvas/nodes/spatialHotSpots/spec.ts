import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialHotSpotsConfiguration } from './defaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialHotSpots } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialHotSpotsCanvasView } from './canvasView';

export const spatialHotSpotsSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SpatialHotSpots,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '寻找热点',
  description: '按投影方格和固定距离邻域计算 Getis-Ord Gi* 热点与冷点',
  searchKeywords: ['空间', '热点', '冷点', 'hot spots', 'getis ord', 'gi*', '显著性', 'fdr'],
  iconKey: CanvasNodeIconKey.SpatialHotSpots,
  order: 86,
  canvasView: spatialHotSpotsCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 69,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialHotSpotsConfiguration,
  summarize: summarizeSpatialHotSpots,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
