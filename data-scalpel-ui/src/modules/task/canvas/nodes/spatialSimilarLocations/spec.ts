import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialSimilarLocationsConfiguration } from '../nodeDefaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialSimilarLocations } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialSimilarLocationsCanvasView } from './canvasView';

export const spatialSimilarLocationsSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialSimilarLocations,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '查找相似位置',
  description: '以参考位置的多个数值属性为目标，对候选位置进行相似或不相似排名',
  searchKeywords: ['空间', '相似位置', '属性值', '属性轮廓', 'find similar locations', 'similarity'],
  iconKey: CanvasNodeIconKey.SpatialSimilarLocations,
  order: 88,
  canvasView: spatialSimilarLocationsCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 75,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialSimilarLocationsConfiguration,
  summarize: summarizeSpatialSimilarLocations,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
