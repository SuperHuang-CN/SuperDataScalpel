import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createSpatialJoinConfiguration } from '../nodeDefaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialJoin } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';

export const spatialJoinSpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialJoin,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '空间连接',
  description: '使用受控空间谓词连接两张 Geometry 数据表',
  searchKeywords: ['空间', 'geometry', 'spatial join', '相交', '包含'],
  iconKey: CanvasNodeIconKey.SpatialJoin,
  order: 110,
  defaultSize: { width: 250, height: 120 },
  supportedModes: ['BATCH'],
  introducedInMinor: 20,
  graph: { minInputs: 2, maxInputs: 2, minOutputs: 1, maxOutputs: null },
  createDefaultConfiguration: createSpatialJoinConfiguration,
  summarize: summarizeSpatialJoin,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
