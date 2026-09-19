import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialGroupByProximityConfiguration } from './defaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialGroupByProximity } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialGroupByProximityCanvasView } from './canvasView';

export const spatialGroupByProximitySpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SpatialGroupByProximity,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '按邻近分组',
  description: '按空间及可选时间、属性关系求传递连通组',
  searchKeywords: ['空间', '邻近', '连通', '分组', '传递闭包', 'group by proximity'],
  iconKey: CanvasNodeIconKey.SpatialGroupByProximity,
  order: 89,
  canvasView: spatialGroupByProximityCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 72,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialGroupByProximityConfiguration,
  summarize: summarizeSpatialGroupByProximity,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
