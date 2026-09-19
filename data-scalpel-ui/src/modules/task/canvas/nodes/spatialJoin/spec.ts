import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createSpatialJoinConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialJoin } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialJoinCanvasView } from './canvasView';

export const spatialJoinSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SpatialJoin,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '空间连接',
  description: '使用受控空间谓词连接两张 Geometry 数据表',
  searchKeywords: ['空间', 'geometry', 'spatial join', '相交', '包含'],
  iconKey: CanvasNodeIconKey.SpatialJoin,
  order: 110,
  canvasView: spatialJoinCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialJoinConfiguration,
  summarize: summarizeSpatialJoin,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
