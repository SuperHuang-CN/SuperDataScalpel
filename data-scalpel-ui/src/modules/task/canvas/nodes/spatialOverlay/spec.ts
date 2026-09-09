import { CanvasNodeCategory, CanvasNodeType } from '../../canvasTypes';
import { collectNoMetadataReferences } from '../nodeMetadataReferences';
import { CanvasNodeGroup } from '../nodeGroups';
import { createSpatialOverlayConfiguration } from '../nodeDefaults';
import { CanvasNodeIconKey } from '../nodeSpec';
import { summarizeSpatialOverlay } from '../nodeSummaries';
import { createCanvasNodeSpec } from '../specFactory';
import { spatialOverlayCanvasView } from './canvasView';

export const spatialOverlaySpec = createCanvasNodeSpec({
  type: CanvasNodeType.SpatialOverlay,
  category: CanvasNodeCategory.Processor,
  group: CanvasNodeGroup.ProcessorSpatial,
  label: '空间叠加',
  description: '对两个有界图层执行相交、擦除或联合分析',
  searchKeywords: ['空间', 'overlay', 'intersect', 'erase', 'union', '叠加'],
  iconKey: CanvasNodeIconKey.SpatialOverlay,
  order: 77,
  canvasView: spatialOverlayCanvasView,
  supportedModes: ['BATCH'],
  introducedInMinor: 13,
  graph: { minInputs: 1, maxInputs: null, minOutputs: 0, maxOutputs: null },
  createDefaultConfiguration: createSpatialOverlayConfiguration,
  summarize: summarizeSpatialOverlay,
  collectMetadataReferences: collectNoMetadataReferences,
  loadInspector: () => import('./inspector'),
});
