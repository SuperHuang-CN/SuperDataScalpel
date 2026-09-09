import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialNearestConfiguration } from '../nodeDefaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialNearest } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialNearestCanvasView } from './canvasView';

export const spatialNearestSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialNearest,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '空间最近邻',
  description: '为每个来源要素匹配最近的候选要素',
  searchKeywords: ['空间', 'nearest', 'knn', '最近邻', '距离', '候选'],
  iconKey: CanvasNodeIconKey.SpatialNearest,
  order: 75,
  canvasView: spatialNearestCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 11,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialNearestConfiguration,
  summarize: summarizeSpatialNearest,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
