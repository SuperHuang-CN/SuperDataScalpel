import { parseNodeConfiguration } from './parser';
import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { createSpatialClipConfiguration } from './defaults';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialClip } from './summary';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialClipCanvasView } from './canvasView';

export const spatialClipSpec = createCanvasNodeSpec({
  parseConfiguration: parseNodeConfiguration,
  type: CanvasNodeType.SpatialClip,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '空间裁剪',
  description: '使用面状 Mask 裁剪来源 Geometry',
  searchKeywords: ['空间', 'geometry', 'clip', 'mask', '裁剪', '掩膜', '相交'],
  iconKey: CanvasNodeIconKey.SpatialClip,
  order: 90,
  canvasView: spatialClipCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 0,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialClipConfiguration,
  summarize: summarizeSpatialClip,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
